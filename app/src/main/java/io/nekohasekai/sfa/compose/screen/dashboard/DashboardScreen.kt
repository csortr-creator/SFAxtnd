package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.RemoteControlMenuItems
import io.nekohasekai.sfa.compose.component.rememberRemoteServers
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.RemoteControlManager

data class CardRenderItem(val cards: List<CardGroup>, val isRow: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val remoteServer by RemoteControlManager.remoteServer.collectAsState()
    val remoteConnected by RemoteControlManager.isConnected.collectAsState()
    val isRemote = remoteServer != null
    val remoteServers by rememberRemoteServers()
    var showOthersMenu by remember { mutableStateOf(false) }

    if (uiState.showPartialImportDialog && uiState.pendingImport != null) {
        val pending = uiState.pendingImport!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelPartialImport() },
            title = { Text("Частичный импорт") },
            text = {
                Text(
                    "Получено: ${pending.received}\n" +
                        "Импортировано: ${pending.imported}\n" +
                        "Отклонено: ${pending.rejected}\n\n" +
                        "Часть серверов не удалось импортировать. " +
                        "Применение может уменьшить список доступных серверов.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPartialImport() }) {
                    Text("Применить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPartialImport() }) {
                    Text("Отмена")
                }
            },
        )
    }


    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_subscriptions)) },
            actions = {
                IconButton(
                    onClick = viewModel::updateAllProfiles,
                    enabled =
                        !isRemote &&
                            !uiState.isUpdatingAll &&
                            uiState.updatingProfileIds.isEmpty() &&
                            uiState.profiles.any {
                                it.typed.type ==
                                    io.nekohasekai.sfa.database.TypedProfile.Type.Remote
                            },
                ) {
                    if (uiState.isUpdatingAll)
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    else Icon(Icons.Default.Refresh, "Обновить все подписки")
                }
                IconButton(onClick = viewModel::showAddProfileSheet, enabled = !isRemote) {
                    Icon(Icons.Default.Add, "Добавить подписку")
                }
                if (remoteServers.isNotEmpty())
                    Box {
                        IconButton(onClick = { showOthersMenu = true }) {
                            Icon(Icons.Default.Dns, "Удалённое управление")
                        }
                        DropdownMenu(
                            expanded = showOthersMenu,
                            onDismissRequest = { showOthersMenu = false },
                        ) {
                            RemoteControlMenuItems(
                                servers = remoteServers,
                                onAction = { showOthersMenu = false },
                                leadingDivider = false,
                            )
                        }
                    }
            },
        )
    }

    if (isRemote && !remoteConnected) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    Box(modifier = Modifier.fillMaxSize()) {
        val bottomPadding =
            when {
                showStartFab -> 88.dp
                showStatusBar -> 74.dp
                else -> 0.dp
            }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item {
                DashboardCardRenderer(
                    cardGroup = CardGroup.Profiles,
                    cardWidth = CardWidth.Full,
                    uiState = uiState,
                    onClashModeSelected = viewModel::selectClashMode,
                    onSystemProxyToggle = viewModel::toggleSystemProxy,
                    // Profile card specific props
                    profiles = uiState.profiles,
                    selectedProfileId = uiState.selectedProfileId,
                    isLoading = uiState.isLoading,
                    showAddProfileSheet = uiState.showAddProfileSheet,
                    updatingProfileIds = uiState.updatingProfileIds,
                    deletingProfileIds = uiState.deletingProfileIds,
                    updatedProfileId = uiState.updatedProfileId,
                    onProfileSelected = viewModel::selectProfile,
                    onProfileEdit = viewModel::editProfile,
                    onProfileDelete = viewModel::deleteProfile,
                    onProfileShare = viewModel::shareProfile,
                    onProfileShareURL = viewModel::shareProfileURL,
                    onProfileUpdate = viewModel::updateProfile,
                    onProfileMove = viewModel::moveProfile,
                    onShowAddProfileSheet = viewModel::showAddProfileSheet,
                    onHideAddProfileSheet = viewModel::hideAddProfileSheet,
                    onOpenNewProfile = onOpenNewProfile,
                    commandClient = viewModel.commandClient,
                )
            }
        }
    }
}
