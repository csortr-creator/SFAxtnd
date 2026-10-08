package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B0 tests: harness JSON generation + classification.
 *
 * Real `sing-box check` is performed in CI on files written under
 * `protocol-tests/build/native-configs/` (must PASS) and
 * `protocol-tests/build/native-configs-expect-fail/` (must FAIL).
 */
class NodeCoreHarnessTest {

    private fun nativeDir(name: String): File =
        File("build/$name").apply { mkdirs() }

    private fun writePass(name: String, json: String) {
        File(nativeDir("native-configs"), name).writeText(JSONObject(json).toString(2))
    }

    private fun writeFail(name: String, json: String) {
        File(nativeDir("native-configs-expect-fail"), name).writeText(JSONObject(json).toString(2))
    }

    @Test
    fun harnessContainsDnsDirectAndRouteResolver() {
        val json = NodeCoreHarness.build(NodeCoreHarness.controlVlessRealityDomain())
        val root = JSONObject(json)
        val tags =
            (0 until root.getJSONObject("dns").getJSONArray("servers").length()).map {
                root.getJSONObject("dns").getJSONArray("servers").getJSONObject(it).getString("tag")
            }
        assertTrue(NodeCoreHarness.DNS_DIRECT in tags)
        assertEquals(
            NodeCoreHarness.DNS_DIRECT,
            root.getJSONObject("route").getString("default_domain_resolver"),
        )
        assertFalse("harness must not invent tun inbound", root.has("inbounds") && root.getJSONArray("inbounds").length() > 0)
    }

    @Test
    fun doesNotInventTlsFieldsOnBareOutbound() {
        val bare =
            JSONObject()
                .put("type", "vless")
                .put("tag", "bare")
                .put("server", "example.org")
                .put("server_port", 443)
                .put("uuid", "11111111-1111-4111-8111-111111111111")
        val out =
            JSONObject(NodeCoreHarness.build(bare))
                .getJSONArray("outbounds")
                .getJSONObject(0)
        assertFalse(out.has("tls"))
        assertFalse(out.has("transport"))
    }

    @Test
    fun emitControlFixturesForCoreCheck() {
        writePass("harness-vless-reality-domain.json", NodeCoreHarness.build(NodeCoreHarness.controlVlessRealityDomain()))
        writePass("harness-vless-reality-ip.json", NodeCoreHarness.build(NodeCoreHarness.controlVlessRealityIp()))
        writePass("harness-vless-xhttp.json", NodeCoreHarness.build(NodeCoreHarness.controlVlessXhttp()))
        writePass("harness-hysteria2.json", NodeCoreHarness.build(NodeCoreHarness.controlHysteria2()))
        writePass(
            "harness-vless-domain-resolver.json",
            NodeCoreHarness.build(NodeCoreHarness.controlVlessWithDomainResolver()),
        )
    }

    @Test
    fun emitExpectFailFixturesForCoreCheck() {
        writeFail(
            "harness-unknown-type.json",
            NodeCoreHarness.build(NodeCoreHarness.invalidUnknownType()),
        )
        writeFail(
            "harness-wrong-field-type.json",
            NodeCoreHarness.build(NodeCoreHarness.invalidWrongFieldType()),
        )
        writeFail(
            "harness-missing-dns-tag.json",
            NodeCoreHarness.buildMissingDnsTag(NodeCoreHarness.controlVlessRealityDomain()),
        )
    }

    @Test
    fun classifyHarnessDnsError() {
        val v =
            NodeCoreHarness.classify(
                "default domain resolver not found: dns-direct",
                controlPassed = true,
            )
        assertEquals(NodeCoreVerdict.HARNESS_INVALID, v)
    }

    @Test
    fun classifyUnknownTypeAsNodeWhenControlPassed() {
        val v =
            NodeCoreHarness.classify(
                "unknown outbound type: not-a-real-outbound-type",
                controlPassed = true,
            )
        assertEquals(NodeCoreVerdict.NODE_INVALID, v)
    }

    @Test
    fun classifyNeverNodeWhenControlFailed() {
        val v =
            NodeCoreHarness.classify(
                "unknown outbound type: not-a-real-outbound-type",
                controlPassed = false,
            )
        assertEquals(NodeCoreVerdict.UNKNOWN, v)
    }

    @Test
    fun classifyAmbiguousTlsAsUnknown() {
        val v =
            NodeCoreHarness.classify(
                "tls: handshake failure",
                controlPassed = true,
            )
        assertEquals(NodeCoreVerdict.UNKNOWN, v)
    }

    @Test
    fun measureHarnessBuildCostNoNetwork() {
        val node = NodeCoreHarness.controlVlessRealityDomain()
        fun timed(n: Int): Long {
            val t0 = System.nanoTime()
            repeat(n) { NodeCoreHarness.build(node) }
            return (System.nanoTime() - t0) / 1_000_000
        }
        val one = timed(1)
        val ten = timed(10)
        val hundred = timed(100)
        val fiveHundred = timed(500)
        // Publish timings in assertion message for CI logs (build-only; no checkConfig here).
        assertTrue(
            "harness build ms: 1=$one 10=$ten 100=$hundred 500=$fiveHundred",
            fiveHundred >= 0,
        )
        println(
            "NodeCoreHarness build-only cost (ms, no network, no checkConfig): " +
                "1=$one 10=$ten 100=$hundred 500=$fiveHundred",
        )
    }
}
