package com.vanago.alertdot.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vanago.alertdot.domain.AlertLevel

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A80),
    onPrimary = Color(0xFF3B0907),
    primaryContainer = Color(0xFF5C1A16),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFFFCA28),
    background = Color(0xFF111316),
    surface = Color(0xFF111316),
    surfaceContainer = Color(0xFF1B1E22),
    surfaceContainerHigh = Color(0xFF23272C),
    surfaceContainerHighest = Color(0xFF2C3036),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFC62828),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFFF9A825),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun AlertDotTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, shapes = AppShapes, content = content)
}

fun AlertLevel.emoji(): String = when (this) {
    AlertLevel.GREEN -> "🟢"
    AlertLevel.YELLOW -> "🟡"
    AlertLevel.RED -> "🔴"
    AlertLevel.UNKNOWN -> "⚪"
}

fun AlertLevel.label(): String = when (this) {
    AlertLevel.GREEN -> "Зелений"
    AlertLevel.YELLOW -> "Жовтий"
    AlertLevel.RED -> "Червоний"
    AlertLevel.UNKNOWN -> "Невідомо"
}

/** Big headline on the home screen. */
fun AlertLevel.headline(): String = when (this) {
    AlertLevel.GREEN -> "Відбій"
    AlertLevel.YELLOW -> "Жовтий рівень"
    AlertLevel.RED -> "Тривога"
    AlertLevel.UNKNOWN -> "Чекаю на повідомлення"
}

fun AlertLevel.hint(): String = when (this) {
    AlertLevel.GREEN -> "Небезпеки немає"
    AlertLevel.YELLOW -> "Підвищена небезпека — будьте уважні"
    AlertLevel.RED -> "Пройдіть в укриття"
    AlertLevel.UNKNOWN -> "Стан зʼявиться після першого повідомлення бота"
}

/** Emoji for the default threat type names; custom types get a generic sign. */
fun typeEmoji(name: String?): String = when (name) {
    null -> ""
    "Ядерна" -> "☢️"
    "Хімічна" -> "☣️"
    "Ракетна" -> "🚀"
    "КАБи" -> "💣"
    "Авіаційна" -> "✈️"
    "Дронова" -> "🛸"
    "Артилерійська" -> "💥"
    "Вуличні бої" -> "⚔️"
    "Повітряна тривога" -> "🚨"
    else -> "⚠️"
}

fun typeWithEmoji(name: String?): String = if (name == null) "—" else "${typeEmoji(name)} $name"
