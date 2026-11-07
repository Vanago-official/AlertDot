package com.vanago.alertdot.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanago.alertdot.app
import com.vanago.alertdot.data.AppSettings
import com.vanago.alertdot.domain.AlertLevel
import com.vanago.alertdot.domain.ParserRules
import com.vanago.alertdot.domain.SoundRule
import com.vanago.alertdot.domain.SoundSettings
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun SoundsScreen(settings: AppSettings, update: UpdateSettings) {
    val context = LocalContext.current
    val app = context.app
    val sound = settings.sound
    val playing by app.player.playing.collectAsStateWithLifecycle()
    fun set(t: (SoundSettings) -> SoundSettings) = update { it.copy(sound = t(it.sound)) }

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val dndOk = remember(resumeTick) { Permissions.hasDndAccess(context) }

    if (playing) {
        Button(onClick = { app.player.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Зупинити звук") }
    }

    SectionCard("🔊 Загальне") {
        SwitchRow("Звуки увімкнено", sound.enabled) { on -> set { it.copy(enabled = on) } }
        SwitchRow(
            "Ігнорувати «Не турбувати»", sound.ignoreDnd,
            subtitle = "Звук іде потоком будильника. Якщо «Не турбувати» блокує й будильники, застосунок тимчасово вимкне цей режим на час звуку.",
        ) { on -> set { it.copy(ignoreDnd = on) } }
        if (sound.ignoreDnd && !dndOk) {
            Text(
                "Потрібен доступ до «Не турбувати»: Налаштування → Сповіщення → Доступ до «Не турбувати» → AlertDot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { context.launchSafely(Permissions.dndAccessIntent()) }) { Text("Надати доступ") }
        }
    }

    SectionCard("🌙 Тихі години") {
        SwitchRow(
            "Тихі години", sound.quietHours.enabled,
            subtitle = "Без звуку й вібрації, але кружечок змінює колір",
        ) { on -> set { it.copy(quietHours = it.quietHours.copy(enabled = on)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                pickTime(context, sound.quietHours.startMinutes) { m ->
                    set { it.copy(quietHours = it.quietHours.copy(startMinutes = m)) }
                }
            }) { Text("Від ${formatMinutes(sound.quietHours.startMinutes)}") }
            OutlinedButton(onClick = {
                pickTime(context, sound.quietHours.endMinutes) { m ->
                    set { it.copy(quietHours = it.quietHours.copy(endMinutes = m)) }
                }
            }) { Text("До ${formatMinutes(sound.quietHours.endMinutes)}") }
        }
    }

    var editing by remember { mutableStateOf<SoundRule?>(null) }
    var deleting by remember { mutableStateOf<SoundRule?>(null) }

    SectionCard(
        "🎵 Що й коли грати",
        subtitle = "Звук грає лише коли стан змінюється. Спрацьовує найточніше правило: «тип + рівень», далі «тільки тип», далі «тільки рівень».",
    ) {
        if (sound.rules.isEmpty()) Text("Правил немає — звуків не буде.")
        sound.rules.forEach { rule ->
            RuleCard(
                rule = rule,
                levelColor = rule.level?.let { settings.overlay.colorFor(it) },
                onToggle = { on -> set { s -> s.copy(rules = s.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) } },
                onTest = { app.player.play(rule, sound.ignoreDnd) },
                onEdit = { editing = rule },
                onDelete = { deleting = rule },
            )
        }
        OutlinedButton(onClick = { editing = SoundRule(id = UUID.randomUUID().toString()) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Text("Додати правило")
        }
        TextButton(onClick = { set { it.copy(rules = SoundSettings.DEFAULT_RULES) } }) { Text("Відновити типові правила") }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            types = settings.parser.types.map { it.name },
            onDismiss = { editing = null },
            onTest = { app.player.play(it, sound.ignoreDnd) },
            onSave = { saved ->
                editing = null
                set { s ->
                    val exists = s.rules.any { it.id == saved.id }
                    s.copy(rules = if (exists) s.rules.map { if (it.id == saved.id) saved else it } else s.rules + saved)
                }
            },
        )
    }
    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Видалити правило?") },
            text = { Text(rule.conditionLabel()) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    set { s -> s.copy(rules = s.rules.filterNot { it.id == rule.id }) }
                }) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Скасувати") } },
        )
    }
}

private fun AlertLevel.locative(): String = when (this) {
    AlertLevel.GREEN -> "зеленому"
    AlertLevel.YELLOW -> "жовтому"
    AlertLevel.RED -> "червоному"
    AlertLevel.UNKNOWN -> "невідомому"
}

