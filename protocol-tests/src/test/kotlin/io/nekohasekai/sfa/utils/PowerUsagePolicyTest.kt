package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerUsagePolicyTest {
    @Test
    fun backgroundAndScreenOffStopUiUpdates() {
        assertTrue(PowerUsagePolicy.uiActive(true, true, false))
        assertFalse(PowerUsagePolicy.uiActive(false, true, false))
        assertFalse(PowerUsagePolicy.uiActive(true, false, false))
        assertFalse(PowerUsagePolicy.uiActive(true, true, true))
    }

    @Test
    fun notificationCanUpdateWithoutForegroundUi() {
        assertTrue(PowerUsagePolicy.notificationActive(true, true, false))
        assertFalse(PowerUsagePolicy.notificationActive(false, true, false))
    }

    @Test
    fun startingDuringSleepDoesNotStartNotificationPolling() {
        assertFalse(PowerUsagePolicy.notificationActive(true, false, false))
        assertFalse(PowerUsagePolicy.notificationActive(true, true, true))
        assertTrue(PowerUsagePolicy.notificationActive(true, true, false))
    }

    @Test
    fun slowerPollingReportsBytesPerSecond() {
        assertEquals(3000L, PowerUsagePolicy.bytesPerSecond(9000L, 3000L))
        assertEquals(9000L, PowerUsagePolicy.bytesPerSecond(9000L, 1000L))
        assertEquals(4000L, PowerUsagePolicy.bytesPerSecond(1000L, 250L))
        assertEquals(0L, PowerUsagePolicy.bytesPerSecond(0L, 3000L))
    }

    @Test
    fun resetCountersDoNotReportNegativeSpeed() {
        assertEquals(0L, PowerUsagePolicy.bytesPerSecond(-1L, 3000L))
    }
}
