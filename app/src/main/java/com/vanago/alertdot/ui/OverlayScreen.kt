package com.vanago.alertdot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vanago.alertdot.app
import com.vanago.alertdot.data.AppSettings
import com.vanago.alertdot.data.Corner
import com.vanago.alertdot.data.DotStyle
import com.vanago.alertdot.data.DotTemplate
import com.vanago.alertdot.data.OverlaySettings
import com.vanago.alertdot.domain.AlertLevel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private val PALETTE = listOf(
    0xFF43A047, 0xFF00C853, 0xFF26A69A, 0xFF1E88E5, 0xFF8E24AA,
    0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFE53935, 0xFFD50000,
    0xFF9E9E9E, 0xFF616161, 0xFFFFFFFF, 0xFF000000,
)

private val LEVEL_ORDER = listOf(AlertLevel.RED, AlertLevel.YELLOW, AlertLevel.GREEN, AlertLevel.UNKNOWN)
private val STEPS = listOf(0.1f, 0.5f, 1f, 5f)

private val GRID_GLYPHS = mapOf(
    Corner.TOP_START to "↖", Corner.TOP_CENTER to "↑", Corner.TOP_END to "↗",
    Corner.CENTER_START to "←", Corner.CENTER to "•", Corner.CENTER_END to "→",
    Corner.BOTTOM_START to "↙", Corner.BOTTOM_CENTER to "↓", Corner.BOTTOM_END to "↘",
)

private fun Corner.label(): String = when (this) {
    Corner.TOP_START -> "вгорі зліва"
    Corner.TOP_CENTER -> "вгорі по центру"
    Corner.TOP_END -> "вгорі справа"
    Corner.CENTER_START -> "зліва по центру"
    Corner.CENTER -> "по центру екрана"
    Corner.CENTER_END -> "справа по центру"
    Corner.BOTTOM_START -> "внизу зліва"
    Corner.BOTTOM_CENTER -> "внизу по центру"
    Corner.BOTTOM_END -> "внизу справа"
    Corner.CAMERA -> "на камері"
}

private fun round2(v: Float): Float = (v * 100).roundToInt() / 100f

