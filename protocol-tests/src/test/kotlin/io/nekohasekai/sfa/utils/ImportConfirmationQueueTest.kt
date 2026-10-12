package io.nekohasekai.sfa.utils

import org.junit.Assert.*
import org.junit.Test

class ImportConfirmationQueueTest {
    @Test fun bulkResultsKeepFirstDialogAndQueueLaterSubscriptions() {
        val first = Any()
        val second = Any()
        val queue = ImportConfirmationQueue<Any>().offer(first).offer(second)
        assertSame(first, queue.current)
        assertTrue(queue.showDialog)
        assertSame(second, queue.complete(first).current)
    }

    @Test fun arrivingResultDoesNotReplaceApplyingRequest() {
        val first = Any()
        val second = Any()
        val queue = ImportConfirmationQueue<Any>().offer(first).begin(first).offer(second)
        assertSame(first, queue.current)
        assertFalse(queue.showDialog)
        val next = queue.complete(first)
        assertSame(second, next.current)
        assertTrue(next.showDialog)
    }

    @Test fun duplicateConfirmAndCancelDuringApplyCannotConsumeRequest() {
        val first = Any()
        val queue = ImportConfirmationQueue<Any>().offer(first).begin(first)
        assertFalse(queue.canBegin(first))
        assertSame(queue, queue.begin(first))
    }

    @Test fun obsoleteDialogCallbackCannotApplyOrRemoveNextSubscription() {
        val first = Any()
        val second = Any()
        val queue = ImportConfirmationQueue<Any>().offer(first).offer(second).complete(first)
        assertFalse(queue.canBegin(first))
        assertSame(queue, queue.begin(first))
        assertSame(queue, queue.complete(first))
        assertSame(second, queue.current)
    }

    @Test fun cancelledDialogAdvancesAndLastCompletionClosesQueue() {
        val first = Any()
        val second = Any()
        val queue = ImportConfirmationQueue<Any>().offer(first).offer(second).complete(first)
        assertTrue(queue.canBegin(second))
        val empty = queue.complete(second)
        assertNull(empty.current)
        assertFalse(empty.showDialog)
    }

    @Test fun duplicateHolderIsNotEnqueuedTwiceAndReducersAreImmutable() {
        val first = Any()
        val empty = ImportConfirmationQueue<Any>()
        val queue = empty.offer(first)
        assertNull(empty.current)
        assertSame(queue, queue.offer(first))
        queue.begin(first)
        assertTrue(queue.canBegin(first))
    }
}
