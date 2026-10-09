package io.nekohasekai.sfa.utils

import java.io.File
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayHysteriaImportTest {
    private val pin = "47161c5463aa4dafc845ce48da9fc44a2e1779d5c5c38849484b85374dfec559"

    private fun config(
        name: String,
        host: String,
        port: Int,
        sni: String,
        pinned: Boolean,
    ): JSONObject {
        val tls =
            JSONObject()
                .put("serverName", sni)
                .put("fingerprint", "firefox")
                .put("enableSessionResumption", false)
                .put("alpn", JSONArray(listOf("h3")))
        if (pinned) tls.put("pinnedPeerCertSha256", pin)
        val outbound =
            JSONObject()
                .put("tag", "proxy")
                .put("protocol", "hysteria")
                .put(
                    "settings",
                    JSONObject().put("address", host).put("port", port).put("version", 2),
                )
                .put(
                    "streamSettings",
                    JSONObject()
                        .put("network", "hysteria")
                        .put("security", "tls")
                        .put(
                            "hysteriaSettings",
                            JSONObject().put("version", 2).put("auth", "fixture-password"),
                        )
                        .put("tlsSettings", tls)
                        .put(
                            "finalmask",
                            JSONObject()
                                .put(
                                    "quicParams",
                                    JSONObject().put("debug", false).put("congestion", "bbr"),
                                ),
                        ),
                )
        return JSONObject()
            .put("remarks", name)
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("protocol", "freedom").put("tag", "direct")),
            )
    }

    private fun nodes(result: SubscriptionImportResult): List<JSONObject> {
        val outbounds = JSONObject(result.config).getJSONArray("outbounds")
        return (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .filter { it.has("server") }
    }

    @Test
    fun geodemaXrayHysteria2PreservesAllThreeEndpointsAndCertificatePin() {
        val configs =
            JSONArray()
                .put(
                    config("Germany", "94.183.197.2", 8444, "premium-de-hy2.geodema.network", false)
                )
                .put(
                    config("Netherlands", "94.183.196.4", 8444, "premium-nl.geodema.network", false)
                )
                .put(config("Austria", "94.183.156.77", 21444, "www.amd.com", true))
        val result = SubscriptionContentParser().parse(configs.toString())
        assertEquals(3, result.report.received)
        assertEquals(3, result.report.imported)
        val nodes = nodes(result)
        assertEquals(listOf("Germany", "Netherlands", "Austria"), nodes.map { it.getString("tag") })
        assertEquals(listOf(8444, 8444, 21444), nodes.map { it.getInt("server_port") })
        assertEquals("94.183.156.77", nodes[2].getString("server"))
        nodes.forEach {
            assertEquals("hysteria2", it.getString("type"))
            assertEquals("fixture-password", it.getString("password"))
            assertEquals(
                "firefox",
                it.getJSONObject("tls").getJSONObject("utls").getString("fingerprint"),
            )
            assertTrue(it.getBoolean("disable_chrome_parrot"))
        }
        assertEquals("www.amd.com", nodes[2].getJSONObject("tls").getString("server_name"))
        assertEquals(
            ProxyLinkParser.certificatePin(pin),
            nodes[2].getJSONObject("tls").getJSONArray("xray_certificate_sha256").getString(0),
        )
        File("build/native-configs").mkdirs()
        File("build/native-configs/import-xray-hysteria2.json").writeText(result.config)
    }

    @Test
    fun certificateAliasesAndFinalmaskPreserveSecurityAndRejectUnknownWireMasks() {
        val fm =
            URLEncoder.encode("{\"quicParams\":{\"congestion\":\"bbr\",\"debug\":false}}", "UTF-8")
        val node =
            ProxyLinkParser.hysteria(
                "hy2://fixture@example.org:443?sni=front.example.org&vcn=cert.example.org&pcs=$pin&fm=$fm"
            )
        assertEquals("front.example.org", node.getJSONObject("tls").getString("server_name"))
        assertEquals(
            "cert.example.org",
            node.getJSONObject("tls").getJSONArray("verify_server_names").getString(0),
        )
        assertTrue(node.getJSONObject("tls").has("xray_certificate_sha256"))
        assertTrue(
            runCatching { ProxyLinkParser.hysteria("hy2://fixture@example.org?pcs=bad") }.isFailure
        )
        assertTrue(
            runCatching {
                    ProxyLinkParser.hysteria(
                        "hy2://fixture@example.org?fm=%7B%22udp%22%3A%5B%7B%22type%22%3A%22sudoku%22%7D%5D%7D"
                    )
                }
                .isFailure
        )
        assertTrue(
            runCatching {
                    ProxyLinkParser.applyFinalMask(
                        node,
                        JSONObject("{\"quicParams\":{\"congestion\":\"reno\"}}"),
                    )
                }
                .isFailure
        )
    }


    @Test
    fun finalmaskQuicSubsetMapsNativeHysteria2FieldsAndRejectsWireMasks() {
        // Full supported quicParams subset → native sing-box 1.14.2 field names.
        val fm =
            URLEncoder.encode(
                """{"quicParams":{"congestion":"bbr","debug":false,"bbrProfile":"conservative","disablePathMTUDiscovery":true,"udpHop":{"ports":"1000-2000","interval":30}},"udp":[],"tcp":[]}""",
                "UTF-8",
            )
        val node =
            ProxyLinkParser.hysteria(
                "hy2://secret@example.org:443?sni=front.example.org&fm=$fm"
            )
        assertEquals("hysteria2", node.getString("type"))
        assertEquals("conservative", node.getString("bbr_profile"))
        assertTrue(node.getBoolean("disable_path_mtu_discovery"))
        assertTrue(node.has("server_ports"))
        assertTrue(node.getJSONArray("server_ports").length() >= 1)
        assertEquals("30s", node.getString("hop_interval"))
        // congestion=bbr is validated but not emitted as a separate outbound field
        // (sing-box uses BBR via profile / default path — not a JSON congestion key).
        assertFalse(node.has("congestion"))

        // Absent finalmask → no native hop/bbr fields forced.
        val plain = ProxyLinkParser.hysteria("hy2://secret@example.org:443?sni=front.example.org")
        assertFalse(plain.has("bbr_profile"))
        assertFalse(plain.has("server_ports"))
        assertFalse(plain.has("hop_interval"))

        // Publish full subscription config for CI patched sing-box check.
        val imported =
            SubscriptionContentParser()
                .parse("hy2://secret@example.org:443?sni=front.example.org&fm=$fm#a2a")
        assertEquals(1, imported.report.imported)
        val root = JSONObject(imported.config)
        val out =
            root.getJSONArray("outbounds").let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }.single { it.has("server") }
            }
        assertEquals("conservative", out.getString("bbr_profile"))
        assertTrue(out.getBoolean("disable_path_mtu_discovery"))
        java.io.File("build/native-configs").mkdirs()
        java.io.File("build/native-configs/import-finalmask-quic-subset.json").writeText(imported.config)

        // Wire obfuscation must hard-fail (not silent ignore).
        assertTrue(
            runCatching {
                    ProxyLinkParser.hysteria(
                        "hy2://secret@example.org:443?fm=" +
                            URLEncoder.encode("""{"udp":[{"type":"sudoku"}]}""", "UTF-8")
                    )
                }
                .isFailure
        )
        assertTrue(
            runCatching {
                    ProxyLinkParser.hysteria(
                        "hy2://secret@example.org:443?fm=" +
                            URLEncoder.encode("""{"tcp":[{"type":"noise"}]}""", "UTF-8")
                    )
                }
                .isFailure
        )

        // Unknown critical quicParams key.
        assertTrue(
            runCatching {
                    ProxyLinkParser.applyFinalMask(
                        ProxyLinkParser.hysteria("hy2://secret@example.org:443"),
                        JSONObject("""{"quicParams":{"unknownCritical":true}}"""),
                    )
                }
                .isFailure
        )

        // Incompatible values.
        assertTrue(
            runCatching {
                    ProxyLinkParser.applyFinalMask(
                        ProxyLinkParser.hysteria("hy2://secret@example.org:443"),
                        JSONObject("""{"quicParams":{"bbrProfile":"turbo"}}"""),
                    )
                }
                .isFailure
        )
        assertTrue(
            runCatching {
                    ProxyLinkParser.applyFinalMask(
                        ProxyLinkParser.hysteria("hy2://secret@example.org:443"),
                        JSONObject("""{"quicParams":{"congestion":"brutal"}}"""),
                    )
                }
                .isFailure
        )
        assertTrue(
            runCatching {
                    ProxyLinkParser.applyFinalMask(
                        ProxyLinkParser.hysteria("hy2://secret@example.org:443"),
                        JSONObject("""{"quicParams":{"debug":true}}"""),
                    )
                }
                .isFailure
        )
    }

    @Test
    fun vlessAndTrojanCertificateAliasesKeepSniSeparateFromVerificationName() {
        for (uri in
            listOf(
                "vless://11111111-1111-4111-8111-111111111111@example.org?encryption=none&security=tls",
                "trojan://fixture@example.org?security=tls",
            )) {
            val result =
                SubscriptionContentParser()
                    .parse("$uri&sni=front.example.org&vcn=cert.example.org&pcs=$pin")
            val tls = nodes(result).single().getJSONObject("tls")
            assertEquals("front.example.org", tls.getString("server_name"))
            assertEquals("cert.example.org", tls.getJSONArray("verify_server_names").getString(0))
        }
        assertTrue(
            runCatching {
                    SubscriptionContentParser()
                        .parse(
                            "vless://11111111-1111-4111-8111-111111111111@example.org?encryption=none&pcs=$pin"
                        )
                }
                .isFailure
        )
    }
}
