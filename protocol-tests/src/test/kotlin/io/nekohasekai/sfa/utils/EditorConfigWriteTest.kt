package io.nekohasekai.sfa.utils

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditorConfigWriteTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var previousProbe: (Long) -> Boolean

    @Before
    fun setUp() {
        previousProbe = ProfileConfigCommit.profileStillExists
        ProfileConfigCommit.profileStillExists = { true }
    }

    @After
    fun restoreProbe() {
        ProfileConfigCommit.profileStillExists = previousProbe
    }

    @Test
    fun deletedProfileFailsWithoutRecreatingFile() = runBlocking {
        val profileId = 63001L
        val target = File(tmp.root, "deleted.json")
        ProfileConfigCommit.markDeleted(profileId)
        try {
            val failure = runCatching {
                ProfileConfigCommit.applyEditorWrite(profileId, target, "DRAFT")
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertFalse(target.exists())
        } finally {
            ProfileConfigCommit.clearDeleted(profileId)
        }
    }

    @Test
    fun missingRoomProfileFailsWithoutReplacingExistingFile() = runBlocking {
        val target = tmp.newFile("missing.json").apply { writeText("LKG") }
        ProfileConfigCommit.profileStillExists = { false }
        val failure = runCatching {
            ProfileConfigCommit.applyEditorWrite(63002L, target, "DRAFT")
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("LKG", target.readText())
    }

    @Test
    fun identicalContentIsSuccessfulNoChangeRatherThanRejection() = runBlocking {
        val profileId = 63003L
        val target = tmp.newFile("same.json").apply { writeText("SAVED") }
        val generation = ProfileConfigCommit.currentWriteGeneration(profileId)
        assertFalse(ProfileConfigCommit.applyEditorWrite(profileId, target, "SAVED"))
        assertEquals("SAVED", target.readText())
        assertEquals(generation, ProfileConfigCommit.currentWriteGeneration(profileId))
    }
}
