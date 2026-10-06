package io.nekohasekai.sfa.compose.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.component.PreferenceSection
import io.nekohasekai.sfa.database.Settings
import java.io.File
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun RuleSetsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf(Settings.routingConfigJson) }
    val arr = settingsObject(raw).optJSONArray("ruleSets") ?: JSONArray()
    val entries = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    var tag by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("binary") }
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun persist(next: List<JSONObject>) {
        val root = settingsObject(Settings.routingConfigJson).put("ruleSets", JSONArray(next))
        raw = root.toString()
        Settings.routingConfigJson = raw
    }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                busy = true
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val dir =
                                    File(context.filesDir, "user_rule_sets").apply { mkdirs() }
                                val file =
                                    File(
                                        dir,
                                        "${UUID.randomUUID()}.${if (format == "binary") "srs" else "json"}",
                                    )
                                try {
                                    context.contentResolver.openInputStream(uri)!!.use { input ->
                                        file.outputStream().use { output ->
                                            val buffer = ByteArray(8192)
                                            var total = 0
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                total += count
                                                require(total <= 16 * 1024 * 1024) {
                                                    "Файл больше 16 МБ"
                                                }
                                                output.write(buffer, 0, count)
                                            }
                                        }
                                    }
                                    val ruleSet =
                                        JSONObject()
                                            .put("tag", tag)
                                            .put("type", "local")
                                            .put("format", format)
                                            .put("path", file.absolutePath)
                                    Libbox.checkConfig(
                                        JSONObject()
                                            .put(
                                                "route",
                                                JSONObject()
                                                    .put("rule_set", JSONArray().put(ruleSet)),
                                            )
                                            .toString()
                                    )
                                    ruleSet
                                } catch (e: Exception) {
                                    file.delete()
                                    throw e
                                }
                            }
                        }
                    result
                        .onSuccess { item ->
                            val current =
                                settingsObject(Settings.routingConfigJson).optJSONArray("ruleSets")
                                    ?: JSONArray()
                            persist(
                                (0 until current.length()).mapNotNull {
                                    current.optJSONObject(it)
                                } + item
                            )
                            open = false
                        }
                        .onFailure { error = it.message ?: "Не удалось импортировать список" }
                    busy = false
                }
            }
        }
    ClientSettingsPage("Файлы маршрутизации", navController) {
        PreferenceSection(
            "Свои списки",
            Icons.Outlined.Public,
            "Добавьте SRS или исходный JSON, затем выберите имя списка в правиле.",
        ) {
            if (entries.isEmpty()) Text("Списки пока не добавлены", Modifier.padding(16.dp))
            entries.forEachIndexed { index, item ->
                ListItem(
                    headlineContent = { Text(item.optString("tag")) },
                    supportingContent = {
                        Text(
                            if (item.optString("type") == "local")
                                "Локальный файл · ${item.optString("format")}"
                            else item.optString("url")
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                val rules =
                                    settingsObject(Settings.routingConfigJson).optJSONArray("rules")
                                        ?: JSONArray()
                                val used =
                                    (0 until rules.length()).any { n ->
                                        rules
                                            .optJSONObject(n)
                                            ?.optString("ruleSet")
                                            ?.split(',', ';', '\n')
                                            ?.map(String::trim)
                                            ?.contains(item.optString("tag")) == true
                                    }
                                if (used)
                                    error =
                                        "Список используется в правиле. Сначала удалите его из условий правила."
                                else persist(entries.filterIndexed { n, _ -> n != index })
                            }
                        ) {
                            Icon(Icons.Default.Delete, "Удалить список")
                        }
                    },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                )
            }
            TextButton(
                enabled = !busy,
                onClick = {
                    tag = ""
                    url = ""
                    open = true
                },
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить список")
            }
        }
    }
    if (open) {
        val tagValid =
            tag.matches(Regex("[A-Za-z0-9_.-]+")) &&
                entries.none { it.optString("tag") == tag } &&
                !tag.startsWith("geosite-") &&
                !tag.startsWith("geoip-") &&
                tag !in io.nekohasekai.sfa.utils.RoutingPresets.remoteRuleSets
        val urlValid =
            runCatching {
                    URI(url).let {
                        it.scheme in setOf("http", "https") &&
                            !it.host.isNullOrBlank() &&
                            it.userInfo == null
                    }
                }
                .getOrDefault(false)
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("Добавить список") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        tag,
                        { tag = it.trim() },
                        label = { Text("Имя списка") },
                        supportingText = {
                            Text("Латинские буквы, цифры, точка, дефис; например my-sites")
                        },
                        isError = !tagValid,
                    )
                    SettingChoice(
                        "Формат",
                        format,
                        listOf("binary" to "SRS · бинарный", "source" to "JSON · исходный"),
                    ) {
                        format = it
                    }
                    OutlinedTextField(
                        url,
                        { url = it.trim() },
                        label = { Text("URL для обновлений") },
                        supportingText = { Text("Оставьте пустым для импорта файла") },
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = tagValid && urlValid && !busy,
                    onClick = {
                        persist(
                            entries +
                                JSONObject()
                                    .put("tag", tag)
                                    .put("type", "remote")
                                    .put("format", format)
                                    .put("url", url)
                                    .put("download_detour", "direct")
                        )
                        open = false
                    },
                ) {
                    Text("Добавить URL")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = tagValid && !busy,
                    onClick = { importFile.launch(arrayOf("*/*")) },
                ) {
                    Text("Выбрать файл")
                }
            },
        )
    }
    if (error != null)
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Не удалось изменить список") },
            text = { Text(error!!) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
}
