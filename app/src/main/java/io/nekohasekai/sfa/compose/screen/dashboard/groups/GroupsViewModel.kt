package io.nekohasekai.sfa.compose.screen.dashboard.groups

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.ScreenEvent
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.model.GroupItem
import io.nekohasekai.sfa.compose.model.toList
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.CommandTarget
import io.nekohasekai.sfa.utils.RemoteControlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val expandedGroups: Set<String> = emptySet(),
    val testingGroups: Set<String> = emptySet(),
    val showCloseConnectionsSnackbar: Boolean = false,
    val sortByPing: Boolean = true,
)

sealed class GroupsEvent : ScreenEvent {
    data class GroupSelected(val groupTag: String, val itemTag: String) : GroupsEvent()
}

class GroupsViewModel(private val sharedCommandClient: CommandClient? = null) :
    BaseViewModel<GroupsUiState, GroupsEvent>(),
    CommandClient.Handler {
    private val commandClient: CommandClient
    private val isUsingSharedClient: Boolean

    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus = _serviceStatus.asStateFlow()
    private var lastServiceStatus: Status = Status.Stopped

    init {
        if (sharedCommandClient != null) {
            commandClient = sharedCommandClient
            isUsingSharedClient = true
            commandClient.addHandler(this)
        } else {
            commandClient =
                CommandClient(
                    viewModelScope,
                    CommandClient.ConnectionType.Groups,
                    this,
                )
            isUsingSharedClient = false
        }

        viewModelScope.launch {
            combine(
                AppLifecycleObserver.isForeground,
                RemoteControlManager.remoteServer,
                RemoteControlManager.isConnected,
                _serviceStatus,
            ) { foreground, remoteServer, remoteConnected, status ->
                SessionTarget(
                    connect = foreground &&
                        if (remoteServer != null) remoteConnected else status == Status.Started,
                    remoteServerId = remoteServer?.id,
                )
            }.distinctUntilChanged().collect { target ->
                if (target.connect) {
                    if (isUsingSharedClient) {
                        commandClient.addHandler(this@GroupsViewModel)
                    } else {
                        updateState { copy(isLoading = true) }
                        commandClient.connect()
                    }
                } else {
                    if (isUsingSharedClient) {
                        commandClient.removeHandler(this@GroupsViewModel)
                    } else {
                        commandClient.disconnect()
                    }
                }
            }
        }
    }

    private data class SessionTarget(val connect: Boolean, val remoteServerId: Long?)

    override fun createInitialState() = GroupsUiState()

    override fun onCleared() {
        super.onCleared()
        if (isUsingSharedClient) {
            commandClient.removeHandler(this)
        } else {
            commandClient.disconnect()
        }
    }

    private fun handleServiceStatusChange(status: Status) {
        if (RemoteControlManager.remoteServer.value != null) {
            return
        }
        // Keep last groups (and ping results) when VPN is not running.
        // If we have nothing yet, try offline parse from selected profile.
        if (status != Status.Started && uiState.value.groups.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                loadGroupsFromSelectedProfile()
            }
        }
    }

    fun updateServiceStatus(status: Status) {
        val statusChanged = status != lastServiceStatus
        lastServiceStatus = status
        viewModelScope.launch {
            if (statusChanged) {
                _serviceStatus.emit(status)
            }
            handleServiceStatusChange(status)
        }
    }

    fun toggleGroupExpand(groupTag: String) {
        val newExpanded = !uiState.value.expandedGroups.contains(groupTag)
        updateState {
            val newExpandedGroups = if (newExpanded) {
                expandedGroups + groupTag
            } else {
                expandedGroups - groupTag
            }
            copy(expandedGroups = newExpandedGroups)
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CommandTarget.standaloneClient().setGroupExpand(groupTag, newExpanded)
            }
        }
    }

    fun toggleAllGroups() {
        val groups = uiState.value.groups
        val allCollapsed = uiState.value.expandedGroups.isEmpty()
        val newExpanded = allCollapsed

        updateState {
            if (allCollapsed) {
                copy(expandedGroups = groups.map { it.tag }.toSet())
            } else {
                copy(expandedGroups = emptySet())
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            groups.forEach { group ->
                runCatching {
                    CommandTarget.standaloneClient().setGroupExpand(group.tag, newExpanded)
                }
            }
        }
    }

    fun toggleSortByPing() {
        updateState { copy(sortByPing = !sortByPing) }
    }

    fun selectGroupItem(groupTag: String, itemTag: String) {
        // Check if this is actually a different selection
        val currentGroup = uiState.value.groups.find { it.tag == groupTag }
        if (currentGroup?.selected == itemTag) {
            // Same item selected, no need to do anything
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Select the new outbound immediately
                CommandTarget.standaloneClient().selectOutbound(groupTag, itemTag)

                // Update local state and show snackbar
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            groups =
                            groups.map { group ->
                                if (group.tag == groupTag) {
                                    group.copy(selected = itemTag)
                                } else {
                                    group
                                }
                            },
                            showCloseConnectionsSnackbar = true,
                        )
                    }
                    sendEvent(GroupsEvent.GroupSelected(groupTag, itemTag))
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun closeConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().closeConnections()
                withContext(Dispatchers.Main) {
                    dismissCloseConnectionsSnackbar()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dismissCloseConnectionsSnackbar()
                }
                sendError(e)
            }
        }
    }

    fun dismissCloseConnectionsSnackbar() {
        updateState {
            copy(showCloseConnectionsSnackbar = false)
        }
    }

    fun urlTest(outboundTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().urlTest(outboundTag)
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun urlTestGroup(groupTag: String) {
        updateState { copy(testingGroups = testingGroups + groupTag) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().urlTest(groupTag)
            } catch (e: Exception) {
                sendError(e)
            } finally {
                withContext(Dispatchers.Main) {
                    updateState { copy(testingGroups = testingGroups - groupTag) }
                }
            }
        }
    }

    // CommandClient.Handler implementation
    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
            // Connection established, waiting for groups
        }
    }

    override fun onDisconnected() {
        // Do not clear groups — keep last known list and ping results offline.
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isLoading = false) }
            if (uiState.value.groups.isEmpty()) {
                launch(Dispatchers.IO) { loadGroupsFromSelectedProfile() }
            }
        }
    }

    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentGroups = uiState.value.groups
            val previousPing = currentGroups
                .flatMap { g -> g.items.map { it.tag to it } }
                .toMap()
            val currentByTag = currentGroups.associateBy { it.tag }
            val mergedGroups = newGroups.map { goGroup ->
                val converted = Group(goGroup)
                val existing = currentByTag[converted.tag]
                val items = converted.items.map { item ->
                    if (item.urlTestDelay > 0 || item.urlTestTime > 0L) {
                        item
                    } else {
                        val prev = previousPing[item.tag]
                        if (prev != null && (prev.urlTestDelay > 0 || prev.urlTestTime > 0L)) {
                            item.copy(urlTestDelay = prev.urlTestDelay, urlTestTime = prev.urlTestTime)
                        } else {
                            item
                        }
                    }
                }
                val withPing = converted.copy(items = items)
                if (existing == withPing) existing else withPing
            }

            withContext(Dispatchers.Main) {
                updateState {
                    val initialExpandedGroups = if (expandedGroups.isEmpty() && currentGroups.isEmpty()) {
                        mergedGroups.filter { it.isExpand }.map { it.tag }.toSet()
                    } else {
                        expandedGroups
                    }
                    copy(
                        groups = if (mergedGroups == groups) groups else mergedGroups,
                        expandedGroups = initialExpandedGroups,
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Build selector/urltest groups from selected profile JSON (works offline, before VPN start). */
    private suspend fun loadGroupsFromSelectedProfile() {
        runCatching {
            val profileId = Settings.selectedProfile
            if (profileId == -1L) return
            val profile = ProfileManager.get(profileId) ?: return
            val path = profile.typed.path
            if (path.isBlank()) return
            val file = File(path)
            if (!file.exists()) return
            val content = file.readText()
            val groups = parseGroupsFromConfig(content)
            if (groups.isEmpty()) return
            withContext(Dispatchers.Main) {
                if (uiState.value.groups.isEmpty()) {
                    updateState {
                        copy(
                            groups = groups,
                            expandedGroups = groups.filter { it.isExpand }.map { it.tag }.toSet()
                                .ifEmpty { groups.map { it.tag }.toSet() },
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun parseGroupsFromConfig(jsonStr: String): List<Group> {
            return try {
                val root = JSONObject(jsonStr.trim())
                val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
                val byTag = mutableMapOf<String, JSONObject>()
                for (i in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(i) ?: continue
                    val tag = ob.optString("tag")
                    if (tag.isNotBlank()) byTag[tag] = ob
                }
                val groups = mutableListOf<Group>()
                for (i in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(i) ?: continue
                    val type = ob.optString("type")
                    if (type != "selector" && type != "urltest") continue
                    val tag = ob.optString("tag").ifBlank { type }
                    val members = ob.optJSONArray("outbounds") ?: JSONArray()
                    val selected = ob.optString("default").ifBlank {
                        if (members.length() > 0) members.optString(0) else ""
                    }
                    val items = mutableListOf<GroupItem>()
                    for (j in 0 until members.length()) {
                        val memberTag = members.optString(j)
                        if (memberTag.isBlank()) continue
                        val member = byTag[memberTag]
                        val memberType = member?.optString("type") ?: "unknown"
                        items.add(
                            GroupItem(
                                tag = memberTag,
                                type = memberType,
                                displayType = memberType.uppercase(),
                                urlTestTime = 0L,
                                urlTestDelay = 0,
                            ),
                        )
                    }
                    if (items.isEmpty()) continue
                    groups.add(
                        Group(
                            tag = tag,
                            type = type,
                            displayType = type.replaceFirstChar { it.uppercase() },
                            selectable = true,
                            selected = selected,
                            isExpand = true,
                            items = items,
                        ),
                    )
                }
                groups
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
