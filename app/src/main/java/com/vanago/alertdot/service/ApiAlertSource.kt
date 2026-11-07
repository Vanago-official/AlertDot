package com.vanago.alertdot.service

import com.vanago.alertdot.data.SettingsRepository
import com.vanago.alertdot.domain.AlertEvent
import com.vanago.alertdot.domain.AlertSource
import com.vanago.alertdot.domain.ApiAlert
import com.vanago.alertdot.domain.ApiAlertMapper
import com.vanago.alertdot.domain.RuleBasedAlertParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface RefreshResult {
    /** [line] is the bot-style text built from the API answer. */
    data class Ok(val line: String) : RefreshResult
    data class Error(val message: String) : RefreshResult
}

/** On-demand check of alerts.in.ua. Never polls: only runs when the user taps "Оновити". */
class ApiAlertSource(private val settingsRepo: SettingsRepository) : AlertSource {
    override val id = "api"
    private val _events = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 4)
    override val events: Flow<AlertEvent> = _events.asSharedFlow()

    suspend fun refresh(): RefreshResult {
        val settings = settingsRepo.current()
        val token = settings.apiToken.trim()
        if (token.isEmpty()) return RefreshResult.Error("Вкажіть токен API у розділі «Джерело»")
        if (settings.apiCity.isBlank()) return RefreshResult.Error("Вкажіть місто у розділі «Джерело»")

        val alerts = try {
            fetch(token)
        } catch (e: ApiException) {
            return RefreshResult.Error(e.message ?: "Помилка API")
        } catch (e: IOException) {
            return RefreshResult.Error("Немає зʼєднання з API")
        } catch (e: Exception) {
            return RefreshResult.Error("Не вдалося прочитати відповідь API")
        }

        val line = ApiAlertMapper.levelLine(alerts, settings.apiCity)
        val parsed = RuleBasedAlertParser(settings.parser).parse(line)
            ?: return RefreshResult.Error("Правила парсингу не розпізнали: «$line»")
        _events.emit(AlertEvent(parsed.level, parsed.type, System.currentTimeMillis(), id))
        return RefreshResult.Ok(line)
    }

    private class ApiException(message: String) : Exception(message)

    private suspend fun fetch(token: String): List<ApiAlert> = withContext(Dispatchers.IO) {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            when (val code = conn.responseCode) {
                200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> throw ApiException("Невірний токен API ($code)")
                429 -> throw ApiException("Забагато запитів — спробуйте за хвилину")
                else -> throw ApiException("API відповів кодом $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<ApiAlert> {
        var root = Json.parseToJsonElement(body).jsonObject
        (root["Data"] as? JsonObject)?.let { root = it }
        val list = root["alerts"] as? JsonArray ?: return emptyList()
        return list.map { el ->
            val o = el.jsonObject
            ApiAlert(
                locationTitle = o.str("location_title").orEmpty(),
                alertType = o.str("alert_type"),
                alertLevel = o.str("alert_level"),
                threatTypes = (o["threats"] as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonObject)?.str("threat_type")
                },
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val ENDPOINT = "https://api.alerts.in.ua/v1/alerts/active.json"
    }
}
