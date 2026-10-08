package io.nekohasekai.sfa.utils

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: exact mapping paths for A+ Xray compatibility (docs/xray-capability-matrix.md).
 * Does not claim RUNTIME_VERIFIED — only PARSED/MAPPED behaviour of the importer.
 */
class XrayCapabilityMappingTest {

    /** 32-byte URL-safe Base64 (no padding) — valid Reality public key shape. */
    private val realityPbk = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    private fun nodes(result: SubscriptionImportResult): List<JSONObject> {
        val array = JSONObject(result.config).getJSONArray("outbounds")
        return (0 until array.length()).map { array.getJSONObject(it) }.filter { it.has("server") }
    }

    @Test
    fun realityShareLinkMapsPbkSidFpSpiderXExactly() {
        val link =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443" +
                "?security=reality&pbk=$realityPbk&sid=abcd&fp=firefox&spx=%2Fclient-a" +
                "&sni=www.example.com&type=tcp#reality-map"
        val result = SubscriptionContentParser().parse(link)
        assertEquals(1, result.report.imported)
        assertTrue(result.report.issues.isEmpty())

        val node = nodes(result).single()
        assertEquals("vless", node.getString("type"))
        assertEquals("example.org", node.getString("server"))
        assertEquals(443, node.getInt("server_port"))

        val tls = node.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertEquals("www.example.com", tls.getString("server_name"))
        assertEquals("firefox", tls.getJSONObject("utls").getString("fingerprint"))
        assertTrue(tls.getJSONObject("utls").getBoolean("enabled"))

        val reality = tls.getJSONObject("reality")
        assertTrue(reality.getBoolean("enabled"))
        assertEquals(realityPbk, reality.getString("public_key"))
        assertEquals("abcd", reality.getString("short_id"))
        assertEquals("/client-a", reality.getString("spider_x"))
        assertFalse(reality.has("spider_y"))
    }

    @Test
    fun realityOmitsSpiderQueryDefaultsSpiderXToRootPath() {
        val link =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443" +
                "?security=reality&pbk=$realityPbk&sid=ab&type=tcp#spx-default"
        val result = SubscriptionContentParser().parse(link)
        val reality = nodes(result).single().getJSONObject("tls").getJSONObject("reality")
        assertEquals("/", reality.getString("spider_x"))
    }

    @Test
    fun unknownOptionalQueryIsWarnedAndAbsentFromOutbound() {
        val link =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443" +
                "?security=tls&sni=example.org&futureExperimentalFlag=secretValue#opt"
        val result = SubscriptionContentParser().parse(link)
        assertEquals(1, result.report.imported)
        assertTrue(result.report.issues.isEmpty())
        assertTrue(
            result.report.warnings.any {
                it.contains("futureExperimentalFlag") && it.contains("Частичная")
            },
        )
        assertFalse(result.report.displayText().contains("secretValue"))

        val node = nodes(result).single()
        assertFalse(node.toString().contains("futureExperimentalFlag"))
        assertFalse(node.toString().contains("secretValue"))
        assertEquals("example.org", node.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun xhttpShareLinkMapsPathAndModeExactly() {
        val link =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443" +
                "?encryption=none&type=xhttp&mode=stream-up&path=%2Fxhttp-path&host=cdn.example.org#xhttp-map"
        val result = SubscriptionContentParser().parse(link)
        assertEquals(1, result.report.imported)
        val transport = nodes(result).single().getJSONObject("transport")
        assertEquals("xhttp", transport.getString("type"))
        assertEquals("/xhttp-path", transport.getString("path"))
        assertEquals("stream-up", transport.getString("mode"))
        assertFalse(transport.optString("type") == "tcp")
    }

    @Test
    fun invalidRealityShortIdIsSkippedAndDoesNotLeakIntoOutbounds() {
        // Same contract as SubscriptionContentParserTest: a valid neighbour keeps the
        // import successful while the bad line is recorded in issues (not mapped).
        val valid =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443?security=tls&sni=example.org#ok"
        val bad =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443" +
                "?security=reality&pbk=$realityPbk&sid=xyz#bad-sid"
        val result = SubscriptionContentParser().parse(valid + "\n" + bad)
        assertEquals(1, result.report.imported)
        assertEquals(1, result.report.issues.size)
        assertTrue(result.report.issues.single().reason.contains("short ID", ignoreCase = true))
        val only = nodes(result).single()
        assertEquals("ok", only.getString("tag"))
        assertFalse(only.optJSONObject("tls")?.has("reality") == true)
    }
}
