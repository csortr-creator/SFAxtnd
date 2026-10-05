package io.nekohasekai.sfa.compose.screen.dashboard

// RESTORE_NEEDED - use file from commit 0fbd6ceb GroupsCard.kt with fillMaxSize patches
// Temporary stub to avoid total breakage - will be replaced
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel

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
    Box(modifier = modifier.fillMaxSize()) {
        Text("GroupsCard restore in progress")
    }
}
