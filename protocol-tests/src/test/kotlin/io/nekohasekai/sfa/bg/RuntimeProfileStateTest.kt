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

    @Test
    fun staleCompletionDoesNotOverwriteLoaded() {
        val req1 = RuntimeProfileState.nextSwitchRequestId()
        val req2 = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(req2 > req1)
        assertTrue(RuntimeProfileState.tryMarkLoaded(req2, 200L, "profile-b"))
        assertEquals(200L, RuntimeProfileState.loadedProfileId)
        assertFalse(RuntimeProfileState.tryMarkLoaded(req1, 100L, "profile-a"))
        assertEquals(200L, RuntimeProfileState.loadedProfileId)
        assertTrue(RuntimeProfileState.selectedMatchesLoaded(200L))
        assertFalse(RuntimeProfileState.selectedMatchesLoaded(100L))
    }

    @Test
    fun supersededRequestIsRejected() {
        val stale = RuntimeProfileState.currentSwitchRequestId()
        RuntimeProfileState.nextSwitchRequestId()
        assertFalse(RuntimeProfileState.tryMarkLoaded(stale, 5L, "stale-config"))
        assertEquals(-1L, RuntimeProfileState.loadedProfileId)
        assertEquals("", RuntimeProfileState.loadedConfigFingerprint)
    }

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

    // --- R2b resolve / persist policy ---

    @Test
    fun resolveProfileIdPrefersIntentTarget() {
        assertEquals(42L, SwitchStartResolver.resolveProfileId(42L, 7L))
        assertEquals(7L, SwitchStartResolver.resolveProfileId(-1L, 7L))
        assertEquals(7L, SwitchStartResolver.resolveProfileId(0L, 7L))
    }

    @Test
    fun resolveRequestIdPrefersIntentSwitch() {
        assertEquals(5L, SwitchStartResolver.resolveRequestId(5L, 9L))
        assertEquals(9L, SwitchStartResolver.resolveRequestId(-1L, 9L))
    }

    @Test
    fun shouldPersistOnlyAfterSuccessfulExplicitTarget() {
        assertTrue(
            SwitchStartResolver.shouldPersistSelectedAfterLoad(
                targetProfileId = 10L,
                markLoadedSucceeded = true,
                requestIdStillCurrent = true,
            ),
        )
        assertFalse(
            SwitchStartResolver.shouldPersistSelectedAfterLoad(
                targetProfileId = 10L,
                markLoadedSucceeded = false,
                requestIdStillCurrent = true,
            ),
        )
        assertFalse(
            SwitchStartResolver.shouldPersistSelectedAfterLoad(
                targetProfileId = 10L,
                markLoadedSucceeded = true,
                requestIdStillCurrent = false,
            ),
        )
        assertFalse(
            SwitchStartResolver.shouldPersistSelectedAfterLoad(
                targetProfileId = -1L,
                markLoadedSucceeded = true,
                requestIdStillCurrent = true,
            ),
        )
    }

    @Test
    fun tryMarkLoadedWithTargetIdMatchesResolverContract() {
        val settingsSelected = 1L
        val target = 99L
        val req = RuntimeProfileState.nextSwitchRequestId()
        val profileId = SwitchStartResolver.resolveProfileId(target, settingsSelected)
        assertEquals(99L, profileId)
        val ok = RuntimeProfileState.tryMarkLoaded(req, profileId, "cfg-99")
        assertTrue(ok)
        assertTrue(
            SwitchStartResolver.shouldPersistSelectedAfterLoad(
                target,
                markLoadedSucceeded = ok,
                requestIdStillCurrent = req == RuntimeProfileState.currentSwitchRequestId(),
            ),
        )
        // Stale completion must not imply persist
        RuntimeProfileState.nextSwitchRequestId()
        val late = RuntimeProfileState.tryMarkLoaded(req, 50L, "late")
        assertFalse(late)
        assertFalse(
            SwitchStartResolver.shouldPersistSelectedAfterLoad(
                50L,
                markLoadedSucceeded = late,
                requestIdStillCurrent = false,
            ),
        )
        assertEquals(99L, RuntimeProfileState.loadedProfileId)
    }
}
