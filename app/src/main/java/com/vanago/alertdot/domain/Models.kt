package com.vanago.alertdot.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AlertLevel { GREEN, YELLOW, RED, UNKNOWN }

/** Persisted current alert state. [type] is a name from the configured type list or null. */
@Serializable
data class AlertState(
    val level: AlertLevel = AlertLevel.UNKNOWN,
    val type: String? = null,
    val timestamp: Long = 0L,
    val source: String? = null,
)

/** A normalized event produced by any [AlertSource]. */
data class AlertEvent(
    val level: AlertLevel,
    val type: String?,
    val timestamp: Long,
    val source: String,
)

/** A raw text message received from some channel (Telegram notification, push, ...). */
data class IncomingMessage(
    val chatTitle: String,
    val text: String,
    val timestamp: Long,
    val origin: String,
)
