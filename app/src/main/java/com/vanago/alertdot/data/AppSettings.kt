package com.vanago.alertdot.data

import com.vanago.alertdot.domain.AlertLevel
import com.vanago.alertdot.domain.ParserRules
import com.vanago.alertdot.domain.SoundSettings
import kotlinx.serialization.Serializable

/**
 * Where the dot is anchored: a 3×3 grid (corners, edge middles, center) or CAMERA,
 * which centers it on the display cutout (punch-hole camera).
 * Names of the v1 values are kept for settings compatibility.
 */
@Serializable
enum class Corner(val col: Int, val row: Int) {
    TOP_START(0, 0), TOP_CENTER(1, 0), TOP_END(2, 0),
    CENTER_START(0, 1), CENTER(1, 1), CENTER_END(2, 1),
    BOTTOM_START(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_END(2, 2),
    CAMERA(1, 0);

    companion object {
        val GRID = listOf(
            TOP_START, TOP_CENTER, TOP_END,
            CENTER_START, CENTER, CENTER_END,
            BOTTOM_START, BOTTOM_CENTER, BOTTOM_END,
        )
    }
}

/** Look of the dot for one alert level. */
@Serializable
data class DotStyle(
    val visible: Boolean = true,
    val corner: Corner = Corner.TOP_END,
    /**
     * Shift from the anchor in dp: +X = right, +Y = down, for every anchor.
     * At 0/0 a corner/edge anchor puts the dot flush with the screen edge; CENTER/CAMERA center it.
     */
    val offsetXdp: Float = 6f,
    val offsetYdp: Float = 6f,
    val sizeDp: Float = 14f,
    val opacity: Float = 0.8f,
    val color: Long = 0xFF9E9E9E,
    /**
     * false = offsets in the v1/v2 form, where corner offsets meant "distance inward from the edge".
     * Defaults to false because JSON saved by older versions has no such field; every style is read
     * through [normalized] (see [OverlaySettings.style]), which converts and sets it to true.
     */
    val signedOffsets: Boolean = false,
) {
    /** Converts v1/v2 offsets to the signed form (keeps the dot exactly where it was). */
    fun normalized(): DotStyle {
        if (signedOffsets) return this
        val x = if (corner == Corner.TOP_END || corner == Corner.BOTTOM_END) -offsetXdp else offsetXdp
        val y = if (corner == Corner.BOTTOM_START || corner == Corner.BOTTOM_END) -offsetYdp else offsetYdp
        return copy(offsetXdp = x, offsetYdp = y, signedOffsets = true)
    }

    /** Same position, size and opacity as [other]; keeps own color and visibility. */
    fun withPlacementOf(other: DotStyle): DotStyle {
        val o = other.normalized()
        return normalized().copy(
            corner = o.corner,
            offsetXdp = o.offsetXdp,
            offsetYdp = o.offsetYdp,
            sizeDp = o.sizeDp,
            opacity = o.opacity,
        )
    }
}

/** A user-saved placement (position, size, opacity) that can be applied to any level. */
@Serializable
data class DotTemplate(val name: String, val style: DotStyle)

@Serializable
data class OverlaySettings(
    /** Per-level styles. Null in settings saved by v1; then the legacy fields below apply. */
    val styles: Map<AlertLevel, DotStyle>? = null,
    val templates: List<DotTemplate> = emptyList(),
    // Legacy single-style fields (v1). Read only for migration.
    val corner: Corner = Corner.TOP_END,
    val offsetXdp: Float = 6f,
    val offsetYdp: Float = 6f,
    val sizeDp: Float = 14f,
    val opacity: Float = 0.8f,
    val greenColor: Long = 0xFF43A047,
    val yellowColor: Long = 0xFFFDD835,
    val redColor: Long = 0xFFE53935,
    val unknownColor: Long = 0xFF9E9E9E,
) {
    private fun legacyStyle(level: AlertLevel) = DotStyle(
        visible = true,
        corner = corner,
        offsetXdp = offsetXdp,
        offsetYdp = offsetYdp,
        sizeDp = sizeDp,
        opacity = opacity,
        color = when (level) {
            AlertLevel.GREEN -> greenColor
            AlertLevel.YELLOW -> yellowColor
            AlertLevel.RED -> redColor
            AlertLevel.UNKNOWN -> unknownColor
        },
        signedOffsets = false,
    )

    /** Always returns the signed-offset form. */
    fun style(level: AlertLevel): DotStyle = (styles?.get(level) ?: legacyStyle(level)).normalized()

    fun colorFor(level: AlertLevel): Long = style(level).color

    fun withStyle(level: AlertLevel, style: DotStyle): OverlaySettings =
        copy(styles = AlertLevel.entries.associateWith { if (it == level) style.normalized() else style(it) })

    fun updateStyle(level: AlertLevel, transform: (DotStyle) -> DotStyle) = withStyle(level, transform(style(level)))

    /** Copies position, size and opacity of [level] to every other level. */
    fun placementToAll(level: AlertLevel): OverlaySettings {
        val source = style(level)
        return copy(styles = AlertLevel.entries.associateWith { style(it).withPlacementOf(source) })
    }

    /** Adds or replaces (by name) a user template. */
    fun saveTemplate(name: String, style: DotStyle): OverlaySettings {
        val t = DotTemplate(name, style.normalized())
        val i = templates.indexOfFirst { it.name == name }
        return copy(templates = if (i >= 0) templates.toMutableList().also { it[i] = t } else templates + t)
    }
}

@Serializable
data class AppSettings(
    /** Comma-separated chat / bot names; a notification title must contain one of them. */
    val chatFilter: String = "",
    /** alerts.in.ua token for the manual "Оновити" check. Stored only on the device. */
    val apiToken: String = "",
    /** Exact `location_title` from the API, same as CITY in the bot's config.cfg. */
    val apiCity: String = "м. Київ",
    val parser: ParserRules = ParserRules(),
    val overlay: OverlaySettings = OverlaySettings(),
    val sound: SoundSettings = SoundSettings(),
    val showOverlay: Boolean = false,
    val autostart: Boolean = true,
    val darkTheme: Boolean = true,
)
