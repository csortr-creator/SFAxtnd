package io.nekohasekai.sfa.utils

import kotlinx.coroutines.withTimeout

/** Await the first underlying network callback; registration alone does not imply readiness. */
internal suspend fun <T : Any> awaitProbeNetwork(
    current: () -> T?,
    awaitAvailable: suspend () -> T,
    timeoutMs: Long = 5000L,
): T = current() ?: withTimeout(timeoutMs) { awaitAvailable() }
