package com.vanago.alertdot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanago.alertdot.app
import com.vanago.alertdot.data.AppSettings
import com.vanago.alertdot.domain.AlertTypeDef
import com.vanago.alertdot.domain.LevelRule
import com.vanago.alertdot.domain.ParserRules
import com.vanago.alertdot.domain.RuleBasedAlertParser
import kotlinx.coroutines.launch

@Composable
fun SourceScreen(settings: AppSettings, update: UpdateSettings) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val last by app.telegramSource.last.collectAsStateWithLifecycle()

    SectionCard("📨 Telegram-бот", subtitle = "Застосунок читає сповіщення Telegram від вашого бота й міняє колір кружечка.") {
        SettingTextField(
            label = "Назва бота",
            initial = settings.chatFilter,
            singleLine = true,
            supporting = "Як у заголовку сповіщення, напр. AirAlert. Кілька — через кому.",
        ) { v -> update { it.copy(chatFilter = v) } }
        Hint("У Telegram для цього чату має бути увімкнено показ тексту повідомлень у сповіщеннях, а сам чат не вимкнений.")
    }

    SectionCard("Останнє сповіщення") {
        val l = last
        if (l == null) {
            Hint("Поки нічого не отримано. Надішліть ботові будь-що або дочекайтесь повідомлення.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (l.accepted && !l.result.contains("ігнор")) "✅" else "⚠️")
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(l.message.chatTitle, style = MaterialTheme.typography.titleSmall)
                    Text(l.message.text, style = MaterialTheme.typography.bodyMedium)
                    Hint(l.result)
                }
            }
        }
    }

    SectionCard("🌐 Ручна перевірка через API", subtitle = "Кнопка «Оновити зараз» на головній питає alerts.in.ua напряму. У фоні нічого не опитується.") {
        SettingTextField(
            label = "Токен alerts.in.ua",
            initial = settings.apiToken,
            singleLine = true,
            password = true,
            supporting = "Той самий, що ALARM у .env бота. Зберігається лише на телефоні.",
        ) { v -> update { it.copy(apiToken = v.trim()) } }
        SettingTextField(
            label = "Місто / громада",
            initial = settings.apiCity,
            singleLine = true,
            supporting = "Точно як location_title в API (CITY у config.cfg бота).",
        ) { v -> update { it.copy(apiCity = v) } }
    }

    ExpandableCard("⚙️ Розширені: правила розпізнавання", subtitle = "Ключові слова рівнів і список типів загроз") {
        Hint("Ключові слова або емодзі через кому, без урахування регістру. Перевірка в порядку: Зелений → Жовтий → Червоний. Повідомлення без збігу ігнорується.")
        ParserRules.PARSED_LEVELS.forEach { level ->
            SettingTextField(
                label = "${level.emoji()} ${level.label()}",
                initial = joinKeywords(settings.parser.keywordsFor(level)),
            ) { v ->
                update { s ->
                    val others = s.parser.levelRules.filterNot { it.level == level }
                    val rules = (others + LevelRule(level, splitKeywords(v)))
                        .sortedBy { ParserRules.PARSED_LEVELS.indexOf(it.level) }
                    s.copy(parser = s.parser.copy(levelRules = rules))
                }
            }
        }
        SwitchRow("Скидати тип при відбої", settings.parser.clearTypeOnGreen) { on ->
            update { it.copy(parser = it.parser.copy(clearTypeOnGreen = on)) }
        }
        TextButton(onClick = {
            update { it.copy(parser = it.parser.copy(levelRules = ParserRules.DEFAULT_LEVEL_RULES)) }
        }) { Text("Відновити типові рівні") }
    }

    var editing by remember { mutableStateOf<AlertTypeDef?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AlertTypeDef?>(null) }

    ExpandableCard("⚙️ Розширені: типи загроз", subtitle = "Порядок = пріоритет: якщо типів кілька, береться верхній") {
        val types = settings.parser.types
        types.forEachIndexed { index, t ->
            if (index > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typeWithEmoji(t.name))
                    Text(joinKeywords(t.keywords), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = index > 0, onClick = { update { it.moveType(index, -1) } }) {
                    Icon(Icons.Default.KeyboardArrowUp, "Вгору")
                }
                IconButton(enabled = index < types.lastIndex, onClick = { update { it.moveType(index, 1) } }) {
                    Icon(Icons.Default.KeyboardArrowDown, "Вниз")
                }
                IconButton(onClick = { editing = t }) { Icon(Icons.Default.Edit, "Редагувати") }
                IconButton(onClick = { deleting = t }) { Icon(Icons.Default.Delete, "Видалити") }
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Text("Додати тип")
        }
        TextButton(onClick = {
            update { it.copy(parser = it.parser.copy(types = ParserRules.DEFAULT_TYPES)) }
        }) { Text("Відновити типові типи") }
    }

    ExpandableCard("🔎 Перевірити текст", subtitle = "Вставте повідомлення й подивіться, як воно розпізнається") {
        var sample by remember { mutableStateOf("🔴 Red level — missile, drone threat") }
        OutlinedTextField(
            value = sample, onValueChange = { sample = it },
            label = { Text("Текст повідомлення") }, modifier = Modifier.fillMaxWidth(),
        )
        val parsed = RuleBasedAlertParser(settings.parser).parse(sample)
        Text(
            if (parsed == null) "❌ Не розпізнано — буде проігноровано" else "${parsed.level.emoji()} ${parsed.level.label()} · ${typeWithEmoji(parsed.type)}",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    val existingNames = settings.parser.types.map { it.name }
    if (adding) {
        TypeDialog(null, existingNames, onDismiss = { adding = false }) { name, keywords ->
            adding = false
            update { it.copy(parser = it.parser.copy(types = it.parser.types + AlertTypeDef(name, keywords))) }
        }
    }
    editing?.let { original ->
        TypeDialog(original, existingNames - original.name, onDismiss = { editing = null }) { name, keywords ->
            editing = null
            scope.launch {
                if (name != original.name) app.settingsRepo.renameType(original.name, name)
                app.settingsRepo.update { s ->
                    s.copy(parser = s.parser.copy(types = s.parser.types.map {
                        if (it.name == name) it.copy(keywords = keywords) else it
                    }))
                }
            }
        }
    }
    deleting?.let { t ->
        val ruleCount = settings.sound.rules.count { it.type == t.name }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Видалити «${t.name}»?") },
            text = { if (ruleCount > 0) Text("Також буде видалено звукових правил: $ruleCount.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { app.settingsRepo.deleteType(t.name) }
                }) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Скасувати") } },
        )
    }
}

private fun AppSettings.moveType(index: Int, delta: Int): AppSettings {
    val list = parser.types.toMutableList()
    val target = index + delta
    if (target !in list.indices) return this
    list.add(target, list.removeAt(index))
    return copy(parser = parser.copy(types = list))
}

@Composable
private fun TypeDialog(
    original: AlertTypeDef?,
    takenNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var keywords by remember { mutableStateOf(joinKeywords(original?.keywords.orEmpty())) }
    val trimmed = name.trim()
    val error = when {
        trimmed.isEmpty() -> "Назва не може бути порожньою"
        takenNames.any { it.equals(trimmed, ignoreCase = true) } -> "Такий тип уже є"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Новий тип" else "Редагувати тип") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Назва") },
                    singleLine = true, isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = keywords, onValueChange = { keywords = it },
                    label = { Text("Ключові слова / емодзі (через кому)") },
                )
                if (original != null && trimmed != original.name && error == null) {
                    Text("Звукові правила для цього типу буде перейменовано.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = { onSave(trimmed, splitKeywords(keywords)) }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
