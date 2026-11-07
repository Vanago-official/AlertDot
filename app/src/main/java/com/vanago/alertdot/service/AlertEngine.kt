package com.vanago.alertdot.service

import android.util.Log
import com.vanago.alertdot.data.SettingsRepository
import com.vanago.alertdot.data.StateRepository
import com.vanago.alertdot.domain.AlertEvent
import com.vanago.alertdot.domain.AlertState
import com.vanago.alertdot.domain.AlertStateMachine
import com.vanago.alertdot.domain.AlertSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** Collects events from all sources, updates the persisted state and plays sounds on changes. */
class AlertEngine(
    private val scope: CoroutineScope,
    private val stateRepo: StateRepository,
    private val settingsRepo: SettingsRepository,
    private val player: AlertSoundPlayer,
    private val sources: List<AlertSource>,
) {
    private val mutex = Mutex()

    fun start() {
        scope.launch {
            merge(*sources.map { it.events }.toTypedArray()).collect { event ->
                try {
                    handle(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle $event", e)
                }
            }
        }
    }

    private suspend fun handle(event: AlertEvent) = mutex.withLock {
        val transition = AlertStateMachine.reduce(stateRepo.current(), event)
        Log.i(TAG, "Event $event, changed=${transition.changed}")
        if (!transition.changed) return@withLock
        stateRepo.save(transition.next)

        val settings = settingsRepo.current()
        val now = LocalTime.now()
        val rule = AlertStateMachine.soundFor(transition, settings.sound, now.hour * 60 + now.minute)
        if (rule != null) {
            withContext(Dispatchers.Main) { player.play(rule, settings.sound.ignoreDnd) }
        }
    }

    suspend fun reset() = mutex.withLock { stateRepo.save(AlertState()) }

    private companion object {
        const val TAG = "AlertEngine"
    }
}
