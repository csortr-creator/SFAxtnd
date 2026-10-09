package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.Application

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.RuntimeProfileState
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.utils.ProfileSafeDelete
import io.nekohasekai.sfa.utils.ProfileConfigCommit
import io.nekohasekai.sfa.utils.ManualSubscriptionUpdate
import io.nekohasekai.sfa.utils.ManualPrepareResult
import io.nekohasekai.sfa.utils.PendingImportHolder
import io.nekohasekai.sfa.utils.ImportOperationOutcome
import io.nekohasekai.sfa.utils.CommitOutcome
import io.nekohasekai.sfa.utils.ImportResultFormatter
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.CommandTarget
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.RemoteControlManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.Collections
import java.util.Date

enum class CardGroup {
    ClashMode,
    UploadTraffic,
    DownloadTraffic,
    Debug,
    Connections,
    SystemProxy,
    Profiles,
}

enum class CardWidth {
    Half,
    Full,
}

data class DashboardUiState(
    val serviceStatus: Status = Status.Stopped,
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: Long = -1L,
    val selectedProfileName: String? = null,
    val isLoading: Boolean = false,
    val hasGroups: Boolean = false,
    val groupsCount: Int = 0,
    val connectionsCount: Int = 0,
    val serviceStartTime: Long? = null,
    val showAddProfileSheet: Boolean = false,
    val updatingProfileIds: Set<Long> = emptySet(),
    val deletingProfileIds: Set<Long> = emptySet(),
    val updatedProfileId: Long? = null,
    val isUpdatingAll: Boolean = false,
    val pendingImport: PendingImportHolder? = null,
    val showPartialImportDialog: Boolean = false,
    // Status
    val memory: String = "",
    val goroutines: String = "",
    val isStatusVisible: Boolean = false,
    // Traffic
    val trafficVisible: Boolean = false,
    val connectionsIn: String = "0",
    val connectionsOut: String = "0",
    val uplink: String = "0 B/s",
    val downlink: String = "0 B/s",
    val uplinkTotal: String = "0 B",
    val downlinkTotal: String = "0 B",
    val uplinkHistory: List<Float> = List(30) { 0f },
    val downlinkHistory: List<Float> = List(30) { 0f },
    // Clash Mode
    val clashModeVisible: Boolean = false,
    val clashModes: List<String> = emptyList(),
    val selectedClashMode: String = "",
    // System Proxy
    val systemProxyVisible: Boolean = false,
    val systemProxyEnabled: Boolean = false,
    val systemProxySwitching: Boolean = false,
    // Card visibility settings
    val visibleCards: Set<CardGroup> =
        setOf(
            CardGroup.Profiles,
        ),
    val cardOrder: List<CardGroup> =
        listOf(
            CardGroup.Profiles,
            CardGroup.ClashMode,
            CardGroup.SystemProxy,
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Connections,
            CardGroup.Debug,
        ),
    val cardWidths: Map<CardGroup, CardWidth> =
        mapOf(
            CardGroup.ClashMode to CardWidth.Full,
            CardGroup.UploadTraffic to CardWidth.Half,
            CardGroup.DownloadTraffic to CardWidth.Half,
            CardGroup.Debug to CardWidth.Half,
            CardGroup.Connections to CardWidth.Half,
            CardGroup.SystemProxy to CardWidth.Full,
            CardGroup.Profiles to CardWidth.Full,
        ),
    val showCardSettingsDialog: Boolean = false,
) {
}

// DashboardViewModel now only uses UiEvent for all events
// No need for DashboardEvent anymore as all events are handled globally

