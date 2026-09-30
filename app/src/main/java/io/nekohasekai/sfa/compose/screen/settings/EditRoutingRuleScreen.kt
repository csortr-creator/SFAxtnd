package io.nekohasekai.sfa.compose.screen.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.models.RoutingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRoutingRuleScreen(
    navController: NavController,
    ruleIndex: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaffoldPadding = LocalScaffoldPadding.current

    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var domainSuffix by remember { mutableStateOf("") }
    var domainKeyword by remember { mutableStateOf("") }
    var ipCidr by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var sourceIp by remember { mutableStateOf("") }
    var sourcePort by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var ruleSet by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiBssid by remember { mutableStateOf("") }
    var clashMode by remember { mutableStateOf("") }
    var outbound by remember { mutableStateOf(RoutingRule.OUTBOUND_PROXY) }
    var dnsRule by remember { mutableStateOf(false) }

    var outboundMenuOpen by remember { mutableStateOf(false) }
    var networkMenuOpen by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(ruleIndex) {
        val raw = withContext(Dispatchers.IO) { Settings.routingConfigJson }
        val rules = decodeRules(raw)
        if (ruleIndex in rules.indices) {
            val rule = rules[ruleIndex]
            name = rule.name
            domain = rule.domain.ifBlank { if (rule.type == RoutingRule.Type.DOMAIN) rule.value else "" }
            domainSuffix = rule.domainSuffix.ifBlank { if (rule.type == RoutingRule.Type.DOMAIN_SUFFIX) rule.value else "" }
            domainKeyword = rule.domainKeyword.ifBlank { if (rule.type == RoutingRule.Type.DOMAIN_KEYWORD) rule.value else "" }
            ipCidr = rule.ipCidr.ifBlank { if (rule.type == RoutingRule.Type.IP_CIDR) rule.value else "" }
            port = rule.port
            sourceIp = rule.sourceIpCidr
            sourcePort = rule.sourcePort
            packageName = rule.packageName.ifBlank { if (rule.type == RoutingRule.Type.PACKAGE_NAME) rule.value else "" }
            ruleSet = rule.ruleSet
            network = rule.network
            protocol = rule.protocol.ifBlank { if (rule.type == RoutingRule.Type.PROTOCOL) rule.value else "" }
            wifiSsid = rule.wifiSsid
            wifiBssid = rule.wifiBssid
            clashMode = rule.clashMode
            outbound = rule.outbound.ifBlank { RoutingRule.OUTBOUND_PROXY }
            dnsRule = rule.dnsRule
        }
        loaded = true
    }

    fun save(delete: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val raw = Settings.routingConfigJson
            val root = if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            val rules = decodeRules(raw).toMutableList()
            if (delete) {
                if (ruleIndex in rules.indices) rules.removeAt(ruleIndex)
            } else {
                val rule = RoutingRule(
                    name = name.trim(),
                    domain = domain.trim(),
                    domainSuffix = domainSuffix.trim(),
                    domainKeyword = domainKeyword.trim(),
                    ipCidr = ipCidr.trim(),
                    port = port.trim(),
                    sourceIpCidr = sourceIp.trim(),
                    sourcePort = sourcePort.trim(),
                    packageName = packageName.trim(),
                    ruleSet = ruleSet.trim(),
                    network = network.trim(),
                    protocol = protocol.trim(),
                    wifiSsid = wifiSsid.trim(),
                    wifiBssid = wifiBssid.trim(),
                    clashMode = clashMode.trim(),
                    outbound = outbound,
                    dnsRule = dnsRule,
                )
                if (ruleIndex in rules.indices) {
                    rules[ruleIndex] = rule
                } else {
                    rules.add(rule)
                }
            }
            root.put("rules", encodeRules(rules))
            if (!root.has("dns")) root.put("dns", JSONObject())
            if (!root.has("geo")) root.put("geo", JSONObject().put("sourceId", ""))
            Settings.routingConfigJson = root.toString()
            withContext(Dispatchers.Main) {
                navController.navigateUp()
            }
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(if (ruleIndex < 0) "Новый маршрут" else "Маршрут") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            actions = {
                if (ruleIndex >= 0) {
                    IconButton(onClick = { save(delete = true) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = {
                    val hasMatch = listOf(
                        domain, domainSuffix, domainKeyword, ipCidr, port, sourceIp, sourcePort,
                        packageName, ruleSet, protocol, wifiSsid, wifiBssid, clashMode,
                    ).any { it.isNotBlank() }
                    if (hasMatch) save(delete = false)
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Сохранить")
                }
            },
        )
    }

    if (!loaded) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text("Домен") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = domainSuffix, onValueChange = { domainSuffix = it }, label = { Text("Суффикс домена") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = domainKeyword, onValueChange = { domainKeyword = it }, label = { Text("Ключевое слово домена") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = ipCidr, onValueChange = { ipCidr = it }, label = { Text("Целевой IP / CIDR") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Целевой порт") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = sourceIp, onValueChange = { sourceIp = it }, label = { Text("Исходный IP / CIDR") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = sourcePort, onValueChange = { sourcePort = it }, label = { Text("Исходный порт") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

        ListItem(
            headlineContent = { Text("Приложения") },
            supportingContent = {
                Text(
                    packageName.ifBlank { "Не указано" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch(Dispatchers.IO) {
                        val pm = context.packageManager
                        val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                            .map { app ->
                                val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
                                app.packageName to label
                            }
                            .sortedBy { it.second.lowercase() }
                        withContext(Dispatchers.Main) {
                            apps = list
                            showAppPicker = true
                        }
                    }
                },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (packageName.isNotBlank()) {
            TextButton(onClick = { packageName = "" }) { Text("Очистить приложения") }
        }

        OutlinedTextField(value = ruleSet, onValueChange = { ruleSet = it }, label = { Text("Набор правил (tag .srs)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

        ListItem(
            headlineContent = { Text("Сеть") },
            supportingContent = {
                Text(
                    network.ifBlank { "Не указано (любая)" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { networkMenuOpen = true },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
        DropdownMenu(expanded = networkMenuOpen, onDismissRequest = { networkMenuOpen = false }) {
            DropdownMenuItem(text = { Text("Не указано") }, onClick = { network = ""; networkMenuOpen = false })
            listOf("tcp", "udp", "tcp,udp").forEach { net ->
                DropdownMenuItem(text = { Text(net) }, onClick = { network = net; networkMenuOpen = false })
            }
        }

        OutlinedTextField(value = protocol, onValueChange = { protocol = it }, label = { Text("Протокол") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = { Text("SSID Wi-Fi") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = wifiBssid, onValueChange = { wifiBssid = it }, label = { Text("BSSID Wi-Fi") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = clashMode, onValueChange = { clashMode = it }, label = { Text("Режим Clash") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

        ListItem(
            headlineContent = { Text("Создать DNS-правило") },
            trailingContent = {
                Switch(checked = dnsRule, onCheckedChange = { dnsRule = it })
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )

        ListItem(
            headlineContent = { Text("Выход через") },
            supportingContent = {
                Text(outbound, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable { outboundMenuOpen = true },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
        DropdownMenu(expanded = outboundMenuOpen, onDismissRequest = { outboundMenuOpen = false }) {
            listOf(RoutingRule.OUTBOUND_PROXY, RoutingRule.OUTBOUND_DIRECT, RoutingRule.OUTBOUND_BLOCK).forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { outbound = item; outboundMenuOpen = false })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAppPicker) {
        val selected = packageName.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        val filtered = apps.filter {
            appQuery.isBlank() ||
                it.first.contains(appQuery, true) ||
                it.second.contains(appQuery, true)
        }
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Приложения") },
            text = {
                Column {
                    OutlinedTextField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        label = { Text("Поиск") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(filtered, key = { it.first }) { (pkg, label) ->
                            val checked = pkg in selected
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selected.add(pkg) else selected.remove(pkg)
                                            packageName = selected.sorted().joinToString(",")
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (checked) selected.remove(pkg) else selected.add(pkg)
                                    packageName = selected.sorted().joinToString(",")
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { packageName = ""; showAppPicker = false }) { Text("Сбросить") }
            },
        )
    }
}

private fun decodeRules(raw: String): List<RoutingRule> {
    if (raw.isBlank()) return emptyList()
    return try {
        val root = JSONObject(raw)
        val rulesJson = root.optJSONArray("rules") ?: return emptyList()
        buildList {
            for (i in 0 until rulesJson.length()) {
                val obj = rulesJson.optJSONObject(i) ?: continue
                add(ruleFromJson(obj))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun ruleFromJson(obj: JSONObject): RoutingRule {
    val type = try {
        RoutingRule.Type.valueOf(obj.optString("type", RoutingRule.Type.DOMAIN.name))
    } catch (_: Exception) {
        RoutingRule.Type.DOMAIN
    }
    return RoutingRule(
        name = obj.optString("name"),
        domain = obj.optString("domain"),
        domainSuffix = obj.optString("domainSuffix"),
        domainKeyword = obj.optString("domainKeyword"),
        ipCidr = obj.optString("ipCidr"),
        port = obj.optString("port"),
        sourceIpCidr = obj.optString("sourceIpCidr"),
        sourcePort = obj.optString("sourcePort"),
        packageName = obj.optString("packageName"),
        ruleSet = obj.optString("ruleSet"),
        network = obj.optString("network"),
        protocol = obj.optString("protocol"),
        wifiSsid = obj.optString("wifiSsid"),
        wifiBssid = obj.optString("wifiBssid"),
        clashMode = obj.optString("clashMode"),
        outbound = obj.optString("outbound", RoutingRule.OUTBOUND_PROXY).ifBlank { RoutingRule.OUTBOUND_PROXY },
        dnsRule = obj.optBoolean("dnsRule", false),
        type = type,
        value = obj.optString("value"),
    )
}

private fun encodeRules(rules: List<RoutingRule>): JSONArray {
    val arr = JSONArray()
    rules.forEach { rule ->
        arr.put(
            JSONObject()
                .put("name", rule.name)
                .put("domain", rule.domain)
                .put("domainSuffix", rule.domainSuffix)
                .put("domainKeyword", rule.domainKeyword)
                .put("ipCidr", rule.ipCidr)
                .put("port", rule.port)
                .put("sourceIpCidr", rule.sourceIpCidr)
                .put("sourcePort", rule.sourcePort)
                .put("packageName", rule.packageName)
                .put("ruleSet", rule.ruleSet)
                .put("network", rule.network)
                .put("protocol", rule.protocol)
                .put("wifiSsid", rule.wifiSsid)
                .put("wifiBssid", rule.wifiBssid)
                .put("clashMode", rule.clashMode)
                .put("outbound", rule.outbound)
                .put("dnsRule", rule.dnsRule)
                .put("type", rule.type.name)
                .put("value", rule.value),
        )
    }
    return arr
}
