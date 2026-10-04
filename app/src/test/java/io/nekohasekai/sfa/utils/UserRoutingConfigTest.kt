package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRoutingConfigTest {

    private fun baseProfile(): String {
        return JSONObject()
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
    }

    @Test
    fun emptyUserConfig_returnsOriginal() {
        val base = baseProfile()
        assertEquals(base, UserRoutingConfig.applyToConfig(base, ""))
        assertEquals(base, UserRoutingConfig.applyToConfig(base, "   "))
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
        // no rule with geosite-category-ru from user
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
            // must not put geoip:ru into ip_cidr
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
        // cld detour resolved to selector tag proxy if exists
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
    fun blockOutbound_createdWhenNeeded() {
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
        assertTrue(tags.contains("block"))
    }
}
