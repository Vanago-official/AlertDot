package com.vanago.alertdot.domain

/** One active alert from alerts.in.ua (`alerts/active.json`). */
data class ApiAlert(
    val locationTitle: String,
    val alertType: String?,
    /** "red", "yellow" or null. */
    val alertLevel: String?,
    val threatTypes: List<String>,
)

/**
 * Builds the same one-line text the AirAlert bot sends (see alarm.py `level_line`),
 * so that the API result goes through the user's parser rules exactly like a Telegram message.
 */
object ApiAlertMapper {
    private val THREAT_GROUPS = listOf(
        "nuclear" to setOf("nuclear"),
        "chemical" to setOf("chemical"),
        "missile" to setOf("ballistic_missiles", "cruise_missiles", "unspecified_missiles", "mig31k_departure"),
        "guided bomb" to setOf("guided_aerial_bombs"),
        "aviation" to setOf("strategic_aircraft_activity", "tactic_aircraft_activity"),
        "drone" to setOf("drones"),
        "artillery" to setOf("artillery_shelling"),
        "urban combat" to setOf("urban_fights"),
    )
    private val LEVEL_RANK = mapOf("yellow" to 1, "red" to 2)

    fun levelLine(alerts: List<ApiAlert>, city: String): String {
        val cityAlerts = alerts.filter { it.locationTitle == city.trim() }
        val kinds = buildSet {
            cityAlerts.forEach { a ->
                a.alertType?.let { add(it) }
                addAll(a.threatTypes)
            }
        }
        if (kinds.isEmpty()) return "🟢 All clear"

        val level = cityAlerts.mapNotNull { it.alertLevel }.maxByOrNull { LEVEL_RANK[it] ?: 0 }
            ?.takeIf { it in LEVEL_RANK }
        val levelName = when (level) {
            "red" -> "🔴 Red level"
            "yellow" -> "🟡 Yellow level"
            else -> "🚨 Alert"
        }
        val words = THREAT_GROUPS.filter { (_, group) -> kinds.any { it in group } }.map { it.first }
        val summary = if (words.isEmpty()) "air raid" else words.joinToString(", ")
        return "$levelName — $summary threat"
    }
}
