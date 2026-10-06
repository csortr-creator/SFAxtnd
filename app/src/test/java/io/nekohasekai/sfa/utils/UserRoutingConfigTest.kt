package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRoutingConfigTest {

    private fun baseProfile(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray()
                .put(JSONObject().put("type", "selector").put("tag", "proxy").put("outbounds", JSONArray().put("node1")))
                .put(JSONObject().put("type", "vless").put("tag", "node1").put("server", "1.2.3.4"))
                .put(JSONObject().put("type", "direct").put("tag", "direct")),
        )
        .put(
            "dns",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "dns-direct")
                        .put("type", "udp")
                        .put("server", "8.8.8.8"),
                ),
            ),
        )
        .put("route", JSONObject().put("rules", JSONArray()))
        .toString()

    @Test
    fun emptyUserConfig_stillAppliesFinalize() {
        val base = baseProfile()
        assertNotNull(JSONObject(UserRoutingConfig.applyToConfig(base, "")))
        assertNotNull(JSONObject(UserRoutingConfig.applyToConfig(base, "   ")))
    }

    @Test
    fun emptyUserConfig_stillForcesDownloadDetourDirect() {
        val base = JSONObject()
            .put("outbounds", JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put(
                "route",
                JSONObject().put(
                    "rule_set",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "remote")
                            .put("tag", "geosite-category-ru")
                            .put("format", "binary")
                            .put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs")
                            .put("download_detour", "Выбор сервера"),
                    ),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(base, ""))
        val item = out.getJSONObject("route").getJSONArray("rule_set").getJSONObject(0)
        assertEquals("direct", item.getString("download_detour"))
    }

    @Test
    fun dnsRule_usesExistingServerTagNotMissingProxy() {
        val user = JSONObject()
            .put(
                "rules",
                JSONArray().put(
                    JSONObject()
                        .put("domain", "example.com")
                        .put("outbound", "proxy")
                        .put("dnsRule", true)
                        .put("enabled", true),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val dnsRules = out.getJSONObject("dns").optJSONArray("rules") ?: JSONArray()
        assertTrue(dnsRules.length() > 0)
        assertEquals("dns-direct", dnsRules.getJSONObject(0).getString("server"))
    }

    @Test
    fun disabledRule_isSkipped() {
        val user = JSONObject()
            .put(
                "rules",
                JSONArray().put(
                    JSONObject()
                        .put("name", "ru-sites")
                        .put("ruleSet", "geosite-category-ru")
                        .put("outbound", "direct")
                        .put("enabled", false),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val rules = out.getJSONObject("route").optJSONArray("rules") ?: JSONArray()
        var found = false
        for (i in 0 until rules.length()) {
            val rs = rules.optJSONObject(i)?.optJSONArray("rule_set")
            if (rs != null) {
                for (j in 0 until rs.length()) {
                    if (rs.optString(j).contains("geosite-category-ru")) found = true
                }
            }
        }
        assertFalse(found)
    }

    @Test
    fun enabledRuRule_addsRuleSetAndDownloadDetourDirect() {
        val user = JSONObject()
            .put(
                "rules",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("name", "ru-sites")
                            .put("ruleSet", "geosite-category-ru")
                            .put("outbound", "direct")
                            .put("enabled", true),
                    )
                    .put(
                        JSONObject()
                            .put("name", "ru-ip")
                            .put("ruleSet", "geoip-ru")
                            .put("outbound", "direct")
                            .put("enabled", true),
                    ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val ruleSet = out.getJSONObject("route").getJSONArray("rule_set")
        val tags = mutableSetOf<String>()
        for (i in 0 until ruleSet.length()) {
            val item = ruleSet.getJSONObject(i)
            tags.add(item.getString("tag"))
            if (item.optString("type") == "remote") {
                assertEquals("direct", item.getString("download_detour"))
            }
        }
        assertTrue(tags.contains("geosite-category-ru"))
        assertTrue(tags.contains("geoip-ru"))
    }

    @Test
    fun geoipColon_convertedToRuleSetTag() {
        val user = JSONObject()
            .put(
                "rules",
                JSONArray().put(
                    JSONObject()
                        .put("name", "ru-ip-legacy")
                        .put("type", "IP_CIDR")
                        .put("value", "geoip:ru")
                        .put("outbound", "direct")
                        .put("enabled", true),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val rules = out.getJSONObject("route").getJSONArray("rules")
        var ok = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            val rs = r.optJSONArray("rule_set") ?: continue
            for (j in 0 until rs.length()) {
                if (rs.getString(j) == "geoip-ru") ok = true
            }
            val cidr = r.optJSONArray("ip_cidr")
            if (cidr != null) {
                for (j in 0 until cidr.length()) {
                    assertFalse(cidr.getString(j).startsWith("geoip:"))
                }
            }
        }
        assertTrue(ok)
    }

    @Test
    fun dnsServers_mergedKeepDnsDirect() {
        val user = JSONObject()
            .put(
                "dns",
                JSONObject()
                    .put("finalServer", "cld")
                    .put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("tag", "cld")
                                .put("address", "https://1.1.1.1/dns-query")
                                .put("detour", "proxy"),
                        ),
                    ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val servers = out.getJSONObject("dns").getJSONArray("servers")
        val tags = (0 until servers.length()).map { servers.getJSONObject(it).getString("tag") }.toSet()
        assertTrue(tags.contains("dns-direct"))
        assertTrue(tags.contains("cld"))
        assertEquals("cld", out.getJSONObject("dns").getString("final"))
        val cld = (0 until servers.length()).map { servers.getJSONObject(it) }.first { it.getString("tag") == "cld" }
        assertEquals("proxy", cld.optString("detour"))
    }

    @Test
    fun dnsDetourDirect_omitted() {
        val user = JSONObject()
            .put(
                "dns",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("tag", "ynd")
                            .put("address", "77.88.8.8")
                            .put("detour", "direct"),
                    ),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val servers = out.getJSONObject("dns").getJSONArray("servers")
        val ynd = (0 until servers.length()).map { servers.getJSONObject(it) }.first { it.getString("tag") == "ynd" }
        assertTrue(ynd.optString("detour").isEmpty())
    }

    @Test
    fun ensureDirectOutbound_whenMissing() {
        val base = JSONObject()
            .put("outbounds", JSONArray().put(JSONObject().put("type", "vless").put("tag", "node1")))
            .put("route", JSONObject())
            .toString()
        val user = JSONObject()
            .put(
                "rules",
                JSONArray().put(
                    JSONObject()
                        .put("ruleSet", "geosite-category-ru")
                        .put("outbound", "direct")
                        .put("enabled", true),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(base, user))
        val tags = (0 until out.getJSONArray("outbounds").length()).map {
            out.getJSONArray("outbounds").getJSONObject(it).optString("tag")
        }
        assertTrue(tags.contains("direct"))
    }

    @Test
    fun blockingUsesRejectAction() {
        val user = JSONObject()
            .put(
                "rules",
                JSONArray().put(
                    JSONObject()
                        .put("name", "ads")
                        .put("ruleSet", "geosite-category-ads-all")
                        .put("outbound", "block")
                        .put("enabled", true),
                ),
            )
            .toString()
        val out = JSONObject(UserRoutingConfig.applyToConfig(baseProfile(), user))
        val tags = (0 until out.getJSONArray("outbounds").length()).map {
            out.getJSONArray("outbounds").getJSONObject(it).optString("tag")
        }
        assertFalse(tags.contains("block"))
        val rules = out.getJSONObject("route").getJSONArray("rules")
        assertTrue((0 until rules.length()).any { rules.optJSONObject(it)?.optString("action") == "reject" })
    }
}
