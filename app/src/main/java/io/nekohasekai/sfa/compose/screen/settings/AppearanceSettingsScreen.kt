package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.compose.component.PreferenceSection
import io.nekohasekai.sfa.compose.theme.AppearanceState
import io.nekohasekai.sfa.database.Settings

@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    var raw by remember { mutableStateOf(Settings.appearanceJson) }
    val appearance = settingsObject(raw)
    fun set(key: String, value: Any) {
        raw = settingsObject(Settings.appearanceJson).put(key, value).toString()
        Settings.appearanceJson = raw
        AppearanceState.revision++
    }
    ClientSettingsPage("Оформление", navController) {
        PreferenceSection("Тема", Icons.Outlined.Palette) {
            SettingChoice(
                "Режим",
                appearance.optString("theme", "system"),
                listOf("system" to "Следовать системе", "light" to "Светлая", "dark" to "Тёмная"),
            ) {
                set("theme", it)
            }
            SettingToggle(
                "Цвета обоев",
                "Динамический акцент Material You на Android 12 и новее",
                appearance.optBoolean("dynamic", true),
            ) {
                set("dynamic", it)
            }
            SettingChoice(
                "Акцент",
                appearance.optString("accent", "blue"),
                listOf(
                    "blue" to "Синий",
                    "green" to "Зелёный",
                    "purple" to "Фиолетовый",
                    "amber" to "Янтарный",
                ),
            ) {
                set("accent", it)
                set("dynamic", false)
            }
            SettingToggle(
                "Чёрный фон",
                "Чёрный фон в тёмной теме",
                appearance.optBoolean("black"),
            ) {
                set("black", it)
            }
        }
        PreferenceSection("Текст", Icons.Outlined.Palette) {
            SettingChoice(
                "Размер текста",
                appearance.optInt("fontPercent", 100).toString(),
                listOf(
                    "85" to "Меньше · 85%",
                    "100" to "Системный · 100%",
                    "115" to "Крупнее · 115%",
                    "130" to "Очень крупный · 130%",
                ),
            ) {
                set("fontPercent", it.toInt())
            }
            Text(
                "Масштаб применяется поверх системного размера шрифта. Изменения оформления видны сразу.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(
            onClick = {
                Settings.appearanceJson = "{}"
                raw = "{}"
                AppearanceState.revision++
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("Восстановить оформление по умолчанию")
        }
    }
}
