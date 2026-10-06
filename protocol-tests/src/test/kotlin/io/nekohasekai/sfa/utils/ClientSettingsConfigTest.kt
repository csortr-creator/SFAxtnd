package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ClientSettingsConfigTest {
    private val profile =
        """{
      "inbounds":[{"type":"tun","tag":"tun","address":["172.19.0.1/30"],"auto_route":true}],
      "outbounds":[{"type":"selector","tag":"pick","outbounds":["node"]},{"type":"socks","tag":"node","server":"192.0.2.1","server_port":1080},{"type":"direct","tag":"direct"}],
      "dns":{"servers":[{"type":"local","tag":"dns-direct"}],"rules":[{"domain_suffix":["ru"],"server":"dns-direct"}]},
      "route":{"rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}],"final":"pick","auto_detect_interface":true}
    }"""

    private fun user(fake: Boolean = false) =
        JSONObject(
            """{"dns":{"managed":true,"optimistic":true,"cacheEnabled":true,"fakeip":$fake,"reverseMapping":true,"directStrategy":"ipv4_only","proxyStrategy":"prefer_ipv6","directTimeoutSeconds":2,"proxyTimeoutSeconds":3,"servers":[{"tag":"d1","address":"udp://1.1.1.1","detour":"direct"},{"tag":"d2","address":"udp://9.9.9.9","detour":"direct"},{"tag":"p1","address":"https://1.1.1.1/dns-query","detour":"proxy"},{"tag":"p2","address":"https://8.8.8.8/dns-query","detour":"proxy"}]},"rules":[{"domainSuffix":"ru","outbound":"direct","dnsRule":true},{"domain":"ads.example","outbound":"block","dnsRule":true}]}"""
        )

    @Test
    fun orderedDnsFallbackHasEvaluateWaitRespondThenNextServer() {
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, user().toString()))
        val rules = root.getJSONObject("dns").getJSONArray("rules")
        val direct =
            (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .first { it.optString("server") == "d1" }
        assertEquals("evaluate", direct.getString("action"))
        assertEquals("2s", direct.getString("timeout"))
        assertFalse(direct.has("strategy"))
        assertEquals("prefer_ipv6", root.getJSONObject("dns").getString("strategy"))
        val index = (0 until rules.length()).first { rules.getJSONObject(it) == direct }
        assertEquals("respond", rules.getJSONObject(index + 1).getString("action"))
        assertEquals(
            direct.getString("tag"),
            rules.getJSONObject(index + 1).getString("match_response"),
        )
        assertEquals("d2", rules.getJSONObject(index + 2).getString("server"))
        assertEquals("route", rules.getJSONObject(index + 2).getString("action"))
        assertEquals("p2", rules.getJSONObject(rules.length() - 1).getString("server"))
        assertEquals(
            "pick",
            root.getJSONObject("dns").getJSONArray("servers").getJSONObject(3).getString("detour"),
        )
    }

    @Test
    fun blockingIsARejectActionForConnectionsAndDns() {
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, user().toString()))
        for (section in listOf("route", "dns")) {
            val rules = root.getJSONObject(section).getJSONArray("rules")
            val block =
                (0 until rules.length())
                    .map { rules.getJSONObject(it) }
                    .first { it.optJSONArray("domain")?.optString(0) == "ads.example" }
            assertEquals("reject", block.getString("action"))
            assertFalse(block.has("outbound"))
        }
    }

    @Test
    fun dnsBootstrapSupportsHostnameIpv6AndCustomPort() {
        val input =
            user().apply {
                getJSONObject("dns")
                    .getJSONArray("servers")
                    .put(
                        JSONObject()
                            .put("tag", "custom")
                            .put("address", "https://dns.example:8443/custom")
                            .put("detour", "direct")
                    )
                    .put(
                        JSONObject()
                            .put("tag", "v6")
                            .put("address", "udp://[2606:4700:4700::1111]:5353")
                            .put("detour", "direct")
                    )
            }
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, input.toString()))
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        val custom =
            (0 until servers.length())
                .map { servers.getJSONObject(it) }
                .first { it.optString("tag") == "custom" }
        assertEquals(8443, custom.getInt("server_port"))
        assertEquals("/custom", custom.getString("path"))
        assertEquals("sfa-bootstrap", custom.getString("domain_resolver"))
        val v6 =
            (0 until servers.length())
                .map { servers.getJSONObject(it) }
                .first { it.optString("tag") == "v6" }
        assertEquals("2606:4700:4700::1111", v6.getString("server"))
        assertEquals(5353, v6.getInt("server_port"))
    }

    @Test
    fun fakeIpUsesNewTransportAndSpecificRulesStayAheadOfIt() {
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, user(true).toString()))
        val dns = root.getJSONObject("dns")
        assertTrue(dns.getBoolean("optimistic"))
        val servers = dns.getJSONArray("servers")
        assertEquals("fakeip", servers.getJSONObject(servers.length() - 1).getString("type"))
        val rules = dns.getJSONArray("rules")
        assertTrue(
            (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .any { it.optString("server") == "sfa-fakeip" && it.has("query_type") }
        )
        assertFalse(dns.has("fakeip"))
        export(root, "fakeip.json")
    }

    @Test
    fun managedDnsAndTunOptionsProduceNativeConfig() {
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, user().toString()))
        ClientSettingsConfig.applyCore(
            root,
            JSONObject(
                """{"mtu":1400,"ipMode":"dual","strictRoute":true,"sniff":"on","sniffers":"http,tls,quic","hijack":"on","sniffTimeoutMs":500,"logLevel":"debug"}"""
            ),
        )
        val tun = root.getJSONArray("inbounds").getJSONObject(0)
        assertEquals(1400, tun.getInt("mtu"))
        assertEquals(2, tun.getJSONArray("address").length())
        val rules = root.getJSONObject("route").getJSONArray("rules")
        assertEquals("500ms", rules.getJSONObject(0).getString("timeout"))
        assertEquals("hijack-dns", rules.getJSONObject(1).getString("action"))
        export(root, "managed-dns.json")
    }

    @Test
    fun disableSniffAndHijackRetainsOtherRulesAndMigratesLegacyBlocks() {
        val root = JSONObject(profile)
        root
            .getJSONObject("route")
            .getJSONArray("rules")
            .put(
                JSONObject().put("domain", JSONArray().put("ads.example")).put("outbound", "block")
            )
        ClientSettingsConfig.applyCore(root, JSONObject("""{"sniff":"off","hijack":"off"}"""))
        val rules = root.getJSONObject("route").getJSONArray("rules")
        assertEquals(1, rules.length())
        assertEquals("reject", rules.getJSONObject(0).getString("action"))
        export(root, "no-sniff.json")
    }

    @Test
    fun remoteAndLocalListsOverrideOnlyTheirTags() {
        val root = JSONObject(profile)
        ClientSettingsConfig.applyRuleSets(
            root,
            JSONArray(
                """[{"type":"remote","tag":"custom","format":"source","url":"https://example.org/rules.json"}]"""
            ),
        )
        assertEquals(
            "source",
            root
                .getJSONObject("route")
                .getJSONArray("rule_set")
                .getJSONObject(0)
                .getString("format"),
        )
    }

    @Test
    fun networksPortsProtocolsAndSpecificServerUseNativeTypes() {
        val user =
            """{"rules":[{"network":"tcp,udp","protocol":"http,tls","port":"80,443,1000:2000","outbound":"node"}]}"""
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, user))
        val rule = root.getJSONObject("route").getJSONArray("rules").getJSONObject(2)
        assertEquals(2, rule.getJSONArray("network").length())
        assertEquals("node", rule.getString("outbound"))
        assertEquals(2, rule.getJSONArray("protocol").length())
        assertEquals(443, rule.getJSONArray("port").getInt(1))
        assertEquals("1000:2000", rule.getJSONArray("port_range").getString(0))
        export(root, "route-conditions.json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPortCannotSilentlyDisableAllUserSettings() {
        UserRoutingConfig.applyToConfig(
            profile,
            """{"rules":[{"port":"99999","outbound":"direct"}]}""",
        )
    }

    @Test
    fun legacyAddressAndLogicalDnsFiltersAreMigratedBeforeEnablingFallback() {
        val root = JSONObject(profile)
        root
            .getJSONObject("dns")
            .getJSONArray("rules")
            .put(
                JSONObject(
                    """{"ip_cidr":["192.0.2.0/24"],"server":"dns-direct","strategy":"ipv4_only"}"""
                )
            )
            .put(
                JSONObject(
                    """{"type":"logical","mode":"and","rules":[{"ip_is_private":true},{"domain_suffix":["example.org"]}],"server":"dns-direct"}"""
                )
            )
        val result = JSONObject(UserRoutingConfig.applyToConfig(root.toString(), user().toString()))
        val rules = result.getJSONObject("dns").getJSONArray("rules")
        assertTrue(
            (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .any { it.has("match_response") && it.has("ip_cidr") }
        )
        export(result, "legacy-dns-migrated.json")
    }

    @Test
    fun explicitHijackCapturesPort53WhenSniffIsOff() {
        val root = JSONObject(profile)
        ClientSettingsConfig.applyCore(root, JSONObject("""{"sniff":"off","hijack":"on"}"""))
        val rules = root.getJSONObject("route").getJSONArray("rules")
        assertEquals(1, rules.length())
        assertEquals(
            53,
            rules
                .getJSONObject(0)
                .getJSONArray("rules")
                .getJSONObject(1)
                .getJSONArray("port")
                .getInt(0),
        )
        export(root, "hijack-without-sniff.json")
    }

    @Test
    fun ipv4ModePreservesSingleStringTunnelAddress() {
        val root = JSONObject(profile)
        root.getJSONArray("inbounds").getJSONObject(0).put("address", "10.23.0.1/30")
        ClientSettingsConfig.applyCore(root, JSONObject("""{"ipMode":"ipv4"}"""))
        assertEquals(
            "10.23.0.1/30",
            root.getJSONArray("inbounds").getJSONObject(0).getJSONArray("address").getString(0),
        )
    }

    @Test
    fun resolveAndFastFallbackUseNativeActionsAndKeepDnsFirst() {
        val root = JSONObject(profile)
        ClientSettingsConfig.applyCore(
            root,
            JSONObject(
                """{"sniff":"on","hijack":"on","resolveMode":"on","resolveStrategy":"prefer_ipv6","fastFallback":true}"""
            ),
        )
        val rules = root.getJSONObject("route").getJSONArray("rules")
        assertEquals("sniff", rules.getJSONObject(0).getString("action"))
        assertEquals("hijack-dns", rules.getJSONObject(1).getString("action"))
        assertEquals("resolve", rules.getJSONObject(2).getString("action"))
        assertEquals("prefer_ipv6", rules.getJSONObject(2).getString("strategy"))
        assertFalse(root.getJSONArray("outbounds").getJSONObject(0).has("fallback_delay"))
        assertEquals(
            "10ms",
            root.getJSONArray("outbounds").getJSONObject(1).getString("fallback_delay"),
        )
        export(root, "resolve-fast-fallback.json")
        ClientSettingsConfig.applyCore(root, JSONObject("""{"resolveMode":"off"}"""))
        assertFalse(
            (0 until rules.length()).any {
                root
                    .getJSONObject("route")
                    .getJSONArray("rules")
                    .optJSONObject(it)
                    ?.optString("action") == "resolve"
            }
        )
    }

    @Test
    fun explicitDnsRoutingWinsBeforeFakeIpAndPreservesQueryType() {
        val input = user(true)
        input
            .getJSONObject("dns")
            .put(
                "rules",
                JSONArray()
                    .put(
                        JSONObject("""{"kind":"domain","value":"ads2.example","target":"block"}""")
                    )
                    .put(
                        JSONObject(
                            """{"kind":"suffix","value":"example.net","target":"direct","queryType":"A"}"""
                        )
                    )
                    .put(
                        JSONObject(
                            """{"kind":"keyword","value":"private","target":"proxy","enabled":false}"""
                        )
                    ),
            )
        val root = JSONObject(UserRoutingConfig.applyToConfig(profile, input.toString()))
        val rules = root.getJSONObject("dns").getJSONArray("rules")
        assertEquals("ads2.example", rules.getJSONObject(0).getJSONArray("domain").getString(0))
        assertEquals("reject", rules.getJSONObject(0).getString("action"))
        val direct =
            (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .first {
                    it.optString("server") == "d1" &&
                        it.optJSONArray("domain_suffix")?.optString(0) == "example.net"
                }
        assertEquals("A", direct.getString("query_type"))
        assertEquals("evaluate", direct.getString("action"))
        assertFalse((0 until rules.length()).any { rules.getJSONObject(it).has("domain_keyword") })
        export(root, "explicit-dns-routes.json")
    }

    private fun export(root: JSONObject, name: String) {
        val target = File("build/native-configs").apply { mkdirs() }
        File(target, name).writeText(root.toString(2))
    }
}
