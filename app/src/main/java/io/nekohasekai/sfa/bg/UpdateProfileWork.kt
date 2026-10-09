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
import io.nekohasekai.sfa.utils.PartialUpdatePolicy
import io.nekohasekai.sfa.utils.PartialUpdateInput
import io.nekohasekai.sfa.utils.UpdateTrigger
import io.nekohasekai.sfa.utils.PolicyDecision
import io.nekohasekai.sfa.utils.ImportResultFormatter
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
                try {
                    val result = HTTPClient().use { it.getSubscription(profile.typed.remoteURL) }
                    val content = result.config
                    val file = File(profile.typed.path)
                    val report = result.report
                    val hadPrevious = ImportResultFormatter.configFileExists(profile.typed.path)
                    val decision =
                        PartialUpdatePolicy.decide(
                            PartialUpdateInput(
                                trigger = UpdateTrigger.AUTO_UPDATE,
                                received = report.received,
                                imported = report.imported,
                                rejected = report.issues.size,
                                reliability = PartialUpdatePolicy.reliabilityOf(report),
                                hadPreviousConfig = hadPrevious,
                            ),
                        )
                    if (decision != PolicyDecision.APPLY) {
                        // Product rejection — LKG untouched; worker may still return success.
                        Log.w(
                            TAG,
                            "event=profile_update_policy_rejected profileId=${profile.id} " +
                                "decision=$decision received=${report.received} " +
                                "imported=${report.imported} rejected=${report.issues.size}",
                        )
                        continue
                    }
                    val operationToken = ProfileConfigCommit.beginOperation(profile.id)
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
