package io.nekohasekai.sfa.compose.screen.log

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.sfa.compose.util.AnsiColorUtils
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.CommandTarget
import io.nekohasekai.sfa.utils.RemoteControlManager
import java.util.LinkedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogViewModel : BaseLogViewModel(), CommandClient.Handler {
    companion object {
        private val maxLines = 3000
    }

    private val bufferedLogs = LinkedList<ProcessedLogEntry>()
    private val commandClient =
        CommandClient(
            scope = viewModelScope,
            connectionType = CommandClient.ConnectionType.Log,
            handler = this,
        )
    private var lastServiceStatus: Status = Status.Stopped
    private val serviceStatusFlow = MutableStateFlow(Status.Stopped)
    private val visibleScreens = MutableStateFlow(0)

    fun setVisible(visible: Boolean) {
        visibleScreens.update { (it + if (visible) 1 else -1).coerceAtLeast(0) }
    }

    init {
        viewModelScope.launch {
            combine(
                    AppLifecycleObserver.isUiActive,
                    RemoteControlManager.remoteServer,
                    RemoteControlManager.isConnected,
                    serviceStatusFlow,
                    visibleScreens,
                ) { foreground, remoteServer, remoteConnected, status, visible ->
                    SessionTarget(
                        connect =
                            foreground &&
                                visible > 0 &&
                                if (remoteServer != null) remoteConnected
                                else status == Status.Started,
                        remoteServerId = remoteServer?.id,
                    )
                }
                .distinctUntilChanged()
                .collect { target ->
                    if (target.connect) {
                        commandClient.connect()
                    } else {
                        commandClient.disconnect()
                    }
                }
        }
    }

    private data class SessionTarget(val connect: Boolean, val remoteServerId: Long?)

    private fun processLogEntry(entry: LogEntry): ProcessedLogEntry {
        val level = LogLevel.entries.find { it.priority == entry.level } ?: LogLevel.Default
        return ProcessedLogEntry(
            id = logIdGenerator.incrementAndGet(),
            entry = LogEntryData(level = level, message = entry.message),
            annotatedString = AnsiColorUtils.ansiToAnnotatedString(entry.message),
        )
    }

    override fun updateServiceStatus(status: Status) {
        lastServiceStatus = status
        serviceStatusFlow.value = status
        _uiState.update { it.copy(serviceStatus = status) }

        if (RemoteControlManager.remoteServer.value != null) {
            return
        }
        when (status) {
            Status.Stopped,
            Status.Stopping -> {
                _uiState.update { it.copy(isConnected = false) }
            }

            else -> {}
        }
    }

    override fun onConnected() {
        _uiState.update { it.copy(isConnected = true) }
    }

    override fun onDisconnected() {
        _uiState.update { it.copy(isConnected = false) }
    }

    override fun setDefaultLogLevel(level: Int) {
        val logLevel =
            LogLevel.entries.find { it.priority == level } ?: error("Unknown log level: $level")
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(defaultLogLevel = logLevel) }
            updateDisplayedLogs()
        }
    }

    override fun clearLogs() {
        viewModelScope.launch(Dispatchers.Main) {
            allLogs.clear()
            bufferedLogs.clear()
            _uiState.update { it.copy(isPaused = false) }
            updateDisplayedLogs()
        }
    }

    override fun requestClearLogs() {
        viewModelScope.launch {
            val sent =
                withContext(Dispatchers.IO) {
                    runCatching { CommandTarget.standaloneClient().clearLogs() }.isSuccess
                }
            // With the service stopped there is no broadcast to clear the UI,
            // so the local buffer is cleared directly.
            if (!sent) {
                clearLogs()
            }
        }
    }

    override fun appendLogs(message: List<LogEntry>) {
        val processedLogs = message.takeLast(maxLines).map { processLogEntry(it) }
        viewModelScope.launch(Dispatchers.Main) {
            if (_uiState.value.isPaused) {
                bufferedLogs.addAll(processedLogs)
                while (bufferedLogs.size > maxLines) bufferedLogs.removeFirst()
            } else {
                val totalSize = allLogs.size + processedLogs.size
                val removeCount = (totalSize - maxLines).coerceAtLeast(0)

                if (removeCount > 0) {
                    repeat(removeCount) { allLogs.removeFirst() }
                }

                allLogs.addAll(processedLogs)
                updateDisplayedLogs()

                if (
                    _autoScrollEnabled.value &&
                        !_uiState.value.isPaused &&
                        !_uiState.value.isSearchActive
                ) {
                    scrollToBottom()
                }
            }
        }
    }

    override fun togglePause() {
        val currentState = _uiState.value
        if (currentState.isPaused && bufferedLogs.isNotEmpty()) {
            val totalSize = allLogs.size + bufferedLogs.size
            val removeCount = (totalSize - maxLines).coerceAtLeast(0)

            if (removeCount > 0) {
                repeat(removeCount) { allLogs.removeFirst() }
            }

            allLogs.addAll(bufferedLogs)
            bufferedLogs.clear()
        }

        _uiState.update { it.copy(isPaused = !it.isPaused) }
        updateDisplayedLogs()
    }

    override fun onCleared() {
        super.onCleared()
        commandClient.disconnect()
    }
}