fun SoundRule.conditionLabel(): String = when {
    type != null && level != null -> "${typeWithEmoji(type)} на ${level.locative()} рівні"
    type != null -> "${typeWithEmoji(type)}, будь-який рівень"
    level != null -> "${level.emoji()} ${level.label()} рівень"
    else -> "Будь-яка зміна"
}

@Composable
private fun RuleCard(
    rule: SoundRule,
    levelColor: Long?,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().alpha(if (rule.enabled) 1f else 0.55f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 12.dp)) { Dot(levelColor ?: 0xFF9E9E9E, 14.dp) }
                Column(Modifier.weight(1f)) {
                    Text(rule.conditionLabel(), style = MaterialTheme.typography.titleSmall)
                    val details = buildList {
                        add(if (rule.soundUri == null) "🔕 без звуку" else "🎵 ${rule.soundTitle}")
                        if (rule.soundUri != null) add("${rule.volume}%")
                        if (rule.vibrate) add("📳")
                        add(if (rule.loop) "🔁 по колу" else "один раз")
                    }.joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Послухати")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Редагувати") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Видалити") }
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initial: SoundRule,
    types: List<String>,
    onDismiss: () -> Unit,
    onTest: (SoundRule) -> Unit,
    onSave: (SoundRule) -> Unit,
) {
    val context = LocalContext.current
    var rule by remember { mutableStateOf(initial) }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        rule = if (uri == null) {
            rule.copy(soundUri = null, soundTitle = "Без звуку")
        } else {
            val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            rule.copy(soundUri = uri.toString(), soundTitle = title ?: "Системна мелодія")
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        rule = rule.copy(soundUri = uri.toString(), soundTitle = displayName(context, uri) ?: "Аудіофайл")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Правило звуку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownSelector(
                    label = "Тип загрози",
                    selected = rule.type,
                    options = listOf<String?>(null) + types,
                    optionLabel = { it ?: "Будь-який" },
                    onSelect = { rule = rule.copy(type = it) },
                )
                DropdownSelector(
                    label = "Рівень",
                    selected = rule.level,
                    options = listOf<AlertLevel?>(null) + ParserRules.PARSED_LEVELS,
                    optionLabel = { it?.label() ?: "Будь-який" },
                    onSelect = { rule = rule.copy(level = it) },
                )
                Text("Звук: ${if (rule.soundUri == null) "Без звуку" else rule.soundTitle}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук тривоги")
                        rule.soundUri?.takeIf { it.contains("://") }?.let {
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                        }
                        runCatching { ringtoneLauncher.launch(intent) }
                    }) { Text("Мелодія") }
                    OutlinedButton(onClick = { runCatching { fileLauncher.launch(arrayOf("audio/*")) } }) { Text("Файл") }
                }
                Text("Вбудовані мʼякі:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "🟢" to SoundRule.SOUND_BUILTIN_GREEN,
                        "🟡" to SoundRule.SOUND_BUILTIN_YELLOW,
                        "🔴" to SoundRule.SOUND_BUILTIN_RED,
                    ).forEach { (label, uri) ->
                        OutlinedButton(onClick = {
                            rule = rule.copy(soundUri = uri, soundTitle = SoundRule.BUILTIN_TITLES.getValue(uri))
                            onTest(rule)
                        }) { Text(label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        rule = rule.copy(soundUri = SoundRule.SOUND_DEFAULT_ALARM, soundTitle = "Типовий будильник")
                    }) { Text("Типовий") }
                    TextButton(onClick = { rule = rule.copy(soundUri = null, soundTitle = "Без звуку") }) { Text("Без звуку") }
                }
                SettingSlider("Гучність", rule.volume.toFloat(), 0f..100f, { "${it.roundToInt()}%" }) { v ->
                    rule = rule.copy(volume = v.roundToInt())
                }
                SwitchRow("Вібрація", rule.vibrate) { rule = rule.copy(vibrate = it) }
                SwitchRow(
                    "По колу до дотику", rule.loop,
                    subtitle = "Інакше — один раз. Зупинка: сповіщення «Зупинити» або кнопка в застосунку; максимум 10 хв.",
                ) { rule = rule.copy(loop = it) }
                SwitchRow("Правило увімкнено", rule.enabled) { rule = rule.copy(enabled = it) }
                TextButton(onClick = { onTest(rule) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Тест звуку")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rule) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private fun pickTime(context: Context, minutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
