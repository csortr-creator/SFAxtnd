package io.nekohasekai.sfa.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationUpdateGateTest {
    @Test
    fun idleSamplesDoNotRepublishNotification() {
        val updates = NotificationUpdateGate()
        assertTrue(updates.changed("Subscription", "0/s"))
        repeat(100) { assertFalse(updates.changed("Subscription", "0/s")) }
    }

    @Test
    fun titleAndTrafficChangesRemainVisible() {
        val updates = NotificationUpdateGate()
        updates.changed("Subscription", "0/s")
        assertTrue(updates.changed("Server", "0/s"))
        assertTrue(updates.changed("Server", "1 KiB/s"))
        assertTrue(updates.changed("Server", "0/s"))
        assertFalse(updates.changed("Server", "0/s"))
    }

    @Test
    fun restartAcceptsSameInitialNotification() {
        val updates = NotificationUpdateGate()
        updates.changed("Subscription", "Connected")
        updates.reset()
        assertTrue(updates.changed("Subscription", "Connected"))
    }
}
