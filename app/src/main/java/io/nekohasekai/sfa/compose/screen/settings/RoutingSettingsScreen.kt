package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSettingsScreen(
    navController: NavController,
) {
    OverrideTopBar {
        TopAppBar(
            title = { Text("Маршруты") },
            navigationIcon = {
                if (navController.previousBackStackEntry != null) {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                }
            },
        )
    }

    val scaffoldPadding = LocalScaffoldPadding.current
    val scope = rememberCoroutineScope()

    var strategy by remember { mutableStateOf(DnsConfig.Strategy.AUTO) }
    var cacheEnabled by remember { mutableStateOf(true) }
    var independentCache by remember { mutableStateOf(false) }
    var reverseMapping by remember { mutableStateOf(false) }
    var finalServer by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<DnsServer>>(emptyList()) }
    var geoSourceId by remember { mutableStateOf("") }
    var geoGeositeUrl by remember { mutableStateOf("") }
    var geoGeoipUrl by remember { mutableStateOf("") }
    var blockIpv6 by remember { mutableStateOf(Settings.routingBlockIpv6) }
    var tunStack by remember { mutableStateOf(Settings.tunStack) }
    var tunStackMenuOpen by remember { mutableStateOf(false) }
    var tunStack by remember { mutableStateOf(Settings.tunStack) }
    var updateInterval by remember { mutableStateOf(Settings.ruleSetUpdateInterval) }
    var intervalMenuOpen by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf<List<RoutingRule>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    var strategyMenuOpen by remember { mutableStateOf(false) }
    var showAddServer by remember { mutableStateOf(false) }
    var editServerIndex by remember { mutableStateOf<Int?>(null) }
    var draftTag by remember { mutableStateOf("") }
    var draftAddress by remember { mutableStateOf("") }
    var draftDetour by remember { mutableStateOf("") }

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
        nextBlockIpv6: Boolean = blockIpv6,
        nextTunStack: String = tunStack,
        nextUpdateInterval: Long = updateInterval,
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
        blockIpv6 = nextBlockIpv6
        Settings.routingBlockIpv6 = nextBlockIpv6
        tunStack = nextTunStack
        Settings.tunStack = nextTunStack
        updateInterval = nextUpdateInterval
        Settings.ruleSetUpdateInterval = nextUpdateInterval
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
        loaded = true
    }

    if (!loaded) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 8.dp,
            ),
    ) {
        Text(
            text = "Правила и DNS накладываются на профиль при старте VPN. Файл профиля не меняется.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        SectionHeader("Общие настройки маршрутизации")
        ListItem(
            headlineContent = { Text("Блокировать IPv6 (::/0)") },
            supportingContent = { Text("Запретить весь IPv6 трафик (может решить проблемы с DNS/доступом)", style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Switch(checked = blockIpv6, onCheckedChange = { persist(nextBlockIpv6 = it) }) },
            modifier = Modifier.clickable { persist(nextBlockIpv6 = !blockIpv6) },
        )
        ListItem(
            headlineContent = { Text("Стек TUN") },
            supportingContent = {
                Text(
                    when (tunStack) {
                        "system" -> "system — системный стек (рекомендуется)"
                        "mixed" -> "mixed — смешанный"
                        else -> "gvisor — userspace gVisor"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { tunStackMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = tunStackMenuOpen, onDismissRequest = { tunStackMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("system") }, onClick = {
                            persist(nextTunStack = "system")
                            tunStackMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("gvisor") }, onClick = {
                            persist(nextTunStack = "gvisor")
                            tunStackMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("mixed") }, onClick = {
                            persist(nextTunStack = "mixed")
                            tunStackMenuOpen = false
                        })
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text("Интервал обновления Rule-set") },
            supportingContent = { Text(intervalLabel(updateInterval), style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                Box {
                    IconButton(onClick = { intervalMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = intervalMenuOpen, onDismissRequest = { intervalMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Каждый запуск") }, onClick = {
                            persist(nextUpdateInterval = 0L)
                            intervalMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Раз в час") }, onClick = {
                            persist(nextUpdateInterval = 60 * 60 * 1000L)
                            intervalMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Раз в день") }, onClick = {
                            persist(nextUpdateInterval = 24 * 60 * 60 * 1000L)
                            intervalMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Раз в неделю") }, onClick = {
                            persist(nextUpdateInterval = 7 * 24 * 60 * 60 * 1000L)
                            intervalMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Никогда (только при отсутствии)") }, onClick = {
                            persist(nextUpdateInterval = -1L)
                            intervalMenuOpen = false
                        })
                    }
                }
            },
            modifier = Modifier.clickable { intervalMenuOpen = true },
        )

        SectionHeader("Пресеты")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(
                text = "DNS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    label = { Text("Cloudflare") },
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
                    label = { Text("Сброс") },
                )
            }

            Text(
                text = "Маршрутизация",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val keep = rules.filter { it.name != "ru-sites" && it.name != "ru-ip" }
                        persist(
                            nextGeoSourceId = GeoFileSources.SAGERNET.id,
                            nextRules = keep + listOf(
                                RoutingRule(
                                    name = "ru-sites",
                                    domainSuffix = ".ru",
                                    outbound = RoutingRule.OUTBOUND_DIRECT,
                                    enabled = true,
                                ),
                                RoutingRule(
                                    name = "ru-ip",
                                    ruleSet = "geoip-ru",
                                    outbound = RoutingRule.OUTBOUND_DIRECT,
                                    enabled = true,
                                ),
                            ),
                        )
                    },
                    label = { Text("RU → direct") },
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        val keep = rules.filter { it.name != "ads" }
                        persist(
                            nextGeoSourceId = GeoFileSources.SAGERNET.id,
                            nextRules = keep + listOf(
                                RoutingRule(
                                    name = "ads",
                                    ruleSet = "geosite-category-ads-all",
                                    outbound = RoutingRule.OUTBOUND_BLOCK,
                                    enabled = true,
                                ),
                            ),
                        )
                    },
                    label = { Text("Ads → block") },
                )
                FilterChip(
                    selected = false,
                    onClick = { persist(nextRules = emptyList()) },
                    label = { Text("Сброс правил") },
                )
            }
            Text(
                text = "Пресеты дополняют список; выключайте правила переключателем. Нужен reload VPN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
            )
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
                ListItem(
                    headlineContent = { Text("Стратегия") },
                    supportingContent = {
                        Text(
                            strategyLabel(strategy),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { strategyMenuOpen = true },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
                DropdownMenu(
                    expanded = strategyMenuOpen,
                    onDismissRequest = { strategyMenuOpen = false },
                ) {
                    DnsConfig.Strategy.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(strategyLabel(item)) },
                            onClick = {
                                strategyMenuOpen = false
                                persist(nextStrategy = item)
                            },
                        )
                    }
                }

                ListItem(
                    headlineContent = { Text("Кэш DNS") },
                    trailingContent = {
                        Switch(
                            checked = cacheEnabled,
                            onCheckedChange = { persist(nextCache = it) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )

                ListItem(
                    headlineContent = { Text("Независимый кэш") },
                    trailingContent = {
                        Switch(
                            checked = independentCache,
                            onCheckedChange = { persist(nextIndependent = it) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )

                ListItem(
                    headlineContent = { Text("Обратный mapping") },
                    trailingContent = {
                        Switch(
                            checked = reverseMapping,
                            onCheckedChange = { persist(nextReverse = it) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )

                OutlinedTextField(
                    value = finalServer,
                    onValueChange = { persist(nextFinal = it) },
                    label = { Text("Final DNS (tag)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }

        SectionHeaderWithAction(
            title = "DNS-серверы",
            onActionClick = {
                editServerIndex = null
                draftTag = ""
                draftAddress = ""
                draftDetour = ""
                showAddServer = true
            },
        )

        if (servers.isEmpty()) {
            EmptyStateBox(
                text = "Нет пользовательских DNS-серверов",
                buttonText = "Добавить",
                onClick = {
                    editServerIndex = null
                    draftTag = ""
                    draftAddress = ""
                    draftDetour = ""
                    showAddServer = true
                },
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    servers.forEachIndexed { index, server ->
                        ListItem(
                            headlineContent = { Text(server.tag.ifBlank { "—" }) },
                            supportingContent = {
                                Column {
                                    Text(server.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!server.detour.isNullOrBlank()) {
                                        Text(
                                            "detour: ${server.detour}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val next = servers.toMutableList().also { it.removeAt(index) }
                                        persist(nextServers = next)
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
                                showAddServer = true
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }
        }

        SectionHeader("Геоданные")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Не выбран") },
                    supportingContent = {
                        Text(
                            "Оставить rule-set из профиля",
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
                    val selected = geoSourceId == source.id
                    val caps = buildList {
                        if (!source.geosite_url.isNullOrBlank()) add("geosite")
                        if (!source.geoip_url.isNullOrBlank()) add("geoip")
                    }.joinToString(" · ").ifBlank { "нет URL" }

                    ListItem(
                        headlineContent = { Text(source.name) },
                        supportingContent = {
                            Column {
                                Text(source.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    caps,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = { persist(nextGeoSourceId = source.id) },
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.clickable { persist(nextGeoSourceId = source.id) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
                OutlinedTextField(
                    value = geoGeositeUrl,
                    onValueChange = { persist(nextGeoGeositeUrl = it) },
                    label = { Text("Базовый URL geosite") },
                    placeholder = { Text("https://.../rule-set") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = geoGeoipUrl,
                    onValueChange = { persist(nextGeoGeoipUrl = it) },
                    label = { Text("Базовый URL geoip") },
                    placeholder = { Text("https://.../rule-set") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = "Файлы: geosite-….srs / geoip-….srs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }
        }

        SectionHeaderWithAction(
            title = "Правила маршрутизации",
            onActionClick = {
                navController.navigate("settings/routing/rule/-1")
            },
        )

        if (rules.isEmpty()) {
            EmptyStateBox(
                text = "Нет пользовательских правил",
                buttonText = "Добавить правило",
                onClick = {
                    navController.navigate("settings/routing/rule/-1")
                },
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    rules.forEachIndexed { index, rule ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    rule.displayTitle(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    rule.displaySubtitle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { on ->
                                            val next = rules.toMutableList()
                                            next[index] = rule.copy(enabled = on)
                                            persist(nextRules = next)
                                        },
                                    )
                                    IconButton(
                                        onClick = {
                                            val next = rules.toMutableList().also { it.removeAt(index) }
                                            persist(nextRules = next)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
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

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAddServer) {
        AlertDialog(
            onDismissRequest = { showAddServer = false },
            title = {
                Text(if (editServerIndex == null) "Добавить DNS-сервер" else "Изменить DNS-сервер")
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftAddress,
                        onValueChange = { draftAddress = it },
                        label = { Text("Address (IP / URL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftDetour,
                        onValueChange = { draftDetour = it },
                        label = { Text("Detour (outbound tag, опц.)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val next = servers.toMutableList()
                        val srv = DnsServer(draftTag, draftAddress, draftDetour.ifBlank { null })
                        if (editServerIndex != null) {
                            next[editServerIndex!!] = srv
                        } else {
                            next.add(srv)
                        }
                        persist(nextServers = next)
                        showAddServer = false
                    },
                    enabled = draftTag.isNotBlank() && draftAddress.isNotBlank(),
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddServer = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SectionHeaderWithAction(title: String, onActionClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onActionClick) {
            Icon(Icons.Default.Add, contentDescription = "Добавить")
        }
    }
}

@Composable
private fun EmptyStateBox(text: String, buttonText: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            FilledTonalButton(onClick = onClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(buttonText)
            }
        }
    }
}

private fun intervalLabel(intervalMs: Long): String = when (intervalMs) {
    0L -> "Каждый запуск"
    60 * 60 * 1000L -> "Раз в час"
    24 * 60 * 60 * 1000L -> "Раз в день"
    7 * 24 * 60 * 60 * 1000L -> "Раз в неделю"
    -1L -> "Никогда (только при отсутствии)"
    else -> "Пользовательский (${intervalMs / 1000} сек)"
}

private fun strategyLabel(strategy: DnsConfig.Strategy): String = when (strategy) {
    DnsConfig.Strategy.AUTO -> "Auto"
    DnsConfig.Strategy.PREFER_IPV4 -> "Prefer IPv4"
    DnsConfig.Strategy.PREFER_IPV6 -> "Prefer IPv6"
    DnsConfig.Strategy.IPV4_ONLY -> "IPv4 only"
    DnsConfig.Strategy.IPV6_ONLY -> "IPv6 only"
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
            val serversJson = dns.optJSONArray("servers") ?: JSONArray()
            servers = buildList {
                for (i in 0 until serversJson.length()) {
                    val obj = serversJson.optJSONObject(i) ?: continue
                    val tag = obj.optString("tag")
                    val address = obj.optString("address")
                    if (tag.isBlank() || address.isBlank()) continue
                    val detour = obj.optString("detour").ifBlank { null }
                    add(DnsServer(tag = tag, address = address, detour = detour))
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
                    val value = obj.optString("value")
                    val outbound = obj.optString("outbound", RoutingRule.OUTBOUND_PROXY)
                        .ifBlank { RoutingRule.OUTBOUND_PROXY }
                    val rule = RoutingRule(
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
                        outbound = outbound,
                        dnsRule = obj.optBoolean("dnsRule", false),
                        type = type,
                        value = value,
                        enabled = obj.optBoolean("enabled", true),
                    )
                    val hasMatch = listOf(
                        rule.domain, rule.domainSuffix, rule.domainKeyword,
                        rule.ipCidr, rule.port, rule.sourceIpCidr, rule.sourcePort,
                        rule.packageName, rule.ruleSet, rule.protocol,
                        rule.wifiSsid, rule.wifiBssid, rule.clashMode, rule.value,
                    ).any { it.isNotBlank() }
                    if (hasMatch) add(rule)
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
        val obj = JSONObject()
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
            .put("value", rule.value)
            .put("enabled", rule.enabled)
        rulesJson.put(obj)
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
