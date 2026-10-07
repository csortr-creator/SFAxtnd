package io.nekohasekai.sfa.compose.base

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class UiEvent {
    data class ErrorMessage(val message: String) : UiEvent()

    data class ImportReport(val profileName: String, val text: String) : UiEvent()

    data class OpenUrl(val url: String) : UiEvent()

    data class EditProfile(val profileId: Long) : UiEvent()

    data class Navigate(val route: String) : UiEvent()

    object RequestStartService : UiEvent()

    object RequestReconnectService : UiEvent()

    data class ApplyServiceChange(val mode: Mode) : UiEvent() {
        enum class Mode {
            Reload,
            Restart,
        }
    }
}

interface ScreenEvent

interface EventHandler<T : UiEvent> {
    val events: SharedFlow<T>

    suspend fun sendEvent(event: T)
}

class UiEventHandler<T : UiEvent> : EventHandler<T> {
    private val _events = MutableSharedFlow<T>()
    override val events: SharedFlow<T> = _events.asSharedFlow()

    override suspend fun sendEvent(event: T) {
        _events.emit(event)
    }
}
