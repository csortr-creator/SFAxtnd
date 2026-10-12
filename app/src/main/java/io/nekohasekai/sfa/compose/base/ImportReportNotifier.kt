package io.nekohasekai.sfa.compose.base

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.utils.ImportOperationOutcome
import io.nekohasekai.sfa.utils.ImportResultFormatter
import io.nekohasekai.sfa.utils.SubscriptionImportReport
import io.nekohasekai.sfa.utils.ManualPrepareResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ImportReportNotifier {
    suspend fun text(
        profile: Profile,
        outcome: ImportOperationOutcome? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val report = SubscriptionImportReport.read(profile.typed.path)
            val hadPrevious = ImportResultFormatter.configFileExists(profile.typed.path)
            when {
                outcome != null && report != null ->
                    ImportResultFormatter.format(
                        outcome, report, hadPrevious,
                        reportIsCurrent = outcome == ImportOperationOutcome.APPLIED ||
                            outcome == ImportOperationOutcome.APPLIED_UNCHANGED ||
                            outcome == ImportOperationOutcome.PARTIAL_APPLIED,
                    )
                outcome != null ->
                    ImportResultFormatter.format(outcome, null, hadPrevious)
                report != null ->
                    report.displayText()
                else ->
                    "Отчёт об импорте недоступен"
            }
        }

    suspend fun show(
        profile: Profile,
        outcome: ImportOperationOutcome? = null,
    ) {
        GlobalEventBus.emit(UiEvent.ImportReport(profile.name, text(profile, outcome)))
    }

    suspend fun text(profile: Profile, result: ManualPrepareResult): String =
        withContext(Dispatchers.IO) {
            val hadPrevious = ImportResultFormatter.configFileExists(profile.typed.path)
            when (result) {
                is ManualPrepareResult.Applied -> ImportResultFormatter.format(result.outcome, result.report, hadPrevious)
                is ManualPrepareResult.Rejected -> ImportResultFormatter.format(
                    result.outcome, result.report, hadPrevious, reason = result.safeMessage,
                )
                is ManualPrepareResult.Failed -> ImportResultFormatter.format(
                    ImportOperationOutcome.KEPT_LKG, result.report, hadPreviousConfig = hadPrevious, reason = result.safeMessage,
                )
                is ManualPrepareResult.AwaitConfirmation -> "Ожидается подтверждение частичного обновления"
            }
        }

    suspend fun show(profile: Profile, result: ManualPrepareResult) {
        GlobalEventBus.emit(UiEvent.ImportReport(profile.name, text(profile, result)))
    }
}
