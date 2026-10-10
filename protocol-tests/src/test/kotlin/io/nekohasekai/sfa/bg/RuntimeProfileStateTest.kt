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
        // Advance id so tests do not depend on prior absolute value
        RuntimeProfileState.nextSwitchRequestId()
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
    fun fingerprintIsStableForSameContent() {
        val a = RuntimeProfileState.fingerprint("same")
        val b = RuntimeProfileState.fingerprint("same")
        assertEquals(a, b)
        assertTrue(a.length == 16)
    }

    @Test
    fun selectedMatchesLoadedFalseWhenCleared() {
        RuntimeProfileState.markLoaded(7L, "x")
        RuntimeProfileState.clear()
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(7L))
    }

    /** R2a: start(reqId=1) then switch bumps generation; completion of 1 must not write loaded. */
    @Test
    fun staleCompletionDoesNotOverwriteLoaded() {
        val req1 = RuntimeProfileState.nextSwitchRequestId()
        val req2 = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(req2 > req1)

        // Successful start for generation 2
        assertTrue(RuntimeProfileState.tryMarkLoaded(req2, 200L, "profile-b"))
        assertEquals(200L, RuntimeProfileState.loadedProfileId)

        // Late completion of generation 1 must be discarded
        assertFalse(RuntimeProfileState.tryMarkLoaded(req1, 100L, "profile-a"))
        assertEquals(200L, RuntimeProfileState.loadedProfileId)
        assertTrue(RuntimeProfileState.selectedMatchesLoaded(200L))
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(100L))
    }

    /** R2a: superseded request id never becomes loaded. */
    @Test
    fun supersededRequestIsRejected() {
        val stale = RuntimeProfileState.currentSwitchRequestId()
        RuntimeProfileState.nextSwitchRequestId() // supersede
        assertFalse(RuntimeProfileState.tryMarkLoaded(stale, 5L, "stale-config"))
        assertEquals(-1L, RuntimeProfileState.loadedProfileId)
        assertEquals("", RuntimeProfileState.loadedConfigFingerprint)
    }

    /** R2a: failure path clears loaded (stopAndAlert / quietStop / stopService). */
    @Test
    fun failureClearsLoaded() {
        val req = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.tryMarkLoaded(req, 9L, "ok"))
        assertEquals(9L, RuntimeProfileState.loadedProfileId)
        RuntimeProfileState.clear()
        assertEquals(-1L, RuntimeProfileState.loadedProfileId)
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(9L))
    }

    @Test
    fun tryMarkLoadedSucceedsForCurrentId() {
        val req = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.tryMarkLoaded(req, 3L, "cfg"))
        assertEquals(3L, RuntimeProfileState.loadedProfileId)
        assertTrue(RuntimeProfileState.loadedConfigFingerprint.isNotEmpty())
    }
}
