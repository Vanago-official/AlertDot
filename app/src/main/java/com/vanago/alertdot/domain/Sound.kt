package com.vanago.alertdot.domain

import kotlinx.serialization.Serializable

@Serializable
data class SoundRule(
    val id: String,
    /** null = any type. */
    val type: String? = null,
    /** null = any level. */
    val level: AlertLevel? = null,
    /** Content/file URI, one of the DEFAULT_* constants, or null for no sound (vibration only). */
    val soundUri: String? = SOUND_DEFAULT_ALARM,
    val soundTitle: String = "Типовий будильник",
    /** 0..100, percent of the alarm stream maximum. */
    val volume: Int = 100,
    val vibrate: Boolean = true,
    /** true = loop until the user stops it, false = play once. */
    val loop: Boolean = false,
    val enabled: Boolean = true,
) {
    companion object {
        const val SOUND_DEFAULT_ALARM = "default:alarm"
        const val SOUND_DEFAULT_NOTIFICATION = "default:notification"

        /** Short soft chimes bundled in res/raw. */
        const val SOUND_BUILTIN_GREEN = "builtin:green"
        const val SOUND_BUILTIN_YELLOW = "builtin:yellow"
        const val SOUND_BUILTIN_RED = "builtin:red"

        val BUILTIN_TITLES = mapOf(
            SOUND_BUILTIN_GREEN to "Мʼякий: зелений",
            SOUND_BUILTIN_YELLOW to "Мʼякий: жовтий",
            SOUND_BUILTIN_RED to "Мʼякий: червоний",
        )

        fun builtinFor(level: AlertLevel?): String = when (level) {
            AlertLevel.GREEN -> SOUND_BUILTIN_GREEN
            AlertLevel.YELLOW -> SOUND_BUILTIN_YELLOW
            else -> SOUND_BUILTIN_RED
        }
    }
}

@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startMinutes: Int = 23 * 60,
    val endMinutes: Int = 7 * 60,
) {
    /** [minuteOfDay] is 0..1439. The window may wrap past midnight. start == end means never. */
    fun isActive(minuteOfDay: Int): Boolean {
        if (!enabled || startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            minuteOfDay in startMinutes until endMinutes
        } else {
            minuteOfDay >= startMinutes || minuteOfDay < endMinutes
        }
    }
}

@Serializable
data class SoundSettings(
    val enabled: Boolean = true,
    val ignoreDnd: Boolean = false,
    val quietHours: QuietHours = QuietHours(),
    val rules: List<SoundRule> = DEFAULT_RULES,
    /** Bumped when default rules change, so [migrated] runs once on old settings files. */
    val defaultsVersion: Int = 0,
) {
    /**
     * Moves untouched default rules (still on the system alarm / notification sound)
     * to the bundled soft chimes. Rules the user picked a sound for are left alone.
     */
    fun migrated(): SoundSettings {
        if (defaultsVersion >= DEFAULTS_VERSION) return this
        val oldSounds = setOf(SoundRule.SOUND_DEFAULT_ALARM, SoundRule.SOUND_DEFAULT_NOTIFICATION)
        return copy(
            defaultsVersion = DEFAULTS_VERSION,
            rules = rules.map { rule ->
                if (rule.id.startsWith("default-") && rule.soundUri in oldSounds) {
                    val uri = SoundRule.builtinFor(rule.level)
                    rule.copy(soundUri = uri, soundTitle = SoundRule.BUILTIN_TITLES.getValue(uri))
                } else {
                    rule
                }
            },
        )
    }

    companion object {
        const val DEFAULTS_VERSION = 1

        private fun builtin(level: AlertLevel) = SoundRule.builtinFor(level).let {
            it to SoundRule.BUILTIN_TITLES.getValue(it)
        }

        val DEFAULT_RULES: List<SoundRule> = run {
            val (red, redTitle) = builtin(AlertLevel.RED)
            val (yellow, yellowTitle) = builtin(AlertLevel.YELLOW)
            val (green, greenTitle) = builtin(AlertLevel.GREEN)
            listOf(
                // Looped: the red chime has a pause at the end, so it repeats every ~2.4 s until stopped.
                SoundRule(
                    id = "default-missile-red", type = "Ракетна", level = AlertLevel.RED,
                    soundUri = red, soundTitle = redTitle, loop = true,
                ),
                SoundRule(id = "default-red", level = AlertLevel.RED, soundUri = red, soundTitle = redTitle),
                SoundRule(
                    id = "default-yellow", level = AlertLevel.YELLOW,
                    soundUri = yellow, soundTitle = yellowTitle, volume = 70,
                ),
                SoundRule(
                    id = "default-green", level = AlertLevel.GREEN,
                    soundUri = green, soundTitle = greenTitle, volume = 60, vibrate = false,
                ),
            )
        }
    }
}

object SoundRuleSelector {
    /**
     * Specificity of [rule] for the given state, or null if it does not match.
     * type + level = 3, type only = 2, level only = 1, catch-all = 0.
     */
    fun specificity(rule: SoundRule, level: AlertLevel, type: String?): Int? {
        if (rule.level != null && rule.level != level) return null
        if (rule.type != null && rule.type != type) return null
        return (if (rule.type != null) 2 else 0) + (if (rule.level != null) 1 else 0)
    }

    /** The most specific enabled rule; ties go to the rule that comes first in the list. */
    fun select(rules: List<SoundRule>, level: AlertLevel, type: String?): SoundRule? {
        var best: SoundRule? = null
        var bestScore = -1
        for (rule in rules) {
            if (!rule.enabled) continue
            val score = specificity(rule, level, type) ?: continue
            if (score > bestScore) {
                best = rule
                bestScore = score
            }
        }
        return best
    }
}
