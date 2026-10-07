package io.nekohasekai.sfa.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal suspend fun <T, R> parallelTasks(items: List<T>, limit: Int = 4, task: suspend (T) -> R): List<R> = coroutineScope {
    val slots = Semaphore(limit)
    items.map { item -> async { slots.withPermit { task(item) } } }.awaitAll()
}
