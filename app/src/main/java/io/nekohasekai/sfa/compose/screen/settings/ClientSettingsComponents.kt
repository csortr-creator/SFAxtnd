package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientSettingsPage(
    title: String,
    navController: NavController,
    content: @Composable ColumnScope.() -> Unit,
) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
            },
        )
    }
    val padding = LocalScaffoldPadding.current
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
    ) {
        content()
        if (title != "Оформление")
            Text(
                "Параметры сети применятся после переподключения VPN.",
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
internal fun SettingToggle(
    title: String,
    description: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            if (description.isNotBlank()) {
                { Text(description) }
            } else null,
        trailingContent = { Switch(value, onChange) },
        modifier = Modifier.clickable { onChange(!value) },
        colors =
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
internal fun SettingChoice(
    title: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(choices.firstOrNull { it.first == value }?.second ?: value) },
        trailingContent = { Icon(Icons.Default.ExpandMore, null) },
        modifier = Modifier.clickable { open = true },
        colors =
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onChange(key)
                                    open = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                value == key,
                                {
                                    onChange(key)
                                    open = false
                                },
                            )
                            Text(label, Modifier.padding(top = 12.dp, start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } },
        )
}

@Composable
internal fun SettingNumber(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    suffix: String = "",
) {
    var open by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value.toString()) }
    val valid = draft.toIntOrNull()?.let { it in range } == true
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text("$value $suffix".trim()) },
        modifier =
            Modifier.clickable {
                draft = value.toString()
                open = true
            },
        colors =
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    draft,
                    { draft = it },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text("${range.first}–${range.last} $suffix") },
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        onChange(draft.toInt())
                        open = false
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена") } },
        )
}

internal fun settingsObject(raw: String) =
    runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())

internal val dnsStrategies =
    listOf(
        "prefer_ipv4" to "Предпочитать IPv4",
        "prefer_ipv6" to "Предпочитать IPv6",
        "ipv4_only" to "Только IPv4",
        "ipv6_only" to "Только IPv6",
    )
