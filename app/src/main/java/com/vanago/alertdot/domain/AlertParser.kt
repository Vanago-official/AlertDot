package com.vanago.alertdot.domain

import kotlinx.serialization.Serializable

data class ParsedAlert(val level: AlertLevel, val type: String?)

/** Turns a message text into an alert level and type. Returns null if the text is not an alert. */
fun interface AlertParser {
    fun parse(text: String): ParsedAlert?
}

@Serializable
data class LevelRule(val level: AlertLevel, val keywords: List<String>)

@Serializable
data class AlertTypeDef(val name: String, val keywords: List<String>)

@Serializable
data class ParserRules(
    /** Checked in list order; the first level with a matching keyword wins. */
    val levelRules: List<LevelRule> = DEFAULT_LEVEL_RULES,
    /** Checked in list order (= priority); the first type with a matching keyword wins. */
    val types: List<AlertTypeDef> = DEFAULT_TYPES,
    /** Green ("all clear") events never carry a type. */
    val clearTypeOnGreen: Boolean = true,
) {
    fun keywordsFor(level: AlertLevel): List<String> =
        levelRules.firstOrNull { it.level == level }?.keywords.orEmpty()

    companion object {
        /** Levels that can be produced by the parser, in check order. */
        val PARSED_LEVELS = listOf(AlertLevel.GREEN, AlertLevel.YELLOW, AlertLevel.RED)

        /** Matches the bot format "<level> — <words> threat", e.g. "🔴 Red level — missile, drone threat". */
        val DEFAULT_LEVEL_RULES = listOf(
            LevelRule(AlertLevel.GREEN, listOf("All clear", "🟢")),
            LevelRule(AlertLevel.YELLOW, listOf("Yellow level", "🟡")),
            // "🚨 Alert" = alert without a level in the API (e.g. artillery) — treated as red.
            LevelRule(AlertLevel.RED, listOf("Red level", "🔴", "🚨")),
        )

        /** Ordered from most to least dangerous, like the words in the bot message. */
        val DEFAULT_TYPES = listOf(
            AlertTypeDef("Ядерна", listOf("nuclear")),
            AlertTypeDef("Хімічна", listOf("chemical")),
            AlertTypeDef("Ракетна", listOf("missile")),
            AlertTypeDef("КАБи", listOf("guided bomb")),
            AlertTypeDef("Авіаційна", listOf("aviation")),
            AlertTypeDef("Дронова", listOf("drone")),
            AlertTypeDef("Артилерійська", listOf("artillery")),
            AlertTypeDef("Вуличні бої", listOf("urban combat")),
            AlertTypeDef("Повітряна тривога", listOf("air raid")),
        )
    }
}

/** Default parser: keyword / emoji rules configured by the user. Case-insensitive. */
class RuleBasedAlertParser(private val rules: ParserRules) : AlertParser {

    /**
     * A notification can hold several bot messages (one per line, oldest first).
     * The newest line that matches a level wins, so an older "🟢 All clear" or
     * "🔴 Red level" above a new "🟡 Yellow level" does not hide it.
     */
    override fun parse(text: String): ParsedAlert? =
        text.lines().asReversed().firstNotNullOfOrNull { parseLine(it) }

    private fun parseLine(text: String): ParsedAlert? {
        val haystack = normalize(text)
        if (haystack.isBlank()) return null

        val level = ParserRules.PARSED_LEVELS.firstOrNull { level ->
            rules.keywordsFor(level).any { matches(haystack, it) }
        } ?: return null

        val type = if (level == AlertLevel.GREEN && rules.clearTypeOnGreen) {
            null
        } else {
            rules.types.firstOrNull { t -> t.keywords.any { matches(haystack, it) } }?.name
        }
        return ParsedAlert(level, type)
    }

    private fun matches(haystack: String, keyword: String): Boolean {
        val k = normalize(keyword).trim()
        return k.isNotEmpty() && haystack.contains(k, ignoreCase = true)
    }

    companion object {
        /** Drops emoji variation selectors so "⚔" matches "⚔️". */
        fun normalize(s: String): String = s.replace("️", "").replace("︎", "")
    }
}

object ChatFilter {
    /** Splits a comma-separated filter string into chat names. */
    fun names(filter: String): List<String> =
        filter.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Empty filter matches nothing, to avoid reacting to arbitrary chats. */
    fun matches(filter: String, chatTitle: String): Boolean =
        names(filter).any { chatTitle.contains(it, ignoreCase = true) }
}
