package io.nekohasekai.sfa.utils

import java.io.File
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForeignSubscriptionParserTest {
    private val uuid = "11111111-1111-4111-8111-111111111111"
    private val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 42 })

    private fun nodes(result: SubscriptionImportResult): List<JSONObject> {
        val array = JSONObject(result.config).getJSONArray("outbounds")
        return (0 until array.length()).map { array.getJSONObject(it) }.filter { it.has("server") }
    }

    private fun native(name: String, result: SubscriptionImportResult) {
        File("build/native-configs").mkdirs()
        File("build/native-configs/import-$name.json").writeText(result.config)
    }

    @Test
    fun conflictingOrUnusedOptionsAreReportedInsteadOfDropped() {
        val base =
            JSONObject()
                .put("name", "good")
                .put("type", "vless")
                .put("server", "example.org")
                .put("port", 443)
                .put("uuid", uuid)
        val proxies =
            JSONArray()
                .put(base)
                .put(JSONObject(base.toString()).put("sni", "front.example.org"))
                .put(JSONObject(base.toString()).put("ws-opts", JSONObject().put("path", "/ws")))
        val result =
            SubscriptionContentParser().parse(JSONObject().put("proxies", proxies).toString())
        assertEquals(1, result.report.imported)
        assertEquals(2, result.report.issues.size)
        for (uri in
            listOf(
                "tuic://$uuid:secret@example.org?fp=chrome",
                "anytls://secret@example.org?congestion_control=bbr",
                "anytls://secret@example.org?sni=one&peer=two",
            )) {
            assertTrue(runCatching { ProxyLinkParser.additional(uri) }.isFailure)
        }
    }

    @Test
    fun bomBase64AndNumericYamlCredentialsAreHandledExplicitly() {
        val yaml =
            "proxies:\n  - {name: SS, type: ss, server: example.org, port: 8388, cipher: aes-128-gcm, password: '1234'}"
        val encoded = Base64.getEncoder().encodeToString(yaml.toByteArray())
        assertTrue(SubscriptionContentParser.supports(encoded))
        assertEquals(1, SubscriptionContentParser().parse(encoded).report.imported)
        assertEquals(1, SubscriptionContentParser().parse("\uFEFF" + yaml).report.imported)
        assertTrue(
            runCatching { SubscriptionContentParser().parse(yaml.replace("'1234'", "1234")) }
                .isFailure
        )
        assertTrue(
            runCatching { SubscriptionContentParser().parse("<html>sign in</html>") }
                .exceptionOrNull()
                ?.message
                .orEmpty()
                .contains("HTML")
        )
        assertEquals(
            80,
            ProxyLinkParser.additional("http://user:pass@example.org").getInt("server_port"),
        )
        assertEquals(
            1080,
            ProxyLinkParser.additional("socks5://user:pass@example.org").getInt("server_port"),
        )
    }

    @Test
    fun clashYamlPreservesRealityTlsWsHeadersAndDnsWarning() {
        val text =
            """
            proxies:
              - name: 'VLESS test'
                type: vless
                server: example.org
                port: 443
                uuid: $uuid
                tls: true
                servername: tls.example.org
                client-fingerprint: chrome
                reality-opts:
                  public-key: $key
                  short-id: abcd
                network: ws
                ws-opts:
                  path: /ws?ed=0
                  headers:
                    Host: front.example.org
                    X-Test: preserved
            dns:
              enable: true
            rules: [MATCH,DIRECT]
        """
                .trimIndent()
        val result = SubscriptionContentParser().parse(text)
        val node = nodes(result).single()
        assertEquals("vless", node.getString("type"))
        assertEquals(
            key,
            node.getJSONObject("tls").getJSONObject("reality").getString("public_key"),
        )
        assertEquals("tls.example.org", node.getJSONObject("tls").getString("server_name"))
        assertFalse(node.getJSONObject("tls").optBoolean("insecure"))
        assertEquals(
            "preserved",
            node.getJSONObject("transport").getJSONObject("headers").getString("X-Test"),
        )
        assertTrue(result.report.warnings.isNotEmpty())
        native("clash-reality-ws", result)
    }

    @Test
    fun clashProviderYamlAndJsonGiveEquivalentParameters() {
        val yaml =
            "proxies:\n  - {name: SS, type: ss, server: example.org, port: 8388, cipher: aes-128-gcm, password: 'secret+%20:literal'}"
        val json =
            JSONObject()
                .put(
                    "proxies",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("name", "SS")
                                .put("type", "ss")
                                .put("server", "example.org")
                                .put("port", 8388)
                                .put("cipher", "aes-128-gcm")
                                .put("password", "secret+%20:literal")
                        ),
                )
        val a = nodes(SubscriptionContentParser().parse(yaml)).single()
        val b = nodes(SubscriptionContentParser().parse(json.toString())).single()
        assertTrue(a.similar(b))
        assertEquals("secret+%20:literal", a.getString("password"))
    }

    @Test
    fun allSupportedClashProtocolsProduceNativeConfigurations() {
        val proxies = JSONArray()
        for (type in
            listOf(
                "ss",
                "vmess",
                "vless",
                "trojan",
                "hysteria",
                "hysteria2",
                "tuic",
                "anytls",
                "socks5",
                "http",
            )) {
            val node =
                JSONObject()
                    .put("name", type)
                    .put("type", type)
                    .put("server", "example.org")
                    .put("port", 443)
            when (type) {
                "ss" -> node.put("cipher", "aes-128-gcm").put("password", "secret")
                "vmess",
                "vless" -> node.put("uuid", uuid).put("tls", true)
                "tuic" ->
                    node
                        .put("uuid", uuid)
                        .put("password", "secret")
                        .put("congestion-controller", "bbr")
                        .put("udp-relay-mode", "native")
                "hysteria" -> node.put("auth-str", "secret").put("up", "20 Mbps").put("down", 50)
                "socks5",
                "http" -> node.put("username", "user").put("password", "secret")
                else -> node.put("password", "secret")
            }
            proxies.put(node)
        }
        val result =
            SubscriptionContentParser().parse(JSONObject().put("proxies", proxies).toString())
        assertEquals(10, result.report.imported)
        assertTrue(result.report.displayText(), result.report.issues.isEmpty())
        native("clash-protocols", result)
    }

    @Test
    fun invalidClashNodesDoNotHideValidNeighbours() {
        val result =
            SubscriptionContentParser()
                .parse(
                    """
            proxies:
              - {name: good, type: ss, server: example.org, port: 8388, cipher: aes-128-gcm, password: secret}
              - {name: invalid-port, type: vless, server: example.org, port: bogus, uuid: $uuid}
              - {name: unsupported, type: ssr, server: example.org, port: 443}
              - {name: unsupported-options, type: ss, server: example.org, port: 8388, cipher: aes-128-gcm, password: secret, shadow-tls-opts: {password: confidential}}
        """
                        .trimIndent()
                )
        assertEquals(4, result.report.received)
        assertEquals(1, result.report.imported)
        assertEquals(3, result.report.issues.size)
        assertFalse(result.report.displayText().contains("confidential"))
    }

    @Test
    fun yamlRejectsObjectConstructionDuplicateKeysAndExcessiveAliases() {
        for (yaml in
            listOf(
                "proxies: !!java.net.URL [https://example.org]",
                "proxies: []\nproxies: []",
                "proxies: &p [*p]",
            )) {
            try {
                SubscriptionContentParser().parse(yaml)
                fail("Expected rejection")
            } catch (_: Exception) {}
        }
    }

    @Test
    fun xrayJsonFlattensMultipleEndpointsAndUsers() {
        val users =
            JSONArray()
                .put(JSONObject().put("id", uuid).put("encryption", "none"))
                .put(JSONObject().put("id", "custom-user").put("encryption", "none"))
        val servers =
            JSONArray()
                .put(
                    JSONObject()
                        .put("address", "one.example.org")
                        .put("port", 443)
                        .put("users", users)
                )
                .put(
                    JSONObject()
                        .put("address", "two.example.org")
                        .put("port", 8443)
                        .put("users", users)
                )
        val outbound =
            JSONObject()
                .put("tag", "vless")
                .put("protocol", "vless")
                .put("settings", JSONObject().put("vnext", servers))
                .put(
                    "streamSettings",
                    JSONObject()
                        .put("security", "reality")
                        .put("network", "grpc")
                        .put(
                            "realitySettings",
                            JSONObject()
                                .put("serverName", "tls.example.org")
                                .put("publicKey", key)
                                .put("shortId", "abcd"),
                        )
                        .put("grpcSettings", JSONObject().put("serviceName", "my-service")),
                )
        val result =
            SubscriptionContentParser()
                .parse(
                    JSONObject()
                        .put(
                            "outbounds",
                            JSONArray().put(outbound).put(JSONObject().put("protocol", "freedom")),
                        )
                        .toString()
                )
        assertEquals(4, result.report.imported)
        assertEquals(4, nodes(result).map { it.getString("tag") }.toSet().size)
        assertEquals(
            "my-service",
            nodes(result).first().getJSONObject("transport").getString("service_name"),
        )
        assertEquals(setOf(443, 8443), nodes(result).map { it.getInt("server_port") }.toSet())
        native("xray-multiple-reality-grpc", result)
    }

    @Test
    fun xrayFullConfigArraysImportEachConfig() {
        fun config(server: String) =
            JSONObject()
                .put(
                    "outbounds",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("protocol", "shadowsocks")
                                .put(
                                    "settings",
                                    JSONObject()
                                        .put(
                                            "servers",
                                            JSONArray()
                                                .put(
                                                    JSONObject()
                                                        .put("address", server)
                                                        .put("port", 8388)
                                                        .put("method", "aes-128-gcm")
                                                        .put("password", "secret")
                                                ),
                                        ),
                                )
                        ),
                )
        val result =
            SubscriptionContentParser()
                .parse(
                    JSONArray()
                        .put(config("one.example.org"))
                        .put(config("two.example.org"))
                        .toString()
                )
        assertEquals(2, result.report.imported)
        assertEquals(
            setOf("one.example.org", "two.example.org"),
            nodes(result).map { it.getString("server") }.toSet(),
        )
        native("xray-config-list", result)
    }

    @Test
    fun unsupportedXrayMuxAndVlessEncryptionAreReported() {
        fun outbound() =
            JSONObject()
                .put("protocol", "vless")
                .put(
                    "settings",
                    JSONObject()
                        .put(
                            "vnext",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("address", "example.org")
                                        .put("port", 443)
                                        .put(
                                            "users",
                                            JSONArray()
                                                .put(
                                                    JSONObject()
                                                        .put("id", uuid)
                                                        .put("encryption", "none")
                                                ),
                                        )
                                ),
                        ),
                )
        val good = outbound()
        val mux = outbound().put("mux", JSONObject().put("enabled", true))
        val encrypted = outbound()
        encrypted
            .getJSONObject("settings")
            .getJSONArray("vnext")
            .getJSONObject(0)
            .getJSONArray("users")
            .getJSONObject(0)
            .put("encryption", "unsupported")
        val result =
            SubscriptionContentParser()
                .parse(
                    JSONObject()
                        .put("outbounds", JSONArray().put(good).put(mux).put(encrypted))
                        .toString()
                )
        assertEquals(1, result.report.imported)
        assertEquals(2, result.report.issues.size)
    }

    @Test
    fun additionalUriProtocolsAndTrojanRealityReachNativeValidation() {
        val links =
            listOf(
                "tuic://$uuid:secret@example.org:443?congestion_control=bbr&udp_relay_mode=native#TUIC",
                "anytls://secret%2Bplus@example.org:443?sni=tls.example.org#AnyTLS",
                "socks5://user:secret@example.org:1080#SOCKS",
                "https://user:secret@example.org:443#HTTP",
                "trojan://secret@example.org:443?security=reality&pbk=$key&sid=abcd#Trojan",
            )
        val result = SubscriptionContentParser().parse(links.joinToString("\n"))
        assertEquals(result.report.displayText(), 5, result.report.imported)
        assertEquals(
            "secret+plus",
            nodes(result).first { it.getString("type") == "anytls" }.getString("password"),
        )
        assertTrue(
            nodes(result)
                .first { it.getString("type") == "trojan" }
                .getJSONObject("tls")
                .has("reality")
        )
        native("additional-uris", result)
    }

    @Test
    fun vmessUnknownTlsModeCannotBecomePlaintext() {
        val json =
            JSONObject()
                .put("add", "example.org")
                .put("port", 443)
                .put("id", uuid)
                .put("tls", "unknown")
        val link = "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
        try {
            SubscriptionContentParser().parse(link)
            fail("Expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Unsupported VMess TLS mode"))
        }
    }

    @Test
    fun reportWarningsAndFormatPersist() {
        val file = File.createTempFile("foreign-report", ".json")
        try {
            val report =
                SubscriptionImportReport(
                    1,
                    1,
                    emptyList(),
                    listOf("Не перенесена маршрутизация"),
                    "Xray JSON",
                )
            report.save(file.path)
            assertEquals(report, SubscriptionImportReport.read(file.path))
            assertTrue(report.displayText().contains("Не перенесена маршрутизация"))
        } finally {
            file.delete()
            File("${file.path}.import.json").delete()
        }
    }
}
