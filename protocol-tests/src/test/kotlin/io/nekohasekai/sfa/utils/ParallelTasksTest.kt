package io.nekohasekai.sfa.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ParallelTasksTest {
    @Test fun startsSeveralSubscriptionsTogetherAndBoundsConcurrency() = runBlocking {
        val active = AtomicInteger()
        val started = AtomicInteger()
        val fourStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = async {
            parallelTasks((1..9).toList()) { id ->
                assertTrue(active.incrementAndGet() <= 4)
                if (started.incrementAndGet() == 4) fourStarted.complete(Unit)
                try { release.await(); "report-$id" }
                finally { active.decrementAndGet() }
            }
        }
        withTimeout(2000) { fourStarted.await() }
        assertEquals(4, started.get())
        release.complete(Unit)
        assertEquals((1..9).map { "report-$it" }, job.await())
        assertEquals(0, active.get())
    }
    @Test fun cancellationFinishesWorkersAndDoesNotStartQueuedSubscriptions() = runBlocking {
        val started = AtomicInteger()
        val ready = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val finished = AtomicInteger()
        val job = async {
            parallelTasks((1..9).toList(), 2) {
                if (started.incrementAndGet() == 2) ready.complete(Unit)
                try { never.await() } finally { finished.incrementAndGet() }
            }
        }
        withTimeout(2000) { ready.await() }
        job.cancelAndJoin()
        assertEquals(2, started.get())
        assertEquals(2, finished.get())
    }
}
