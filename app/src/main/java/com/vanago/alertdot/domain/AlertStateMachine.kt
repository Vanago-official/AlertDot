package com.vanago.alertdot.domain

data class Transition(val previous: AlertState, val next: AlertState, val changed: Boolean)

object AlertStateMachine {
    /** Applies [event]. The state (and its timestamp) only changes when level or type differ. */
    fun reduce(current: AlertState, event: AlertEvent): Transition {
        val changed = current.level != event.level || current.type != event.type
        val next = if (changed) {
            AlertState(event.level, event.type, event.timestamp, event.source)
        } else {
            current
        }
        return Transition(current, next, changed)
    }

    /** Which sound rule to play for a transition, or null for silence. */
    fun soundFor(transition: Transition, settings: SoundSettings, minuteOfDay: Int): SoundRule? {
        if (!transition.changed || !settings.enabled) return null
        if (settings.quietHours.isActive(minuteOfDay)) return null
        return SoundRuleSelector.select(settings.rules, transition.next.level, transition.next.type)
    }
}
