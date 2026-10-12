import io.nekohasekai.sfa.bg.ProfileSelectionQueue
import org.junit.Assert.*
import org.junit.Test

class ProfileSelectionQueueTest {
    @Test fun startingRetainsOnlyLatestChoice() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        assertNull(queue.take(false))
        queue.offer(3, true)
        assertEquals(ProfileSelectionQueue.Request(3, true), queue.take(true))
    }

    @Test fun activeSwitchQueuesReturnToOriginalSelection() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        val active = queue.take(true)!!
        queue.offer(1, false)
        assertNull(queue.take(true))
        queue.complete(active)
        assertEquals(ProfileSelectionQueue.Request(1, true), queue.take(true))
    }

    @Test fun failedStartDoesNotLosePendingConnectionIntent() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        val active = queue.take(true)!!
        queue.offer(3, false)
        queue.offer(4, false)
        queue.complete(active)
        assertEquals(ProfileSelectionQueue.Request(4, true), queue.take(true))
    }

    @Test fun stoppingSelectionDoesNotAutoConnect() {
        val queue = ProfileSelectionQueue()
        queue.offer(3, false)
        assertNull(queue.take(false))
        assertEquals(ProfileSelectionQueue.Request(3, false), queue.take(true))
    }

    @Test fun laterStoppingRequestRetainsEarlierStartingIntent() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        queue.offer(3, false)
        assertEquals(ProfileSelectionQueue.Request(3, true), queue.take(true))
    }

    @Test fun obsoleteCompletionCannotReleaseNewActiveRequest() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, false)
        val old = queue.take(true)!!
        queue.complete(old)
        queue.offer(3, false)
        val current = queue.take(true)!!
        queue.offer(4, false)
        queue.complete(old)
        assertNull(queue.take(true))
        queue.complete(current)
        assertEquals(4L, queue.take(true)!!.profileId)
    }

    @Test fun stopBeforeDispatchRejectsActiveStart() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        val active = queue.take(true)!!
        queue.cancelAll()
        var starts = 0
        assertFalse(queue.runIfCurrent(active) { starts++ })
        assertEquals(0, starts)
    }

    @Test fun stoppedActiveRequestCannotCarryConnectionIntentIntoNewSelection() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        val active = queue.take(true)!!
        queue.cancelAll()
        queue.offer(3, false)
        assertNull(queue.take(true))
        queue.complete(active)
        assertEquals(ProfileSelectionQueue.Request(3, false), queue.take(true))
    }

    @Test fun dispatchedStartIsInvalidatedByStopGeneration() {
        val queue = ProfileSelectionQueue()
        val runtime = io.nekohasekai.sfa.bg.RuntimeProfileState
        val switchId = runtime.nextSwitchRequestId()
        queue.offer(2, true)
        val active = queue.take(true)!!
        var dispatched = -1L
        assertTrue(queue.runIfCurrent(active) { dispatched = switchId })
        queue.cancelAll()
        runtime.nextSwitchRequestId()
        assertFalse(runtime.beginStart(dispatched, 2))
        assertFalse(runtime.tryMarkLoaded(dispatched, 2, "test"))
    }

    @Test fun newerManualStartSupersedesEarlierScopedStop() {
        val runtime = io.nekohasekai.sfa.bg.RuntimeProfileState
        val oldStart = runtime.nextSwitchRequestId()
        val stop = runtime.nextSwitchRequestId()
        assertFalse(runtime.beginStart(oldStart, 23456))
        val manualStart = runtime.nextSwitchRequestId()
        assertNotEquals(stop, runtime.currentSwitchRequestId())
        assertTrue(runtime.beginStart(manualStart, 23456))
        runtime.clear()
    }

    @Test fun skippedRequestCanImmediatelyDrainNewPendingSelection() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, false)
        val skipped = queue.take(true)!!
        queue.offer(3, false)
        queue.complete(skipped)
        assertEquals(3L, queue.take(true)!!.profileId)
    }

    @Test fun explicitStopCancelsPendingChoice() {
        val queue = ProfileSelectionQueue()
        queue.offer(2, true)
        val active = queue.take(true)!!
        queue.offer(3, true)
        queue.cancelAll()
        queue.complete(active)
        assertNull(queue.take(true))
    }
}
