package io.nekohasekai.sfa.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnDetectionTestTest {

    @Test
    fun testIsVpnInterface_happyPaths() {
        assertTrue("tun0 should be detected as VPN interface", VpnDetectionTest.isVpnInterface("tun0"))
        assertTrue("ppp1 should be detected as VPN interface", VpnDetectionTest.isVpnInterface("ppp1"))
        assertTrue("tap2 should be detected as VPN interface", VpnDetectionTest.isVpnInterface("tap2"))
    }

    @Test
    fun testIsVpnInterface_caseInsensitive() {
        assertTrue("TUN0 should be detected as VPN interface", VpnDetectionTest.isVpnInterface("TUN0"))
        assertTrue("Ppp1 should be detected as VPN interface", VpnDetectionTest.isVpnInterface("Ppp1"))
        assertTrue("TaP2 should be detected as VPN interface", VpnDetectionTest.isVpnInterface("TaP2"))
    }

    @Test
    fun testIsVpnInterface_negativeScenarios() {
        assertFalse("eth0 should not be detected as VPN interface", VpnDetectionTest.isVpnInterface("eth0"))
        assertFalse("wlan0 should not be detected as VPN interface", VpnDetectionTest.isVpnInterface("wlan0"))
        assertFalse("rmnet0 should not be detected as VPN interface", VpnDetectionTest.isVpnInterface("rmnet0"))
        assertFalse("dummy0 should not be detected as VPN interface", VpnDetectionTest.isVpnInterface("dummy0"))
        assertFalse("lo should not be detected as VPN interface", VpnDetectionTest.isVpnInterface("lo"))
        assertFalse("random_string should not be detected as VPN interface", VpnDetectionTest.isVpnInterface("random_string"))
    }

    @Test
    fun testIsVpnInterface_edgeCases() {
        assertFalse("null should return false", VpnDetectionTest.isVpnInterface(null))
        assertFalse("empty string should return false", VpnDetectionTest.isVpnInterface(""))
        assertFalse("blank string should return false", VpnDetectionTest.isVpnInterface("   "))
    }
}