private fun fmt(v: Float): String =
    BigDecimal(round2(v).toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun DotStyle.summary(): String =
    "${corner.label()} · ${fmt(sizeDp)} dp · X ${fmt(offsetXdp)} · Y ${fmt(offsetYdp)} · ${(opacity * 100).roundToInt()}%"

@Composable
fun OverlayScreen(settings: AppSettings, update: UpdateSettings) {
    val context = LocalContext.current
    val o = settings.overlay
    var level by rememberSaveable { mutableStateOf(AlertLevel.RED) }
    var step by rememberSaveable { mutableFloatStateOf(0.5f) }
    val st = o.style(level)
    val setOverlay: ((OverlaySettings) -> OverlaySettings) -> Unit = { t -> update { it.copy(overlay = t(it.overlay)) } }
    val setStyle: ((DotStyle) -> DotStyle) -> Unit = { t -> setOverlay { it.updateStyle(level, t) } }

    // While this screen is open, the real dot on the screen shows the level being edited.
    LifecycleResumeEffect(level) {
        context.app.overlayPreviewLevel.value = level
        onPauseOrDispose { context.app.overlayPreviewLevel.value = null }
    }

    SectionCard(subtitle = "Кожен рівень має власний вигляд. Поки цей екран відкрито, справжній кружечок показує вибраний рівень.") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LEVEL_ORDER.forEach { l ->
                val s = o.style(l)
                FilterChip(
                    selected = level == l,
                    onClick = { level = l },
                    label = { Text(if (s.visible) l.label() else "${l.label()} · сховано") },
                    leadingIcon = { Dot(s.color, 16.dp, if (s.visible) 1f else 0.25f) },
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PhonePreview(st) }
        SwitchRow(
            "Показувати на рівні «${level.label()}»", st.visible,
            subtitle = if (st.visible) null else "Кружечка не буде зовсім — вікно оверлею знімається",
        ) { on -> setStyle { it.copy(visible = on) } }
    }

    if (!st.visible) return

    TemplatesCard(o, st, onApply = { t -> setStyle { it.withPlacementOf(t.style) } }, setOverlay = setOverlay)

    SectionCard("📍 Прив'язка", subtitle = "Точка ${st.corner.label()}. Зсуви нижче відраховуються від неї.") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Corner.GRID.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            GridCell(GRID_GLYPHS.getValue(c), selected = st.corner == c) {
                                setStyle { it.copy(corner = c, offsetXdp = 0f, offsetYdp = 0f) }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = st.corner == Corner.CAMERA,
                    onClick = { setStyle { it.copy(corner = Corner.CAMERA, offsetXdp = 0f, offsetYdp = 0f) } },
                    label = { Text("📷 Камера") },
                )
                Hint("Камера — центр точки рівно на вирізі камери (координати бере Android). Збільште розмір і зменште непрозорість, щоб вийшло кільце.")
            }
        }
    }

    SectionCard("🎯 Точне налаштування") {
        Text("Крок", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STEPS.forEach { s ->
                FilterChip(selected = step == s, onClick = { step = s }, label = { Text("${fmt(s)} dp") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DPad(
                onMove = { dx, dy ->
                    setStyle { it.copy(offsetXdp = round2(it.offsetXdp + dx * step), offsetYdp = round2(it.offsetYdp + dy * step)) }
                },
                onReset = { setStyle { it.copy(offsetXdp = 0f, offsetYdp = 0f) } },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("X, dp", st.offsetXdp, step, -2000f..2000f, allowNegative = true) { v ->
                    setStyle { it.copy(offsetXdp = v) }
                }
                NumberField("Y, dp", st.offsetYdp, step, -2000f..2000f, allowNegative = true) { v ->
                    setStyle { it.copy(offsetYdp = v) }
                }
            }
        }
        Hint("X: + праворуч, − ліворуч. Y: + вниз, − вгору. ⟲ — повернути на прив'язку.")
        HorizontalDivider()
        NumberField("Розмір, dp", st.sizeDp, step, 1f..200f) { v -> setStyle { it.copy(sizeDp = v) } }
        NumberField("Непрозорість, %", st.opacity * 100, 1f, 5f..100f) { v ->
            setStyle { it.copy(opacity = round2(v / 100f)) }
        }
        OpacitySlider(st.opacity) { v -> setStyle { it.copy(opacity = v) } }
        if (st.opacity > 0.8f) {
            Hint("ℹ️ Понад 80% Android 12+ не пропускає дотики крізь кружечок — місце під ним не натискатиметься.")
        }
    }

    ColorCard(st, setStyle)

    SectionCard {
        OutlinedButton(onClick = { setOverlay { it.placementToAll(level) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Застосувати положення й розмір до всіх рівнів")
        }
        Hint("Кольори й видимість інших рівнів не зміняться.")
    }
}

@Composable
private fun GridCell(glyph: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DPad(onMove: (Int, Int) -> Unit, onReset: () -> Unit) {
    @Composable
    fun Key(text: String, onClick: () -> Unit) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }

    @Composable
    fun Gap() = Box(Modifier.size(44.dp))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row { Gap(); Key("↑") { onMove(0, -1) }; Gap() }
        Row { Key("←") { onMove(-1, 0) }; Key("⟲", onReset); Key("→") { onMove(1, 0) } }
        Row { Gap(); Key("↓") { onMove(0, 1) }; Gap() }
    }
}

/** Decimal field with −/+ buttons. Commits on every valid edit; partial input like "0.0" is kept. */
@Composable
private fun NumberField(
    label: String,
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
    allowNegative: Boolean = false,
    onChange: (Float) -> Unit,
) {
    fun parse(t: String) = t.trim().replace(',', '.').toFloatOrNull()?.let(::round2)
    var text by remember { mutableStateOf(fmt(value)) }
    LaunchedEffect(value) { if (parse(text) != round2(value)) text = fmt(value) }

    fun commit(v: Float) {
        val c = round2(v.coerceIn(range.start, range.endInclusive))
        text = fmt(c)
        onChange(c)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { commit(value - step) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t
                parse(t)?.takeIf { it in range }?.let(onChange)
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { commit(value + step) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        if (allowNegative) {
            TextButton(onClick = { commit(-value) }) { Text("±") }
        }
    }
}

@Composable
private fun OpacitySlider(value: Float, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(round2(local)) },
        valueRange = 0.05f..1f,
    )
}

@Composable
private fun TemplatesCard(
    o: OverlaySettings,
    st: DotStyle,
    onApply: (DotTemplate) -> Unit,
    setOverlay: ((OverlaySettings) -> OverlaySettings) -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DotTemplate?>(null) }
    SectionCard("💾 Мої шаблони", subtitle = "Шаблон зберігає положення, розмір і прозорість. Колір не змінює.") {
        if (o.templates.isEmpty()) {
            Hint("Поки порожньо. Налаштуйте точку й збережіть.")
        }
        o.templates.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Hint(t.style.normalized().summary())
                }
                FilledTonalButton(onClick = { onApply(t) }) { Text("Застосувати") }
                IconButton(onClick = { deleting = t }) { Icon(Icons.Default.Delete, "Видалити") }
            }
        }
        OutlinedButton(onClick = { saving = true }, modifier = Modifier.fillMaxWidth()) { Text("Зберегти поточне як шаблон") }
    }
    if (saving) {
        var name by remember { mutableStateOf("") }
        val exists = o.templates.any { it.name == name.trim() }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Зберегти шаблон") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Назва") }, singleLine = true)
                    Hint(st.summary())
                    if (o.templates.isNotEmpty()) {
                        Hint("Або перезаписати наявний:")
                        o.templates.forEach { t ->
                            TextButton(onClick = { name = t.name }) { Text(t.name) }
                        }
                    }
                    if (exists) Text("Шаблон «${name.trim()}» буде перезаписано.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    saving = false
                    setOverlay { it.saveTemplate(name.trim(), st) }
                }) { Text(if (exists) "Перезаписати" else "Зберегти") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Скасувати") } },
        )
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Видалити «${t.name}»?") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    setOverlay { it.copy(templates = it.templates.filterNot { x -> x.name == t.name }) }
                }) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun ColorCard(st: DotStyle, setStyle: ((DotStyle) -> DotStyle) -> Unit) {
    SectionCard("🎨 Колір") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PALETTE.forEach { c ->
                val selected = c == st.color
                Box(
                    Modifier
                        .size(40.dp)
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { setStyle { it.copy(color = c) } },
                    contentAlignment = Alignment.Center,
                ) { Dot(c, 30.dp) }
            }
        }
        key(st.color) {
            SettingTextField(
                label = "Свій колір (HEX, напр. FF5722)",
                initial = "%06X".format(st.color and 0xFFFFFF),
                singleLine = true,
            ) { v -> parseHex(v)?.let { c -> setStyle { it.copy(color = c) } } }
        }
    }
}

