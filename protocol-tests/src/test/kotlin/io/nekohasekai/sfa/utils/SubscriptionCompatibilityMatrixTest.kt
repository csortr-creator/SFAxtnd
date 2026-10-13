package io.nekohasekai.sfa.utils

import java.io.File
import java.net.URLEncoder
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The same connection must retain its semantics across panel output formats. */
class SubscriptionCompatibilityMatrixTest {
    private val uuid = "11111111-1111-4111-8111-111111111111"
    private val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 42 })
    private fun base64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
    private fun nodes(result: SubscriptionImportResult): List<JSONObject> =
        JSONObject(result.config).getJSONArray("outbounds").let { list ->
            (0 until list.length()).map { list.getJSONObject(it) }.filter { it.has("server") }
        }
    private fun fixture(name: String, result: SubscriptionImportResult) {
        File("build/native-configs").mkdirs()
        File("build/native-configs/compatibility-$name.json").writeText(result.config)
    }

    @Test fun encryptionVisionRealitySurvivesEverySupportedContainer() {
        for (fp in listOf("chrome", "firefox", "safari")) {
            for (mode in listOf("0rtt", "1rtt")) {
                val encryption = "mlkem768x25519plus.native.$mode.$key"
                val uri = "vless://$uuid@example.org:443?encryption=$encryption&flow=xtls-rprx-vision&security=reality&sni=front.example.org&fp=$fp&pbk=$key&sid=abcd#fixture"
                val xray = """{"outbounds":[{"tag":"fixture","protocol":"vless","settings":{"vnext":[{"address":"example.org","port":443,"users":[{"id":"$uuid","flow":"xtls-rprx-vision","encryption":"$encryption"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"front.example.org","fingerprint":"$fp","publicKey":"$key","shortId":"abcd"}}}]}"""
                val clash = """{"proxies":[{"name":"fixture","type":"vless","server":"example.org","port":443,"uuid":"$uuid","flow":"xtls-rprx-vision","encryption":"$encryption","tls":true,"servername":"front.example.org","client-fingerprint":"$fp","reality-opts":{"public-key":"$key","short-id":"abcd"}}]}"""
                val yaml = """
                    proxies:
                      - name: fixture
                        type: vless
                        server: example.org
                        port: 443
                        uuid: $uuid
                        flow: xtls-rprx-vision
                        encryption: $encryption
                        tls: true
                        servername: front.example.org
                        client-fingerprint: $fp
                        reality-opts: {public-key: $key, short-id: abcd}
                """.trimIndent()
                val parser = SubscriptionContentParser()
                val expected = nodes(parser.parse(uri)).single()
                val native = JSONObject().put("outbounds", JSONArray().put(expected)).toString()
                val sources = linkedMapOf(
                    "uri" to uri, "base64-uri" to base64(uri), "xray" to xray,
                    "xray-array" to "[$xray]", "clash-json" to clash, "clash-yaml" to yaml,
                    "base64-yaml" to base64(yaml), "singbox" to native,
                    "singbox-array" to JSONArray().put(expected).toString(),
                )
                for ((format, content) in sources) {
                    val result = parser.parse(content)
                    assertEquals("$format received", 1, result.report.received)
                    assertEquals("$format imported", 1, result.report.imported)
                    assertTrue("$format issues", result.report.issues.isEmpty())
                    val node = nodes(result).single()
                    assertEquals(encryption, node.getString("encryption"))
                    assertEquals("xtls-rprx-vision", node.getString("flow"))
                    assertEquals(uuid, node.getString("uuid"))
                    val tls = node.getJSONObject("tls")
                    assertEquals("front.example.org", tls.getString("server_name"))
                    assertFalse(tls.optBoolean("insecure"))
                    assertEquals(fp, tls.getJSONObject("utls").getString("fingerprint"))
                    assertEquals(key, tls.getJSONObject("reality").getString("public_key"))
                    assertEquals("abcd", tls.getJSONObject("reality").getString("short_id"))
                    fixture("$format-$fp-$mode", result)
                }
            }
        }
    }

    @Test fun hysteriaSourceFingerprintDoesNotChangeIndependentQuicOptions() {
        for (fp in listOf("chrome", "firefox", "safari", "random", "future-tcp-preset")) {
            for (disableParrot in listOf(false, true)) {
                val mask = JSONObject("""{"udp":[{"type":"salamander","settings":{"password":"mask-password"}}],"quicParams":{"disableChromeParrot":$disableParrot}}""")
                val uri = "hy2://proxy-password@example.org:9000?sni=front.example.org&fp=$fp&alpn=h3&fm=${URLEncoder.encode(mask.toString(), "UTF-8")}#fixture"
                val xray = """{"outbounds":[{"tag":"fixture","protocol":"hysteria","settings":{"version":2,"address":"example.org","port":9000},"streamSettings":{"network":"hysteria","security":"tls","hysteriaSettings":{"version":2,"auth":"proxy-password"},"tlsSettings":{"serverName":"front.example.org","fingerprint":"$fp","alpn":["h3"],"allowInsecure":false},"finalmask":$mask}}]}"""
                for ((format, content) in mapOf("uri" to uri, "xray" to xray)) {
                    val result = SubscriptionContentParser().parse(content)
                    assertEquals(1, result.report.imported)
                    assertTrue(result.report.issues.isEmpty())
                    assertTrue(result.report.warnings.contains(QuicTlsCompatibility.XRAY_HYSTERIA_NOTE))
                    val node = nodes(result).single()
                    assertEquals(disableParrot, node.getBoolean("disable_chrome_parrot"))
                    assertEquals("proxy-password", node.getString("password"))
                    assertEquals("mask-password", node.getJSONObject("obfs").getString("password"))
                    val tls = node.getJSONObject("tls")
                    assertFalse(tls.has("utls"))
                    assertFalse(tls.optBoolean("insecure"))
                    assertEquals("front.example.org", tls.getString("server_name"))
                    assertEquals("h3", tls.getJSONArray("alpn").getString(0))
                    fixture("hy2-$format-$fp-$disableParrot", result)
                }
            }
        }
    }

    @Test fun panelAliasesAreEquivalentAndContradictionsRemainErrors() {
        val prefix = "hy2://proxy-password@example.org:9000?"
        val canonical = ProxyLinkParser.hysteria(prefix + "sni=front.example.org&fp=firefox")
        val aliases = ProxyLinkParser.hysteria(prefix + "peer=front.example.org&fingerprint=firefox")
        assertTrue(canonical.similar(aliases))
        for (query in listOf("sni=one.example&peer=two.example", "fp=chrome&fingerprint=firefox", "fm=")) {
            assertTrue(query, runCatching { ProxyLinkParser.hysteria(prefix + query) }.isFailure)
        }
    }
}
