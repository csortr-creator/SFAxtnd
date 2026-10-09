package io.nekohasekai.sfa.compose.base

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.utils.ImportOperationOutcome
import io.nekohasekai.sfa.utils.ImportResultFormatter
import io.nekohasekai.sfa.utils.SubscriptionImportReport
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
                    ImportResultFormatter.format(outcome, report, hadPrevious)
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
}
