package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

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

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_subscriptions)) },
            actions = {
                Box {
                    IconButton(onClick = { showOthersMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.title_others),
                        )
                    }
                    DropdownMenu(
                        expanded = showOthersMenu,
                        onDismissRequest = { showOthersMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dashboard_items)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                showOthersMenu = false
                                viewModel.toggleCardSettingsDialog()
                            },
                        )
                        RemoteControlMenuItems(
                            servers = remoteServers,
                            onAction = { showOthersMenu = false },
                        )
                    }
                }
            },
        )
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Show dashboard settings bottom sheet
    if (uiState.showCardSettingsDialog) {
        DashboardSettingsBottomSheet(
            sheetState = sheetState,
            visibleCards = uiState.visibleCards,
            cardOrder = uiState.cardOrder,
            onToggleCard = viewModel::toggleCardVisibility,
            onReorderCards = viewModel::reorderCards,
            onResetOrder = viewModel::resetCardOrder,
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    viewModel.closeCardSettingsDialog()
                }
            },
        )
    }

    if (isRemote && !remoteConnected) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val bottomPadding = when {
            showStartFab -> 88.dp
            showStatusBar -> 74.dp
            else -> 0.dp
        }
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp),
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
                    showProfilePickerSheet = uiState.showProfilePickerSheet,
                    updatingProfileId = uiState.updatingProfileId,
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
                    onShowProfilePickerSheet = viewModel::showProfilePickerSheet,
                    onHideProfilePickerSheet = viewModel::hideProfilePickerSheet,
                    onOpenNewProfile = onOpenNewProfile,
                    commandClient = viewModel.commandClient,
                )
            }
        }
    }
}

