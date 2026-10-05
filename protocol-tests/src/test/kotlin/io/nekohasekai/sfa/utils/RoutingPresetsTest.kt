package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.models.RoutingRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingPresetsTest {
    private fun apply(enabled: Boolean = true): JSONObject {
        val rules = RoutingPresets.whitelist(emptyList()).map { it.copy(enabled = enabled) }
        val user = JSONObject().put("rules", JSONArray(rules.map {
            JSONObject().put("name", it.name).put("ruleSet", it.ruleSet).put("outbound", it.outbound)
                .put("enabled", it.enabled).put("dnsRule", it.dnsRule)
        }))
        return JSONObject(UserRoutingConfig.applyToConfig("""{
          "outbounds":[{"type":"selector","tag":"my-subscription","outbounds":["server"]},
            {"type":"vless","tag":"server"},{"type":"direct","tag":"direct"}],
          "dns":{"servers":[{"type":"https","tag":"cld","detour":"my-subscription"},
            {"type":"udp","tag":"ynd","detour":"direct"}]},
          "route":{"rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"},
            {"domain_suffix":["ru"],"outbound":"direct"}]}
        }""", user.toString()))
    }

    @Test fun whitelistResolvesRealSrsAndKeepsDnsInterceptionBeforeCatchAll() {
        val root = apply()
        val route = root.getJSONObject("route")
        val entries = route.getJSONArray("rule_set")
        assertEquals(2, entries.length())
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            assertEquals(RoutingPresets.remoteRuleSets[entry.getString("tag")], entry.getString("url"))
            assertEquals("binary", entry.getString("format"))
            assertEquals("direct", entry.getString("download_detour"))
        }
        val rules = route.getJSONArray("rules")
        assertEquals("sniff", rules.getJSONObject(0).getString("action"))
        assertEquals("hijack-dns", rules.getJSONObject(1).getString("action"))
        assertEquals("direct", rules.getJSONObject(2).getString("outbound"))
        assertEquals("direct", rules.getJSONObject(3).getString("outbound"))
        assertEquals("my-subscription", rules.getJSONObject(4).getString("outbound"))
        assertEquals(1, rules.getJSONObject(4).length())
        assertEquals("ynd", root.getJSONObject("dns").getJSONArray("rules").getJSONObject(0).getString("server"))
    }

    @Test fun disablingWhitelistRestoresOriginalProfileRouting() {
        val root = apply(enabled = false)
        assertEquals(3, root.getJSONObject("route").getJSONArray("rules").length())
        assertEquals("direct", root.getJSONObject("route").getJSONArray("rules").getJSONObject(2).getString("outbound"))
        assertFalse(root.getJSONObject("route").has("rule_set"))
    }

    @Test fun applyingPresetTwiceDoesNotDuplicateRulesAndReplacesBroadRussianBypass() {
        val original = listOf(RoutingRule(name = "ru-sites", domainSuffix = ".ru"), RoutingRule(name = "ads", ruleSet = "geosite-category-ads-all"))
        val once = RoutingPresets.whitelist(original)
        assertEquals(once, RoutingPresets.whitelist(once))
        assertEquals(3, once.size)
        assertFalse(once.any { it.name == "ru-sites" })
    }
}
