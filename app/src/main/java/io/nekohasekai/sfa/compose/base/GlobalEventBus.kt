package io.nekohasekai.sfa.compose.base

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GlobalEventBus {
    private val _events =
        MutableSharedFlow<UiEvent>(
            replay = 0,
            extraBufferCapacity = 10,
        )

    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    suspend fun emit(event: UiEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: UiEvent): Boolean = _events.tryEmit(event)
}
