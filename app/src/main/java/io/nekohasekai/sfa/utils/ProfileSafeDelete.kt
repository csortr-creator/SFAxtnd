package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safe subscription/profile deletion (UI-1c).
 *
 * Per-profile [ProfileConfigCommit] locking prevents background updates from resurrecting
 * files after delete. Does not auto-switch VPN to another profile.
 */
object ProfileSafeDelete {

    sealed class Outcome {
        data object Success : Outcome()

        data class Failed(val code: String, val message: String) : Outcome()
    }

    fun interface Db {
        suspend fun delete(profile: Profile): Int
    }

    interface Selection {
        fun getSelected(): Long

        fun setSelected(id: Long)
    }

    val defaultDb = Db { ProfileManager.delete(it) }
    val defaultSelection =
        object : Selection {
            override fun getSelected(): Long = Settings.selectedProfile

            override fun setSelected(id: Long) {
                Settings.selectedProfile = id
            }
        }

    /**
     * @param stopVpnAndAwait called when VPN is Starting/Started and selection equals [profile].id.
     *   Must return true only when service is confirmed stopped.
     */
    suspend fun delete(
        profile: Profile,
        allProfiles: List<Profile>,
        filesDir: File,
        serviceStatus: Status,
        stopVpnAndAwait: suspend () -> Boolean,
        db: Db = defaultDb,
        selection: Selection = defaultSelection,
    ): Outcome =
        withContext(Dispatchers.IO) {
            val configsDir = ProfilePathGuard.managedConfigsDir(filesDir)
            val configFile = File(profile.typed.path)
            val reportFile = File("${profile.typed.path}.import.json")
            val others = allProfiles.associate { it.id to it.typed.path }

            if (profile.typed.path.isNotBlank()) {
                if (!ProfilePathGuard.isUnderManagedDir(configFile, configsDir)) {
                    return@withContext Outcome.Failed(
                        "PATH_UNMANAGED",
                        "Config path is outside managed profiles directory",
                    )
                }
                if (ProfilePathGuard.pathSharedWithOther(profile.typed.path, profile.id, others)) {
                    return@withContext Outcome.Failed(
                        "PATH_SHARED",
                        "Config path is shared with another profile",
                    )
                }
            }

            ProfileConfigCommit.invalidate(profile.id)

            ProfileConfigCommit.withProfileLock(profile.id) {
                // Tombstone under lock so a concurrent updater cannot beginOperation+commit
                // after we leave this section and recreate the config file.
                // Cleared on any failure path so a surviving profile can still update.
                ProfileConfigCommit.markDeleted(profile.id)

                fun rollbackTombstone() {
                    ProfileConfigCommit.clearDeleted(profile.id)
                }

                val selected = selection.getSelected()
                val vpnUsesThis =
                    serviceStatus == Status.Started || serviceStatus == Status.Starting
                if (vpnUsesThis && selected == profile.id) {
                    if (!stopVpnAndAwait()) {
                        rollbackTombstone()
                        return@withProfileLock Outcome.Failed(
                            "VPN_STOP_FAILED",
                            "Could not stop VPN before deleting the active profile",
                        )
                    }
                }

                var staged: File? = null
                if (profile.typed.path.isNotBlank() && configFile.exists()) {
                    staged =
                        File(
                            configFile.parentFile,
                            ".sfax-deleted-${profile.id}-${configFile.name}",
                        )
                    if (!configFile.renameTo(staged)) {
                        rollbackTombstone()
                        return@withProfileLock Outcome.Failed(
                            "FILE_STAGE_FAILED",
                            "Could not stage config file for deletion",
                        )
                    }
                }

                try {
                    db.delete(profile)
                } catch (e: Exception) {
                    staged?.let { s ->
                        if (s.exists() && !configFile.exists()) {
                            runCatching { s.renameTo(configFile) }
                        }
                    }
                    rollbackTombstone()
                    return@withProfileLock Outcome.Failed(
                        "DB_DELETE_FAILED",
                        e.message ?: "Room delete failed",
                    )
                }

                // Room succeeded — keep tombstone permanently (process lifetime).
                if (selection.getSelected() == profile.id) {
                    selection.setSelected(-1L)
                }

                staged?.let { runCatching { it.delete() } }
                if (reportFile.exists()) runCatching { reportFile.delete() }
                if (configFile.exists()) runCatching { configFile.delete() }

                Outcome.Success
            }
        }
}
