package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.compose.component.PreferenceSection
import io.nekohasekai.sfa.database.Settings

@Composable
fun NetworkOptionsScreen(navController: NavController) {
    ClientSettingsPage("Параметры маршрутизации", navController) { NetworkOptionsContent(false) }
}

@Composable
internal fun NetworkOptionsContent(core: Boolean) {
    var raw by remember { mutableStateOf(Settings.coreOptionsJson) }
    val options = settingsObject(raw)
    fun set(key: String, value: Any) {
        val current = settingsObject(Settings.coreOptionsJson).put(key, value)
        raw = current.toString()
        Settings.coreOptionsJson = raw
    }
    if (core) {
        var stack by remember { mutableStateOf(Settings.tunStack) }
        var ipv6Block by remember { mutableStateOf(Settings.routingBlockIpv6) }
        PreferenceSection("Туннель", Icons.Outlined.Settings) {
            SettingChoice(
                "Сетевой стек",
                stack,
                listOf(
                    "system" to "System · системный",
                    "gvisor" to "gVisor · пользовательский",
                    "mixed" to "Mixed · смешанный",
                ),
            ) {
                stack = it
                Settings.tunStack = it
            }
            SettingToggle(
                "Задать MTU",
                "Выключено: использовать MTU из подписки",
                options.has("mtu"),
            ) {
                if (it) set("mtu", 1500)
                else {
                    val current = settingsObject(Settings.coreOptionsJson)
                    current.remove("mtu")
                    raw = current.toString()
                    Settings.coreOptionsJson = raw
                }
            }
            if (options.has("mtu"))
                SettingNumber(
                    "MTU",
                    options.optInt("mtu", 1500),
                    1280..9000,
                    { set("mtu", it) },
                    "байт",
                )
            SettingChoice(
                "Адреса туннеля",
                options.optString("ipMode", "profile"),
                listOf("profile" to "Из подписки", "ipv4" to "IPv4", "dual" to "IPv4 и IPv6"),
            ) {
                set("ipMode", it)
            }
            SettingToggle(
                "Блокировать IPv6",
                "Отклонять IPv6-трафик; не изменяет стратегию DNS",
                ipv6Block,
            ) {
                ipv6Block = it
                Settings.routingBlockIpv6 = it
            }
            SettingChoice(
                "Строгая маршрутизация",
                if (options.has("strictRoute")) options.optBoolean("strictRoute").toString()
                else "profile",
                listOf("profile" to "Из подписки", "true" to "Включить", "false" to "Выключить"),
            ) {
                if (it == "profile") {
                    val current = settingsObject(Settings.coreOptionsJson)
                    current.remove("strictRoute")
                    raw = current.toString()
                    Settings.coreOptionsJson = raw
                } else set("strictRoute", it.toBoolean())
            }
        }
        PreferenceSection("Диагностика ядра", Icons.Outlined.BugReport) {
            SettingChoice(
                "Уровень журнала",
                options.optString("logLevel", "profile"),
                listOf(
                    "profile" to "Из подписки",
                    "trace" to "Trace",
                    "debug" to "Debug",
                    "info" to "Info",
                    "warn" to "Warning",
                    "error" to "Error",
                ),
            ) {
                set("logLevel", it)
            }
            SettingToggle(
                "Определять приложения",
                "Определение процесса для правил приложений",
                options.optBoolean("findProcess", false),
            ) {
                set("findProcess", it)
            }
        }
    } else {
        PreferenceSection("Сниффинг трафика", Icons.Outlined.Route) {
            Text(
                "Определяет протокол и домен соединения для правил маршрутизации: например, Host в HTTP или имя сервера в TLS/QUIC. Содержимое зашифрованного HTTPS не расшифровывается.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            SettingChoice(
                "Сниффинг",
                options.optString("sniff", "profile"),
                listOf("profile" to "Из подписки", "on" to "Включить", "off" to "Выключить"),
            ) {
                set("sniff", it)
            }
            SettingNumber(
                "Тайм-аут сниффинга",
                options.optInt("sniffTimeoutMs", 300),
                50..5000,
                { set("sniffTimeoutMs", it) },
                "мс",
            )
            val supported =
                listOf(
                    "tls",
                    "http",
                    "quic",
                    "dns",
                    "stun",
                    "bittorrent",
                    "dtls",
                    "ssh",
                    "rdp",
                    "ntp",
                )
            val selected =
                options.optString("sniffers").split(',').filter(String::isNotBlank).toSet()
            Text(
                "Дополнительно: какие протоколы распознавать. Это не протокол VPN-сервера. Пустой список использует набор ядра; параметры применяются при включённом сниффинге.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            supported.forEach { name ->
                SettingToggle(name.uppercase(), "", name in selected) { enabled ->
                    set(
                        "sniffers",
                        (if (enabled) selected + name else selected - name).joinToString(","),
                    )
                }
            }
        }
        PreferenceSection("Обновление списков", Icons.Outlined.Update) {
            var interval by remember { mutableStateOf(Settings.ruleSetUpdateInterval) }
            SettingChoice(
                "Период обновления",
                (interval / 3600000L).toString(),
                listOf(
                    "1" to "Каждый час",
                    "6" to "Каждые 6 часов",
                    "12" to "Каждые 12 часов",
                    "24" to "Раз в день",
                    "72" to "Раз в 3 дня",
                    "168" to "Раз в неделю",
                ),
            ) {
                interval = it.toLong() * 3600000L
                Settings.ruleSetUpdateInterval = interval
            }
        }
    }
}
