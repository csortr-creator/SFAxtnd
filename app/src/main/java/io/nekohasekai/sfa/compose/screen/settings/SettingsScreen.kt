package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PreferenceSection
import io.nekohasekai.sfa.compose.component.RoutingSectionLink
import io.nekohasekai.sfa.compose.navigation.Screen
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_settings)) },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Информация о приложении") },
                        onClick = {
                            showMenu = false
                            showAboutDialog = true
                        },
                    )
                }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("SFAxtnd") },
            text = { Text("Версия ${BuildConfig.VERSION_NAME}\nКлиент sing-box для Android") },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK") } },
            dismissButton = {
                TextButton(
                    onClick = { uriHandler.openUri("https://github.com/csortr-creator/SFAxtnd") }
                ) {
                    Text("GitHub")
                }
            },
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasUpdate by UpdateState.hasUpdate

    val scaffoldPadding = LocalScaffoldPadding.current

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 8.dp,
                )
    ) {
        PreferenceSection("Сеть", Icons.Outlined.Shield) {
            RoutingSectionLink(
                "Маршрутизация",
                "Прокси, обход, блокировка и списки сайтов",
                Icons.Outlined.Route,
            ) {
                navController.navigate("settings/routing")
            }
            RoutingSectionLink(
                "DNS",
                "Прямое соединение, прокси и резервные серверы",
                Icons.Outlined.Dns,
            ) {
                navController.navigate("settings/dns")
            }
            RoutingSectionLink(
                "Ядро",
                "Сетевой стек, MTU и параметры соединения",
                Icons.Outlined.Settings,
            ) {
                navController.navigate("settings/core")
            }
            RoutingSectionLink(
                "Сервис VPN",
                "Фоновая работа и системные разрешения",
                Icons.Outlined.Tune,
            ) {
                navController.navigate("settings/service")
            }
        }
        PreferenceSection("Приложение", Icons.Outlined.Palette) {
            RoutingSectionLink(
                "Оформление",
                "Тема, акцент и размер текста",
                Icons.Outlined.Palette,
            ) {
                navController.navigate("settings/appearance")
            }
            RoutingSectionLink(
                "Обновления и поведение",
                if (hasUpdate) "Доступно обновление" else "Язык, уведомления и обновления",
                Icons.Outlined.SystemUpdate,
            ) {
                navController.navigate("settings/app")
            }
        }
        PreferenceSection("Диагностика", Icons.Outlined.BugReport) {
            RoutingSectionLink(
                "Журнал событий",
                "Подробности работы и ошибок подключения",
                Icons.Outlined.ReceiptLong,
            ) {
                navController.navigate(Screen.Log.route)
            }
        }
        PreferenceSection("О приложении", Icons.Outlined.Info) {
            RoutingSectionLink(
                "SFAxtnd",
                "Версия ${BuildConfig.VERSION_NAME}",
                Icons.Outlined.Info,
            ) {
                showAboutDialog = true
            }
            RoutingSectionLink(
                "Релизы",
                "Обновления SFAxtnd на GitHub",
                Icons.Outlined.SystemUpdate,
            ) {
                uriHandler.openUri("https://github.com/csortr-creator/SFAxtnd/releases")
            }
            RoutingSectionLink(
                "Документация",
                "Возможности и параметры sing-box",
                Icons.Outlined.MenuBook,
            ) {
                uriHandler.openUri("https://sing-box.sagernet.org/")
            }
            RoutingSectionLink(
                "Разработчик SFAxtnd",
                "csortr-creator · GitHub",
                Icons.Outlined.FavoriteBorder,
            ) {
                uriHandler.openUri("https://github.com/csortr-creator")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
