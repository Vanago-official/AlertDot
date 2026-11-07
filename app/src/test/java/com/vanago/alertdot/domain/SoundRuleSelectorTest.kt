package com.vanago.alertdot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundRuleSelectorTest {
    private val typeAndLevel = SoundRule("tl", type = "Ракетна", level = AlertLevel.RED)
    private val typeOnly = SoundRule("t", type = "Ракетна")
    private val levelOnly = SoundRule("l", level = AlertLevel.RED)
    private val greenOnly = SoundRule("g", level = AlertLevel.GREEN)
    private val all = listOf(levelOnly, typeOnly, greenOnly, typeAndLevel)

    @Test
    fun `type and level beats everything`() {
        assertEquals("tl", SoundRuleSelector.select(all, AlertLevel.RED, "Ракетна")?.id)
    }

    @Test
    fun `type only beats level only`() {
        assertEquals("t", SoundRuleSelector.select(all, AlertLevel.YELLOW, "Ракетна")?.id)
    }

    @Test
    fun `level only when type does not match`() {
        assertEquals("l", SoundRuleSelector.select(all, AlertLevel.RED, "Дронова")?.id)
        assertEquals("l", SoundRuleSelector.select(all, AlertLevel.RED, null)?.id)
    }

    @Test
    fun `no rule means silence`() {
        assertNull(SoundRuleSelector.select(all, AlertLevel.YELLOW, "Дронова"))
        assertNull(SoundRuleSelector.select(emptyList(), AlertLevel.RED, null))
    }

    @Test
    fun `disabled rules are skipped and fall through to general`() {
        val rules = listOf(typeAndLevel.copy(enabled = false), typeOnly.copy(enabled = false), levelOnly)
        assertEquals("l", SoundRuleSelector.select(rules, AlertLevel.RED, "Ракетна")?.id)
    }

    @Test
    fun `ties go to the first rule`() {
        val rules = listOf(SoundRule("a", level = AlertLevel.RED), SoundRule("b", level = AlertLevel.RED))
        assertEquals("a", SoundRuleSelector.select(rules, AlertLevel.RED, null)?.id)
    }

    @Test
    fun `catch all is the last resort`() {
        val rules = listOf(SoundRule("any"), greenOnly)
        assertEquals("g", SoundRuleSelector.select(rules, AlertLevel.GREEN, null)?.id)
        assertEquals("any", SoundRuleSelector.select(rules, AlertLevel.RED, null)?.id)
    }

    @Test
    fun specificity() {
        assertEquals(3, SoundRuleSelector.specificity(typeAndLevel, AlertLevel.RED, "Ракетна"))
        assertEquals(2, SoundRuleSelector.specificity(typeOnly, AlertLevel.GREEN, "Ракетна"))
        assertEquals(1, SoundRuleSelector.specificity(levelOnly, AlertLevel.RED, "x"))
        assertNull(SoundRuleSelector.specificity(typeAndLevel, AlertLevel.YELLOW, "Ракетна"))
    }
}

class AlertStateMachineTest {
    private val settings = SoundSettings(rules = listOf(SoundRule("red", level = AlertLevel.RED)))
    private fun event(level: AlertLevel, type: String? = null) = AlertEvent(level, type, 1000L, "test")

    @Test
    fun `first event from unknown is a change`() {
        val t = AlertStateMachine.reduce(AlertState(), event(AlertLevel.RED))
        assertTrue(t.changed)
        assertEquals(AlertState(AlertLevel.RED, null, 1000L, "test"), t.next)
        assertEquals("red", AlertStateMachine.soundFor(t, settings, 12 * 60)?.id)
    }

    @Test
    fun `same state again does not play`() {
        val current = AlertState(AlertLevel.RED, "Ракетна", 5L, "test")
        val t = AlertStateMachine.reduce(current, event(AlertLevel.RED, "Ракетна"))
        assertFalse(t.changed)
        assertEquals(current, t.next)
        assertNull(AlertStateMachine.soundFor(t, settings, 12 * 60))
    }

    @Test
    fun `type change is a change`() {
        val t = AlertStateMachine.reduce(AlertState(AlertLevel.RED, "Дронова"), event(AlertLevel.RED, "Ракетна"))
        assertTrue(t.changed)
    }

    @Test
    fun `quiet hours and master switch silence sounds`() {
        val t = AlertStateMachine.reduce(AlertState(), event(AlertLevel.RED))
        val quiet = settings.copy(quietHours = QuietHours(true, 22 * 60, 7 * 60))
        assertNull(AlertStateMachine.soundFor(t, quiet, 23 * 60))
        assertNull(AlertStateMachine.soundFor(t, quiet, 3 * 60))
        assertEquals("red", AlertStateMachine.soundFor(t, quiet, 12 * 60)?.id)
        assertNull(AlertStateMachine.soundFor(t, settings.copy(enabled = false), 12 * 60))
    }
}

class QuietHoursTest {
    @Test
    fun `same day window`() {
        val q = QuietHours(true, 13 * 60, 15 * 60)
        assertTrue(q.isActive(14 * 60))
        assertTrue(q.isActive(13 * 60))
        assertFalse(q.isActive(15 * 60))
        assertFalse(q.isActive(9 * 60))
    }

    @Test
    fun `window across midnight`() {
        val q = QuietHours(true, 23 * 60, 7 * 60)
        assertTrue(q.isActive(23 * 60 + 30))
        assertTrue(q.isActive(0))
        assertTrue(q.isActive(6 * 60 + 59))
        assertFalse(q.isActive(7 * 60))
        assertFalse(q.isActive(12 * 60))
    }

    @Test
    fun `disabled or empty window is never active`() {
        assertFalse(QuietHours(false, 0, 1439).isActive(100))
        assertFalse(QuietHours(true, 600, 600).isActive(600))
    }

    @Test
    fun `migration moves untouched default rules to builtin chimes`() {
        val old = SoundSettings(
            rules = listOf(
                SoundRule(id = "default-red", level = AlertLevel.RED),
                SoundRule(id = "default-yellow", level = AlertLevel.YELLOW, soundUri = SoundRule.SOUND_DEFAULT_NOTIFICATION),
                SoundRule(id = "default-green", level = AlertLevel.GREEN, soundUri = "content://custom"),
                SoundRule(id = "user-1", level = AlertLevel.RED),
            ),
        )
        val m = old.migrated()
        assertEquals(SoundSettings.DEFAULTS_VERSION, m.defaultsVersion)
        assertEquals(
            listOf(SoundRule.SOUND_BUILTIN_RED, SoundRule.SOUND_BUILTIN_YELLOW, "content://custom", SoundRule.SOUND_DEFAULT_ALARM),
            m.rules.map { it.soundUri },
        )
        // Runs only once
        val userChoice = m.copy(rules = m.rules.map { it.copy(soundUri = SoundRule.SOUND_DEFAULT_ALARM) })
        assertEquals(userChoice, userChoice.migrated())
    }
}
