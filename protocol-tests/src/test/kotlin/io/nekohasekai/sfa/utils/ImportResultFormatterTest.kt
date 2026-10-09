package io.nekohasekai.sfa.utils

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportResultFormatterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun report(received: Int, imported: Int, issues: Int = received - imported) =
        SubscriptionImportReport(
            received = received,
            imported = imported,
            issues =
                (1..issues).map {
                    SubscriptionImportIssue(it, "n$it", "x", ImportIssueCode.PARSE_ERROR)
                },
        )

    @Test
    fun appliedFull() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.APPLIED, report(100, 100, 0))
        assertTrue(t.contains("Конфигурация обновлена"))
        assertTrue(t.contains("Получено: 100"))
        assertTrue(t.contains("Импортировано: 100"))
        assertFalse(t.contains("://"))
    }

    @Test
    fun partial99() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.PARTIAL_APPLIED, report(100, 99))
        assertTrue(t.contains("часть серверов пропущена"))
        assertTrue(t.contains("Отклонено"))
        assertFalse(t.contains("меньше половины"))
    }

    @Test
    fun partial1of100Warns() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.PARTIAL_APPLIED, report(100, 1))
        assertTrue(t.contains("меньше половины"))
    }

    @Test
    fun rejected() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.REJECTED, null)
        assertTrue(t.contains("отклонён") || t.contains("отклонен"))
    }

    @Test
    fun keptLkgWithFile() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, null, true)
        assertTrue(t.contains("прежняя конфигурация сохранена"))
    }

    @Test
    fun keptLkgWithoutFile() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, null, false)
        assertFalse(t.contains("прежняя конфигурация сохранена"))
        assertTrue(t.contains("не применено"))
    }

    @Test
    fun unchanged() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.APPLIED_UNCHANGED, null)
        assertTrue(t.contains("без изменений"))
    }

    @Test
    fun metadataFailed() {
        val t = ImportResultFormatter.format(ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED, null)
        assertTrue(t.contains("метаданные"))
        assertFalse(t.contains("не применено"))
    }

    @Test
    fun fromCommitMapping() {
        assertEquals(ImportOperationOutcome.APPLIED, ImportResultFormatter.fromCommit(true, report(10, 10, 0)))
        assertEquals(ImportOperationOutcome.PARTIAL_APPLIED, ImportResultFormatter.fromCommit(true, report(10, 9)))
        assertEquals(ImportOperationOutcome.APPLIED_UNCHANGED, ImportResultFormatter.fromCommit(false, report(10, 10, 0)))
        assertEquals(
            ImportOperationOutcome.FILE_COMMITTED_METADATA_FAILED,
            ImportResultFormatter.fromCommit(true, report(10, 10, 0), true),
        )
    }

    @Test
    fun legacyReportStillReadable() {
        val path = File(tmp.root, "p.json").absolutePath
        File("$path.import.json").writeText(
            """{"received":5,"imported":4,"format":"Список ссылок","warnings":[],"issues":[{"line":1,"name":"a","reason":"x"}]}"""
        )
        val r = SubscriptionImportReport.read(path)!!
        assertEquals(5, r.received)
        assertEquals(ImportIssueCode.UNKNOWN, r.issues[0].code)
    }

    @Test
    fun noSecretsInFormat() {
        val t =
            ImportResultFormatter.format(
                ImportOperationOutcome.PARTIAL_APPLIED,
                SubscriptionImportReport(
                    2,
                    1,
                    listOf(
                        SubscriptionImportIssue(
                            1,
                            "n",
                            "Некорректные параметры или кодировка ссылки",
                            ImportIssueCode.UNKNOWN,
                        ),
                    ),
                ),
            )
        assertFalse(t.contains("vless://"))
        assertFalse(t.contains("uuid"))
        assertFalse(t.contains("http"))
    }
}
