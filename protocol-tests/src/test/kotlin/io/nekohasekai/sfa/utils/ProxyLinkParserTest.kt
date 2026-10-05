package io.nekohasekai.sfa.utils

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class ProxyLinkParserTest {
    @Test fun hysteria2PreservesCredentialsTlsAndObfuscation() {
        val node = ProxyLinkParser.hysteria("hy2://user%3Apass+word@example.org:8443/?sni=tls.example.org&obfs=salamander&obfs-password=a%2Bb&insecure=0#%D0%A2%D0%B5%D1%81%D1%82")
        assertEquals("hysteria2", node.getString("type"))
        assertEquals("user:pass+word", node.getString("password"))
        assertEquals(8443, node.getInt("server_port"))
        assertEquals("Тест", node.getString("tag"))
        assertEquals("a+b", node.getJSONObject("obfs").getString("password"))
        assertEquals("tls.example.org", node.getJSONObject("tls").getString("server_name"))
        assertFalse(node.getJSONObject("tls").getBoolean("insecure"))
        assertFalse(node.has("multiplex"))
    }

    @Test fun hysteria2Ipv6AndPortHopping() {
        val node = ProxyLinkParser.hysteria("hysteria2://secret@[2001:db8::1]:443,5000-6000/?hopInterval=10")
        assertEquals("2001:db8::1", node.getString("server"))
        assertEquals("[\"443\",\"5000:6000\"]", node.getJSONArray("server_ports").toString())
        assertEquals("10s", node.getString("hop_interval"))
    }

    @Test fun hysteria2DefaultPortAndStrictTls() {
        val node = ProxyLinkParser.hysteria("hysteria2://secret@example.org/#test")
        assertEquals(443, node.getInt("server_port"))
        assertTrue(node.getJSONObject("tls").getBoolean("enabled"))
        assertFalse(node.getJSONObject("tls").optBoolean("insecure"))
        assertFalse(node.has("up_mbps"))
    }

    @Test fun hysteria1KeepsItsOwnProtocolFields() {
        val node = ProxyLinkParser.hysteria("hysteria://example.org:443?auth=p%2Bss&peer=tls.example.org&upmbps=20&downmbps=80&obfs=xplus&obfsParam=obfs-secret&alpn=hysteria")
        assertEquals("hysteria", node.getString("type"))
        assertEquals("p+ss", node.getString("auth_str"))
        assertEquals("obfs-secret", node.getString("obfs"))
        assertEquals(20, node.getInt("up_mbps"))
        assertEquals(80, node.getInt("down_mbps"))
        assertFalse(node.has("password"))
    }

    @Test fun hysteria1MissingBandwidthStillProducesUsableOptions() {
        val node = ProxyLinkParser.hysteria("hysteria://example.org:443?auth=secret")
        assertTrue(node.getInt("up_mbps") > 0)
        assertTrue(node.getInt("down_mbps") > 0)
    }

    @Test fun certificatePinIsNeverSilentlyDiscardedOrChangedToSpki() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.hysteria("hy2://secret@example.org?pinSHA256=abcd")
        }
    }

    @Test fun unsupportedHysteriaProtocolIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.hysteria("hysteria://example.org?protocol=faketcp")
        }
    }

    @Test fun xhttpModesArePreservedAndNeverBecomeTcp() {
        for (mode in listOf("auto", "packet-up", "stream-up", "stream-one")) {
            val transport = ProxyLinkParser.transport("xhttp", mapOf("mode" to mode, "path" to "/split", "host" to "cdn.example.org"), "tls.example.org")!!
            assertEquals("xhttp", transport.getString("type"))
            assertEquals(mode, transport.getString("mode"))
            assertEquals("/split", transport.getString("path"))
            assertEquals("cdn.example.org", transport.getString("host"))
        }
        assertEquals("xhttp", ProxyLinkParser.transport("splithttp", emptyMap(), "example.org")!!.getString("type"))
    }

    @Test fun xhttpExtraRangesAndXmuxUseNativeSchema() {
        val params = ProxyLinkParser.query("extra=" + URLEncoder.encode("""{"xPaddingBytes":"100-300","scMaxEachPostBytes":1000000,"noGRPCHeader":true,"xmux":{"maxConcurrency":"8-16","hKeepAlivePeriod":30}}""", "UTF-8"))
        val transport = ProxyLinkParser.transport("xhttp", params, "example.org")!!
        assertEquals(300, transport.getJSONObject("x_padding_bytes").getInt("to"))
        assertEquals(1000000, transport.getJSONObject("sc_max_each_post_bytes").getInt("from"))
        assertTrue(transport.getBoolean("no_grpc_header"))
        assertEquals(8, transport.getJSONObject("xmux").getJSONObject("max_concurrency").getInt("from"))
        assertEquals(30, transport.getJSONObject("xmux").getInt("h_keep_alive_period"))
        assertFalse(transport.has("extra"))
    }

    @Test fun xhttpIndependentDownloadKeepsTlsAndEndpoint() {
        val extra = """{"downloadSettings":{"address":"download.example.org","port":443,"network":"xhttp","security":"tls","tlsSettings":{"serverName":"tls.example.org","alpn":["h2"]},"xhttpSettings":{"path":"/down","mode":"stream-up"}}}"""
        val download = ProxyLinkParser.transport("xhttp", mapOf("extra" to extra), "example.org")!!.getJSONObject("download_settings")
        assertEquals("download.example.org", download.getString("server"))
        assertEquals("/down", download.getString("path"))
        assertEquals("tls.example.org", download.getJSONObject("tls").getString("server_name"))
        assertTrue(download.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test fun unknownTransportAndXhttpOptionsFailInsteadOfLosingSettings() {
        assertThrows(IllegalStateException::class.java) { ProxyLinkParser.transport("unknown", emptyMap(), "example.org") }
        assertThrows(IllegalStateException::class.java) { ProxyLinkParser.transport("xhttp", mapOf("extra" to """{"unknown":true}"""), "example.org") }
        assertThrows(IllegalArgumentException::class.java) { ProxyLinkParser.transport("xhttp", mapOf("mode" to "invalid"), "example.org") }
    }

    @Test fun invalidPortsFailInsteadOfConnectingToAnotherEndpoint() {
        for (endpoint in listOf("example.org:0", "example.org:65536", "example.org:6000-5000", "2001:db8::1")) {
            assertThrows(IllegalArgumentException::class.java) { ProxyLinkParser.endpoint(endpoint) }
        }
    }

    @Test fun existingWebsocketGrpcAndTcpStillMapCorrectly() {
        assertNull(ProxyLinkParser.transport("tcp", emptyMap(), "example.org"))
        assertEquals("example.org", ProxyLinkParser.transport("ws", emptyMap(), "example.org")!!.getJSONObject("headers").getString("Host"))
        assertEquals("svc", ProxyLinkParser.transport("grpc", mapOf("serviceName" to "svc"), "example.org")!!.getString("service_name"))
    }

    @Test fun allSupportedShareSchemesReachLocalImport() {
        for (scheme in listOf("hy2", "hysteria2", "hysteria", "vless", "vmess", "trojan", "ss")) assertTrue(ProxyLinkParser.isShareLink("$scheme://test"))
        assertFalse(ProxyLinkParser.isShareLink("https://subscription.example.org"))
    }
}
