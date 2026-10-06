package io.nekohasekai.sfa.utils

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
}
