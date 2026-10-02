package io.nekohasekai.sfa.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TailscaleSSHTerminalState(
    val sessions: List<ManagedSession> = emptyList(),
    val activeSessionId: String? = null,
) {
    val activeSession: ManagedSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }
}

object TailscaleSSHSessionStore {
    private val _state = MutableStateFlow(TailscaleSSHTerminalState())
    val state: StateFlow<TailscaleSSHTerminalState> = _state.asStateFlow()

    fun addSession(presented: TailscaleSSHPresentedSession) {
        // Built-in terminal removed; sessions are not started.
    }

    fun removeSession(id: String) {
        val current = _state.value
        val remaining = current.sessions.filter { it.id != id }
        val newActiveId = if (current.activeSessionId == id) {
            remaining.lastOrNull()?.id
        } else {
            current.activeSessionId
        }
        _state.value = current.copy(sessions = remaining, activeSessionId = newActiveId)
    }

    fun setActiveSession(id: String) {
        if (_state.value.sessions.any { it.id == id }) {
            _state.value = _state.value.copy(activeSessionId = id)
        }
    }
}
