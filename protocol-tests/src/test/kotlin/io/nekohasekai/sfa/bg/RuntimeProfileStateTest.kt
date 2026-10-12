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

    @Test
    fun deletingStartingTargetRejectsItsLateCompletion() {
        val target = 62001L
        val request = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.beginStart(request, target))
        assertEquals(target, RuntimeProfileState.startingProfileId)
        try {
            assertTrue(RuntimeProfileState.blockProfileForDeletion(target))
            assertTrue(RuntimeProfileState.usesProfile(target))
            assertTrue(RuntimeProfileState.shouldStopForDeletion(target, 999L, true))
            assertFalse(RuntimeProfileState.isStartCurrent(request, target))
            assertFalse(RuntimeProfileState.tryMarkLoaded(request, target, "late"))
            assertEquals(-1L, RuntimeProfileState.loadedProfileId)
            assertFalse(RuntimeProfileState.beginStart(RuntimeProfileState.currentSwitchRequestId(), target))
            RuntimeProfileState.clear()
            assertFalse(RuntimeProfileState.usesProfile(target))
        } finally {
            RuntimeProfileState.unblockProfileAfterFailedDelete(target)
        }
    }

    @Test
    fun deletionFindsLoadedProfileWithoutUsingSelectedProfile() {
        val loaded = 62002L
        val request = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.tryMarkLoaded(request, loaded, "loaded-b"))
        try {
            assertTrue(RuntimeProfileState.blockProfileForDeletion(loaded))
            assertFalse(RuntimeProfileState.tryMarkLoaded(request, loaded, "late"))
        } finally {
            RuntimeProfileState.unblockProfileAfterFailedDelete(loaded)
        }
    }

    @Test
    fun deletingUnrelatedProfileDoesNotCancelStart() {
        val target = 62003L
        val unrelated = 62004L
        val request = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.beginStart(request, target))
        try {
            assertFalse(RuntimeProfileState.blockProfileForDeletion(unrelated))
            assertFalse(RuntimeProfileState.shouldStopForDeletion(unrelated, unrelated, true))
            assertEquals(request, RuntimeProfileState.currentSwitchRequestId())
            assertTrue(RuntimeProfileState.tryMarkLoaded(request, target, "current"))
        } finally {
            RuntimeProfileState.unblockProfileAfterFailedDelete(unrelated)
        }
    }

    @Test
    fun failedDeleteAllowsNewStartButNotCancelledCompletion() {
        val target = 62005L
        val old = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.beginStart(old, target))
        assertTrue(RuntimeProfileState.blockProfileForDeletion(target))
        RuntimeProfileState.unblockProfileAfterFailedDelete(target)
        assertFalse(RuntimeProfileState.tryMarkLoaded(old, target, "cancelled"))
        val fresh = RuntimeProfileState.nextSwitchRequestId()
        assertTrue(RuntimeProfileState.beginStart(fresh, target))
        assertTrue(RuntimeProfileState.tryMarkLoaded(fresh, target, "fresh"))
        assertEquals(-1L, RuntimeProfileState.startingProfileId)
    }

    @Test
    fun stoppedRuntimeDoesNotUnblockDeletedProfile() {
        val target = 62006L
        RuntimeProfileState.blockProfileForDeletion(target)
        RuntimeProfileState.clear()
        try {
            assertEquals(-1L, RuntimeProfileState.startingProfileId)
            assertFalse(RuntimeProfileState.beginStart(RuntimeProfileState.nextSwitchRequestId(), target))
        } finally {
            RuntimeProfileState.unblockProfileAfterFailedDelete(target)
        }
    }

    @Test
    fun selectedProfileIsFallbackOnlyWhenRuntimeOwnershipIsUnknown() {
        assertTrue(RuntimeProfileState.shouldStopForDeletion(62007L, 62007L, true))
        assertFalse(RuntimeProfileState.shouldStopForDeletion(62007L, 62008L, true))
        assertFalse(RuntimeProfileState.shouldStopForDeletion(62007L, 62007L, false))
        RuntimeProfileState.markLoaded(62008L, "actual")
        assertFalse(RuntimeProfileState.shouldStopForDeletion(62007L, 62007L, true))
        assertTrue(RuntimeProfileState.shouldStopForDeletion(62008L, 62007L, true))
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