/** Scaled copy of the real screen: same anchor math as OverlayService, real cutout position. */
@Composable
private fun PhonePreview(st: DotStyle) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val metrics = view.resources.displayMetrics
    val screenW = metrics.widthPixels / density
    val screenH = metrics.heightPixels / density
    val previewW = 170f
    val scale = previewW / screenW
    val previewH = (screenH * scale).coerceIn(200f, 420f)

    val cutout = remember(config) {
        view.display?.cutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.width() * it.height() }
    }
    val camX = cutout?.exactCenterX()?.div(density) ?: (screenW / 2f)
    val camY = cutout?.exactCenterY()?.div(density) ?: 12f

    val r = st.sizeDp / 2f
    val (ax, ay) = if (st.corner == Corner.CAMERA) {
        camX to camY
    } else {
        val x = when (st.corner.col) { 0 -> r; 1 -> screenW / 2f; else -> screenW - r }
        val y = when (st.corner.row) { 0 -> r; 1 -> screenH / 2f; else -> screenH - r }
        x to y
    }
    val cx = (ax + st.offsetXdp) * scale
    val cy = (ay + st.offsetYdp) * scale
    val d = (st.sizeDp * scale).coerceAtLeast(3f)

    Box(
        Modifier
            .width(previewW.dp + 8.dp)
            .height(previewH.dp + 8.dp)
            .border(4.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(18.dp)),
        ) {
            Column(Modifier.padding(top = 28.dp, start = 14.dp, end = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { i ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (i % 2 == 0) 0.9f else 0.6f)
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)),
                    )
                }
            }
            // Camera hole.
            val hole = 4f
            Box(
                Modifier
                    .offset((camX * scale - hole / 2).dp, (camY * scale - hole / 2).dp)
                    .size(hole.dp)
                    .background(Color.Black, CircleShape),
            )
            if (st.visible) {
                Box(Modifier.offset((cx - d / 2).dp, (cy - d / 2).dp)) { Dot(st.color, d.dp, st.opacity) }
            }
        }
    }
}

private fun parseHex(s: String): Long? {
    val h = s.trim().removePrefix("#")
    if (h.length != 6 && h.length != 8) return null
    val v = h.toLongOrNull(16) ?: return null
    return if (h.length == 6) 0xFF000000 or v else v
}
