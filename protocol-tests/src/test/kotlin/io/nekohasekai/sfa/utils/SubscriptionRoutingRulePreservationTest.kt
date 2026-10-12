package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRoutingRulePreservationTest {
    private val domains = listOf("first.example", "second.example", "third.example")

    private fun domainRule(domain: String) = JSONObject()
        .put("domain", JSONArray().put(domain))
        .put("outbound", "direct")

    private fun profile(rules: JSONArray) = JSONObject()
        .put("outbounds", JSONArray()
            .put(JSONObject()
                .put("type", "vless").put("tag", "node")
                .put("server", "example.org").put("server_port", 443)
                .put("uuid", "11111111-1111-4111-8111-111111111111"))
            .put(JSONObject()
                .put("type", "selector").put("tag", SubscriptionRouting.NORMAL_SELECTOR_TAG)
                .put("outbounds", JSONArray().put("node")))
            .put(JSONObject().put("type", "direct").put("tag", "direct")))
        .put("route", JSONObject().put("rules", rules))

    private fun rules(root: JSONObject) = root.getJSONObject("route").getJSONArray("rules")

    private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }

    private fun preservedDomains(root: JSONObject) = objects(rules(root))
        .filter { it.has("domain") }.map { it.getJSONArray("domain").getString(0) }

    @Test
    fun missingSystemActionsDoNotReplaceSourceRulesInEitherMode() {
        for (mode in SubscriptionRouting.Mode.values()) {
            for (hasSniff in listOf(false, true)) {
                for (hasDns in listOf(false, true)) {
                    val source = JSONArray().put(domainRule(domains[0]))
                    if (hasSniff) source.put(JSONObject().put("action", "sniff"))
                    source.put(domainRule(domains[1]))
                    if (hasDns) source.put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
                    source.put(domainRule(domains[2]))
                    val root = profile(source)
                    SubscriptionRouting.apply(root, mode, blockIpv6 = false)
                    assertEquals("$mode sniff=$hasSniff dns=$hasDns", domains, preservedDomains(root))
                    val actions = objects(rules(root)).map { it.optString("action") }
                    assertEquals(1, actions.count { it == "sniff" })
                    assertEquals(1, actions.count { it == "hijack-dns" })
                    assertTrue(actions.indexOf("sniff") < actions.indexOf("hijack-dns"))
                }
            }
        }
    }

    @Test
    fun fullSingBoxImportPreservesFirstTwoRulesAndExportsNativeFixture() {
        val source = JSONArray()
        domains.forEach { source.put(domainRule(it)) }
        val imported = SubscriptionContentParser(blockIpv6 = false).parse(profile(source).toString())
        assertEquals(1, imported.report.imported)
        assertEquals(domains, preservedDomains(JSONObject(imported.config)))
        val dir = File("build/native-configs").apply { mkdirs() }
        File(dir, "import-route-rule-preservation.json").writeText(imported.config)
    }

    @Test
    fun logicalRuleIsPreservedAsAnIndependentDeepCopy() {
        val original = JSONObject()
            .put("type", "logical").put("mode", "or")
            .put("rules", JSONArray()
                .put(JSONObject().put("domain", JSONArray().put("first.example")))
                .put(JSONObject().put("ip_cidr", JSONArray().put("198.51.100.0/24"))))
            .put("outbound", "direct")
        val root = profile(JSONArray().put(original))
        SubscriptionRouting.apply(root, SubscriptionRouting.Mode.NORMAL, blockIpv6 = false)
        val copy = objects(rules(root)).single { it.optString("type") == "logical" }
        assertTrue(copy.similar(original))
        assertNotSame(original, copy)
        copy.getJSONArray("rules").getJSONObject(0)
            .put("domain", JSONArray().put("changed.example"))
        assertEquals("first.example", original.getJSONArray("rules").getJSONObject(0)
            .getJSONArray("domain").getString(0))
    }

    @Test
    fun repeatedApplyKeepsUserRulesAndDoesNotDuplicateSystemActions() {
        val source = JSONArray()
        domains.forEach { source.put(domainRule(it)) }
        val root = profile(source)
        repeat(2) { SubscriptionRouting.apply(root, SubscriptionRouting.Mode.NORMAL, blockIpv6 = false) }
        assertEquals(domains, preservedDomains(root))
        val actions = objects(rules(root)).map { it.optString("action") }
        assertEquals(1, actions.count { it == "sniff" })
        assertEquals(1, actions.count { it == "hijack-dns" })
    }
}
