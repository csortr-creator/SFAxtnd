package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 test-only dual validation. Emits configs for CI `sing-box check`; does not call production import.
 */
class NodeValidationCompareTest {

    private fun compareDir(): File = File("build/node-core-compare").apply { mkdirs() }

    private fun writePair(index: Int, node: JSONObject) {
        val dir = compareDir()
        File(dir, "%03d-old.json".format(index)).writeText(
            JSONObject(NodeValidationCompare.oldScaffoldConfig(node)).toString(2),
        )
        val t0 = System.nanoTime()
        val harness = NodeValidationCompare.newHarnessConfig(node)
        val buildMs = (System.nanoTime() - t0) / 1_000_000
        File(dir, "%03d-new.json".format(index)).writeText(JSONObject(harness).toString(2))
        File(dir, "%03d-meta.txt".format(index)).writeText(
            "index=$index family=${NodeValidationCompare.familyOf(node)} harnessBuildMs=$buildMs\n",
        )
    }

    /** Structural baseNodes before any core validation (synthetic; not production parse). */
    private fun goldenBaseNodes(): List<JSONObject> =
        listOf(
            NodeCoreHarness.controlVlessRealityDomain(),
            NodeCoreHarness.controlVlessRealityIp(),
            NodeCoreHarness.controlVlessXhttp(),
            NodeCoreHarness.controlHysteria2(),
            NodeCoreHarness.controlVlessWithDomainResolver(),
            NodeCoreHarness.invalidUnknownType(),
            NodeCoreHarness.invalidWrongFieldType(),
        )

    @Test
    fun baseNodesPreserveFieldsThroughHarnessCopy() {
        val original = NodeCoreHarness.controlVlessRealityDomain()
        val before = original.toString()
        NodeCoreHarness.build(original)
        assertEquals("harness must not mutate caller node", before, original.toString())
        val reality =
            JSONObject(NodeValidationCompare.newHarnessConfig(original))
                .getJSONArray("outbounds")
                .getJSONObject(0)
                .getJSONObject("tls")
                .getJSONObject("reality")
        assertTrue(reality.getBoolean("enabled"))
        assertEquals("abcd", reality.getString("short_id"))
    }

    @Test
    fun parserWithNoopValidateNodeDoesNotInvokeCore() {
        var coreCalls = 0
        val line =
            "vless://11111111-1111-4111-8111-111111111111@example.org:443" +
                "?type=tcp&security=reality&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "&sid=abcd&sni=www.example.com&fp=chrome#fixture"
        val result =
            SubscriptionContentParser(validateNode = { coreCalls++ }).parse(line)
        assertEquals(0, coreCalls)
        assertTrue(result.report.imported >= 1)
    }

    @Test
    fun emitGoldenComparePairsForCi() {
        goldenBaseNodes().forEachIndexed { i, node -> writePair(i, node) }
        // broken harness fixture (expect new harness path issues on missing dns)
        File(compareDir(), "broken-harness-new.json").writeText(
            JSONObject(
                NodeCoreHarness.buildMissingDnsTag(NodeCoreHarness.controlVlessRealityDomain()),
            ).toString(2),
        )
        File(compareDir(), "broken-harness-old.json").writeText(
            JSONObject(
                NodeValidationCompare.oldScaffoldConfig(NodeCoreHarness.controlVlessRealityDomain()),
            ).toString(2),
        )
    }

    @Test
    fun emitNinetyNinePlusOne() {
        val nodes = MutableList(99) { idx ->
            NodeCoreHarness.controlVlessRealityDomain().put("tag", "n-$idx")
        }
        nodes.add(NodeCoreHarness.invalidUnknownType())
        nodes.forEachIndexed { i, n -> writePair(100 + i, n) }
        File(compareDir(), "count-99-1.txt").writeText("valid=99 invalid=1 total=${nodes.size}\n")
    }

    @Test
    fun offlineDiffRulesNewOnlyNodeFails() {
        val sample =
            NodeValidationSample(
                index = 0,
                family = "vless-reality",
                oldVerdict = OldNodeVerdict.PASS,
                newVerdict = NodeCoreVerdict.NODE_INVALID,
                reasonCode = "unknown_type",
                oldCheckMs = 1,
                newCheckMs = 1,
                harnessBuildMs = 0,
            )
        assertEquals("NEW_ONLY_NODE", NodeValidationCompare.diffCategory(sample))
        try {
            NodeValidationCompare.assertNoNewOnlyNode(listOf(sample))
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun offlineAgreePassAndAgreeFail() {
        assertEquals(
            "AGREE_PASS",
            NodeValidationCompare.diffCategory(
                NodeValidationSample(
                    0, "vless-reality", OldNodeVerdict.PASS, NodeCoreVerdict.UNKNOWN,
                    "pass", 0, 0, 0,
                ),
            ),
        )
        assertEquals(
            "AGREE_FAIL",
            NodeValidationCompare.diffCategory(
                NodeValidationSample(
                    1, "invalid", OldNodeVerdict.FAIL, NodeCoreVerdict.NODE_INVALID,
                    "unknown_type", 0, 0, 0,
                ),
            ),
        )
        assertFalse(
            NodeValidationCompare.diffCategory(
                NodeValidationSample(
                    2, "x", OldNodeVerdict.PASS, NodeCoreVerdict.HARNESS_INVALID,
                    "dns_resolver", 0, 0, 0,
                ),
            ) == "AGREE_PASS",
        )
    }

    @Test
    fun sampleFromCheckResultsMapsErrors() {
        val node = NodeCoreHarness.invalidUnknownType()
        val s =
            NodeValidationCompare.sampleFromCheckResults(
                index = 0,
                node = node,
                oldErr = "unknown outbound type",
                newErr = "unknown outbound type",
                familyControlPassed = true,
                oldCheckMs = 2,
                newCheckMs = 3,
                harnessBuildMs = 1,
            )
        assertEquals(OldNodeVerdict.FAIL, s.oldVerdict)
        assertEquals(NodeCoreVerdict.NODE_INVALID, s.newVerdict)
        assertEquals("AGREE_FAIL", NodeValidationCompare.diffCategory(s))
    }
}
