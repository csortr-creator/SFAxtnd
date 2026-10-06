package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.compose.component.PreferenceSection
import io.nekohasekai.sfa.database.Settings
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DnsSettingsScreen(navController: NavController) {
    var raw by remember { mutableStateOf(Settings.routingConfigJson) }
    val dns = settingsObject(raw).optJSONObject("dns") ?: JSONObject()
    val servers = dns.optJSONArray("servers") ?: JSONArray()
    fun update(change: (JSONObject) -> Unit) {
        val root = settingsObject(Settings.routingConfigJson)
        val next = root.optJSONObject("dns") ?: JSONObject()
        change(next)
        root.put("dns", next)
        raw = root.toString()
        Settings.routingConfigJson = raw
    }
    fun set(key: String, value: Any) = update { it.put(key, value) }
    var coreRaw by remember { mutableStateOf(Settings.coreOptionsJson) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var draftTag by remember { mutableStateOf("") }
    var draftAddress by remember { mutableStateOf("") }
    var draftGroup by remember { mutableStateOf("direct") }
    fun edit(index: Int) {
        val server = servers.optJSONObject(index)
        draftTag = server?.optString("tag").orEmpty()
        draftAddress = server?.optString("address").orEmpty()
        draftGroup =
            server?.optString("detour")?.takeIf { it in setOf("direct", "proxy") } ?: "direct"
        editing = index
    }
    fun replaceServers(next: List<JSONObject>) = set("servers", JSONArray(next))
    val list = (0 until servers.length()).mapNotNull { servers.optJSONObject(it) }
    ClientSettingsPage("DNS", navController) {
        PreferenceSection("Режим DNS", Icons.Outlined.Dns) {
            SettingToggle(
                "Собственные настройки DNS",
                "Выключено: настройки подписки с существующими дополнениями. Включено: группы и резервные серверы ниже.",
                dns.optBoolean("managed", false),
            ) {
                update { target ->
                    target.put("managed", it)
                    if (it && list.none { s -> s.optString("detour") == "direct" })
                        target.put(
                            "servers",
                            JSONArray(
                                list +
                                    JSONObject()
                                        .put("tag", "sfa-direct")
                                        .put("address", "udp://1.1.1.1")
                                        .put("detour", "direct")
                            ),
                        )
                    val current = target.optJSONArray("servers") ?: JSONArray()
                    if (
                        it &&
                            (0 until current.length()).none { n ->
                                current.optJSONObject(n)?.optString("detour") == "proxy"
                            }
                    )
                        current.put(
                            JSONObject()
                                .put("tag", "sfa-proxy")
                                .put("address", "https://1.1.1.1/dns-query")
                                .put("detour", "proxy")
                        )
                    target.put("servers", current)
                }
            }
            SettingChoice(
                "DNS по умолчанию",
                dns.optString("defaultGroup", "proxy"),
                listOf("proxy" to "Через прокси", "direct" to "Напрямую"),
            ) {
                set("defaultGroup", it)
            }
            SettingChoice(
                "Резолвер адресов серверов",
                dns.optString("resolverServer"),
                listOf("" to "Первый DNS прямого соединения") +
                    list
                        .filter { it.optString("detour") == "direct" }
                        .map { it.optString("tag") to it.optString("tag") },
            ) {
                set("resolverServer", it)
            }
        }
        for ((group, title) in listOf("direct" to "Прямое соединение", "proxy" to "Через прокси")) {
            PreferenceSection(
                title,
                Icons.Outlined.Dns,
                "Серверы используются сверху вниз. Следующий — после ошибки или тайм-аута. Предпочтение IP относится к резолвингу ядра; режим «только» также фильтрует A/AAAA.",
            ) {
                SettingChoice(
                    "Версия IP для ответа",
                    dns.optString("${group}Strategy", "prefer_ipv4"),
                    dnsStrategies,
                ) {
                    set("${group}Strategy", it)
                }
                SettingNumber(
                    "Тайм-аут одного сервера",
                    dns.optInt("${group}TimeoutSeconds", 5),
                    1..60,
                    { set("${group}TimeoutSeconds", it) },
                    "с",
                )
                val entries = list.withIndex().filter { it.value.optString("detour") == group }
                entries.forEachIndexed { position, (index, item) ->
                    ListItem(
                        headlineContent = { Text(item.optString("tag")) },
                        supportingContent = { Text(item.optString("address")) },
                        modifier = Modifier.clickable { edit(index) },
                        trailingContent = {
                            Row {
                                if (position > 0)
                                    IconButton(
                                        onClick = {
                                            val previous = entries[position - 1].index
                                            val changed = list.toMutableList()
                                            val temp = changed[previous]
                                            changed[previous] = changed[index]
                                            changed[index] = temp
                                            replaceServers(changed)
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, "Поднять")
                                    }
                                IconButton(onClick = { edit(index) }) {
                                    Icon(Icons.Default.Edit, "Изменить")
                                }
                                IconButton(
                                    enabled = entries.size > 1 || !dns.optBoolean("managed"),
                                    onClick = {
                                        update { target ->
                                            target.put(
                                                "servers",
                                                JSONArray(list.filterIndexed { i, _ -> i != index }),
                                            )
                                            if (
                                                target.optString("resolverServer") ==
                                                    item.optString("tag")
                                            )
                                                target.remove("resolverServer")
                                            if (
                                                target.optString("finalServer") ==
                                                    item.optString("tag")
                                            )
                                                target.remove("finalServer")
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, "Удалить")
                                }
                            }
                        },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    )
                }
                TextButton(
                    onClick = {
                        edit(-1)
                        draftGroup = group
                    }
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить DNS")
                }
            }
        }
        val other =
            list.withIndex().filter { it.value.optString("detour") !in setOf("direct", "proxy") }
        if (other.isNotEmpty())
            PreferenceSection("DNS из прежних настроек", Icons.Outlined.Dns) {
                other.forEach { (index, item) ->
                    ListItem(
                        headlineContent = { Text(item.optString("tag")) },
                        supportingContent = { Text(item.optString("address")) },
                        modifier = Modifier.clickable { edit(index) },
                    )
                }
            }
        PreferenceSection("Кэш и ответы", Icons.Outlined.Settings) {
            SettingToggle(
                "Кэш DNS",
                "Повторно использовать полученные ответы",
                dns.optBoolean("cacheEnabled", true),
            ) {
                update { d ->
                    d.put("cacheEnabled", it)
                    if (!it) d.put("optimistic", false)
                }
            }
            SettingToggle(
                "Оптимистичный кэш",
                "Возвращать устаревший ответ сразу и обновлять его в фоне",
                dns.optBoolean("optimistic"),
            ) {
                update { d ->
                    d.put("optimistic", it)
                    if (it) d.put("cacheEnabled", true)
                }
            }
            SettingNumber(
                "Размер кэша",
                dns.optInt("cacheCapacity", 4096),
                1024..65536,
                { set("cacheCapacity", it) },
                "записей",
            )
            SettingToggle(
                "Обратное сопоставление",
                "Сохранять соответствие IP и домена для маршрутизации",
                dns.optBoolean("reverseMapping"),
            ) {
                set("reverseMapping", it)
            }
            SettingToggle(
                "FakeIP",
                "Виртуальные адреса для A/AAAA; требуется собственный DNS и перехват DNS",
                dns.optBoolean("fakeip"),
            ) { enabled ->
                if (enabled && !dns.optBoolean("managed")) return@SettingToggle
                set("fakeip", enabled)
                if (enabled) {
                    val opts = settingsObject(Settings.coreOptionsJson).put("hijack", "on")
                    coreRaw = opts.toString()
                    Settings.coreOptionsJson = coreRaw
                }
            }
            SettingChoice(
                "Перехват DNS (hijack)",
                settingsObject(coreRaw).optString("hijack", "profile"),
                listOf("profile" to "Из подписки", "on" to "Включить", "off" to "Выключить"),
            ) {
                val opts = settingsObject(Settings.coreOptionsJson).put("hijack", it)
                coreRaw = opts.toString()
                Settings.coreOptionsJson = coreRaw
                if (it == "off") set("fakeip", false)
            }
        }
        PreferenceSection("Пресеты DNS", Icons.Outlined.Dns) {
            for ((name, addresses) in
                listOf(
                    "Cloudflare" to listOf("udp://1.1.1.1", "https://1.1.1.1/dns-query"),
                    "Google" to listOf("udp://8.8.8.8", "https://8.8.8.8/dns-query"),
                    "Quad9" to listOf("udp://9.9.9.9", "https://9.9.9.9/dns-query"),
                    "Yandex + Cloudflare" to listOf("udp://77.88.8.8", "https://1.1.1.1/dns-query"),
                )) {
                TextButton(
                    onClick = {
                        update { d ->
                            d.put("managed", true)
                                .put("resolverServer", "sfa-direct")
                                .put("finalServer", "")
                                .put(
                                    "servers",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("tag", "sfa-direct")
                                                .put("address", addresses[0])
                                                .put("detour", "direct")
                                        )
                                        .put(
                                            JSONObject()
                                                .put("tag", "sfa-proxy")
                                                .put("address", addresses[1])
                                                .put("detour", "proxy")
                                        ),
                                )
                        }
                    }
                ) {
                    Text(name)
                }
            }
        }
    }
    if (editing != null) {
        val index = editing!!
        val tagValid =
            draftTag.matches(Regex("[A-Za-z0-9_.-]+")) &&
                draftTag !in setOf("sfa-fakeip", "sfa-bootstrap") &&
                list.withIndex().none { it.index != index && it.value.optString("tag") == draftTag }
        val addressValid =
            runCatching {
                    if (draftAddress == "local") true
                    else {
                        val uri =
                            URI(
                                if (draftAddress.contains("://")) draftAddress
                                else "udp://$draftAddress"
                            )
                        uri.scheme in setOf("udp", "tcp", "tls", "https", "quic", "h3") &&
                            !uri.host.isNullOrBlank() &&
                            (uri.port == -1 || uri.port in 1..65535) &&
                            uri.rawUserInfo == null &&
                            uri.rawFragment == null
                    }
                }
                .getOrDefault(false)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (index < 0) "Добавить DNS" else "Изменить DNS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        draftTag,
                        { draftTag = it.trim() },
                        label = { Text("Имя сервера") },
                        singleLine = true,
                        isError = !tagValid,
                    )
                    OutlinedTextField(
                        draftAddress,
                        { draftAddress = it.trim() },
                        label = { Text("Адрес DNS") },
                        supportingText = {
                            Text("local, udp://, tcp://, tls://, https://, quic://, h3://")
                        },
                        isError = !addressValid,
                    )
                    SettingChoice(
                        "Соединение",
                        draftGroup,
                        listOf("direct" to "Напрямую", "proxy" to "Через прокси"),
                    ) {
                        draftGroup = it
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = tagValid && addressValid,
                    onClick = {
                        val oldTag = list.getOrNull(index)?.optString("tag")
                        val item =
                            JSONObject()
                                .put("tag", draftTag)
                                .put("address", draftAddress)
                                .put("detour", draftGroup)
                        val changed = list.toMutableList()
                        if (index in changed.indices) changed[index] = item else changed.add(item)
                        update { d ->
                            d.put("servers", JSONArray(changed))
                            if (oldTag != null && d.optString("resolverServer") == oldTag)
                                d.put("resolverServer", draftTag)
                            if (oldTag != null && d.optString("finalServer") == oldTag)
                                d.put("finalServer", draftTag)
                        }
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
