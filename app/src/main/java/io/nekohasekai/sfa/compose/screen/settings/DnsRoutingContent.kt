package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.component.PreferenceSection
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun DnsRoutingContent(dns: JSONObject, onChange: (JSONArray) -> Unit) {
    val rules = dns.optJSONArray("rules") ?: JSONArray()
    val list = (0 until rules.length()).mapNotNull { rules.optJSONObject(it) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("{}") }
    val kinds =
        listOf(
            "domain" to "Домены",
            "suffix" to "Суффиксы доменов",
            "keyword" to "Часть домена",
            "ruleset" to "Geosite / SRS",
        )
    val targets =
        listOf(
            "proxy" to "DNS через прокси",
            "direct" to "DNS напрямую",
            "block" to "Отклонить запрос",
        )
    fun save(next: List<JSONObject>) = onChange(JSONArray(next))
    PreferenceSection("Маршрутизация DNS", Icons.Outlined.Dns) {
        Text(
            "Отдельные правила DNS: сверху вниз, до FakeIP и DNS по умолчанию. Требуется режим собственных настроек DNS. Маршрут соединения задаётся отдельно в Маршрутизации.",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        list.forEachIndexed { index, rule ->
            ListItem(
                headlineContent = { Text(rule.optString("value")) },
                supportingContent = {
                    Text(
                        targets.firstOrNull { it.first == rule.optString("target") }?.second
                            ?: "DNS через прокси"
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = {
                                val next =
                                    JSONObject(rule.toString())
                                        .put("enabled", !rule.optBoolean("enabled", true))
                                save(list.toMutableList().also { it[index] = next })
                            }
                        ) {
                            Icon(
                                if (rule.optBoolean("enabled", true)) Icons.Default.CheckCircle
                                else Icons.Default.RadioButtonUnchecked,
                                "Включить правило",
                            )
                        }
                        IconButton(
                            onClick = {
                                editing = index
                                draft = rule.toString()
                            }
                        ) {
                            Icon(Icons.Default.Edit, "Изменить")
                        }
                        IconButton(onClick = { save(list.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Default.Delete, "Удалить")
                        }
                    }
                },
                colors =
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(
                    enabled = index > 0,
                    onClick = {
                        save(
                            list.toMutableList().also {
                                val item = it.removeAt(index)
                                it.add(index - 1, item)
                            }
                        )
                    },
                ) {
                    Text("Выше")
                }
                TextButton(
                    enabled = index < list.lastIndex,
                    onClick = {
                        save(
                            list.toMutableList().also {
                                val item = it.removeAt(index)
                                it.add(index + 1, item)
                            }
                        )
                    },
                ) {
                    Text("Ниже")
                }
            }
        }
        TextButton(
            enabled = dns.optBoolean("managed"),
            onClick = {
                editing = -1
                draft = "{}"
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.Add, null)
            Text("Добавить DNS-правило")
        }
    }
    if (editing != null) {
        val item = settingsObject(draft)
        fun set(key: String, value: Any) {
            draft = settingsObject(draft).put(key, value).toString()
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Правило DNS") },
            text = {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                    SettingChoice("Условие", item.optString("kind", "domain"), kinds) {
                        set("kind", it)
                    }
                    OutlinedTextField(
                        item.optString("value"),
                        { set("value", it) },
                        label = { Text("Значения через запятую") },
                        supportingText = {
                            Text("Например: example.org или geosite:category-ru для SRS")
                        },
                    )
                    SettingChoice(
                        "Тип запроса",
                        item.optString("queryType", ""),
                        listOf("" to "Любой", "A" to "IPv4 · A", "AAAA" to "IPv6 · AAAA"),
                    ) {
                        set("queryType", it)
                    }
                    SettingChoice("Действие", item.optString("target", "proxy"), targets) {
                        set("target", it)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = item.optString("value").isNotBlank(),
                    onClick = {
                        val next =
                            JSONObject(item.toString())
                                .put("kind", item.optString("kind", "domain"))
                                .put("target", item.optString("target", "proxy"))
                        save(
                            list.toMutableList().also {
                                if (editing == -1) it.add(next) else it[editing!!] = next
                            }
                        )
                        editing = null
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Отмена") } },
        )
    }
}
