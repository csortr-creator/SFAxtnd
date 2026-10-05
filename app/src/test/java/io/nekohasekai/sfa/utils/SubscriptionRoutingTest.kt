package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import io.nekohasekai.sfa.database.Settings as SFASettings

class SubscriptionRoutingTest {

    @Test
    fun testIpv6BlockRuleIncludedWhenSettingIsTrue() {
        mockStatic(SFASettings::class.java).use { mockedSettings ->
            mockedSettings.`when`<Boolean> { SFASettings.routingBlockIpv6 }.thenReturn(true)

            val root = JSONObject()
            SubscriptionRouting.apply(root, SubscriptionRouting.Mode.NORMAL)

            val rules = root.getJSONObject("route").getJSONArray("rules")
            var hasIpv6Block = false
            for (i in 0 until rules.length()) {
                val rule = rules.optJSONObject(i) ?: continue
                if (rule.optJSONArray("ip_cidr")?.optString(0) == "::/0" && rule.optString("outbound") == "block") {
                    hasIpv6Block = true
                    break
                }
            }
            assertTrue(hasIpv6Block)
        }
    }

    @Test
    fun testIpv6BlockRuleExcludedWhenSettingIsFalse() {
        mockStatic(SFASettings::class.java).use { mockedSettings ->
            mockedSettings.`when`<Boolean> { SFASettings.routingBlockIpv6 }.thenReturn(false)

            val root = JSONObject()
            SubscriptionRouting.apply(root, SubscriptionRouting.Mode.NORMAL)

            val rules = root.getJSONObject("route").getJSONArray("rules")
            var hasIpv6Block = false
            for (i in 0 until rules.length()) {
                val rule = rules.optJSONObject(i) ?: continue
                if (rule.optJSONArray("ip_cidr")?.optString(0) == "::/0" && rule.optString("outbound") == "block") {
                    hasIpv6Block = true
                    break
                }
            }
            assertFalse(hasIpv6Block)
        }
    }
}
