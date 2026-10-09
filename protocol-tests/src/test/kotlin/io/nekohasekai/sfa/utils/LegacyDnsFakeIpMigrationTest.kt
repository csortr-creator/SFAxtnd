package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Import-time legacy dns.fakeip migration (sing-box 1.11/1.12 semantics → 1.14).
 */
class LegacyDnsFakeIpMigrationTest {

    private fun baseOutbounds() =
        JSONArray()
            .put(
                JSONObject()
                    .put("type", "direct")
                    .put("tag", "direct"),
            )
            .put(
                JSONObject()
                    .put("type", "vless")
                    .put("tag", "proxy")
                    .put("server", "example.org")
                    .put("server_port", 443)
                    .put("uuid", "11111111-1111-4111-8111-111111111111"),
            )

    private fun wrap(dns: JSONObject): String =
        JSONObject()
            .put("outbounds", baseOutbounds())
            .put("dns", dns)
            .put("route", JSONObject().put("final", "proxy"))
            .toString()

    private fun parse(json: String) = SubscriptionContentParser().parse(json)

    private fun export(name: String, config: String) {
        File("build/native-configs").mkdirs()
        File("build/native-configs/$name").writeText(config)
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
        val result = parse(wrap(dns))
        assertTrue(result.report.issues.isEmpty())
        val root = JSONObject(result.config)
        val outDns = root.getJSONObject("dns")
        assertFalse(outDns.has("fakeip"))
        assertEquals("remote", outDns.getString("final"))
        val rules = outDns.getJSONArray("rules")
        assertEquals(1, rules.length())
        assertEquals("fakeip", rules.getJSONObject(0).getString("server"))
        val servers = outDns.getJSONArray("servers")
        val fake =
            (0 until servers.length())
                .map { servers.getJSONObject(it) }
                .first { it.optString("tag") == "fakeip" }
        assertEquals("fakeip", fake.getString("type"))
        assertEquals("198.18.0.0/15", fake.getString("inet4_range"))
        assertEquals("fc00::/18", fake.getString("inet6_range"))
        assertFalse(fake.has("address"))
        export("legacy-fakeip-enabled.json", result.config)
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
            parse(wrap(dns))
            fail("expected LegacyDnsFakeIpIncompatibleException")
        } catch (e: LegacyDnsFakeIpIncompatibleException) {
            assertTrue(e.message!!.startsWith("LEGACY_DNS_FAKEIP_INCOMPATIBLE"))
        } catch (e: IllegalArgumentException) {
            // may be wrapped
            assertTrue(e.message!!.contains("LEGACY_DNS_FAKEIP_INCOMPATIBLE") || e is LegacyDnsFakeIpIncompatibleException)
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
        val result = parse(wrap(dns))
        val outDns = JSONObject(result.config).getJSONObject("dns")
        assertFalse(outDns.has("fakeip"))
        val s = outDns.getJSONArray("servers").getJSONObject(0)
        assertEquals("fakeip", s.getString("type"))
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
        val result = parse(wrap(dns))
        assertFalse(JSONObject(result.config).getJSONObject("dns").has("fakeip"))
        // no invented fakeip server
        val servers = JSONObject(result.config).getJSONObject("dns").getJSONArray("servers")
        assertFalse(
            (0 until servers.length()).any {
                servers.getJSONObject(it).optString("type") == "fakeip"
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
        val fake =
            JSONObject(parse(wrap(dns)).config)
                .getJSONObject("dns")
                .getJSONArray("servers")
                .getJSONObject(0)
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
        val fake =
            JSONObject(parse(wrap(dns)).config)
                .getJSONObject("dns")
                .getJSONArray("servers")
                .getJSONObject(0)
        assertEquals("fakeip", fake.getString("type"))
        assertFalse(fake.has("inet4_range"))
        assertFalse(fake.has("inet6_range"))
    }

    @Test
    fun unknownFieldThrows() {
        val dns =
            JSONObject()
                .put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "l")))
                .put(
                    "fakeip",
                    JSONObject().put("enabled", true).put("store_mode", "weird"),
                )
        try {
            parse(wrap(dns))
            fail("expected incompatible")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("LEGACY_DNS_FAKEIP_INCOMPATIBLE"))
        }
    }

    @Test
    fun modernConfigUnchangedAsideFromExistingSanitize() {
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
                        )
                        .put(JSONObject().put("type", "local").put("tag", "local")),
                )
                .put("final", "local")
        val result = parse(wrap(dns))
        val out = JSONObject(result.config).getJSONObject("dns")
        assertFalse(out.has("fakeip"))
        val fake =
            (0 until out.getJSONArray("servers").length())
                .map { out.getJSONArray("servers").getJSONObject(it) }
                .first { it.optString("type") == "fakeip" }
        assertEquals("198.18.0.0/15", fake.getString("inet4_range"))
        assertEquals("local", out.getString("final"))
        export("modern-fakeip.json", result.config)
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
                    JSONObject()
                        .put("enabled", true)
                        .put("inet4_range", "198.18.0.0/15"),
                )
        val once = parse(wrap(dns)).config
        val twice = parse(once).config
        val a = JSONObject(once).getJSONObject("dns")
        val b = JSONObject(twice).getJSONObject("dns")
        assertFalse(a.has("fakeip"))
        assertFalse(b.has("fakeip"))
        assertEquals(
            a.getJSONArray("servers").getJSONObject(0).getString("type"),
            b.getJSONArray("servers").getJSONObject(0).getString("type"),
        )
        assertEquals(
            a.getJSONArray("servers").getJSONObject(0).optString("inet4_range"),
            b.getJSONArray("servers").getJSONObject(0).optString("inet4_range"),
        )
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
        val out = JSONObject(parse(wrap(dns)).config).getJSONObject("dns")
        assertEquals("google", out.getString("final"))
        assertEquals(2, out.getJSONArray("rules").length())
        assertEquals("google", out.getJSONArray("rules").getJSONObject(0).getString("server"))
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
        val result = parse(wrap(dns))
        assertFalse(JSONObject(result.config).getJSONObject("dns").has("fakeip"))
        assertTrue(result.report.warnings.any { it.contains("no fakeip DNS server") })
        assertFalse(
            (0 until JSONObject(result.config).getJSONObject("dns").getJSONArray("servers").length())
                .any {
                    JSONObject(result.config)
                        .getJSONObject("dns")
                        .getJSONArray("servers")
                        .getJSONObject(it)
                        .optString("type") == "fakeip"
                },
        )
    }
}
