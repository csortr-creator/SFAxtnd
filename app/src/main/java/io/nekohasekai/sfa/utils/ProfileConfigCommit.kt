package io.nekohasekai.sfa.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Safe config-file commit for remote subscription updates.
 *
 * - Unique temp file in the same directory as the target (no shared ".tmp" name).
 * - Content is fully written and verified before replace.
 * - Replace uses [StandardCopyOption.ATOMIC_MOVE] only; if the FS cannot atomic-move,
 *   the active file is left untouched and an error is thrown (no silent non-atomic fallback).
 * - Per-profile [Mutex] serializes concurrent commits; [beginOperation]/[isCurrent] guards
 *   stale HTTP responses that finish after a newer update started.
 */
object ProfileConfigCommit {

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val generations = ConcurrentHashMap<Long, AtomicLong>()

    /** Issue a monotonic operation token for [profileId]. Call before fetch. */
    fun beginOperation(profileId: Long): Long {
        val gen = generations.getOrPut(profileId) { AtomicLong(0L) }
        return gen.incrementAndGet()
    }

    fun isCurrent(profileId: Long, operationToken: Long): Boolean {
        val gen = generations[profileId] ?: return false
        return gen.get() == operationToken
    }

    fun mutexFor(profileId: Long): Mutex = locks.getOrPut(profileId) { Mutex() }

    /**
     * Atomically replace [target] with [content], or leave [target] unchanged on any failure.
     *
     * @return true if the file bytes were replaced; false if existing content was already identical
     *         (no write performed).
     */
    fun replaceAtomically(target: File, content: String): Boolean {
        val parent =
            target.parentFile
                ?: throw IOException("Config path has no parent directory: ${target.path}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create config directory: ${parent.path}")
        }

        if (target.exists()) {
            val existing =
                try {
                    target.readText()
                } catch (_: Exception) {
                    null
                }
            if (existing == content) return false
        }

        val tmp =
            File(
                parent,
                ".sfax-commit-${target.name}-${UUID.randomUUID()}.tmp",
            )
        try {
            writeFully(tmp, content)
            val verified =
                try {
                    tmp.readText()
                } catch (e: Exception) {
                    throw IOException("Failed to verify temporary config: ${tmp.path}", e)
                }
            if (verified != content) {
                throw IOException("Temporary config content mismatch after write")
            }

            val src = tmp.toPath()
            val dst = target.toPath()
            try {
                Files.move(
                    src,
                    dst,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                throw IOException(
                    "Atomic move not supported on this filesystem; refusing unsafe config replace",
                    e,
                )
            }
            // move consumed tmp
            return true
        } catch (e: Exception) {
            // Ensure active file was not partially overwritten (we never write to target directly).
            throw e
        } finally {
            if (tmp.exists()) {
                runCatching { tmp.delete() }
            }
        }
    }

    /**
     * Commit under per-profile lock with stale-operation check.
     *
     * [validate] runs inside the lock (e.g. Libbox.checkConfig) before any write.
     * [afterFileCommit] runs only after a successful file replace or skip-identical
     * (report + metadata). Failures there are returned as [CommitOutcome.FileCommittedMetadataFailed]
     * without rolling back the already-committed file.
     */
    suspend fun <T> commit(
        profileId: Long,
        operationToken: Long,
        target: File,
        content: String,
        validate: (String) -> Unit,
        afterFileCommit: suspend () -> T,
    ): CommitOutcome<T> {
        mutexFor(profileId).withLock {
            if (!isCurrent(profileId, operationToken)) {
                return CommitOutcome.Stale
            }
            try {
                validate(content)
            } catch (e: Exception) {
                return CommitOutcome.Failed(e)
            }
            val replaced =
                try {
                    replaceAtomically(target, content)
                } catch (e: Exception) {
                    return CommitOutcome.Failed(e)
                }
            return try {
                val meta = afterFileCommit()
                CommitOutcome.Success(replaced = replaced, metadata = meta)
            } catch (e: Exception) {
                CommitOutcome.FileCommittedMetadataFailed(replaced = replaced, error = e)
            }
        }
    }

    private fun writeFully(file: File, content: String) {
        FileOutputStream(file).use { fos ->
            val bytes = content.toByteArray(Charsets.UTF_8)
            fos.write(bytes)
            fos.flush()
            try {
                fos.fd.sync()
            } catch (_: Exception) {
                // Best-effort fsync; still rely on ATOMIC_MOVE of a fully written temp.
            }
        }
    }
}

sealed class CommitOutcome<out T> {
    data class Success<T>(val replaced: Boolean, val metadata: T) : CommitOutcome<T>()

    /** File on disk is the new content; report/DB update failed. */
    data class FileCommittedMetadataFailed(val replaced: Boolean, val error: Throwable) :
        CommitOutcome<Nothing>()

    data class Failed(val error: Throwable) : CommitOutcome<Nothing>()

    data object Stale : CommitOutcome<Nothing>()
}