class DashboardViewModel :
    BaseViewModel<DashboardUiState, UiEvent>(),
    CommandClient.Handler {
    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus: StateFlow<Status> = _serviceStatus.asStateFlow()

    internal val commandClient =
        CommandClient(
            viewModelScope,
            listOf(
                CommandClient.ConnectionType.Status,
                CommandClient.ConnectionType.ClashMode,
                CommandClient.ConnectionType.Groups,
            ),
            this,
        )

    override fun createInitialState(): DashboardUiState {
        val savedOrder = loadItemOrder()
        val disabledItems = loadDisabledItems()

        // Calculate visible items (all items minus disabled)
        val allItems = CardGroup.values().toSet()
        val visibleCards = allItems - disabledItems

        return DashboardUiState(
            cardOrder = savedOrder,
            visibleCards = visibleCards,
        )
    }

    init {
        loadProfiles()
        ProfileManager.registerCallback(::onProfilesChanged)

        viewModelScope.launch {
            combine(
                AppLifecycleObserver.isUiActive,
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
                    commandClient.connect()
                } else {
                    commandClient.disconnect()
                }
            }
        }
    }

    private data class SessionTarget(val connect: Boolean, val remoteServerId: Long?)

    override fun onCleared() {
        super.onCleared()
        ProfileManager.unregisterCallback(::onProfilesChanged)
        commandClient.disconnect()
    }

    private fun onProfilesChanged() {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile

                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            profiles = profiles,
                            selectedProfileId = selectedId,
                            selectedProfileName = profiles.find { it.id == selectedId }?.name,
                        )
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }


    fun toggleService() {
        when (currentState.serviceStatus) {
            Status.Starting, Status.Started -> stopService()
            Status.Stopped -> sendGlobalEvent(UiEvent.RequestStartService)
            else -> {   }
        }
    }

    private fun stopService() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BoxService.stop()
                // Status will be updated via updateServiceStatus callback
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }


    fun selectProfile(profileId: Long) {
        if (currentState.isLoading || profileId == Settings.selectedProfile ||
            _serviceStatus.value !in setOf(Status.Started, Status.Stopped)
        ) {
            return
        }
        updateState { copy(isLoading = true) }
        val previousProfileId = Settings.selectedProfile
        val wasRunning = _serviceStatus.value == Status.Started

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProfileManager.get(profileId) ?: return@launch
                // Persist target before rebuild/start so BoxService reads the new profile.
                Settings.selectedProfile = profileId
                val switchId = RuntimeProfileState.nextSwitchRequestId()
                Log.i(
                    TAG,
                    "SELECT_REQUEST id=$switchId target=$profileId previous=$previousProfileId " +
                        "running=$wasRunning loaded=${RuntimeProfileState.loadedProfileId}",
                )

                if (wasRunning) {
                    BoxService.stop()
                    Log.i(TAG, "STOP_OLD requested")
                    val stopped =
                        withTimeoutOrNull(STOP_TIMEOUT_MS) {
                            while (_serviceStatus.value != Status.Stopped) {
                                delay(50L)
                            }
                            true
                        } == true
                    if (!stopped) {
                        Log.e(TAG, "STOP_OLD timeout status=${_serviceStatus.value}")
                        Settings.selectedProfile = previousProfileId
                        sendError(IllegalStateException("VPN stop timeout while switching subscription"))
                        return@launch
                    }
                    Log.i(TAG, "OLD_STOPPED")

                    Settings.rebuildServiceMode()
                    sendGlobalEvent(UiEvent.RequestReconnectService)
                    sendGlobalEvent(UiEvent.RequestStartService)
                    Log.i(TAG, "START_NEW requested")

                    // Wait until start leaves Stopped (Starting/Started), then until terminal.
                    val leftStopped =
                        withTimeoutOrNull(START_BEGIN_TIMEOUT_MS) {
                            while (_serviceStatus.value == Status.Stopped) {
                                delay(50L)
                            }
                            true
                        } == true
                    if (!leftStopped) {
                        Log.e(TAG, "START_NEW never left Stopped")
                        Settings.selectedProfile = previousProfileId
                        sendError(IllegalStateException("VPN start did not begin after subscription switch"))
                        return@launch
                    }

                    val terminal =
                        withTimeoutOrNull(START_TIMEOUT_MS) {
                            while (_serviceStatus.value == Status.Starting) {
                                delay(50L)
                            }
                            _serviceStatus.value
                        }
                    if (terminal != Status.Started) {
                        Log.e(TAG, "START_NEW failed terminal=$terminal")
                        Settings.selectedProfile = previousProfileId
                        sendError(
                            IllegalStateException(
                                "VPN failed to start with the selected subscription (status=$terminal)",
                            ),
                        )
                        return@launch
                    }
                    Log.i(
                        TAG,
                        "CONNECTED id=$switchId target=$profileId " +
                            "loaded=${RuntimeProfileState.loadedProfileId} " +
                            "fp=${RuntimeProfileState.loadedConfigFingerprint} " +
                            "match=${RuntimeProfileState.selectedMatchesLoaded(profileId)}",
                    )
                }

                withContext(Dispatchers.Main) { loadProfiles() }
            } catch (e: Exception) {
                Settings.selectedProfile = previousProfileId
                Log.e(TAG, "SELECT_FAILED target=$profileId", e)
                sendError(e)
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    private companion object {
        private const val TAG = "SFA.Switch"
        private const val STOP_TIMEOUT_MS = 15_000L
        private const val START_BEGIN_TIMEOUT_MS = 10_000L
        private const val START_TIMEOUT_MS = 45_000L
    }

    fun editProfile(profile: Profile) {
        sendGlobalEvent(UiEvent.EditProfile(profile.id))
    }

    fun deleteProfile(profile: Profile) {
        if (profile.id in currentState.deletingProfileIds) return
        updateState { copy(deletingProfileIds = deletingProfileIds + profile.id) }
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = currentState.profiles
            val status = _serviceStatus.value
            try {
                val outcome =
                    ProfileSafeDelete.delete(
                        profile = profile,
                        allProfiles = snapshot,
                        filesDir = Application.application.filesDir,
                        serviceStatus = status,
                        stopVpnAndAwait = {
                            if (_serviceStatus.value == Status.Stopped) return@delete true
                            withContext(Dispatchers.Main) { stopService() }
                            val deadline = System.currentTimeMillis() + 15_000L
                            while (System.currentTimeMillis() < deadline) {
                                if (_serviceStatus.value == Status.Stopped) return@delete true
                                delay(100)
                            }
                            _serviceStatus.value == Status.Stopped
                        },
                    )
                when (outcome) {
                    is ProfileSafeDelete.Outcome.Success -> {
                        withContext(Dispatchers.Main) {
                            updateState {
                                val cleared = selectedProfileId == profile.id
                                copy(
                                    profiles = profiles.filter { p -> p.id != profile.id },
                                    selectedProfileId = if (cleared) -1L else selectedProfileId,
                                    selectedProfileName = if (cleared) null else selectedProfileName,
                                )
                            }
                        }
                        loadProfiles()
                    }
                    is ProfileSafeDelete.Outcome.Failed -> {
                        loadProfiles()
                        sendErrorMessage("Не удалось удалить подписку: ${outcome.message}")
                    }
                }
            } finally {
                updateState { copy(deletingProfileIds = deletingProfileIds - profile.id) }
            }
        }
    }

    fun shareProfile(profile: Profile) {
        // Handled directly in ProfilesCard
    }

    fun shareProfileURL(profile: Profile) {
        // Handled directly in ProfilesCard
    }

    fun updateProfile(profile: Profile) {
        if (profile.typed.type != TypedProfile.Type.Remote || profile.id in currentState.updatingProfileIds || currentState.isUpdatingAll) return
        updateState { copy(updatingProfileIds = updatingProfileIds + profile.id) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outcome = refreshProfile(profile)
                if (outcome != null) {
                    io.nekohasekai.sfa.compose.base.ImportReportNotifier.show(profile, outcome)
                    when (outcome) {
                        ImportOperationOutcome.APPLIED,
                        ImportOperationOutcome.APPLIED_UNCHANGED,
                        ImportOperationOutcome.PARTIAL_APPLIED,
                        ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED,
                        -> updateState { copy(updatedProfileId = profile.id) }
                        ImportOperationOutcome.KEPT_LKG,
                        ImportOperationOutcome.REJECTED,
                        -> { /* dialog only */ }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                sendErrorMessage("Не удалось обновить подписку: ошибка загрузки или проверки конфигурации")
            }
            finally { updateState { copy(updatingProfileIds = updatingProfileIds - profile.id) } }
            delay(1500)
            updateState { if (updatedProfileId == profile.id) copy(updatedProfileId = null) else this }
        }
    }

    fun updateAllProfiles() {
        if (currentState.isUpdatingAll || currentState.updatingProfileIds.isNotEmpty()) return
        val profiles = currentState.profiles.filter { it.typed.type == TypedProfile.Type.Remote }
        if (profiles.isEmpty()) return
        updateState { copy(isUpdatingAll = true, updatingProfileIds = profiles.map { it.id }.toSet()) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reports = io.nekohasekai.sfa.utils.parallelTasks(profiles) { profile ->
                    try {
                        val outcome = refreshProfile(profile)
                        if (outcome != null) {
                            "${profile.name}\n${io.nekohasekai.sfa.compose.base.ImportReportNotifier.text(profile, outcome)}"
                        } else {
                            "${profile.name}\nОбновление пропущено"
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) {
                        "${profile.name}\nНе удалось обновить: ошибка загрузки или проверки конфигурации"
                    }
                    finally { updateState { copy(updatingProfileIds = updatingProfileIds - profile.id) } }
                }
                io.nekohasekai.sfa.compose.base.GlobalEventBus.emit(UiEvent.ImportReport("Обновление всех подписок", reports.joinToString("\n\n")))
            } finally { updateState { copy(isUpdatingAll = false, updatingProfileIds = emptySet()) } }
        }
    }

    /**
     * Remote subscription refresh via [ProfileConfigCommit] (same LKG path as Edit/Work).
     * Does not use plain writeText — failed validate/write leaves the previous file intact.
     */
    /**
     * Remote subscription refresh via [ProfileConfigCommit] (same LKG path as Edit/Work).
     * @return operation outcome, or null if skipped (deleted / missing / stale).
     */
    private suspend fun refreshProfile(profile: Profile): ImportOperationOutcome? {
        if (ProfileConfigCommit.isMarkedDeleted(profile.id)) return null
        if (ProfileManager.get(profile.id) == null) return null

        return when (val result = ManualSubscriptionUpdate.prepare(profile)) {
            is ManualPrepareResult.Applied -> {
                loadProfiles()
                if (result.replaced &&
                    profile.id == Settings.selectedProfile &&
                    _serviceStatus.value == Status.Started
                ) {
                    sendGlobalEvent(UiEvent.RequestReconnectService)
                }
                result.outcome
            }
            is ManualPrepareResult.AwaitConfirmation -> {
                updateState {
                    copy(
                        pendingImport = result.pending,
                        showPartialImportDialog = true,
                    )
                }
                null // UI will confirm; no false success
            }
            is ManualPrepareResult.Rejected -> result.outcome
            is ManualPrepareResult.Failed -> ImportOperationOutcome.KEPT_LKG
        }
    }

    fun confirmPartialImport() {
        val pending = currentState.pendingImport ?: return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(showPartialImportDialog = false) }
            when (val result = ManualSubscriptionUpdate.confirm(pending)) {
                is ManualPrepareResult.Applied -> {
                    loadProfiles()
                    if (result.replaced &&
                        pending.profileId == Settings.selectedProfile &&
                        _serviceStatus.value == Status.Started
                    ) {
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                    }
                    val profile = ProfileManager.get(pending.profileId)
                    if (profile != null) {
                        io.nekohasekai.sfa.compose.base.ImportReportNotifier.show(
                            profile,
                            result.outcome,
                        )
                    }
                    updateState { copy(pendingImport = null) }
                }
                else -> {
                    updateState {
                        copy(
                            pendingImport = null,
                        )
                    }
                    io.nekohasekai.sfa.compose.base.GlobalEventBus.emit(
                        UiEvent.ImportReport(
                            "Обновление подписки",
                            "Обновление не применено: конфигурация изменилась или профиль недоступен",
                        ),
                    )
                }
            }
        }
    }

    fun cancelPartialImport() {
        val pending = currentState.pendingImport
        if (pending != null) ManualSubscriptionUpdate.cancel(pending)
        updateState { copy(pendingImport = null, showPartialImportDialog = false) }
    }


    fun moveProfile(from: Int, to: Int) {
        val currentProfiles = currentState.profiles.toMutableList()

        if (from < to) {
            for (i in from until to) {
                Collections.swap(currentProfiles, i, i + 1)
            }
        } else {
            for (i in from downTo to + 1) {
                Collections.swap(currentProfiles, i, i - 1)
            }
        }

        // Update UI immediately
        updateState { copy(profiles = currentProfiles) }

        // Update user order in database
        viewModelScope.launch(Dispatchers.IO) {
            currentProfiles.forEachIndexed { index, profile ->
                profile.userOrder = index.toLong()
            }
            ProfileManager.update(currentProfiles)
        }
    }

    fun showAddProfileSheet() {
        updateState { copy(showAddProfileSheet = true) }
    }

    fun hideAddProfileSheet() {
        updateState { copy(showAddProfileSheet = false) }
    }


    fun updateServiceStatus(status: Status) {
        viewModelScope.launch {
            _serviceStatus.emit(status)
            updateState {
                copy(
                    serviceStatus = status,
                    isStatusVisible =
                    if (RemoteControlManager.remoteServer.value != null) {
                        isStatusVisible
                    } else {
                        status == Status.Starting || status == Status.Started
                    },
                )
            }
            handleServiceStatusChange(status)
        }
    }

    private fun handleServiceStatusChange(status: Status) {
        val isRemote = RemoteControlManager.remoteServer.value != null
        when (status) {
            Status.Started -> {
                if (isRemote) {
                    return
                }
                reloadSystemProxyStatus()
                reloadStartedAt()
            }

            Status.Stopped -> {
                if (isRemote) {
                    return
                }
                updateState {
                    copy(
                        hasGroups = false,
                        groupsCount = 0,
                        connectionsCount = 0,
                        serviceStartTime = null,
                        clashModeVisible = false,
                        systemProxyVisible = false,
                        trafficVisible = false,
                        memory = "",
                        goroutines = "",
                        connectionsIn = "0",
                        connectionsOut = "0",
                        uplink = "0 B/s",
                        downlink = "0 B/s",
                        uplinkTotal = "0 B",
                        downlinkTotal = "0 B",
                        uplinkHistory = List(30) { 0f },
                        downlinkHistory = List(30) { 0f },
                    )
                }
            }

            else -> {}
        }
    }

    private fun reloadStartedAt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startedAt = Libbox.newStandaloneCommandClient().startedAt
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(serviceStartTime = startedAt)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun reloadSystemProxyStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = Libbox.newStandaloneCommandClient().systemProxyStatus
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            systemProxyVisible = status.available,
                            systemProxyEnabled = status.enabled,
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun toggleSystemProxy(enabled: Boolean) {
        if (currentState.systemProxySwitching) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(systemProxySwitching = true) }
                Settings.systemProxyEnabled = enabled
                Libbox.newStandaloneCommandClient().setSystemProxyEnabled(enabled)
                delay(1000L)
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            systemProxyEnabled = enabled,
                            systemProxySwitching = false,
                        )
                    }
                }
            } catch (e: Exception) {
                sendError(e)
                updateState { copy(systemProxySwitching = false) }
            }
        }
    }

    fun selectClashMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().setClashMode(mode)
                // Update UI state directly without reconnecting
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(selectedClashMode = mode)
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    // CommandClient.Handler implementation
    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isStatusVisible = true) }
            // Returning from remote control skipped the local reloads that
            // normally run when the service starts.
            if (RemoteControlManager.remoteServer.value == null && _serviceStatus.value == Status.Started) {
                reloadSystemProxyStatus()
                reloadStartedAt()
            }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    memory = "",
                    goroutines = "",
                    isStatusVisible = false,
                )
            }
        }
    }

    override fun updateStatus(status: StatusMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                // Update history by adding new values and removing old ones
                val newUplinkHistory = (uplinkHistory.drop(1) + status.uplink.toFloat())
                val newDownlinkHistory = (downlinkHistory.drop(1) + status.downlink.toFloat())

                // Format the total values
                val newUplinkTotal = Libbox.formatBytes(status.uplinkTotal)
                val newDownlinkTotal = Libbox.formatBytes(status.downlinkTotal)

                copy(
                    memory = Libbox.formatBytes(status.memory),
                    goroutines = status.goroutines.toString(),
                    // Only set trafficVisible to true, never back to false from status updates
                    trafficVisible = if (status.trafficAvailable) true else trafficVisible,
                    connectionsCount = status.connectionsIn,
                    connectionsIn = status.connectionsIn.toString(),
                    connectionsOut = status.connectionsOut.toString(),
                    uplink = "${Libbox.formatBytes(status.uplink)}/s",
                    downlink = "${Libbox.formatBytes(status.downlink)}/s",
                    // Only update total values if they've actually changed
                    uplinkTotal = if (newUplinkTotal != uplinkTotal) newUplinkTotal else uplinkTotal,
                    downlinkTotal = if (newDownlinkTotal != downlinkTotal) newDownlinkTotal else downlinkTotal,
                    uplinkHistory = newUplinkHistory,
                    downlinkHistory = newDownlinkHistory,
                )
            }
        }
    }

    override fun initializeClashMode(modeList: List<String>, currentMode: String) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    clashModeVisible = modeList.size > 1,
                    clashModes = modeList,
                    selectedClashMode = currentMode,
                )
            }
        }
    }

    override fun updateClashMode(newMode: String) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(selectedClashMode = newMode)
            }
        }
    }

    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        viewModelScope.launch(Dispatchers.Main) {
            val hasGroups = newGroups.isNotEmpty()
            updateState {
                copy(hasGroups = hasGroups, groupsCount = newGroups.size)
            }
        }
    }

    fun toggleCardSettingsDialog() {
        updateState {
            copy(showCardSettingsDialog = !showCardSettingsDialog)
        }
    }

    fun toggleCardVisibility(cardGroup: CardGroup) {
        // Profiles card cannot be disabled
        if (cardGroup == CardGroup.Profiles) {
            return
        }

        updateState {
            val newVisibleCards =
                if (visibleCards.contains(cardGroup)) {
                    visibleCards - cardGroup
                } else {
                    visibleCards + cardGroup
                }
            // Save disabled items to settings
            saveDisabledItems(newVisibleCards)
            // Also save the current order if not already saved (indicates user has configured dashboard)
            if (Settings.dashboardItemOrder.isBlank()) {
                saveItemOrder(cardOrder)
            }
            copy(visibleCards = newVisibleCards)
        }
    }

    fun closeCardSettingsDialog() {
        updateState {
            copy(showCardSettingsDialog = false)
        }
    }

    fun reorderCards(newOrder: List<CardGroup>) {
        updateState {
            saveItemOrder(newOrder)
            copy(cardOrder = newOrder)
        }
    }

    fun resetCardOrder() {
        // Clear saved settings to restore defaults
        Settings.dashboardItemOrder = ""
        Settings.dashboardDisabledItems = emptySet()

        updateState {
            copy(
                cardOrder = getDefaultItemOrder(),
                visibleCards = CardGroup.values().toSet(),
            )
        }
    }

    // Helper functions for serialization
    private fun getDefaultItemOrder() = listOf(
        CardGroup.UploadTraffic,
        CardGroup.DownloadTraffic,
        CardGroup.Debug,
        CardGroup.Connections,
        CardGroup.SystemProxy,
        CardGroup.ClashMode,
        CardGroup.Profiles,
    )

    private fun loadItemOrder(): List<CardGroup> {
        val savedOrder = Settings.dashboardItemOrder
        if (savedOrder.isBlank()) {
            return getDefaultItemOrder()
        }

        return try {
            val jsonArray = JSONArray(savedOrder)
            val order = mutableListOf<CardGroup>()

            for (i in 0 until jsonArray.length()) {
                val itemName = jsonArray.getString(i)
                stringToCardGroup(itemName)?.let { order.add(it) }
            }

            // Add any new items that aren't in the saved order
            val allItems = CardGroup.values().toSet()
            val savedItems = order.toSet()
            val newItems = allItems - savedItems

            order.addAll(newItems)
            order
        } catch (e: JSONException) {
            getDefaultItemOrder()
        }
    }

    private fun saveItemOrder(order: List<CardGroup>) {
        val jsonArray = JSONArray()
        order.forEach { item ->
            jsonArray.put(cardGroupToString(item))
        }
        Settings.dashboardItemOrder = jsonArray.toString()
    }

    private fun loadDisabledItems(): Set<CardGroup> {
        val savedDisabled = Settings.dashboardDisabledItems
        // Filter out Profiles from disabled items (it cannot be disabled)
        return savedDisabled.mapNotNull { stringToCardGroup(it) }
            .filter { it != CardGroup.Profiles }
            .toSet()
    }

    private fun saveDisabledItems(visibleCards: Set<CardGroup>) {
        val allItems = CardGroup.values().toSet()
        // Always ensure Profiles is in visibleCards (cannot be disabled)
        val actualVisibleCards = visibleCards + CardGroup.Profiles
        val disabledItems = allItems - actualVisibleCards
        Settings.dashboardDisabledItems = disabledItems.map { cardGroupToString(it) }.toSet()
    }

    private fun cardGroupToString(card: CardGroup): String = card.name

    private fun stringToCardGroup(name: String): CardGroup? = try {
        CardGroup.valueOf(name)
    } catch (e: IllegalArgumentException) {
        null
    }
}
