package com.vanago.alertdot.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanago.alertdot.app
import com.vanago.alertdot.data.AppSettings
import com.vanago.alertdot.domain.AlertLevel
import com.vanago.alertdot.domain.AlertState
import com.vanago.alertdot.service.OverlayService
import com.vanago.alertdot.service.RefreshResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM HH:mm")

private class SetupStep(
    val title: String,
    val why: String,
    val done: Boolean,
    val action: String,
    val onAction: () -> Unit,
)

@Composable
fun HomeScreen(settings: AppSettings, update: UpdateSettings, navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val state by app.stateRepo.state.collectAsStateWithLifecycle(initialValue = AlertState())
    val playing by app.player.playing.collectAsStateWithLifecycle()

    // Re-check permissions every time the user comes back from system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val overlayOk = remember(resumeTick) { Permissions.canDrawOverlays(context) }
    val listenerOk = remember(resumeTick) { Permissions.hasNotificationAccess(context) }
    val batteryOk = remember(resumeTick) { Permissions.ignoresBatteryOptimizations(context) }
    var notifOk by remember(resumeTick) { mutableStateOf(Permissions.canPostNotifications(context)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = it
    }

    // Keep the service in sync with the setting (e.g. after the system killed it).
    LaunchedEffect(settings.showOverlay, overlayOk) {
        if (settings.showOverlay && overlayOk) OverlayService.start(context)
    }

    var refreshing by remember { mutableStateOf(false) }
    var refreshInfo by remember { mutableStateOf<RefreshResult?>(null) }
    StatusHero(
        state = state,
        dotColor = settings.overlay.colorFor(state.level),
        refreshing = refreshing,
        refreshInfo = refreshInfo,
        onRefresh = {
            if (settings.apiToken.isBlank()) {
                Toast.makeText(context, "Спершу вкажіть токен API у розділі «Джерело»", Toast.LENGTH_LONG).show()
                navigate(Screen.SOURCE)
            } else {
                scope.launch {
                    refreshing = true
                    refreshInfo = app.apiSource.refresh()
                    refreshing = false
                }
            }
        },
    )

    if (playing) {
        Button(
            onClick = { app.player.stop() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("🔇  Зупинити звук", style = MaterialTheme.typography.titleMedium) }
    }

    // Main switch.
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(settings.overlay.colorFor(state.level), 28.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Кружечок на екрані", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Hint(if (settings.showOverlay) "Показується поверх усіх застосунків" else "Вимкнено")
            }
            Switch(checked = settings.showOverlay, onCheckedChange = { on ->
                if (on && !overlayOk) {
                    Toast.makeText(context, "Спершу дозвольте показ поверх інших застосунків", Toast.LENGTH_LONG).show()
                    context.launchSafely(Permissions.overlayIntent(context))
                    return@Switch
                }
                update { it.copy(showOverlay = on) }
                if (on) OverlayService.start(context) else OverlayService.stop(context)
            })
        }
    }

    val steps = buildList {
        add(SetupStep("Показ поверх інших застосунків", "Щоб малювати кружечок", overlayOk, "Дозволити") {
            context.launchSafely(Permissions.overlayIntent(context))
        })
        add(SetupStep(
            "Доступ до сповіщень",
            "Щоб читати повідомлення бота в Telegram. Якщо бачите «Restricted setting» — відкрийте налаштування застосунку → ⋮ → «Allow restricted settings».",
            listenerOk, "Дозволити",
        ) { context.launchSafely(Permissions.notificationAccessIntent(), Permissions.appDetailsIntent(context)) })
        add(SetupStep("Робота у фоні", "Щоб Android не приспав застосунок. На Samsung додайте ще в «Never sleeping apps».", batteryOk, "Дозволити") {
            context.launchSafely(Permissions.batteryIntent(context), Permissions.appDetailsIntent(context))
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(SetupStep("Сповіщення", "Для кнопки «Зупинити», коли звук грає по колу", notifOk, "Дозволити") {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        add(SetupStep("Назва бота", "Щоб реагувати лише на повідомлення вашого бота", settings.chatFilter.isNotBlank(), "Вказати") {
            navigate(Screen.SOURCE)
        })
    }
    SetupCard(steps)

    TestCard(
        types = settings.parser.types.map { it.name },
        onSend = { level, type -> app.simulatedSource.emit(level, type) },
        onReset = { scope.launch { app.engine.reset() } },
    )

    SectionCard("Інше") {
        SwitchRow("Запускати після перезавантаження", settings.autostart, subtitle = "Якщо кружечок увімкнено") { on ->
            update { it.copy(autostart = on) }
        }
        SwitchRow("Темна тема", settings.darkTheme) { on -> update { it.copy(darkTheme = on) } }
    }
}

@Composable
private fun StatusHero(
    state: AlertState,
    dotColor: Long,
    refreshing: Boolean,
    refreshInfo: RefreshResult?,
    onRefresh: () -> Unit,
) {
    val accent = Color(dotColor.toInt())
    val bg by animateColorAsState(
        accent.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.surfaceContainer),
        label = "heroBg",
    )
    // Keep "5 хв тому" fresh: wake only when the label text can actually change, and only while
    // the activity is visible (a plain LaunchedEffect loop kept ticking in the background).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.timestamp, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                val wait = nextLabelChangeIn(state.timestamp, now) ?: break
                delay(wait)
            }
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(dotColor, 72.dp, glow = true)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(state.level.headline(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.level.hint(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.type != null) Pill(typeWithEmoji(state.type))
                if (state.timestamp > 0) Pill("🕒 ${relativeTime(state.timestamp, now)}")
                if (state.source == "simulation") Pill("🧪 тест")
                if (state.source == "api") Pill("🌐 API")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Перевіряю…")
                    } else {
                        Text("🔄  Оновити зараз")
                    }
                }
                Spacer(Modifier.width(12.dp))
                when (refreshInfo) {
                    is RefreshResult.Ok -> Hint("API: ${refreshInfo.line}")
                    is RefreshResult.Error -> Text(
                        refreshInfo.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    null -> Hint("Перевірити рівень через alerts.in.ua")
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SetupCard(steps: List<SetupStep>) {
    val doneCount = steps.count { it.done }
    val allDone = doneCount == steps.size
    var showAll by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (allDone) "✅ Усе налаштовано" else "Підготовка: $doneCount з ${steps.size}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                if (!allDone) Hint("Виконайте кроки нижче, щоб застосунок працював у фоні")
            }
            if (allDone) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Сховати" else "Деталі") }
            }
        }
        if (!allDone) {
            LinearProgressIndicator(
                progress = { doneCount.toFloat() / steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        steps.forEachIndexed { i, step ->
            if (!allDone || showAll) StepRow(i + 1, step)
        }
    }
}

@Composable
private fun StepRow(number: Int, step: SetupStep) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .background(
                    if (step.done) Color(0xFF43A047) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (step.done) "✓" else "$number", color = if (step.done) Color.White else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge)
            if (!step.done) Hint(step.why)
        }
        if (!step.done) FilledTonalButton(onClick = step.onAction) { Text(step.action) }
    }
}

@Composable
private fun TestCard(
    types: List<String>,
    onSend: (AlertLevel, String?) -> Unit,
    onReset: () -> Unit,
) {
    var type by remember { mutableStateOf<String?>(null) }
    ExpandableCard("🧪 Перевірка", subtitle = "Імітувати повідомлення бота, щоб почути звук і побачити колір") {
        DropdownSelector(
            label = "Загроза",
            selected = type,
            options = listOf<String?>(null) + types,
            optionLabel = { if (it == null) "Без типу" else typeWithEmoji(it) },
            onSelect = { type = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AlertLevel.GREEN, AlertLevel.YELLOW, AlertLevel.RED).forEach { level ->
                OutlinedButton(
                    onClick = { onSend(level, if (level == AlertLevel.GREEN) null else type) },
                    modifier = Modifier.weight(1f),
                ) { Text("${level.emoji()} ${level.label()}", maxLines = 1) }
            }
        }
        Hint("Звук грає лише якщо стан змінився.")
        TextButton(onClick = onReset) { Text("Скинути стан") }
    }
}

/**
 * Milliseconds until [relativeTime] for [ts] can show a different text, or null if it never will.
 * Under an hour the label changes every full minute; after that it shows "о HH:mm" until midnight
 * and then a fixed date.
 */
private fun nextLabelChangeIn(ts: Long, now: Long): Long? {
    if (ts <= 0) return null
    val elapsed = (now - ts).coerceAtLeast(0)
    if (elapsed < 60 * 60_000L) return 60_000L - elapsed % 60_000L
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    if (Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() != today) return null
    val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return (midnight - now).coerceAtLeast(1_000L)
}

private fun relativeTime(ts: Long, now: Long): String {
    val minutes = (now - ts).coerceAtLeast(0) / 60_000
    val zoned = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
    return when {
        minutes < 1 -> "щойно"
        minutes < 60 -> "$minutes хв тому"
        zoned.toLocalDate() == LocalDate.now() -> "о ${clockFormat.format(zoned)}"
        else -> dateFormat.format(zoned)
    }
}
