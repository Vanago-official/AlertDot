package com.vanago.alertdot.domain

import kotlinx.coroutines.flow.Flow

/**
 * A source of alert events. Telegram notifications are the only implementation for now;
 * ntfy / FCM sources can be added by implementing this interface and registering
 * the instance in the app graph.
 */
interface AlertSource {
    val id: String
    val events: Flow<AlertEvent>
}
