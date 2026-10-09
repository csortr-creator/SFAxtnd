package io.nekohasekai.sfa.bg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeProfileStateTest {

    @Before
    fun reset() {
        RuntimeProfileState.clear()
    }

    @Test
    fun markLoadedSetsIdAndFingerprint() {
        RuntimeProfileState.markLoaded(42L, """{"outbounds":[]}""")
        assertEquals(42L, RuntimeProfileState.loadedProfileId)
        assertTrue(RuntimeProfileState.loadedConfigFingerprint.length == 16)
        assertTrue(RuntimeProfileState.selectedMatchesLoaded(42L))
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(99L))
    }

    @Test
    fun clearResetsLoaded() {
        RuntimeProfileState.markLoaded(1L, "a")
        RuntimeProfileState.clear()
        assertEquals(-1L, RuntimeProfileState.loadedProfileId)
        assertEquals("", RuntimeProfileState.loadedConfigFingerprint)
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(1L))
    }

    @Test
    fun fingerprintStableForSameContent() {
        val a = RuntimeProfileState.fingerprint("same")
        val b = RuntimeProfileState.fingerprint("same")
        val c = RuntimeProfileState.fingerprint("other")
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun switchRequestIdMonotonic() {
        val x = RuntimeProfileState.nextSwitchRequestId()
        val y = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(y > x)
    }

    @Test
    fun selectedNotLoadedAfterStopSemantics() {
        RuntimeProfileState.markLoaded(7L, "cfg")
        RuntimeProfileState.clear()
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(7L))
    }
}
