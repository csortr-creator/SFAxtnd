package io.nekohasekai.sfa.utils

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OutboundProfileStateTest {
    private val profile = """{
      "inbounds":[{"type":"tun","tag":"tun"}],
      "experimental":{"cache_file":{"enabled":true}},
      "dns":{"servers":[{"type":"https","tag":"remote","server":"1.1.1.1","detour":"pick"}]},
      "route":{"rules":[{"domain_suffix":["ru"],"outbound":"direct"}]},
      "outbounds":[
        {"type":"selector","tag":"pick","outbounds":["a","b"],"default":"a"},
        {"type":"vless","tag":"a","server":"a.example","server_port":443,"uuid":"test","domain_resolver":"remote"},
        {"type":"hysteria2","tag":"b","server":"b.example","server_port":443,"password":"test","detour":"direct"},
        {"type":"direct","tag":"direct"},
        {"type":"urltest","tag":"auto","outbounds":["a","b"],"url":"https://example.com","interval":"5m","tolerance":50}
      ]
    }"""

    @Test fun offlineChoiceOverridesNativeCacheButOnlyForExistingMembers() {
        val updated = JSONObject(OutboundProfileState.withSelections(profile, """{"pick":"b"}"""))
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals("b", updated.getString("default"))
        assertTrue(updated.getBoolean("force_default"))
        val stale = JSONObject(OutboundProfileState.withSelections(profile, """{"pick":"removed"}"""))
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals("a", stale.getString("default"))
        assertFalse(stale.has("force_default"))
    }

    @Test fun probeHasNoVpnListenersCacheOrRoutingAndRetainsUdpProtocol() {
        val root = JSONObject(OutboundProfileState.probeConfig(profile, listOf("b")))
        assertFalse(root.has("inbounds"))
        assertFalse(root.has("experimental"))
        assertFalse(root.getJSONObject("route").has("rules"))
        val items = root.getJSONArray("outbounds")
        assertEquals(2, items.length())
        assertEquals("hysteria2", items.getJSONObject(0).getString("type"))
        assertEquals("direct", items.getJSONObject(1).getString("tag"))
        assertEquals("local", root.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("type"))
    }

    @Test fun probeDoesNotStartBackgroundAutomaticTestsOrReferToProfileDns() {
        val root = JSONObject(OutboundProfileState.probeConfig(profile, listOf("auto")))
        val items = root.getJSONArray("outbounds")
        assertEquals("selector", items.getJSONObject(0).getString("type"))
        assertFalse(items.getJSONObject(0).has("interval"))
        assertFalse(items.getJSONObject(1).has("domain_resolver"))
    }

    @Test(expected = IllegalStateException::class) fun missingServerCannotProduceFakePing() {
        OutboundProfileState.probeConfig(profile, listOf("missing"))
    }
}
