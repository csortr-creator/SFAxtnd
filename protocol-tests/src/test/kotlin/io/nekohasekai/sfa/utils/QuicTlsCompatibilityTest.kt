package io.nekohasekai.sfa.utils

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuicTlsCompatibilityTest {
    private fun node(type: String, fp: String) = JSONObject("""{
        "type":"$type","server":"example.org","server_port":443,"password":"fixture",
        "tls":{"enabled":true,"server_name":"front.example.org","insecure":false,
            "certificate_sha256":["fixture-pin"],"utls":{"enabled":true,"fingerprint":"$fp"}}
    }""")

    @Test fun normalizationPreservesCertificateSecurityAndSni() {
        val n = node("hysteria2", "chrome")
        val tls = n.getJSONObject("tls")
        val expected = JSONObject(tls.toString()).apply { remove("utls") }
        QuicTlsCompatibility.normalize(n)
        assertTrue(expected.similar(tls))
        assertFalse(n.getBoolean("disable_chrome_parrot"))
        assertFalse(tls.getBoolean("insecure"))
    }

    @Test fun tcpRealityUtlsIsUntouched() {
        val n = node("vless", "firefox")
        val copy = JSONObject(n.toString())
        QuicTlsCompatibility.normalize(n)
        assertTrue(copy.similar(n))
    }

    @Test(expected = IllegalArgumentException::class)
    fun contradictoryChromeParrotSettingIsRejected() {
        QuicTlsCompatibility.normalize(node("hysteria2", "chrome").put("disable_chrome_parrot", true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tuicCannotUseTcpUtls() {
        QuicTlsCompatibility.normalize(node("tuic", "chrome"))
    }

    @Test fun fullJsonAndArrayImportsNormalizeChromeWithoutDroppingNode() {
        val source = node("hysteria2", "chrome").put("tag", "fixture")
        for (raw in listOf("{\"outbounds\":[$source]}", "[$source]")) {
            val result = SubscriptionContentParser().parse(raw)
            assertEquals(1, result.report.imported)
            val outbounds = JSONObject(result.config).getJSONArray("outbounds")
            val hy = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }.single { it.optString("type") == "hysteria2" }
            assertFalse(hy.getJSONObject("tls").has("utls"))
            assertFalse(hy.getBoolean("disable_chrome_parrot"))
        }
    }
    @Test fun unsupportedFingerprintHasTypedIssueAndKeepsValidNodes() {
        val result = SubscriptionContentParser().parse(
            "hy2://fixture@example.org:443?fp=chrome#good\n" +
                "hy2://fixture@example.org:443?fp=firefox#unsupported",
        )
        assertEquals(2, result.report.received)
        assertEquals(1, result.report.imported)
        assertEquals(ImportIssueCode.UNSUPPORTED_FEATURE, result.report.issues.single().code)
    }

    @Test fun nativeUtlsFetchFailureIsIdentifiedWithoutLeakingSubscriptionUrl() {
        val secret = "https://private.example/sub?token=private-secret"
        val error = Exception("Get \"$secret\": unsupported usage for uTLS")
        assertEquals("NET_UTLS_UNSUPPORTED", NetworkErrorPresentation.logCode(error))
        assertFalse(SubscriptionFetchRetry.isRetriable(error))
        assertFalse(ImportResultFormatter.fetchFailureReason(error).contains(secret))
        assertFalse(ImportResultFormatter.fetchFailureReason(error).contains("private-secret"))
    }

}
