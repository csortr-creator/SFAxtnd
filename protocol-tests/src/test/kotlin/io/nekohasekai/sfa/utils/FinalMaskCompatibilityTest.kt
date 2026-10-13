package io.nekohasekai.sfa.utils

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinalMaskCompatibilityTest {
    private val udp = """{"udp":[{"type":"salamander","settings":{"password":"mask-password"}}]}"""
    private val tcp = """{"tcp":[{"type":"header-custom","settings":{"clients":[[{"type":"str","packet":"HELLO\r\n"}]],"servers":[[{"type":"str","packet":"READY\r\n"}]]}}]}"""
    private fun nodes(result: SubscriptionImportResult): List<JSONObject> =
        JSONObject(result.config).getJSONArray("outbounds").let { list ->
            (0 until list.length()).map { list.getJSONObject(it) }.filter { it.has("server") }
        }

    @Test fun salamanderUriMapsToNativeObfsAndKeepsAuthenticationAndTls() {
        val fm = URLEncoder.encode(udp, "UTF-8")
        val result = SubscriptionContentParser().parse("hy2://proxy-password@example.org:9000?sni=front.example.org&alpn=h3&fm=$fm#Masked-Hy2")
        val node = nodes(result).single()
        assertEquals("proxy-password", node.getString("password"))
        assertEquals("salamander", node.getJSONObject("obfs").getString("type"))
        assertEquals("mask-password", node.getJSONObject("obfs").getString("password"))
        assertEquals("front.example.org", node.getJSONObject("tls").getString("server_name"))
        assertEquals("h3", node.getJSONObject("tls").getJSONArray("alpn").getString(0))
        assertFalse(node.getJSONObject("tls").optBoolean("insecure"))
        java.io.File("build/native-configs").mkdirs()
        java.io.File("build/native-configs/import-finalmask-salamander.json").writeText(result.config)
    }

    @Test fun xrayJsonUsesSameMappingForVlessAndHysteria() {
        val vless = JSONObject().put("protocol", "vless").put("tag", "Masked-Vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
                .put("address", "example.org").put("port", 443)
                .put("users", JSONArray().put(JSONObject().put("id", "11111111-1111-4111-8111-111111111111").put("encryption", "none"))))))
            .put("streamSettings", JSONObject().put("network", "tcp").put("security", "none").put("finalmask", JSONObject(tcp)))
        val hy = JSONObject().put("protocol", "hysteria").put("tag", "Masked-Hy2")
            .put("settings", JSONObject().put("version", 2).put("address", "example.org").put("port", 9000))
            .put("streamSettings", JSONObject().put("network", "hysteria").put("security", "tls")
                .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", "proxy-password"))
                .put("tlsSettings", JSONObject().put("serverName", "front.example.org").put("fingerprint", "chrome"))
                .put("finalmask", JSONObject(udp)))
        val result = SubscriptionContentParser().parse(JSONObject().put("outbounds", JSONArray().put(vless).put(hy)).toString())
        assertEquals(2, result.report.imported)
        val list = nodes(result)
        assertEquals("HELLO\r\n", list[0].getJSONObject("tcp_header").getJSONArray("clients").getString(0))
        assertEquals("mask-password", list[1].getJSONObject("obfs").getString("password"))
        assertEquals("proxy-password", list[1].getString("password"))
        assertFalse(list[1].getJSONObject("tls").has("utls"))
        java.io.File("build/native-configs").mkdirs()
        java.io.File("build/native-configs/import-finalmask-xray.json").writeText(result.config)
    }

    @Test fun unsupportedChainsAndSettingsRejectWithoutPartialMutation() {
        for (mask in listOf(
            udp.replace("salamander", "sudoku"),
            udp.replace("\"password\":", "\"packetSize\":1200,\"password\":"),
            udp.replace("mask-password", "abc"),
            """{"udp":[{"type":"salamander","settings":{"password":"mask-password"}},{"type":"salamander","settings":{"password":"another"}}]}""",
            udp.dropLast(1)+""", "quicParams":{"unknownCritical":true}}""",
        )) {
            val node = ProxyLinkParser.hysteria("hy2://proxy-password@example.org:9000?sni=front.example.org")
            val before = node.toString()
            assertTrue(runCatching { FinalMaskCompatibility.apply(node, JSONObject(mask)) }.isFailure)
            assertEquals(before, node.toString())
        }
        val existing = ProxyLinkParser.hysteria("hy2://proxy-password@example.org:9000?obfs=salamander&obfs-password=mask-password")
        assertTrue(runCatching { FinalMaskCompatibility.apply(existing, JSONObject(udp)) }.isFailure)
    }

    @Test fun xrayHysteriaFingerprintIsNotMistakenForTcpUtls() {
        val fm = URLEncoder.encode(udp, "UTF-8")
        val node = ProxyLinkParser.hysteria("hy2://proxy-password@example.org:9000?fp=firefox&fm=$fm")
        assertFalse(node.getJSONObject("tls").has("utls"))
        assertFalse(node.getBoolean("disable_chrome_parrot"))
        assertEquals("mask-password", node.getJSONObject("obfs").getString("password"))
    }

    @Test fun trojanDoesNotSilentlyAcceptAnUnsupportedFinalMask() {
        val fm = URLEncoder.encode(tcp, "UTF-8")
        assertTrue(runCatching { SubscriptionContentParser().parse("trojan://proxy-password@example.org:443?fm=$fm#Masked-Trojan") }.isFailure)
    }
}
