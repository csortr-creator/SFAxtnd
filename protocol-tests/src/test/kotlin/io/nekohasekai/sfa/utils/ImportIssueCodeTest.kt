package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportIssueCodeTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun fromWireUnknownFutureDoesNotCrash() {
        assertEquals(ImportIssueCode.UNKNOWN, ImportIssueCode.fromWire(null))
        assertEquals(ImportIssueCode.UNKNOWN, ImportIssueCode.fromWire(""))
        assertEquals(ImportIssueCode.UNKNOWN, ImportIssueCode.fromWire("FUTURE_CODE_XYZ"))
        assertEquals(ImportIssueCode.PARSE_ERROR, ImportIssueCode.fromWire("PARSE_ERROR"))
    }

    @Test
    fun legacyReportWithoutCodeReadsAsUnknown() {
        val path = File(tmp.root, "legacy.json").absolutePath
        File("$path.import.json")
            .writeText(
                """{"received":2,"imported":1,"format":"Список ссылок","warnings":[],"issues":[{"line":1,"name":"a","reason":"old"}]}"""
            )
        val report = SubscriptionImportReport.read(path)!!
        assertEquals(2, report.received)
        assertEquals(1, report.imported)
        assertEquals(ImportIssueCode.UNKNOWN, report.issues.single().code)
    }

    @Test
    fun saveIncludesCodeWithoutSecrets() {
        val path = File(tmp.root, "cfg.json").absolutePath
        File(path).writeText("{}")
        SubscriptionImportReport(
                received = 3,
                imported = 1,
                issues =
                    listOf(
                        SubscriptionImportIssue(
                            1,
                            "node",
                            "Неподдерживаемый параметр: foo",
                            ImportIssueCode.UNSUPPORTED_FEATURE,
                        ),
                    ),
            )
            .save(path)
        val raw = File("$path.import.json").readText()
        assertFalse(raw.contains("vless://"))
        assertFalse(raw.contains("://"))
        assertEquals(
            "UNSUPPORTED_FEATURE",
            JSONObject(raw).getJSONArray("issues").getJSONObject(0).getString("code"),
        )
        assertEquals(3, JSONObject(raw).getInt("received"))
        assertEquals(1, JSONObject(raw).getInt("imported"))
    }

    @Test
    fun wireMaskRejectionIsUnsupportedFeature() {
        // One valid + one rejected → report retained (0-imported alone throws).
        val result =
            SubscriptionContentParser()
                .parse(
                    """
                    hy2://fixture@example.org:443?sni=front.example.org#ok
                    hy2://fixture@example.org:443?fm=%7B%22udp%22%3A%5B%7B%22type%22%3A%22sudoku%22%7D%5D%7D#bad
                    """.trimIndent()
                )
        assertEquals(2, result.report.received)
        assertEquals(1, result.report.imported)
        assertTrue(result.report.issues.isNotEmpty())
        assertEquals(ImportIssueCode.UNSUPPORTED_FEATURE, result.report.issues.first().code)
        val blob = result.report.displayText() + result.report.issues.joinToString { it.reason }
        assertFalse(blob.contains("vless://"))
        assertFalse(blob.contains("://"))
    }

    @Test
    fun invalidUriIsParseOrUnknownWithoutUrlLeak() {
        val result =
            runCatching {
                    SubscriptionContentParser().parse("not-a-valid-subscription-line")
                }
                .exceptionOrNull()
        // 0 nodes throws — message must not embed raw secrets
        assertNotNull(result)
        assertFalse(result!!.message.orEmpty().contains("://"))
    }
}
