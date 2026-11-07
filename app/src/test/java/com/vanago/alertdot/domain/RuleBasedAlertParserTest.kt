package com.vanago.alertdot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleBasedAlertParserTest {
    private val parser = RuleBasedAlertParser(ParserRules())

    private fun check(text: String, level: AlertLevel, type: String?) =
        assertEquals(text, ParsedAlert(level, type), parser.parse(text))

    @Test
    fun `single word messages`() {
        check("🔴 Red level — missile threat", AlertLevel.RED, "Ракетна")
        check("🔴 Red level — guided bomb threat", AlertLevel.RED, "КАБи")
        check("🔴 Red level — aviation threat", AlertLevel.RED, "Авіаційна")
        check("🔴 Red level — drone threat", AlertLevel.RED, "Дронова")
        check("🔴 Red level — air raid threat", AlertLevel.RED, "Повітряна тривога")
        check("🟡 Yellow level — drone threat", AlertLevel.YELLOW, "Дронова")
        check("🟡 Yellow level — air raid threat", AlertLevel.YELLOW, "Повітряна тривога")
    }

    @Test
    fun `alert without level is red`() {
        check("🚨 Alert — artillery threat", AlertLevel.RED, "Артилерійська")
        check("🚨 Alert — urban combat threat", AlertLevel.RED, "Вуличні бої")
        check("🚨 Alert — chemical threat", AlertLevel.RED, "Хімічна")
        check("🚨 Alert — nuclear threat", AlertLevel.RED, "Ядерна")
    }

    @Test
    fun `several words take the most dangerous`() {
        check("🔴 Red level — missile, drone threat", AlertLevel.RED, "Ракетна")
        check("🔴 Red level — missile, aviation, drone threat", AlertLevel.RED, "Ракетна")
        check("🟡 Yellow level — guided bomb, drone threat", AlertLevel.YELLOW, "КАБи")
    }

    @Test
    fun `newest line wins in a multi-message notification`() {
        check("🟢 All clear\n🟡 Yellow level — drone threat", AlertLevel.YELLOW, "Дронова")
        check("🔴 Red level — missile threat\n🟡 Yellow level — guided bomb threat", AlertLevel.YELLOW, "КАБи")
        check("🟡 Yellow level — drone threat\n🟢 All clear", AlertLevel.GREEN, null)
        check("🟡 Yellow level — drone threat\n👋 Hi!", AlertLevel.YELLOW, "Дронова")
    }

    @Test
    fun `all clear is green without type`() {
        check("🟢 All clear", AlertLevel.GREEN, null)
    }

    @Test
    fun `unrelated text is ignored`() {
        assertNull(parser.parse("👋 Hi! I'm AirAlert."))
        assertNull(parser.parse("📊 Current Status:"))
        assertNull(parser.parse(""))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(ParsedAlert(AlertLevel.RED, "Дронова"), parser.parse("RED LEVEL — DRONE THREAT"))
    }

    @Test
    fun `emoji variation selector does not matter`() {
        val rules = ParserRules(
            levelRules = listOf(LevelRule(AlertLevel.RED, listOf("⚔"))),
            types = listOf(AlertTypeDef("Бої", listOf("⚔️"))),
        )
        val p = RuleBasedAlertParser(rules)
        assertEquals(ParsedAlert(AlertLevel.RED, "Бої"), p.parse("⚔️ urban"))
        assertEquals(ParsedAlert(AlertLevel.RED, "Бої"), p.parse("⚔ urban"))
    }

    @Test
    fun `yellow wins over red when both match`() {
        assertEquals(AlertLevel.YELLOW, parser.parse("🟡 Yellow level 🔴")?.level)
    }

    @Test
    fun `green checked before red regardless of rule list order`() {
        val rules = ParserRules(
            levelRules = listOf(
                LevelRule(AlertLevel.RED, listOf("тривога")),
                LevelRule(AlertLevel.GREEN, listOf("відбій")),
            ),
        )
        assertEquals(AlertLevel.GREEN, RuleBasedAlertParser(rules).parse("Відбій: повітряна тривога")?.level)
    }

    @Test
    fun `custom rules and no type match`() {
        val rules = ParserRules(
            levelRules = listOf(LevelRule(AlertLevel.RED, listOf("🔴"))),
            types = listOf(AlertTypeDef("Ракетна", listOf("ракета"))),
        )
        assertEquals(ParsedAlert(AlertLevel.RED, null), RuleBasedAlertParser(rules).parse("🔴 Невідома загроза"))
    }

    @Test
    fun `blank keywords never match`() {
        val rules = ParserRules(levelRules = listOf(LevelRule(AlertLevel.RED, listOf("", "  "))))
        assertNull(RuleBasedAlertParser(rules).parse("anything"))
    }

    @Test
    fun `green keeps type when option disabled`() {
        val p = RuleBasedAlertParser(ParserRules(clearTypeOnGreen = false))
        assertEquals(ParsedAlert(AlertLevel.GREEN, "Дронова"), p.parse("🟢 All clear — drone"))
    }
}

class ChatFilterTest {
    @Test
    fun matching() {
        assertEquals(true, ChatFilter.matches("AirAlert", "AirAlert"))
        assertEquals(true, ChatFilter.matches("airalert", "AirAlert (2)"))
        assertEquals(true, ChatFilter.matches("Other, AirAlert", "AirAlert"))
        assertEquals(false, ChatFilter.matches("AirAlert", "Mom"))
        assertEquals(false, ChatFilter.matches("", "AirAlert"))
        assertEquals(false, ChatFilter.matches(" , ", "AirAlert"))
    }
}
