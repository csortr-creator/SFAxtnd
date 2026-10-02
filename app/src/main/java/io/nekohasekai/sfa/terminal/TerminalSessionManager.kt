package io.nekohasekai.sfa.terminal

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

enum class TerminalSessionPhase { CONNECTING, RUNNING, FINISHED }

data class ManagedSession(
    val id: String = UUID.randomUUID().toString(),
    val presentedSession: TailscaleSSHPresentedSession,
) {
    var commandClient: io.nekohasekai.libbox.CommandClient? = null
    val phase = MutableStateFlow(TerminalSessionPhase.CONNECTING)
    val banner = MutableStateFlow<String?>(null)
    var exitWatcher: Job? = null
    var exitSignal: String? = null
    var exitError: String? = null
}
