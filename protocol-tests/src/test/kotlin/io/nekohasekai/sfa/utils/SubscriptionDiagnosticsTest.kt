package io.nekohasekai.sfa.utils

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SubscriptionDiagnosticsTest {
    private fun failure(stage: SubscriptionStage, block: () -> Any): Exception =
        try { SubscriptionDiagnostics.at(stage, block); error("expected failure") }
        catch (e: SubscriptionStageFailure) { e }

    @Test fun identicalErrorsAreDistinguishedByProductionStage() {
        val fetch = failure(SubscriptionStage.FETCH) { throw IllegalArgumentException("https://private.example/?token=secret") }
        val parse = failure(SubscriptionStage.PARSE) { throw IllegalArgumentException("https://private.example/?token=secret") }
        assertTrue(ImportResultFormatter.fetchFailureReason(fetch).startsWith("FETCH_UNKNOWN:"))
        assertTrue(ImportResultFormatter.fetchFailureReason(parse).startsWith("IMPORT_PARSE_UNKNOWN:"))
        assertFalse(ImportResultFormatter.fetchFailureReason(parse).contains("secret"))
    }

    @Test fun htmlAndMalformedJsonHaveSpecificSafeReasons() {
        val html = failure(SubscriptionStage.PARSE) { SubscriptionContentParser().parse("<html>private token</html>") }
        val json = failure(SubscriptionStage.PARSE) { SubscriptionContentParser().parse("{\"outbounds\": [") }
        assertTrue(SubscriptionDiagnostics.reason(html).startsWith("IMPORT_HTML:"))
        assertTrue(SubscriptionDiagnostics.reason(json).startsWith("IMPORT_JSON_INVALID:"))
        assertFalse(SubscriptionDiagnostics.reason(html).contains("private"))
    }

    @Test fun noUsableNodesCarriesCurrentStatisticsWithoutExposingRawInput() {
        val error = failure(SubscriptionStage.PARSE) {
            SubscriptionContentParser().parse("hy2://fixture@example.org:443?obfs=unsupported-mask#private-name")
        }
        val report = requireNotNull(SubscriptionDiagnostics.reportOf(error))
        assertEquals(1, report.received)
        assertEquals(0, report.imported)
        val text = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, report, reason = SubscriptionDiagnostics.reason(error))
        assertTrue(text.contains("IMPORT_NO_USABLE_NODES"))
        assertTrue(text.contains("UNSUPPORTED_FEATURE"))
        assertTrue(text.contains("private-name"))
        assertFalse(text.contains("example.org"))
    }

    @Test fun arbitraryPersistedReasonCannotBecomeDetailedUiText() {
        val report = SubscriptionImportReport(1, 0, listOf(SubscriptionImportIssue(
            1, "private-name", "Unsupported QUIC uTLS fingerprint https://private.example/key", ImportIssueCode.UNSUPPORTED_FEATURE,
        )))
        val text = ImportResultFormatter.format(ImportOperationOutcome.KEPT_LKG, report)
        assertTrue(text.contains("private-name"))
        assertFalse(text.contains("private.example"))
        assertFalse(text.contains("uTLS fingerprint"))
    }

    @Test fun metadataFailureIsDistinctAndDoesNotLeakHeaderText() {
        val error = failure(SubscriptionStage.METADATA) { throw IllegalStateException("private-hwid") }
        assertTrue(SubscriptionDiagnostics.reason(error).startsWith("IMPORT_METADATA:"))
        assertFalse(SubscriptionDiagnostics.reason(error).contains("private-hwid"))
    }

    @Test fun cancellationPropagatesWithoutStageWrapping() {
        val original = CancellationException("cancel")
        try { SubscriptionDiagnostics.at(SubscriptionStage.FETCH) { throw original }; fail() }
        catch (e: CancellationException) { assertSame(original, e) }
    }
    @Test fun realityFailureDuringFetchIsDistinctWithoutRawMessage() {
        val error = failure(SubscriptionStage.FETCH) {
            throw Exception("Get https://private.example/?token=secret: reality verification failed")
        }
        val text = SubscriptionDiagnostics.reason(error)
        assertTrue(text.startsWith("FETCH_REALITY_VERIFICATION:"))
        assertFalse(text.contains("private"))
        assertFalse(SubscriptionFetchRetry.isRetriable(error))
    }

}
