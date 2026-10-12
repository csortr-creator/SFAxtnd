package io.nekohasekai.sfa.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ProbeNetworkReadinessTest {
    @Test fun waitsForCallbackRatherThanRejectingConnectedDevice() = runBlocking {
        val callback = CompletableDeferred<String>()
        val waiting = CompletableDeferred<Unit>()
        val result = async {
            awaitProbeNetwork({ null }, {
                waiting.complete(Unit)
                callback.await()
            })
        }
        withTimeout(2000L) { waiting.await() }
        assertFalse(result.isCompleted)
        callback.complete("underlying-mobile")
        assertEquals("underlying-mobile", withTimeout(2000L) { result.await() })
    }

    @Test fun availableNetworkDoesNotWaitAgain() = runBlocking {
        assertEquals("wifi", awaitProbeNetwork({ "wifi" }, { error("unexpected wait") }))
    }

    @Test fun missingNetworkTimesOutRatherThanHanging() = runBlocking {
        try {
            awaitProbeNetwork<String>({ null }, { CompletableDeferred<String>().await() }, 20L)
            fail("Missing network must time out")
        } catch (_: TimeoutCancellationException) {
            // Native startup can report a bounded failure when connectivity is absent.
        }
    }
}
