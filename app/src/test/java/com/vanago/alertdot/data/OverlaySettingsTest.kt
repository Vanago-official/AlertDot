package com.vanago.alertdot.data

import com.vanago.alertdot.domain.AlertLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySettingsTest {
    @Test
    fun `v2 json with int offsets migrates to signed form`() {
        val json = """{"styles":{"RED":{"corner":"TOP_END","offsetXdp":180,"offsetYdp":6,"sizeDp":24,"opacity":0.6},
            "GREEN":{"corner":"BOTTOM_START","offsetXdp":10,"offsetYdp":20,"sizeDp":14},
            "YELLOW":{"corner":"TOP_CENTER","offsetXdp":-3,"offsetYdp":6,"sizeDp":14}}}"""
        val o = AppJson.decodeFromString(OverlaySettings.serializer(), json)
        val red = o.style(AlertLevel.RED)
        assertEquals(-180f, red.offsetXdp)
        assertEquals(6f, red.offsetYdp)
        assertEquals(24f, red.sizeDp)
        assertTrue(red.signedOffsets)
        val green = o.style(AlertLevel.GREEN)
        assertEquals(10f, green.offsetXdp)
        assertEquals(-20f, green.offsetYdp)
        assertEquals(-3f, o.style(AlertLevel.YELLOW).offsetXdp)
    }

    @Test
    fun `v1 legacy fields migrate`() {
        val o = AppJson.decodeFromString(OverlaySettings.serializer(), """{"corner":"TOP_START","offsetXdp":180,"offsetYdp":6,"sizeDp":24}""")
        val s = o.style(AlertLevel.UNKNOWN)
        assertEquals(Corner.TOP_START, s.corner)
        assertEquals(180f, s.offsetXdp)
    }

    @Test
    fun `saved style stays signed and round trips`() {
        val o = OverlaySettings().updateStyle(AlertLevel.RED) { it.copy(corner = Corner.CAMERA, offsetXdp = 0.5f, offsetYdp = -1.25f, sizeDp = 24.5f) }
        val back = AppJson.decodeFromString(OverlaySettings.serializer(), AppJson.encodeToString(OverlaySettings.serializer(), o))
        val s = back.style(AlertLevel.RED)
        assertEquals(0.5f, s.offsetXdp)
        assertEquals(-1.25f, s.offsetYdp)
        assertEquals(24.5f, s.sizeDp)
        // Normalizing twice must not flip signs again.
        assertEquals(s, s.normalized())
    }

    @Test
    fun `templates save and overwrite by name`() {
        val a = DotStyle(corner = Corner.CAMERA, sizeDp = 24f)
        val b = DotStyle(corner = Corner.CENTER, sizeDp = 10f)
        val o = OverlaySettings().saveTemplate("Кільце", a).saveTemplate("Кільце", b)
        assertEquals(1, o.templates.size)
        assertEquals(Corner.CENTER, o.templates.single().style.corner)
    }
}
