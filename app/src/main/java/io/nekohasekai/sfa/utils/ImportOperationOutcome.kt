package io.nekohasekai.sfa.utils

import java.io.File

/**
 * B3-2 operation-level result of import / remote update.
 * Independent of per-node [ImportIssueCode] list.
 */
enum class ImportOperationOutcome {
    /** New config bytes written; metadata OK. */
    APPLIED,
    /** Commit succeeded but file bytes unchanged. */
    APPLIED_UNCHANGED,
    /** New config written; some nodes were rejected (imported < received). */
    PARTIAL_APPLIED,
    /** Previous config left in place (validate/commit/fetch failure on update). */
    KEPT_LKG,
    /** First import failed; no profile config applied. */
    REJECTED,
    /** Config file replaced; report/metadata after commit failed. */
    FILE_COMMITTED_METADATA_FAILED,
}

/**
 * Builds user-visible text from outcome + optional parse report.
 * Never includes URLs, UUIDs, keys, or raw exception messages.
 */
internal object ImportResultFormatter {
    fun format(
        outcome: ImportOperationOutcome,
        report: SubscriptionImportReport? = null,
        hadPreviousConfig: Boolean = true,
    ): String =
        buildString {
            append(outcomeLine(outcome, hadPreviousConfig))
            if (report != null) {
                append("\n")
                append(statsLine(report))
                if (report.issues.isNotEmpty()) {
                    append("\nОтклонено записей: ${report.issues.size}")
                }
                if (outcome == ImportOperationOutcome.PARTIAL_APPLIED &&
                    report.received > 0 &&
                    report.imported * 2 < report.received
                ) {
                    append("\nВнимание: импортировано меньше половины полученных серверов")
                }
            }
        }

    fun outcomeLine(outcome: ImportOperationOutcome, hadPreviousConfig: Boolean): String =
        when (outcome) {
            ImportOperationOutcome.APPLIED ->
                "Конфигурация обновлена"
            ImportOperationOutcome.APPLIED_UNCHANGED ->
                "Подписка без изменений"
            ImportOperationOutcome.PARTIAL_APPLIED ->
                "Конфигурация обновлена, часть серверов пропущена"
            ImportOperationOutcome.KEPT_LKG ->
                if (hadPreviousConfig) {
                    "Обновление не применено, прежняя конфигурация сохранена"
                } else {
                    "Обновление не применено"
                }
            ImportOperationOutcome.REJECTED ->
                "Импорт отклонён"
            ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED ->
                "Файл конфигурации обновлён, но метаданные не сохранены"
        }

    fun statsLine(report: SubscriptionImportReport): String {
        val skipped =
            if (report.issues.isNotEmpty()) {
                " · Отклонено: ${report.issues.size}"
            } else {
                ""
            }
        return "Получено: ${report.received} · Импортировано: ${report.imported}$skipped"
    }

    fun fromCommit(
        replaced: Boolean,
        report: SubscriptionImportReport?,
        metadataFailed: Boolean = false,
    ): ImportOperationOutcome {
        if (metadataFailed) return ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED
        if (!replaced) return ImportOperationOutcome.APPLIED_UNCHANGED
        val partial =
            report != null && report.received > 0 && report.imported < report.received
        return if (partial) ImportOperationOutcome.PARTIAL_APPLIED
        else ImportOperationOutcome.APPLIED
    }

    fun configFileExists(path: String): Boolean =
        path.isNotBlank() && File(path).exists()
}
