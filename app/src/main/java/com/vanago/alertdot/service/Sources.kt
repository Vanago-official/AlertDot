package com.vanago.alertdot.service

import com.vanago.alertdot.data.SettingsRepository
import com.vanago.alertdot.domain.AlertEvent
import com.vanago.alertdot.domain.AlertLevel
import com.vanago.alertdot.domain.AlertSource
import com.vanago.alertdot.domain.ChatFilter
import com.vanago.alertdot.domain.IncomingMessage
import com.vanago.alertdot.domain.RuleBasedAlertParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Debug info about the last notification seen from Telegram (shown on the Source screen). */
data class LastNotification(
    val message: IncomingMessage,
    val accepted: Boolean,
    val result: String,
)

/** Receives Telegram notification texts from [TelegramNotificationListener]. */
class TelegramNotificationSource(
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
) : AlertSource {
    override val id = "telegram"

    private val _events = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 32)
    override val events: Flow<AlertEvent> = _events.asSharedFlow()

    private val _last = MutableStateFlow<LastNotification?>(null)
    val last: StateFlow<LastNotification?> = _last.asStateFlow()

    private val recent = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 64
    }

    /**
     * Messages are handled one by one by a single consumer, in arrival order. Launching a coroutine per
     * notification (as before) let two quick messages (e.g. alert + all-clear) race and be applied
     * in the wrong order, leaving the wrong final state.
     */
    private val queue = Channel<IncomingMessage>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (message in queue) {
                try {
                    process(message)
                } catch (e: Exception) {
                    android.util.Log.e("TelegramSource", "Failed to process notification", e)
                }
            }
        }
    }

    fun submit(message: IncomingMessage) {
        val dedupKey = "${message.chatTitle}|${message.timestamp}|${message.text}"
        synchronized(recent) {
            if (recent.containsKey(dedupKey)) return
            recent[dedupKey] = Unit
        }
        queue.trySend(message)
    }

    private suspend fun process(message: IncomingMessage) {
        val settings = settingsRepo.current()
        if (!ChatFilter.matches(settings.chatFilter, message.chatTitle)) {
            _last.value = LastNotification(message, false, "Чат не відповідає фільтру")
            return
        }
        val parsed = RuleBasedAlertParser(settings.parser).parse(message.text)
        if (parsed == null) {
            _last.value = LastNotification(message, true, "Рівень не розпізнано — ігнор")
            return
        }
        _last.value = LastNotification(message, true, "${parsed.level} / ${parsed.type ?: "—"}")
        _events.emit(AlertEvent(parsed.level, parsed.type, System.currentTimeMillis(), id))
    }
}

/** Manual events from the "Симулювати подію" button. */
class SimulatedSource : AlertSource {
    override val id = "simulation"
    private val _events = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 8)
    override val events: Flow<AlertEvent> = _events.asSharedFlow()

    fun emit(level: AlertLevel, type: String?) {
        _events.tryEmit(AlertEvent(level, type, System.currentTimeMillis(), id))
    }
}
