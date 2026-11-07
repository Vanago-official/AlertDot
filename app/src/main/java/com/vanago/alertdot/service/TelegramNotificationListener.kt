package com.vanago.alertdot.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.vanago.alertdot.app
import com.vanago.alertdot.domain.IncomingMessage

class TelegramNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in TELEGRAM_PACKAGES) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val message = extract(sbn) ?: return
        app.telegramSource.submit(message)
    }

    private fun extract(sbn: StatusBarNotification): IncomingMessage? {
        val n = sbn.notification
        val extras = n.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val last = style?.messages?.lastOrNull()

        val title = (style?.conversationTitle
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()

        // InboxStyle (several messages in one notification) puts them in EXTRA_TEXT_LINES, oldest first.
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n")?.takeIf { it.isNotBlank() }
        val text = (last?.text
            ?: lines
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        if (text.isBlank()) return null
        val timestamp = last?.timestamp?.takeIf { it > 0 } ?: sbn.postTime
        return IncomingMessage(title, text, timestamp, sbn.packageName)
    }

    companion object {
        val TELEGRAM_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
        )
    }
}
