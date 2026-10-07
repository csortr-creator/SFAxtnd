package io.nekohasekai.sfa.utils

import java.net.URLEncoder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProxyLinkParserTest {
    @Test
    fun serializedXhttpNullOptionsUseDefaultsWithoutDroppingTransport() {
        val uuid = "11111111-1111-4111-8111-111111111111"
        for (name in listOf("downloadSettings", "download_settings")) {
            val extra =
                JSONObject()
                    .put(name, JSONObject.NULL)
                    .put("headers", JSONObject.NULL)
                    .put("xmux", JSONObject.NULL)
                    .put("extra", JSONObject.NULL)
                    .put("scMaxConcurrentPosts", 3)
            val encoded = URLEncoder.encode(extra.toString(), "UTF-8")
            val result =
                SubscriptionContentParser()
                    .parse(
                        "vless://$uuid@example.org:443?type=xhttp&mode=packet-up&path=%2Ffixture&extra=$encoded#null-options"
                    )
            assertEquals(1, result.report.imported)
            assertTrue(result.report.issues.isEmpty())
            val array = JSONObject(result.config).getJSONArray("outbounds")
            val node =
                (0 until array.length()).map { array.getJSONObject(it) }.single { it.has("server") }
            val transport = node.getJSONObject("transport")
            assertEquals("xhttp", transport.getString("type"))
            assertEquals("packet-up", transport.getString("mode"))
            assertEquals("/fixture", transport.getString("path"))
            assertEquals(3, transport.getInt("sc_max_concurrent_posts"))
            for (field in listOf("download_settings", "headers", "xmux", "extra")) assertFalse(
                transport.has(field)
            )
            java.io.File("build/native-configs").mkdirs()
            java.io
                .File("build/native-configs/import-xhttp-null-$name.json")
                .writeText(result.config)
        }
    }

    @Test
    fun malformedXhttpDownloadAndUnknownNullOptionsRemainErrors() {
        for (value in listOf("broken", 12, org.json.JSONArray())) {
            val extra = JSONObject().put("downloadSettings", value).toString()
            assertTrue(
                runCatching {
                        ProxyLinkParser.transport("xhttp", mapOf("extra" to extra), "example.org")
                    }
                    .isFailure
            )
        }
        assertTrue(
            runCatching {
                    ProxyLinkParser.transport(
                        "xhttp",
                        mapOf("extra" to "{\"unknownWireFeature\":null}"),
                        "example.org",
                    )
                }
                .isFailure
        )
    }

    @Test
    fun concurrentPostTuningIsPreserved() {
        val extra = URLEncoder.encode("{\"scMaxConcurrentPosts\":4}", "UTF-8")
        val result =
            SubscriptionContentParser()
                .parse(
                    "vless://11111111-1111-4111-8111-111111111111@example.org:443?encryption=none&type=xhttp&extra=$extra#test"
                )
        assertEquals(1, result.report.imported)
        assertTrue(result.report.warnings.isEmpty())
        val outbounds = JSONObject(result.config).getJSONArray("outbounds")
        val node =
            (0 until outbounds.length())
                .map { outbounds.getJSONObject(it) }
                .single { it.has("server") }
        assertEquals(4, node.getJSONObject("transport").getInt("sc_max_concurrent_posts"))
        java.io.File("build/native-configs").mkdirs()
        java.io.File("build/native-configs/import-post-fallback.json").writeText(result.config)
        assertTrue(
            runCatching {
                    ProxyLinkParser.transport(
                        "xhttp",
                        mapOf("extra" to "{\"scMaxConcurrentPosts\":-1}"),
                        "example.org",
                    )
                }
                .isFailure
        )
    }

    @Test
    fun legacyXmuxReuseBudgetIsPreservedAndConflictsAreRejected() {
        val transport =
            ProxyLinkParser.transport(
                "xhttp",
                mapOf("extra" to "{\"xmux\":{\"maxConnections\":1,\"maxReuseTimes\":\"2-4\"}}"),
                "example.org",
            )!!
        val reuse = transport.getJSONObject("xmux").getJSONObject("c_max_reuse_times")
        assertEquals(2, reuse.getInt("from"))
        assertEquals(4, reuse.getInt("to"))
        assertTrue(
            runCatching {
                    ProxyLinkParser.transport(
                        "xhttp",
                        mapOf("extra" to "{\"xmux\":{\"maxReuseTimes\":2,\"cMaxReuseTimes\":3}}"),
                        "example.org",
                    )
                }
                .isFailure
        )
    }

    @Test
    fun hysteriaPanelAliasesPreserveTlsBandwidthAndHopSettings() {
        val node =
            ProxyLinkParser.hysteria(
                "hy2://secret@example.org:443?allowInsecure=0&serverName=tls.example.org&up=20%20Mbps&down=30&hop-interval=10&type=udp&security=tls"
            )
        assertFalse(node.getJSONObject("tls").getBoolean("insecure"))
        assertEquals("tls.example.org", node.getJSONObject("tls").getString("server_name"))
        assertEquals(20, node.getInt("up_mbps"))
        assertEquals(30, node.getInt("down_mbps"))
        assertEquals("10s", node.getString("hop_interval"))
        assertTrue(
            runCatching {
                    ProxyLinkParser.hysteria("hy2://secret@example.org?insecure=1&allowInsecure=0")
                }
                .isFailure
        )
    }

    @Test
    fun reportNamesUnsupportedParameterWithoutSavingItsValue() {
        val result =
            SubscriptionContentParser()
                .parse(
                    "ss://aes-128-gcm:secret@example.org:8388#good\nhy2://secret@example.org?unsupportedOption=confidential-value#bad"
                )
        assertEquals(1, result.report.imported)
        assertTrue(result.report.displayText().contains("unsupportedOption"))
        assertFalse(result.report.displayText().contains("confidential-value"))
    }

    @Test
    fun hysteria2PreservesCredentialsTlsAndObfuscation() {
        val node =
            ProxyLinkParser.hysteria(
                "hy2://user%3Apass+word@example.org:8443/?sni=tls.example.org&obfs=salamander&obfs-password=a%2Bb&insecure=0#%D0%A2%D0%B5%D1%81%D1%82"
            )
        assertEquals("hysteria2", node.getString("type"))
        assertEquals("user:pass+word", node.getString("password"))
        assertEquals(8443, node.getInt("server_port"))
        assertEquals("Тест", node.getString("tag"))
        assertEquals("a+b", node.getJSONObject("obfs").getString("password"))
        assertEquals("tls.example.org", node.getJSONObject("tls").getString("server_name"))
        assertFalse(node.getJSONObject("tls").getBoolean("insecure"))
        assertFalse(node.has("multiplex"))
    }

    @Test
    fun hysteria2Ipv6AndPortHopping() {
        val node =
            ProxyLinkParser.hysteria(
                "hysteria2://secret@[2001:db8::1]:443,5000-6000/?hopInterval=10"
            )
        assertEquals("2001:db8::1", node.getString("server"))
        assertEquals("[\"443\",\"5000:6000\"]", node.getJSONArray("server_ports").toString())
        assertEquals("10s", node.getString("hop_interval"))
    }

    @Test
    fun hysteria2DefaultPortAndStrictTls() {
        val node = ProxyLinkParser.hysteria("hysteria2://secret@example.org/#test")
        assertEquals(443, node.getInt("server_port"))
        assertTrue(node.getJSONObject("tls").getBoolean("enabled"))
        assertFalse(node.getJSONObject("tls").optBoolean("insecure"))
        assertFalse(node.has("up_mbps"))
    }

    @Test
    fun hysteriaEchUsesThePemFormatExpectedByTheCore() {
        val tls = ProxyLinkParser.hysteria("hy2://secret@example.org?ech=AQID").getJSONObject("tls")
        val pem = tls.getJSONObject("ech").getJSONArray("config").getString(0)
        assertEquals("-----BEGIN ECH CONFIGS-----\nAQID\n-----END ECH CONFIGS-----", pem)
    }

    @Test
    fun hysteria1KeepsItsOwnProtocolFields() {
        val node =
            ProxyLinkParser.hysteria(
                "hysteria://example.org:443?auth=p%2Bss&peer=tls.example.org&upmbps=20&downmbps=80&obfs=xplus&obfsParam=obfs-secret&alpn=hysteria"
            )
        assertEquals("hysteria", node.getString("type"))
        assertEquals("p+ss", node.getString("auth_str"))
        assertEquals("obfs-secret", node.getString("obfs"))
        assertEquals(20, node.getInt("up_mbps"))
        assertEquals(80, node.getInt("down_mbps"))
        assertFalse(node.has("password"))
    }

    @Test
    fun hysteria1MissingBandwidthStillProducesUsableOptions() {
        val node = ProxyLinkParser.hysteria("hysteria://example.org:443?auth=secret")
        assertTrue(node.getInt("up_mbps") > 0)
        assertTrue(node.getInt("down_mbps") > 0)
    }

    @Test
    fun certificatePinIsNeverSilentlyDiscardedOrChangedToSpki() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.hysteria("hy2://secret@example.org?pinSHA256=abcd")
        }
    }

    @Test
    fun certificateFingerprintKeepsExactBytesForHexColonHexAndBase64() {
        val bytes = ByteArray(32) { it.toByte() }
        val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        for (value in listOf(hex, hex.chunked(2).joinToString(":"), base64)) {
            val tls =
                ProxyLinkParser.hysteria(
                        "hy2://secret@example.org?insecure=1&pinSHA256=" +
                            URLEncoder.encode(value, "UTF-8")
                    )
                    .getJSONObject("tls")
            assertEquals(base64, tls.getJSONArray("certificate_sha256").getString(0))
            assertTrue(tls.getBoolean("insecure"))
            assertFalse(tls.has("certificate_public_key_sha256"))
        }
        val tls =
            ProxyLinkParser.hysteria("hy2://secret@example.org?pinSHA256=$hex").getJSONObject("tls")
        assertFalse(tls.optBoolean("insecure"))
    }

    @Test
    fun blankXhttpModesUseAutoInExtraAndDownloadSettings() {
        for (mode in listOf("", " ", JSONObject.NULL)) {
            val extra = JSONObject().put("mode", mode).toString()
            val transport =
                ProxyLinkParser.transport("xhttp", mapOf("extra" to extra), "example.org")!!
            assertEquals("auto", transport.getString("mode"))
        }
        val extra =
            """{"downloadSettings":{"address":"download.example.org","port":443,"xhttpSettings":{"mode":""}}}"""
        val transport =
            ProxyLinkParser.transport(
                "xhttp",
                mapOf("extra" to extra, "mode" to "stream-up"),
                "example.org",
            )!!
        assertEquals("stream-up", transport.getString("mode"))
        assertEquals("auto", transport.getJSONObject("download_settings").getString("mode"))
    }

    @Test
    fun unsupportedHysteriaProtocolIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.hysteria("hysteria://example.org?protocol=faketcp")
        }
    }

    @Test
    fun xhttpModesArePreservedAndNeverBecomeTcp() {
        for (mode in listOf("auto", "packet-up", "stream-up", "stream-one")) {
            val transport =
                ProxyLinkParser.transport(
                    "xhttp",
                    mapOf("mode" to mode, "path" to "/split", "host" to "cdn.example.org"),
                    "tls.example.org",
                )!!
            assertEquals("xhttp", transport.getString("type"))
            assertEquals(mode, transport.getString("mode"))
            assertEquals("/split", transport.getString("path"))
            assertEquals("cdn.example.org", transport.getString("host"))
        }
        assertEquals(
            "xhttp",
            ProxyLinkParser.transport("splithttp", emptyMap(), "example.org")!!.getString("type"),
        )
    }

    @Test
    fun xhttpExtraRangesAndXmuxUseNativeSchema() {
        val params =
            ProxyLinkParser.query(
                "extra=" +
                    URLEncoder.encode(
                        """{"xPaddingBytes":"100-300","scMaxEachPostBytes":1000000,"noGRPCHeader":true,"xmux":{"maxConcurrency":"8-16","hKeepAlivePeriod":30}}""",
                        "UTF-8",
                    )
            )
        val transport = ProxyLinkParser.transport("xhttp", params, "example.org")!!
        assertEquals(300, transport.getJSONObject("x_padding_bytes").getInt("to"))
        assertEquals(1000000, transport.getJSONObject("sc_max_each_post_bytes").getInt("from"))
        assertTrue(transport.getBoolean("no_grpc_header"))
        assertEquals(
            8,
            transport.getJSONObject("xmux").getJSONObject("max_concurrency").getInt("from"),
        )
        assertEquals(30, transport.getJSONObject("xmux").getInt("h_keep_alive_period"))
        assertFalse(transport.has("extra"))
    }

    @Test
    fun xraySessionFieldNamesMapToNativeMetadataPlacement() {
        val extra =
            """{"sessionPlacement":"header","sessionKey":"X-Session","seqPlacement":"query","seqKey":"seq"}"""
        val transport = ProxyLinkParser.transport("xhttp", mapOf("extra" to extra), "example.org")!!
        assertEquals("header", transport.getString("session_id_placement"))
        assertEquals("X-Session", transport.getString("session_id_key"))
        assertEquals("query", transport.getString("seq_placement"))
    }

    @Test
    fun xhttpIndependentDownloadKeepsTlsAndEndpoint() {
        val extra =
            """{"downloadSettings":{"address":"download.example.org","port":443,"network":"xhttp","security":"tls","tlsSettings":{"serverName":"tls.example.org","alpn":["h2"]},"xhttpSettings":{"path":"/down","mode":"stream-up"}}}"""
        val download =
            ProxyLinkParser.transport("xhttp", mapOf("extra" to extra), "example.org")!!
                .getJSONObject("download_settings")
        assertEquals("download.example.org", download.getString("server"))
        assertEquals("/down", download.getString("path"))
        assertEquals("tls.example.org", download.getJSONObject("tls").getString("server_name"))
        assertTrue(download.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test
    fun unknownTransportAndXhttpOptionsFailInsteadOfLosingSettings() {
        assertThrows(IllegalStateException::class.java) {
            ProxyLinkParser.transport("unknown", emptyMap(), "example.org")
        }
        assertThrows(IllegalStateException::class.java) {
            ProxyLinkParser.transport(
                "xhttp",
                mapOf("extra" to """{"unknown":true}"""),
                "example.org",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.transport("xhttp", mapOf("mode" to "invalid"), "example.org")
        }
    }

    @Test
    fun invalidPortsFailInsteadOfConnectingToAnotherEndpoint() {
        for (endpoint in
            listOf("example.org:0", "example.org:65536", "example.org:6000-5000", "2001:db8::1")) {
            assertThrows(IllegalArgumentException::class.java) {
                ProxyLinkParser.endpoint(endpoint)
            }
        }
    }

    @Test
    fun existingWebsocketGrpcAndTcpStillMapCorrectly() {
        assertNull(ProxyLinkParser.transport("tcp", emptyMap(), "example.org"))
        assertEquals(
            "example.org",
            ProxyLinkParser.transport("ws", emptyMap(), "example.org")!!.getJSONObject("headers")
                .getString("Host"),
        )
        assertEquals(
            "svc",
            ProxyLinkParser.transport("grpc", mapOf("serviceName" to "svc"), "example.org")!!
                .getString("service_name"),
        )
    }

    @Test
    fun allSupportedShareSchemesReachLocalImport() {
        for (scheme in
            listOf("hy2", "hysteria2", "hysteria", "vless", "vmess", "trojan", "ss")) assertTrue(
            ProxyLinkParser.isShareLink("$scheme://test")
        )
        assertFalse(ProxyLinkParser.isShareLink("https://subscription.example.org"))
    }
}
