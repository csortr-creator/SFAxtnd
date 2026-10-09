package io.nekohasekai.sfa.utils

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileConfigCommitTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun successfulReplaceLeavesNoTempFiles() {
        val target = tmp.newFile("profile.json")
        target.writeText("{\"old\":true}")
        val ok = ProfileConfigCommit.replaceAtomically(target, "{\"new\":true}")
        assertTrue(ok)
        assertEquals("{\"new\":true}", target.readText())
        val leftovers =
            tmp.root.listFiles()?.filter {
                it.name.startsWith(".sfax-commit-") || it.extension == "tmp"
            }
        assertTrue(leftovers.isNullOrEmpty())
    }

    @Test
    fun identicalContentSkipsWrite() {
        val target = tmp.newFile("same.json")
        target.writeText("{\"x\":1}")
        assertFalse(ProfileConfigCommit.replaceAtomically(target, "{\"x\":1}"))
        assertEquals("{\"x\":1}", target.readText())
    }

    @Test
    fun failedValidateDoesNotTouchFile() = runBlocking {
        val target = tmp.newFile("keep.json")
        target.writeText("GOOD")
        val token = ProfileConfigCommit.beginOperation(42L)
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 42L,
                operationToken = token,
                target = target,
                content = "BAD",
                validate = { throw IllegalStateException("checkConfig failed") },
                afterFileCommit = { },
            )
        assertTrue(outcome is CommitOutcome.Failed)
        assertEquals("GOOD", target.readText())
    }

    @Test
    fun metadataFailureKeepsCommittedFile() = runBlocking {
        val target = tmp.newFile("meta.json")
        target.writeText("OLD")
        val token = ProfileConfigCommit.beginOperation(7L)
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 7L,
                operationToken = token,
                target = target,
                content = "NEW",
                validate = { },
                afterFileCommit = { error("report boom") },
            )
        assertTrue(outcome is CommitOutcome.FileCommittedMetadataFailed)
        assertEquals("NEW", target.readText())
    }

    @Test
    fun staleOperationSkipsCommit() = runBlocking {
        val target = tmp.newFile("stale.json")
        target.writeText("ORIGINAL")
        val oldToken = ProfileConfigCommit.beginOperation(99L)
        ProfileConfigCommit.beginOperation(99L) // newer
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 99L,
                operationToken = oldToken,
                target = target,
                content = "STALE_WRITE",
                validate = { },
                afterFileCommit = { },
            )
        assertTrue(outcome is CommitOutcome.Stale)
        assertEquals("ORIGINAL", target.readText())
    }

    @Test
    fun concurrentCommitsSerialize() {
        val target = tmp.newFile("race.json")
        target.writeText("start")
        val profileId = 2000L
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val done = CountDownLatch(12)
        val failures = AtomicInteger(0)
        repeat(12) { i ->
            pool.execute {
                try {
                    start.await(5, TimeUnit.SECONDS)
                    val token = ProfileConfigCommit.beginOperation(profileId)
                    val body = "{\"n\":$i}"
                    runBlocking {
                        ProfileConfigCommit.commit(
                            profileId = profileId,
                            operationToken = token,
                            target = target,
                            content = body,
                            validate = { },
                            afterFileCommit = { },
                        )
                    }
                } catch (_: Exception) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(20, TimeUnit.SECONDS))
        pool.shutdownNow()
        val text = target.readText()
        assertTrue(text.startsWith("{") && text.contains("\"n\":"))
        assertTrue(
            tmp.root.listFiles()?.none { it.name.startsWith(".sfax-commit-") } != false,
        )
    }

    @Test
    fun lastUpdatedSemantics_onlyAfterSuccessfulCommitPath() = runBlocking {
        // Documented by Failed outcome: caller must not bump lastUpdated on Failed/Stale.
        val target = tmp.newFile("ts.json")
        target.writeText("OLD")
        var lastUpdated = false
        val token = ProfileConfigCommit.beginOperation(55L)
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 55L,
                operationToken = token,
                target = target,
                content = "NEW",
                validate = { error("nope") },
                afterFileCommit = { lastUpdated = true },
            )
        assertTrue(outcome is CommitOutcome.Failed)
        assertFalse(lastUpdated)
        assertEquals("OLD", target.readText())
    }


    @Test
    fun invalidateBlocksStaleCommitAfterDelete() = runBlocking {
        val target = tmp.newFile("del-race.json")
        target.writeText("LIVE")
        val fetchToken = ProfileConfigCommit.beginOperation(55L)
        ProfileConfigCommit.invalidate(55L)
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 55L,
                operationToken = fetchToken,
                target = target,
                content = "RESURRECT",
                validate = {},
                afterFileCommit = {},
            )
        assertTrue(outcome is CommitOutcome.Stale)
        assertEquals("LIVE", target.readText())
    }

    @Test
    fun withProfileLockAllowsExclusiveSection() = runBlocking {
        val target = tmp.newFile("lock.json")
        target.writeText("A")
        ProfileConfigCommit.withProfileLock(88L) {
            target.writeText("LOCKED")
        }
        assertEquals("LOCKED", target.readText())
    }


    @Test
    fun markDeletedBlocksNewCommitToken() = runBlocking {
        val target = tmp.newFile("tomb.json")
        target.writeText("GONE")
        ProfileConfigCommit.markDeleted(77L)
        // Fresh token after delete — still must not write (tombstone, not only generation).
        val token = ProfileConfigCommit.beginOperation(77L)
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 77L,
                operationToken = token,
                target = target,
                content = "RESURRECT",
                validate = {},
                afterFileCommit = {},
            )
        assertTrue(outcome is CommitOutcome.Stale)
        assertEquals("GONE", target.readText())
        assertTrue(ProfileConfigCommit.isMarkedDeleted(77L))
        ProfileConfigCommit.clearDeleted(77L)
        assertFalse(ProfileConfigCommit.isMarkedDeleted(77L))
    }


    @Test
    fun clearDeletedAllowsFreshCommitAfterFailedDeleteSimulation() = runBlocking {
        val target = tmp.newFile("rollback.json")
        target.writeText("ALIVE")
        ProfileConfigCommit.markDeleted(101L)
        // Simulated Room failure path: clear tombstone so updates work again.
        ProfileConfigCommit.clearDeleted(101L)
        val token = ProfileConfigCommit.beginOperation(101L)
        val outcome =
            ProfileConfigCommit.commit(
                profileId = 101L,
                operationToken = token,
                target = target,
                content = "UPDATED",
                validate = {},
                afterFileCommit = {},
            )
        assertTrue(outcome is CommitOutcome.Success)
        assertEquals("UPDATED", target.readText())
    }

    @Test
    fun missingProfileRejectsCommitEvenWithFreshToken() = runBlocking {
        val target = tmp.newFile("missing.json")
        target.writeText("OLD")
        val prev = ProfileConfigCommit.profileStillExists
        try {
            ProfileConfigCommit.profileStillExists = { false }
            val token = ProfileConfigCommit.beginOperation(202L)
            val outcome =
                ProfileConfigCommit.commit(
                    profileId = 202L,
                    operationToken = token,
                    target = target,
                    content = "RESURRECT",
                    validate = {},
                    afterFileCommit = {},
                )
            assertTrue(outcome is CommitOutcome.Stale)
            assertEquals("OLD", target.readText())
        } finally {
            ProfileConfigCommit.profileStillExists = prev
        }
    }
}
