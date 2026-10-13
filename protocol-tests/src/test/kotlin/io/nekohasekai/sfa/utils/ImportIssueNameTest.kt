package io.nekohasekai.sfa.utils

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ImportIssueNameTest {
    @Test fun partialPreviewIdentifiesMissingNodesBeforeAnyApplyDecision() {
        val report = SubscriptionImportReport(8, 6, listOf(
            SubscriptionImportIssue(2, "Польша Hysteria2", "Unsupported QUIC uTLS fingerprint", ImportIssueCode.UNSUPPORTED_FEATURE),
            SubscriptionImportIssue(4, "Финляндия Hysteria2", "Unsupported QUIC uTLS fingerprint", ImportIssueCode.UNSUPPORTED_FEATURE),
        ))
        val text = ImportResultFormatter.partialPreview(report)
        assertTrue(text.contains("Получено: 8 · Импортировано: 6 · Отклонено: 2"))
        assertTrue(text.contains("Запись 2 · Польша Hysteria2"))
        assertTrue(text.contains("Запись 4 · Финляндия Hysteria2"))
        assertTrue(text.contains("Применение может уменьшить"))
        assertFalse(text.contains("Конфигурация обновлена"))
        assertFalse(text.contains("Обновление не применено"))
    }

    @Test fun pendingPreviewUsesTheSameNameAndReasonPrivacyRules() {
        val report = SubscriptionImportReport(2, 1, listOf(
            SubscriptionImportIssue(1, "https://private.example/?token=TOP_SECRET", "raw reason TOP_SECRET", ImportIssueCode.PARSE_ERROR),
        ))
        val text = ImportResultFormatter.partialPreview(report)
        assertTrue(text.contains("Название скрыто"))
        assertFalse(text.contains("TOP_SECRET"))
        assertFalse(text.contains("private.example"))
    }

    private fun rejected(content: String): SubscriptionImportReport = try {
        SubscriptionContentParser().parse(content)
        throw AssertionError("Fixture must fail import")
    } catch (e: SubscriptionResponseFailure) { requireNotNull(e.report) }

    @Test fun rejectedShareLinkShowsItsDisplayNameAndKeepsCredentialsHidden() {
        val report = rejected("hy2://TOP_SECRET_PASSWORD@private.example:443?fp=firefox#%F0%9F%87%B5%F0%9F%87%B1%20Poland%20Hysteria2")
        val text = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, report)
        assertTrue(text.contains("Запись 1 · 🇵🇱 Poland Hysteria2"))
        assertTrue(text.contains("UNSUPPORTED_FEATURE"))
        assertFalse(text.contains("TOP_SECRET_PASSWORD"))
        assertFalse(text.contains("private.example"))
        assertFalse(text.contains("://"))
    }

    @Test fun vmessNameIsAvailableBeforeInvalidPortIsParsed() {
        val source = JSONObject().put("ps", "Финляндия VMess").put("add", "private.example")
            .put("port", 0).put("id", "00000000-0000-0000-0000-000000000001")
        val content = "vmess://" + Base64.getEncoder().encodeToString(source.toString().toByteArray())
        val text = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, rejected(content))
        assertTrue(text.contains("Финляндия VMess"))
        assertTrue(text.contains("PARSE_ERROR"))
        assertFalse(text.contains("private.example"))
        assertFalse(text.contains("00000000-0000"))
    }

    @Test fun rejectedJsonNodeKeepsItsTag() {
        val source = JSONObject().put("type", "hysteria2").put("tag", "Польша QUIC")
            .put("tls", JSONObject().put("utls", JSONObject().put("enabled", true).put("fingerprint", "firefox")))
        val text = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, rejected(JSONArray().put(source).toString()))
        assertTrue(text.contains("Польша QUIC"))
        assertTrue(text.contains("UNSUPPORTED_FEATURE"))
    }

    @Test fun hostileOrLegacyConnectionTextCannotBeRenderedAsName() {
        for (name in listOf("https://private.example/?token=TOP_SECRET", "vless://TOP_SECRET@private.example",
            "https%253A%252F%252Fprivate.example%252Fsecret", "token=TOP_SECRET",
            "00000000-0000-0000-0000-000000000001")) {
            val issue = SubscriptionImportIssue(2, name, "raw reason TOP_SECRET", ImportIssueCode.PARSE_ERROR)
            val text = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, SubscriptionImportReport(1, 0, listOf(issue)))
            assertTrue(text.contains("Название скрыто"))
            assertFalse(text.contains("TOP_SECRET"))
            assertFalse(text.contains("private.example"))
            assertFalse(text.contains("00000000-0000"))
        }
    }

    @Test fun unnamedAndControlCharacterNamesHaveHonestBoundedFallbacks() {
        assertEquals("Germany 10%25", SubscriptionDiagnostics.issueName(SubscriptionImportIssue(1, "Germany 10%25", "")))
        assertEquals("Название не указано", SubscriptionDiagnostics.issueName(SubscriptionImportIssue(1, "", "")))
        val name = SubscriptionDiagnostics.issueName(SubscriptionImportIssue(1, "🇩🇪\nGermany\u202E" + "X".repeat(200), ""))
        assertTrue(name.startsWith("🇩🇪 Germany"))
        assertEquals(120, name.length)
        assertFalse(name.contains('\n'))
        assertFalse(name.contains('\u202E'))
    }
}
