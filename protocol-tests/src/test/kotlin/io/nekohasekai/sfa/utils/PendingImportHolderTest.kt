package io.nekohasekai.sfa.utils

import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingImportHolderTest {
    private enum class Phase { PENDING, APPLYING, CANCELLED }

    @Test
    fun doubleConfirmCasPreventsSecondApply() {
        val phase = AtomicReference(Phase.PENDING)
        assertTrue(phase.compareAndSet(Phase.PENDING, Phase.APPLYING))
        assertFalse(phase.compareAndSet(Phase.PENDING, Phase.APPLYING))
        assertEquals(Phase.APPLYING, phase.get())
    }

    @Test
    fun cancelFromPendingBlocksConfirm() {
        val phase = AtomicReference(Phase.PENDING)
        assertTrue(phase.compareAndSet(Phase.PENDING, Phase.CANCELLED))
        assertFalse(phase.compareAndSet(Phase.PENDING, Phase.APPLYING))
    }
}
