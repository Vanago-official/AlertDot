package com.vanago.alertdot

import android.app.Application
import android.content.Context
import com.vanago.alertdot.data.AppSettings
import com.vanago.alertdot.data.SettingsRepository
import com.vanago.alertdot.data.StateRepository
import com.vanago.alertdot.data.jsonDataStore
import com.vanago.alertdot.domain.AlertState
import com.vanago.alertdot.domain.SoundSettings
import com.vanago.alertdot.service.AlertEngine
import com.vanago.alertdot.service.ApiAlertSource
import com.vanago.alertdot.service.AlertSoundPlayer
import com.vanago.alertdot.service.Notifications
import com.vanago.alertdot.service.SimulatedSource
import com.vanago.alertdot.service.TelegramNotificationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import com.vanago.alertdot.domain.AlertLevel
import kotlinx.coroutines.launch

class AlertDotApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** DataStore does blocking file I/O; give it the IO pool (its own default) instead of the CPU pool. */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** While the overlay settings screen is open, the real dot shows this level instead of the current one. */
    val overlayPreviewLevel = MutableStateFlow<AlertLevel?>(null)

    lateinit var settingsRepo: SettingsRepository
        private set
    lateinit var stateRepo: StateRepository
        private set
    lateinit var player: AlertSoundPlayer
        private set
    lateinit var telegramSource: TelegramNotificationSource
        private set
    lateinit var simulatedSource: SimulatedSource
        private set
    lateinit var apiSource: ApiAlertSource
        private set
    lateinit var engine: AlertEngine
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        settingsRepo = SettingsRepository(
            jsonDataStore(this, "settings.json", AppSettings.serializer(), AppSettings(), storeScope)
        )
        stateRepo = StateRepository(
            jsonDataStore(this, "alert_state.json", AlertState.serializer(), AlertState(), storeScope)
        )
        player = AlertSoundPlayer(this)
        telegramSource = TelegramNotificationSource(settingsRepo, appScope)
        simulatedSource = SimulatedSource()
        apiSource = ApiAlertSource(settingsRepo)
        // New sources (ntfy, FCM, ...) only need to be added to this list.
        engine = AlertEngine(
            scope = appScope,
            stateRepo = stateRepo,
            settingsRepo = settingsRepo,
            player = player,
            sources = listOf(telegramSource, simulatedSource, apiSource),
        )
        engine.start()
        appScope.launch {
            settingsRepo.update { s ->
                if (s.sound.defaultsVersion >= SoundSettings.DEFAULTS_VERSION) s
                else s.copy(sound = s.sound.migrated())
            }
        }
    }
}

val Context.app: AlertDotApp get() = applicationContext as AlertDotApp
