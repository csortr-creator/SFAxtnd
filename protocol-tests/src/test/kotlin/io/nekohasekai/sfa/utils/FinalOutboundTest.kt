package io.nekohasekai.sfa.utils

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinalOutboundTest {
    private val source = """{"outbounds":[{"type":"selector","tag":"pick","outbounds":["node"]},{"type":"vless","tag":"node"}],"route":{"final":"pick","rules":[{"domain":["allowed.test"],"outbound":"node"}]}}"""
    private fun apply(choice: String) = JSONObject(UserRoutingConfig.applyToConfig(source, """{"finalOutbound":"$choice"}"""))
    @Test fun directAndProxyPreserveRulesAndUseRealGroupTag() {
        assertEquals("pick", apply("profile").getJSONObject("route").getString("final"))
        assertEquals("pick", apply("proxy").getJSONObject("route").getString("final"))
        val direct = apply("direct")
        assertEquals("direct", direct.getJSONObject("route").getString("final"))
        assertEquals("node", direct.getJSONObject("route").getJSONArray("rules").getJSONObject(0).getString("outbound"))
        assertTrue((0 until direct.getJSONArray("outbounds").length()).any { direct.getJSONArray("outbounds").getJSONObject(it).optString("tag") == "direct" })
    }
    @Test fun blockRejectsOnlyTrafficAfterExistingRules() {
        val rules = apply("block").getJSONObject("route").getJSONArray("rules")
        assertEquals(2, rules.length())
        assertEquals("node", rules.getJSONObject(0).getString("outbound"))
        assertEquals("reject", rules.getJSONObject(1).getString("action"))
        assertFalse(rules.getJSONObject(1).has("outbound"))
    }
    @Test fun explicitFinalDoesNotLeaveWhitelistCatchAllAheadOfSubscriptionRules() {
        val preset = RoutingPresets.whitelist(emptyList()).first()
        val user = JSONObject().put("finalOutbound", "direct").put("rules", org.json.JSONArray().put(JSONObject().put("ruleSet", preset.ruleSet).put("outbound", "direct").put("enabled", true)))
        val route = JSONObject(UserRoutingConfig.applyToConfig(source, user.toString())).getJSONObject("route")
        assertEquals("direct", route.getString("final"))
        val rules = route.getJSONArray("rules")
        assertFalse((0 until rules.length()).any { rules.getJSONObject(it).length() == 1 && rules.getJSONObject(it).optString("outbound") == "pick" })
    }
}
