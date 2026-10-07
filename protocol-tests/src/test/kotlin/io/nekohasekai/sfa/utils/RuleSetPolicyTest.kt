package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RuleSetPolicyTest {
    private fun entry() =
        JSONObject(
            """{"type":"remote","tag":"test","format":"binary","url":"https://example.org/test.srs","download_detour":"direct"}"""
        )

    @Test
    fun downloadsRetryThroughSelectedProxyAndDisabledUpdatesRemoveFallback() {
        val root = JSONObject(UserRoutingConfig.applyToConfig(
            """{"outbounds":[{"type":"selector","tag":"proxy","outbounds":["server"]},{"type":"vless","tag":"server"}],"route":{"rule_set":[{"type":"remote","tag":"test","format":"binary","url":"https://example.org/test.srs"}]}}""", "{}"))
        val e = root.getJSONObject("route").getJSONArray("rule_set").getJSONObject(0)
        assertEquals("direct", e.getString("download_detour"))
        assertEquals("proxy", e.getString("download_fallback_detour"))
        RuleSetPolicy.configure(e, "/cache/test.srs", -1)
        assertFalse(e.has("download_fallback_detour"))
    }

    @Test
    fun liveRemoteTimerKeepsCachedInitialCopy() {
        val e = entry()
        RuleSetPolicy.configure(e, "/cache/rules.srs", 1800000)
        assertEquals("remote", e.getString("type"))
        assertEquals("1800000ms", e.getString("update_interval"))
        assertEquals("/cache/rules.srs", e.getString("initial_path"))
        assertTrue(e.has("url"))
        assertFalse(e.has("path"))
    }

    @Test
    fun disabledUpdatesUseLocalCopyWithoutRemoteOnlyFields() {
        val e = entry()
        RuleSetPolicy.configure(e, "/cache/rules.srs", -1)
        assertEquals("local", e.getString("type"))
        assertEquals("/cache/rules.srs", e.getString("path"))
        assertFalse(e.has("url"))
        assertFalse(e.has("download_detour"))
    }

    @Test(expected = IllegalStateException::class)
    fun disabledUpdatesNeedAnInitialCopy() {
        RuleSetPolicy.configure(entry(), null, -1)
    }

    @Test
    fun aliasesNormalizeToDefinedSrsTags() {
        val root =
            JSONObject(
                UserRoutingConfig.applyToConfig(
                    """{"outbounds":[{"type":"direct","tag":"direct"}]}""",
                    """{"rules":[{"ruleSet":"geosite:category-ru,geoip:ru","outbound":"direct"}]}""",
                )
            )
        val tags =
            root
                .getJSONObject("route")
                .getJSONArray("rules")
                .getJSONObject(0)
                .getJSONArray("rule_set")
        assertEquals("geosite-category-ru", tags.getString(0))
        assertEquals("geoip-ru", tags.getString(1))
        val entries = root.getJSONObject("route").getJSONArray("rule_set")
        assertEquals(tags.getString(0), entries.getJSONObject(0).getString("tag"))
    }

    @Test
    fun bundledPresetsAreValidNativeRemoteInitialCopies() {
        val sets = JSONArray()
        for ((tag, url) in RoutingPresets.remoteRuleSets) {
            val file = File("../app/src/main/assets/rule_sets/$tag.srs").canonicalFile
            assertTrue(file.isFile)
            val e = entry().put("tag", tag).put("url", url)
            RuleSetPolicy.configure(e, file.absolutePath, 86400000)
            sets.put(e)
        }
        val root =
            JSONObject()
                .put(
                    "outbounds",
                    JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct")),
                )
                .put("route", JSONObject().put("rule_set", sets))
        val target = File("build/native-configs").apply { mkdirs() }
        File(target, "bundled-remote-initial.json").writeText(root.toString(2))
        for (i in 0 until sets.length()) {
            val entry = sets.getJSONObject(i)
            RuleSetPolicy.configure(entry, entry.getString("initial_path"), -1)
        }
        File(target, "bundled-local.json").writeText(root.toString(2))
    }
}
