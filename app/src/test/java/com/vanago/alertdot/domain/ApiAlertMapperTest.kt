package com.vanago.alertdot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiAlertMapperTest {
    private val city = "м. Київ"
    private fun alert(type: String?, level: String? = null, vararg threats: String, location: String = city) =
        ApiAlert(location, type, level, threats.toList())

    @Test
    fun `no alerts is all clear`() {
        assertEquals("🟢 All clear", ApiAlertMapper.levelLine(emptyList(), city))
        assertEquals("🟢 All clear", ApiAlertMapper.levelLine(listOf(alert("air_raid", "red", location = "Харківська область")), city))
    }

    @Test
    fun `red level with threats in danger order`() {
        val alerts = listOf(alert("air_raid", "red", "drones", "ballistic_missiles"))
        assertEquals("🔴 Red level — missile, drone threat", ApiAlertMapper.levelLine(alerts, city))
    }

    @Test
    fun `highest level wins`() {
        val alerts = listOf(alert("air_raid", "yellow", "drones"), alert("air_raid", "red", "guided_aerial_bombs"))
        assertEquals("🔴 Red level — guided bomb, drone threat", ApiAlertMapper.levelLine(alerts, city))
    }

    @Test
    fun `alert without level`() {
        assertEquals("🚨 Alert — artillery threat", ApiAlertMapper.levelLine(listOf(alert("artillery_shelling")), city))
    }

    @Test
    fun `only air raid or unknown kinds fall back to air raid`() {
        val alerts = listOf(alert("air_raid", "yellow", "air_defense", "unknown"))
        assertEquals("🟡 Yellow level — air raid threat", ApiAlertMapper.levelLine(alerts, city))
    }

    @Test
    fun `api line goes through default parser`() {
        val line = ApiAlertMapper.levelLine(listOf(alert("air_raid", "red", "mig31k_departure", "drones")), city)
        assertEquals(ParsedAlert(AlertLevel.RED, "Ракетна"), RuleBasedAlertParser(ParserRules()).parse(line))
    }
}
