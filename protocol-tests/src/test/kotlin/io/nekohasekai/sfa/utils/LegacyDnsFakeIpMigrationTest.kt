package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LegacyDnsFakeIpMigrationTest {

    private fun migrate(dns: JSONObject): List<String> {
        val warnings = mutableListOf<String>()
        DnsFakeIpMigration.migrate(dns, warnings)
        return warnings
    }

    private fun findFakeIp(dns: JSONObject): JSONObject =
        (0 until dns.getJSONArray("servers").length())
            .map { dns.getJSONArray("servers").getJSONObject(it) }
            .first { it.optString("type") == "fakeip" }

    private fun export(name: String, dns: JSONObject) {
        // Residual legacy address-only DNS servers are not FakeIP-migrated; convert for
        // sing-box 1.14 native check so the fixture validates FakeIP shape, not address→type.
        val servers = dns.optJSONArray("servers")
        if (servers != null) {
            for (i in 0 until servers.length()) {
                val s = servers.optJSONObject(i) ?: continue
                if (s.has("address") && !s.has("type")) {
                    val addr = s.remove("address").toString().trim()
                    s.put("type", "udp")
                    s.put("server", addr)
                }
            }
        }
        val serversOut = dns.optJSONArray("servers") ?: JSONArray().also { dns.put("servers", it) }
        val hasDirect =
            (0 until serversOut.length()).any {
                serversOut.optJSONObject(it)?.optString("tag") == "dns-direct"
            }
        if (!hasDirect) {
            serversOut.put(JSONObject().put("type", "local").put("tag", "dns-direct"))
        }
        File("build/native-configs").mkdirs()
        File("build/native-configs/$name").writeText(
            JSONObject()
                .put("dns", dns)
                .put(
                    "outbounds",
                    JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct")),
                )
                .put(
                    "route",
                    JSONObject()
                        .put("final", "direct")
                        .put("default_domain_resolver", "dns-direct"),
                )
                .toString(2),
        )
    }

    @Test
    fun enabledTrueWithLegacyServerMigratesRangesAndRemovesObject() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(JSONObject().put("address", "1.1.1.1").put("tag", "remote"))
                        .put(JSONObject().put("address", "fakeip").put("tag", "fakeip")),
                )
                .put(
                    "rules",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("query_type", JSONArray(listOf("A", "AAAA")))
                                .put("server", "fakeip"),
                        ),
                )
                .put("final", "remote")
                .put(
                    "fakeip",
                    JSONObject()
                        .put("enabled", true)
                        .put("inet4_range", "198.18.0.0/15")
                        .put("inet6_range", "fc00::/18"),
                )
        migrate(dns)
        assertFalse(dns.has("fakeip"))
        assertEquals("remote", dns.getString("final"))
        assertEquals(1, dns.getJSONArray("rules").length())
        val fake = findFakeIp(dns)
        assertEquals("fakeip", fake.getString("type"))
        assertEquals("198.18.0.0/15", fake.getString("inet4_range"))
        assertEquals("fc00::/18", fake.getString("inet6_range"))
        assertFalse(fake.has("address"))
        export("legacy-fakeip-enabled.json", dns)
    }

    @Test
    fun enabledFalseWithLegacyAddressServerFails() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(JSONObject().put("address", "fakeip").put("tag", "fakeip")),
                )
                .put("fakeip", JSONObject().put("enabled", false))
        try {
            migrate(dns)
            fail("expected LEGACY_DNS_FAKEIP_INCOMPATIBLE")
        } catch (e: LegacyDnsFakeIpIncompatibleException) {
            assertTrue(e.message!!.startsWith("LEGACY_DNS_FAKEIP_INCOMPATIBLE"))
        }
    }

    @Test
    fun enabledFalseWithModernFakeIpServerKeepsModern() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "fakeip")
                                .put("tag", "fakeip")
                                .put("inet4_range", "198.18.0.0/15"),
                        ),
                )
                .put("fakeip", JSONObject().put("enabled", false))
        migrate(dns)
        assertFalse(dns.has("fakeip"))
        val s = findFakeIp(dns)
        assertEquals("198.18.0.0/15", s.getString("inet4_range"))
    }

    @Test
    fun enabledOmittedTreatedAsFalse() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(JSONObject().put("type", "local").put("tag", "local")),
                )
                .put("fakeip", JSONObject().put("inet4_range", "198.18.0.0/15"))
        migrate(dns)
        assertFalse(dns.has("fakeip"))
        assertFalse(
            (0 until dns.getJSONArray("servers").length()).any {
                dns.getJSONArray("servers").getJSONObject(it).optString("type") == "fakeip"
            },
        )
    }

    @Test
    fun customRangesPreserved() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(JSONObject().put("address", "fakeip").put("tag", "fp")),
                )
                .put(
                    "fakeip",
                    JSONObject()
                        .put("enabled", true)
                        .put("inet4_range", "10.0.0.0/8")
                        .put("inet6_range", "fd00::/8"),
                )
        migrate(dns)
        val fake = findFakeIp(dns)
        assertEquals("10.0.0.0/8", fake.getString("inet4_range"))
        assertEquals("fd00::/8", fake.getString("inet6_range"))
    }

    @Test
    fun missingRangesDoesNotInventDefaults() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(JSONObject().put("address", "fakeip").put("tag", "fp")),
                )
                .put("fakeip", JSONObject().put("enabled", true))
        migrate(dns)
        val fake = findFakeIp(dns)
        assertEquals("fakeip", fake.getString("type"))
        assertFalse(fake.has("inet4_range"))
        assertFalse(fake.has("inet6_range"))
    }

    @Test
    fun unknownFieldThrows() {
        val dns =
            JSONObject()
                .put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "l")))
                .put("fakeip", JSONObject().put("enabled", true).put("store_mode", "weird"))
        try {
            migrate(dns)
            fail("expected incompatible")
        } catch (e: LegacyDnsFakeIpIncompatibleException) {
            assertTrue(e.message!!.contains("LEGACY_DNS_FAKEIP_INCOMPATIBLE"))
        }
    }

    @Test
    fun modernConfigUnchanged() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "fakeip")
                                .put("tag", "fakeip")
                                .put("inet4_range", "198.18.0.0/15")
                                .put("inet6_range", "fc00::/18"),
                        ),
                )
                .put("final", "local")
        migrate(dns)
        assertFalse(dns.has("fakeip"))
        assertEquals("198.18.0.0/15", findFakeIp(dns).getString("inet4_range"))
        export("modern-fakeip.json", dns)
    }

    @Test
    fun migrationIsIdempotent() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(JSONObject().put("address", "fakeip").put("tag", "fakeip")),
                )
                .put(
                    "fakeip",
                    JSONObject().put("enabled", true).put("inet4_range", "198.18.0.0/15"),
                )
        migrate(dns)
        val once = dns.toString()
        migrate(dns)
        assertEquals(once, dns.toString())
        assertEquals("fakeip", findFakeIp(dns).getString("type"))
    }

    @Test
    fun existingRulesFinalPreserved() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(JSONObject().put("address", "8.8.8.8").put("tag", "google"))
                        .put(JSONObject().put("address", "fakeip").put("tag", "fakeip")),
                )
                .put(
                    "rules",
                    JSONArray()
                        .put(JSONObject().put("domain", "example.com").put("server", "google"))
                        .put(
                            JSONObject()
                                .put("query_type", JSONArray(listOf("A")))
                                .put("server", "fakeip"),
                        ),
                )
                .put("final", "google")
                .put("fakeip", JSONObject().put("enabled", true).put("inet4_range", "198.18.0.0/15"))
        migrate(dns)
        assertEquals("google", dns.getString("final"))
        assertEquals(2, dns.getJSONArray("rules").length())
    }

    @Test
    fun enabledTrueWithoutServerOnlyWarns() {
        val dns =
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(JSONObject().put("type", "local").put("tag", "local")),
                )
                .put("fakeip", JSONObject().put("enabled", true).put("inet4_range", "198.18.0.0/15"))
        val warnings = migrate(dns)
        assertFalse(dns.has("fakeip"))
        assertTrue(warnings.any { it.contains("no fakeip DNS server") })
        assertFalse(
            (0 until dns.getJSONArray("servers").length()).any {
                dns.getJSONArray("servers").getJSONObject(it).optString("type") == "fakeip"
            },
        )
    }


}
