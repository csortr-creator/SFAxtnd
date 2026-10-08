package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-1: route.default_domain_resolver must reference an existing dns.servers tag.
 * Reproduces "default domain resolver not found: dns-direct" on panel sing-box JSON
 * that has no dns block (e.g. Vynel FormatSingBox shape — format-agnostic fixture).
 */
class DomainResolverImportTest {

    /** Panel-like sing-box body: outbounds + route, no dns (matches common panel export). */
    private fun panelSingBoxWithoutDns(): String =
        """
        {
          "log": { "level": "warn" },
          "outbounds": [
            {
              "type": "vless",
              "tag": "node1",
              "server": "example.org",
              "server_port": 443,
              "uuid": "11111111-1111-4111-8111-111111111111",
              "tls": {
                "enabled": true,
                "server_name": "www.example.com",
                "utls": { "enabled": true, "fingerprint": "chrome" },
                "reality": {
                  "enabled": true,
                  "public_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "short_id": "abcd"
                }
              }
            },
            { "type": "direct", "tag": "direct" }
          ],
          "route": {
            "final": "node1",
            "auto_detect_interface": true,
            "rules": []
          }
        }
        """.trimIndent()

    @Test
    fun applyInjectsDnsDirectWhenDefaultResolverMissingServer() {
        val root = JSONObject(panelSingBoxWithoutDns())
        assertFalse(root.has("dns"))
        SubscriptionRouting.apply(root, SubscriptionRouting.Mode.NORMAL)
        val route = root.getJSONObject("route")
        assertEquals("dns-direct", route.getString("default_domain_resolver"))
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        val tags =
            (0 until servers.length()).map { servers.getJSONObject(it).getString("tag") }
        assertTrue("dns-direct must be registered", "dns-direct" in tags)
        val direct =
            (0 until servers.length())
                .map { servers.getJSONObject(it) }
                .first { it.getString("tag") == "dns-direct" }
        assertEquals("local", direct.getString("type"))
    }

    @Test
    fun applyDoesNotDuplicateExistingDnsDirect() {
        val root =
            JSONObject()
                .put(
                    "dns",
                    JSONObject()
                        .put(
                            "servers",
                            JSONArray()
                                .put(JSONObject().put("type", "udp").put("tag", "dns-direct").put("server", "1.1.1.1")),
                        ),
                )
                .put("outbounds", JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct")))
                .put("route", JSONObject())
        SubscriptionRouting.apply(root, SubscriptionRouting.Mode.NORMAL)
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        val count =
            (0 until servers.length()).count {
                servers.getJSONObject(it).optString("tag") == "dns-direct"
            }
        assertEquals(1, count)
        assertEquals("udp", servers.getJSONObject(0).getString("type"))
    }

    @Test
    fun sanitizeMigratesPanelJsonAndRegistersResolver() {
        val result = SubscriptionContentParser().parse(panelSingBoxWithoutDns())
        assertTrue(result.report.issues.isEmpty())
        val root = JSONObject(result.config)
        assertEquals("dns-direct", root.getJSONObject("route").getString("default_domain_resolver"))
        val tags =
            root.getJSONObject("dns").getJSONArray("servers").let { servers ->
                (0 until servers.length()).map { servers.getJSONObject(it).getString("tag") }
            }
        assertTrue(tags.contains("dns-direct"))
    }

    @Test
    fun missingResolverTagIsDetectableBeforeEnsure() {
        val root = JSONObject(panelSingBoxWithoutDns())
        root.getJSONObject("route").put("default_domain_resolver", "dns-direct")
        val servers = root.optJSONObject("dns")?.optJSONArray("servers")
        val has =
            servers != null &&
                (0 until servers.length()).any {
                    servers.optJSONObject(it)?.optString("tag") == "dns-direct"
                }
        assertFalse(has)
        SubscriptionRouting.ensureDefaultDomainResolverAvailable(root)
        val after = root.getJSONObject("dns").getJSONArray("servers")
        assertTrue(
            (0 until after.length()).any {
                after.getJSONObject(it).getString("tag") == "dns-direct"
            },
        )
    }
}
