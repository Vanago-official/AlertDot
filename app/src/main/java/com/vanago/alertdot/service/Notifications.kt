package com.vanago.alertdot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_OVERLAY = "overlay"
    const val CHANNEL_PLAYING = "alarm_playing"
    const val ID_OVERLAY = 1
    const val ID_PLAYING = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY, "Кружечок поверх екрана", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Постійне сповіщення сервісу кружечка"
                setShowBadge(false)
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PLAYING, "Звук тривоги грає", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Кнопка зупинки звуку. Сам звук грає застосунок, не канал."
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
