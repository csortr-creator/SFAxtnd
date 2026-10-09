package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

enum class PendingPhase {
    PENDING,
    APPLYING,
    CONSUMED,
    STALE,
    CANCELLED,
}

class PendingImportHolder(
    val profileId: Long,
    val profileName: String,
    val configPath: String,
    val content: String,
    val report: SubscriptionImportReport,
    val expected: ConfigExpectedState,
    val phase: AtomicReference<PendingPhase> = AtomicReference(PendingPhase.PENDING),
) {
    val received: Int get() = report.received
    val imported: Int get() = report.imported
    val rejected: Int get() = report.issues.size
}

sealed class ManualPrepareResult {
    data class Applied(val outcome: ImportOperationOutcome, val replaced: Boolean) : ManualPrepareResult()
    data class AwaitConfirmation(val pending: PendingImportHolder) : ManualPrepareResult()
    data class Rejected(val outcome: ImportOperationOutcome) : ManualPrepareResult()
    data class Failed(val safeMessage: String) : ManualPrepareResult()
}

object ManualSubscriptionUpdate {

    suspend fun prepare(profile: Profile): ManualPrepareResult {
        if (profile.typed.type != TypedProfile.Type.Remote) {
            return ManualPrepareResult.Failed("Профиль не является удалённой подпиской")
        }
        if (ProfileConfigCommit.isMarkedDeleted(profile.id) || ProfileManager.get(profile.id) == null) {
            return ManualPrepareResult.Rejected(ImportOperationOutcome.KEPT_LKG)
        }
        val hadPrevious = ImportResultFormatter.configFileExists(profile.typed.path)
        return try {
            val result = HTTPClient().use { it.getSubscription(profile.typed.remoteURL) }
            val report = result.report
            val decision =
                PartialUpdatePolicy.decide(
                    PartialUpdateInput(
                        trigger = UpdateTrigger.MANUAL_UPDATE,
                        received = report.received,
                        imported = report.imported,
                        rejected = report.issues.size,
                        reliability = PartialUpdatePolicy.reliabilityOf(report),
                        hadPreviousConfig = hadPrevious,
                    ),
                )
            when (decision) {
                PolicyDecision.APPLY -> applyNow(profile, result)
                PolicyDecision.AWAIT_CONFIRMATION -> {
                    val expected =
                        ProfileConfigCommit.snapshotExpectedState(profile.id, profile.typed.path)
                    ManualPrepareResult.AwaitConfirmation(
                        PendingImportHolder(
                            profileId = profile.id,
                            profileName = profile.name,
                            configPath = profile.typed.path,
                            content = result.config,
                            report = report,
                            expected = expected,
                        ),
                    )
                }
                PolicyDecision.REJECT_KEEP_LKG,
                PolicyDecision.REJECT_FIRST,
                -> ManualPrepareResult.Rejected(ImportOperationOutcome.KEPT_LKG)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            ManualPrepareResult.Failed("Не удалось обновить: ошибка загрузки или проверки конфигурации")
        }
    }

    private suspend fun applyNow(
        profile: Profile,
        result: SubscriptionImportResult,
    ): ManualPrepareResult {
        val operationToken = ProfileConfigCommit.beginOperation(profile.id)
        return when (
            val outcome =
                ProfileConfigCommit.commit(
                    profileId = profile.id,
                    operationToken = operationToken,
                    target = File(profile.typed.path),
                    content = result.config,
                    validate = { Libbox.checkConfig(it) },
                    afterFileCommit = {
                        result.report.save(profile.typed.path)
                        profile.typed.lastUpdated = Date()
                        ProfileManager.update(profile)
                    },
                )
        ) {
            is CommitOutcome.Success ->
                ManualPrepareResult.Applied(
                    ImportResultFormatter.fromCommit(outcome.replaced, result.report),
                    outcome.replaced,
                )
            is CommitOutcome.FileCommittedMetadataFailed ->
                ManualPrepareResult.Applied(
                    ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED,
                    outcome.replaced,
                )
            is CommitOutcome.Stale,
            is CommitOutcome.Failed,
            -> ManualPrepareResult.Rejected(ImportOperationOutcome.KEPT_LKG)
        }
    }

    suspend fun confirm(pending: PendingImportHolder): ManualPrepareResult {
        if (!pending.phase.compareAndSet(PendingPhase.PENDING, PendingPhase.APPLYING)) {
            return ManualPrepareResult.Rejected(ImportOperationOutcome.KEPT_LKG)
        }
        val profile = ProfileManager.get(pending.profileId)
        return when (
            val outcome =
                ProfileConfigCommit.commitIfUnchanged(
                    expected = pending.expected,
                    content = pending.content,
                    validate = { Libbox.checkConfig(it) },
                    afterFileCommit = {
                        pending.report.save(pending.configPath)
                        if (profile != null) {
                            profile.typed.lastUpdated = Date()
                            ProfileManager.update(profile)
                        }
                    },
                )
        ) {
            is CommitOutcome.Success -> {
                pending.phase.set(PendingPhase.CONSUMED)
                ManualPrepareResult.Applied(
                    ImportResultFormatter.fromCommit(outcome.replaced, pending.report),
                    outcome.replaced,
                )
            }
            is CommitOutcome.FileCommittedMetadataFailed -> {
                pending.phase.set(PendingPhase.CONSUMED)
                ManualPrepareResult.Applied(
                    ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED,
                    outcome.replaced,
                )
            }
            is CommitOutcome.Stale -> {
                pending.phase.set(PendingPhase.STALE)
                ManualPrepareResult.Rejected(ImportOperationOutcome.KEPT_LKG)
            }
            is CommitOutcome.Failed -> {
                pending.phase.set(PendingPhase.STALE)
                ManualPrepareResult.Rejected(ImportOperationOutcome.KEPT_LKG)
            }
        }
    }

    fun cancel(pending: PendingImportHolder) {
        pending.phase.compareAndSet(PendingPhase.PENDING, PendingPhase.CANCELLED)
    }
}
