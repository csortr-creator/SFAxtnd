package io.nekohasekai.sfa.bg

import io.nekohasekai.sfa.utils.NetworkErrorPresentation

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import kotlinx.coroutines.CancellationException
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.CommitOutcome
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.ProfileConfigCommit
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class UpdateProfileWork {
    companion object {
        private const val WORK_NAME = "UpdateProfile"
        private const val TAG = "UpdateProfileWork"

        suspend fun reconfigureUpdater() {
            runCatching {
                reconfigureUpdater0()
            }.onFailure {
                Log.e(TAG, "reconfigureUpdater", it)
            }
        }

        private suspend fun reconfigureUpdater0() {
            val remoteProfiles =
                ProfileManager.list()
                    .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) {
                WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)
                return
            }

            var minDelay =
                remoteProfiles.minByOrNull { it.typed.autoUpdateInterval }!!.typed.autoUpdateInterval.toLong()
            val nowSeconds = System.currentTimeMillis() / 1000L
            val minInitDelay =
                remoteProfiles.minOf { (it.typed.autoUpdateInterval * 60) - (nowSeconds - (it.typed.lastUpdated.time / 1000L)) }
            if (minDelay < 15) minDelay = 15
            WorkManager.getInstance(Application.application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(UpdateTask::class.java, minDelay, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .apply {
                        if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                        setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    }
                    .build(),
            )
        }
    }

    class UpdateTask(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            var selectedProfileUpdated = false
            val remoteProfiles =
                ProfileManager.list()
                    .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) return Result.success()
            var success = true
            val selectedProfile = Settings.selectedProfile
            for (profile in remoteProfiles) {
                val lastSeconds =
                    (System.currentTimeMillis() - profile.typed.lastUpdated.time) / 1000L
                if (lastSeconds < profile.typed.autoUpdateInterval * 60) {
                    continue
                }
                val operationToken = ProfileConfigCommit.beginOperation(profile.id)
                try {
                    val result = HTTPClient().use { it.getSubscription(profile.typed.remoteURL) }
                    val content = result.config
                    val file = File(profile.typed.path)
                    when (
                        val outcome =
                            ProfileConfigCommit.commit(
                                profileId = profile.id,
                                operationToken = operationToken,
                                target = file,
                                content = content,
                                validate = { Libbox.checkConfig(it) },
                                afterFileCommit = {
                                    result.report.save(profile.typed.path)
                                    profile.typed.lastUpdated = Date()
                                    ProfileManager.update(profile)
                                },
                            )
                    ) {
                        is CommitOutcome.Success -> {
                            if (outcome.replaced && profile.id == selectedProfile) {
                                selectedProfileUpdated = true
                            }
                        }
                        is CommitOutcome.FileCommittedMetadataFailed -> {
                            // File is the new LKG; metadata/report incomplete — do not hide.
                            Log.e(
                                TAG,
                                "event=profile_update_metadata_failed profileId=${profile.id} code=META",
                            )
                            if (outcome.replaced && profile.id == selectedProfile) {
                                selectedProfileUpdated = true
                            }
                            success = false
                        }
                        is CommitOutcome.Stale -> {
                            Log.w(TAG, "event=profile_update_stale profileId=${profile.id}")
                        }
                        is CommitOutcome.Failed -> {
                            Log.e(
                                TAG,
                                "event=profile_update_commit_failed profileId=${profile.id} code=COMMIT",
                            )
                            success = false
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "event=profile_update_fetch_failed profileId=${profile.id} code=${NetworkErrorPresentation.logCode(e)}",
                    )
                    success = false
                }
            }
            if (selectedProfileUpdated) {
                runCatching {
                    Libbox.newStandaloneCommandClient().serviceReload()
                }
            }
            return if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}
