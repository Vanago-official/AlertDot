package com.vanago.alertdot.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vanago.alertdot.R
import com.vanago.alertdot.domain.SoundRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/**
 * Plays alert sounds on the alarm stream (USAGE_ALARM), so they are heard with the ringer muted.
 * Must be called on the main thread.
 */
class AlertSoundPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null
    private var savedFilter: Int? = null
    private val stopRunnable = Runnable { stop() }

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun play(rule: SoundRule, ignoreDnd: Boolean) {
        stop()
        _playing.value = true

        if (ignoreDnd && nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        ) {
            savedFilter = nm.currentInterruptionFilter
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }

        val uri = resolve(rule.soundUri)
        if (uri != null && rule.volume > 0) {
            setAlarmVolume(rule.volume)
            startPlayer(uri, rule.loop) || startPlayer(builtinUri(SoundRule.builtinFor(rule.level)), rule.loop)
        }
        if (rule.vibrate) vibrate(rule.loop)

        if (rule.loop) {
            showStopNotification()
            handler.postDelayed(stopRunnable, MAX_LOOP_MS)
        } else if (player == null) {
            // Vibration only: release DND / state after the pattern ends.
            handler.postDelayed(stopRunnable, VIBRATION_MS)
        } else {
            // One-shot sound normally ends via onCompletion; this is a safety net so a player that never
            // completes cannot keep the MediaPlayer, the raised alarm volume and the DND change forever.
            handler.postDelayed(stopRunnable, MAX_LOOP_MS)
        }
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator.cancel()
        savedVolume?.let { runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) } }
        savedVolume = null
        savedFilter?.let { runCatching { nm.setInterruptionFilter(it) } }
        savedFilter = null
        NotificationManagerCompat.from(context).cancel(Notifications.ID_PLAYING)
        _playing.value = false
    }

    private fun startPlayer(uri: Uri?, loop: Boolean): Boolean {
        if (uri == null) return false
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(alarmAttributes)
            mp.setDataSource(context, uri)
            mp.isLooping = loop
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { if (!loop) stop() }
            mp.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "MediaPlayer error $what/$extra for $uri")
                // Async failure (e.g. a deleted file): fall back to the bundled chime instead of silence.
                val fallback = builtinUri(SoundRule.SOUND_BUILTIN_RED)
                if (player === failed && uri != fallback) {
                    failed.release()
                    player = null
                    startPlayer(fallback, loop)
                } else {
                    stop()
                }
                true
            }
            mp.prepareAsync()
            player = mp
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot play $uri", e)
            false
        }
    }

    private fun setAlarmVolume(percent: Int) {
        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = ceil(max * percent / 100.0).toInt().coerceIn(1, max)
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: SecurityException) {
            savedVolume = null
            Log.w(TAG, "Cannot change alarm volume", e)
        }
    }

    private fun vibrate(loop: Boolean) {
        val pattern = longArrayOf(0, 600, 300, 600, 300, 600)
        @Suppress("DEPRECATION")
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, if (loop) 0 else -1), alarmAttributes)
    }

    private fun resolve(sound: String?): Uri? = when (sound) {
        null -> null
        SoundRule.SOUND_DEFAULT_ALARM -> fallbackUri()
        SoundRule.SOUND_DEFAULT_NOTIFICATION ->
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: fallbackUri()
        in SoundRule.BUILTIN_TITLES -> builtinUri(sound)
        else -> Uri.parse(sound)
    }

    private fun builtinUri(sound: String): Uri {
        val res = when (sound) {
            SoundRule.SOUND_BUILTIN_GREEN -> R.raw.alert_green
            SoundRule.SOUND_BUILTIN_YELLOW -> R.raw.alert_yellow
            else -> R.raw.alert_red
        }
        return Uri.parse("android.resource://${context.packageName}/$res")
    }

    private fun fallbackUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun showStopNotification() {
        val stop = PendingIntent.getBroadcast(
            context, 0, Intent(context, StopSoundReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_PLAYING)
            .setSmallIcon(R.drawable.ic_stat_dot)
            .setContentTitle("Тривога — грає звук")
            .setContentText("Торкніться, щоб зупинити")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(stop)
            .setDeleteIntent(stop)
            .addAction(0, "Зупинити", stop)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(Notifications.ID_PLAYING, notification) }
        }
    }

    private companion object {
        const val TAG = "AlertSoundPlayer"
        const val MAX_LOOP_MS = 10 * 60 * 1000L
        const val VIBRATION_MS = 3_000L
    }
}
