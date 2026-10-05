package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compat.LazyColumnCompat
import io.nekohasekai.sfa.compat.rememberOverscrollEffectCompat
import io.nekohasekai.sfa.compose.component.SnackbarHost
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsUiState
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.rememberSheetDismissFromContentOnlyIfGestureStartedAtTopModifier
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.CommandClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsCard(
    serviceStatus: Status,
    commandClient: CommandClient? = null,
    viewModel: GroupsViewModel? = null,
    showTopBar: Boolean = false,
    listHeaderContent: (@Composable () -> Unit)? = null,
    asSheet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val actualViewModel: GroupsViewModel = viewModel ?: viewModel(
        factory =
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GroupsViewModel(commandClient) as T
            }
        },
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by actualViewModel.uiState.collectAsState()

    if (showTopBar) {
        val allCollapsed = uiState.expandedGroups.isEmpty()
        OverrideTopBar {
            TopAppBar(
                title = { Text(stringResource(R.string.title_dashboard)) },
                actions = {
                    IconButton(onClick = { actualViewModel.toggleSortByPing() }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = if (uiState.sortByPing) "Sort by Name" else "Sort by Ping",
                            tint = if (uiState.sortByPing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (uiState.groups.isNotEmpty()) {
                        IconButton(onClick = { actualViewModel.toggleAllGroups() }) {
                            Icon(
                                imageVector = if (allCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription =
                                if (allCollapsed) stringResource(R.string.expand_all) else stringResource(R.string.collapse_all),
                            )
                        }
                    }
                },
            )
        }
    }

    val onToggleExpanded = remember(actualViewModel) { { groupTag: String -> actualViewModel.toggleGroupExpand(groupTag) } }
    val onItemSelected = remember(actualViewModel) {
        { groupTag: String, itemTag: String -> actualViewModel.selectGroupItem(groupTag, itemTag) }
    }
    val onUrlTest = remember(actualViewModel) { { groupTag: String -> actualViewModel.urlTestGroup(groupTag) } }
    val onItemUrlTest = remember(actualViewModel) { { itemTag: String -> actualViewModel.urlTest(itemTag) } }

    LaunchedEffect(serviceStatus) { actualViewModel.updateServiceStatus(serviceStatus) }

    val closeConnectionsMessage = stringResource(R.string.close_connections_confirm)
    val closeConnectionsAction = stringResource(R.string.close)

    LaunchedEffect(uiState.showCloseConnectionsSnackbar) {
        if (uiState.showCloseConnectionsSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = closeConnectionsMessage,
                actionLabel = closeConnectionsAction,
                duration = SnackbarDuration.Indefinite,
                withDismissAction = true,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> actualViewModel.closeConnections()
                SnackbarResult.Dismissed -> actualViewModel.dismissCloseConnectionsSnackbar()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GroupsCardContent(
            uiState = uiState,
            serviceStatus = serviceStatus,
            onToggleExpanded = onToggleExpanded,
            onItemSelected = onItemSelected,
            onUrlTest = onUrlTest,
            onItemUrlTest = onItemUrlTest,
            listHeaderContent = listHeaderContent,
            asSheet = asSheet,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
