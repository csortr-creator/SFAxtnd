package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.models.DnsConfig
import io.nekohasekai.sfa.models.DnsServer
import io.nekohasekai.sfa.models.GeoFileSources
import io.nekohasekai.sfa.models.RoutingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutingSettingsScreen(
    navController: NavController,
) {
    val scope = rememberCoroutineScope()
    val scaffoldPadding = LocalScaffoldPadding.current

    var strategy by remember { mutableStateOf(DnsConfig.Strategy.AUTO) }
    var cacheEnabled by remember { mutableStateOf(true) }
    var independentCache by remember { mutableStateOf(false) }
    var reverseMapping by remember { mutableStateOf(false) }
    var finalServer by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<DnsServer>>(emptyList()) }
    var geoSourceId by remember { mutableStateOf("") }
    var geoGeositeUrl by remember { mutableStateOf("") }
    var geoGeoipUrl by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf<List<RoutingRule>>(emptyList()) }

    var strategyMenuOpen by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var editServerIndex by remember { mutableStateOf<Int?>(null) }
    var draftTag by remember { mutableStateOf("") }
    var draftAddress by remember { mutableStateOf("") }
    var draftDetour by remember { mutableStateOf("proxy") }

    fun persist(
        nextStrategy: DnsConfig.Strategy = strategy,
        nextCache: Boolean = cacheEnabled,
        nextIndependent: Boolean = independentCache,
        nextReverse: Boolean = reverseMapping,
        nextFinal: String = finalServer,
        nextServers: List<DnsServer> = servers,
        nextGeoSourceId: String = geoSourceId,
        nextGeoGeositeUrl: String = geoGeositeUrl,
        nextGeoGeoipUrl: String = geoGeoipUrl,
        nextRules: List<RoutingRule> = rules,
    ) {
        strategy = nextStrategy
        cacheEnabled = nextCache
        independentCache = nextIndependent
        reverseMapping = nextReverse
        finalServer = nextFinal
        servers = nextServers
        geoSourceId = nextGeoSourceId
        geoGeositeUrl = nextGeoGeositeUrl
        geoGeoipUrl = nextGeoGeoipUrl
        rules = nextRules
        scope.launch(Dispatchers.IO) {
            Settings.routingConfigJson = encodeRoutingConfig(
                nextStrategy,
                nextCache,
                nextIndependent,
                nextReverse,
                nextFinal,
                nextServers,
                nextGeoSourceId,
                nextGeoGeositeUrl,
                nextGeoGeoipUrl,
                nextRules,
            )
        }
    }

    LaunchedEffect(Unit) {
        val raw = withContext(Dispatchers.IO) { Settings.routingConfigJson }
        val parsed = decodeRoutingConfig(raw)
        strategy = parsed.strategy
        cacheEnabled = parsed.cacheEnabled
        independentCache = parsed.independentCache
        reverseMapping = parsed.reverseMapping
        finalServer = parsed.finalServer
        servers = parsed.servers
        geoSourceId = parsed.geoSourceId
        geoGeositeUrl = parsed.geoGeositeUrl
        geoGeoipUrl = parsed.geoGeoipUrl
        rules = parsed.rules
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text("Роутинг") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(
                text = "Настройки применяются при старте или reload VPN. Файл профиля не меняется.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        SectionHeader("Пресеты")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(Modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    "DNS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            persist(
                                nextStrategy = DnsConfig.Strategy.IPV4_ONLY,
                                nextCache = true,
                                nextIndependent = true,
                                nextReverse = true,
                                nextFinal = "cld",
                                nextServers = listOf(
                                    DnsServer("cld", "https://1.1.1.1/dns-query", "proxy"),
                                    DnsServer("ynd", "77.88.8.8", "direct"),
                                ),
                            )
                        },
                        label = { Text("CF + Yandex") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            persist(
                                nextStrategy = DnsConfig.Strategy.IPV4_ONLY,
                                nextCache = true,
                                nextIndependent = false,
                                nextReverse = false,
                                nextFinal = "cld",
                                nextServers = listOf(
                                    DnsServer("cld", "https://1.1.1.1/dns-query", "proxy"),
                                ),
                            )
                        },
                        label = { Text("Только Cloudflare") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            persist(
                                nextStrategy = DnsConfig.Strategy.AUTO,
                                nextCache = true,
                                nextIndependent = false,
                                nextReverse = false,
                                nextFinal = "",
                                nextServers = emptyList(),
                            )
                        },
                        label = { Text("Очистить DNS") },
                    )
                }
                Text(
                    "Маршруты",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            persist(
                                nextGeoSourceId = GeoFileSources.SAGERNET.id,
                                nextRules = listOf(
                                    RoutingRule(
                                        name = "ru-sites",
                                        ruleSet = "geosite-category-ru",
                                        outbound = RoutingRule.OUTBOUND_DIRECT,
                                        dnsRule = true,
                                    ),
                                    RoutingRule(
                                        name = "ru-ip",
                                        ruleSet = "geoip-ru",
                                        outbound = RoutingRule.OUTBOUND_DIRECT,
                                    ),
                                ),
                            )
                        },
                        label = { Text("RU → direct") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            persist(nextRules = emptyList())
                        },
                        label = { Text("Очистить правила") },
                    )
                }
                Text(
                    "Пресеты можно править вручную ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        SectionHeader("DNS")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                ExposedDropdownMenuBox(
                    expanded = strategyMenuOpen,
                    onExpandedChange = { strategyMenuOpen = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = strategyLabel(strategy),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Стратегия") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyMenuOpen)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = strategyMenuOpen,
                        onDismissRequest = { strategyMenuOpen = false },
                    ) {
                        DnsConfig.Strategy.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(strategyLabel(item)) },
                                onClick = {
                                    persist(nextStrategy = item)
                                    strategyMenuOpen = false
                                },
                            )
                        }
                    }
                }
                SwitchRow(
                    title = "Кэш DNS",
                    checked = cacheEnabled,
                    onCheckedChange = { persist(nextCache = it) },
                )
                SwitchRow(
                    title = "Независимый кэш",
                    checked = independentCache,
                    onCheckedChange = { persist(nextIndependent = it) },
                )
                SwitchRow(
                    title = "Обратный mapping",
                    checked = reverseMapping,
                    onCheckedChange = { persist(nextReverse = it) },
                )
                OutlinedTextField(
                    value = finalServer,
                    onValueChange = { persist(nextFinal = it) },
                    label = { Text("Финальный DNS (tag)") },
                    placeholder = { Text("например cld") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SectionHeader(
            title = "DNS-серверы",
            action = {
                IconButton(
                    onClick = {
                        editServerIndex = null
                        draftTag = ""
                        draftAddress = ""
                        draftDetour = "proxy"
                        showServerDialog = true
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            if (servers.isEmpty()) {
                Text(
                    "Нет серверов — добавьте или выберите пресет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                servers.forEachIndexed { index, server ->
                    ListItem(
                        headlineContent = { Text(server.tag.ifBlank { "—" }) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(server.address)
                                    if (!server.detour.isNullOrBlank()) {
                                        append(" · ")
                                        append(server.detour)
                                    }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    persist(nextServers = servers.toMutableList().also { it.removeAt(index) })
                                },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            editServerIndex = index
                            draftTag = server.tag
                            draftAddress = server.address
                            draftDetour = server.detour.orEmpty()
                            showServerDialog = true
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }

        SectionHeader("Geo (rule-set)")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            ListItem(
                headlineContent = { Text("Из профиля") },
                supportingContent = {
                    Text(
                        "Не подменять rule-set",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    RadioButton(
                        selected = geoSourceId.isBlank(),
                        onClick = { persist(nextGeoSourceId = "") },
                    )
                },
                modifier = Modifier.clickable { persist(nextGeoSourceId = "") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
            GeoFileSources.ALL.forEach { source ->
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = {
                        Text(
                            source.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        RadioButton(
                            selected = geoSourceId == source.id,
                            onClick = { persist(nextGeoSourceId = source.id) },
                        )
                    },
                    trailingContent = {
                        if (geoSourceId == source.id) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable { persist(nextGeoSourceId = source.id) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
            OutlinedTextField(
                value = geoGeositeUrl,
                onValueChange = { persist(nextGeoGeositeUrl = it) },
                label = { Text("Свой base URL geosite") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = geoGeoipUrl,
                onValueChange = { persist(nextGeoGeoipUrl = it) },
                label = { Text("Свой base URL geoip") },
                supportingText = { Text("Файлы: geosite-….srs / geoip-….srs") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        SectionHeader(
            title = "Правила",
            action = {
                IconButton(onClick = { navController.navigate("settings/routing/rule/-1") }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            if (rules.isEmpty()) {
                Text(
                    "Нет правил — добавьте или пресет «RU → direct»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                rules.forEachIndexed { index, rule ->
                    ListItem(
                        headlineContent = { Text(rule.displayTitle()) },
                        supportingContent = {
                            Text(
                                rule.displaySubtitle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    persist(nextRules = rules.toMutableList().also { it.removeAt(index) })
                                },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            navController.navigate("settings/routing/rule/$index")
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Text(if (editServerIndex == null) "DNS-сервер" else "Изменить DNS-сервер")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftTag,
                        onValueChange = { draftTag = it },
                        label = { Text("Tag") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftAddress,
                        onValueChange = { draftAddress = it },
                        label = { Text("Адрес") },
                        placeholder = { Text("https://1.1.1.1/dns-query") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = draftDetour,
                        onValueChange = { draftDetour = it },
                        label = { Text("Detour") },
                        placeholder = { Text("proxy / direct") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (draftTag.isBlank() || draftAddress.isBlank()) return@TextButton
                        val server = DnsServer(
                            tag = draftTag.trim(),
                            address = draftAddress.trim(),
                            detour = draftDetour.trim().ifBlank { null },
                        )
                        val next = servers.toMutableList()
                        val index = editServerIndex
                        if (index == null) {
                            next.add(server)
                        } else if (index in next.indices) {
                            next[index] = server
                        }
                        persist(nextServers = next)
                        showServerDialog = false
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        action?.invoke()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

private fun strategyLabel(strategy: DnsConfig.Strategy): String = when (strategy) {
    DnsConfig.Strategy.AUTO -> "Авто"
    DnsConfig.Strategy.PREFER_IPV4 -> "Сначала IPv4"
    DnsConfig.Strategy.PREFER_IPV6 -> "Сначала IPv6"
    DnsConfig.Strategy.IPV4_ONLY -> "Только IPv4"
    DnsConfig.Strategy.IPV6_ONLY -> "Только IPv6"
}

private data class RoutingConfigState(
    val strategy: DnsConfig.Strategy = DnsConfig.Strategy.AUTO,
    val cacheEnabled: Boolean = true,
    val independentCache: Boolean = false,
    val reverseMapping: Boolean = false,
    val finalServer: String = "",
    val servers: List<DnsServer> = emptyList(),
    val geoSourceId: String = "",
    val geoGeositeUrl: String = "",
    val geoGeoipUrl: String = "",
    val rules: List<RoutingRule> = emptyList(),
)

private fun decodeRoutingConfig(raw: String): RoutingConfigState {
    if (raw.isBlank()) return RoutingConfigState()
    return try {
        val root = JSONObject(raw)
        val dns = root.optJSONObject("dns")
        val geo = root.optJSONObject("geo")
        val rulesJson = root.optJSONArray("rules")

        var strategy = DnsConfig.Strategy.AUTO
        var cacheEnabled = true
        var independentCache = false
        var reverseMapping = false
        var finalServer = ""
        var servers: List<DnsServer> = emptyList()

        if (dns != null) {
            strategy = try {
                DnsConfig.Strategy.valueOf(dns.optString("strategy", DnsConfig.Strategy.AUTO.name))
            } catch (_: Exception) {
                DnsConfig.Strategy.AUTO
            }
            cacheEnabled = dns.optBoolean("cacheEnabled", true)
            independentCache = dns.optBoolean("independentCache", false)
            reverseMapping = dns.optBoolean("reverseMapping", false)
            finalServer = dns.optString("finalServer", "")
            val arr = dns.optJSONArray("servers")
            if (arr != null) {
                servers = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val tag = obj.optString("tag")
                        val address = obj.optString("address")
                        if (tag.isBlank() || address.isBlank()) continue
                        val detour = obj.optString("detour").ifBlank { null }
                        add(DnsServer(tag, address, detour))
                    }
                }
            }
        }

        val rules = buildList {
            if (rulesJson != null) {
                for (i in 0 until rulesJson.length()) {
                    val obj = rulesJson.optJSONObject(i) ?: continue
                    val type = try {
                        RoutingRule.Type.valueOf(obj.optString("type", RoutingRule.Type.DOMAIN.name))
                    } catch (_: Exception) {
                        RoutingRule.Type.DOMAIN
                    }
                    add(
                        RoutingRule(
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
                            outbound = obj.optString("outbound", RoutingRule.OUTBOUND_PROXY)
                                .ifBlank { RoutingRule.OUTBOUND_PROXY },
                            dnsRule = obj.optBoolean("dnsRule", false),
                            type = type,
                            value = obj.optString("value"),
                        ),
                    )
                }
            }
        }

        RoutingConfigState(
            strategy = strategy,
            cacheEnabled = cacheEnabled,
            independentCache = independentCache,
            reverseMapping = reverseMapping,
            finalServer = finalServer,
            servers = servers,
            geoSourceId = geo?.optString("sourceId").orEmpty(),
            geoGeositeUrl = geo?.optString("geositeUrl").orEmpty(),
            geoGeoipUrl = geo?.optString("geoipUrl").orEmpty(),
            rules = rules,
        )
    } catch (_: Exception) {
        RoutingConfigState()
    }
}

private fun encodeRoutingConfig(
    strategy: DnsConfig.Strategy,
    cacheEnabled: Boolean,
    independentCache: Boolean,
    reverseMapping: Boolean,
    finalServer: String,
    servers: List<DnsServer>,
    geoSourceId: String,
    geoGeositeUrl: String,
    geoGeoipUrl: String,
    rules: List<RoutingRule>,
): String {
    val dns = JSONObject()
        .put("strategy", strategy.name)
        .put("cacheEnabled", cacheEnabled)
        .put("independentCache", independentCache)
        .put("reverseMapping", reverseMapping)
        .put("finalServer", finalServer)
    val serversJson = JSONArray()
    servers.forEach { server ->
        val obj = JSONObject()
            .put("tag", server.tag)
            .put("address", server.address)
        if (!server.detour.isNullOrBlank()) {
            obj.put("detour", server.detour)
        }
        serversJson.put(obj)
    }
    dns.put("servers", serversJson)

    val rulesJson = JSONArray()
    rules.forEach { rule ->
        rulesJson.put(
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

    return JSONObject()
        .put("dns", dns)
        .put(
            "geo",
            JSONObject()
                .put("sourceId", geoSourceId)
                .put("geositeUrl", geoGeositeUrl)
                .put("geoipUrl", geoGeoipUrl),
        )
        .put("rules", rulesJson)
        .toString()
}
