package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * B1 — Test-only dual evaluation of old per-node scaffold vs [NodeCoreHarness].
 *
 * Not used by production [HTTPClient.parseSubscription]. Does not skip servers.
 */
enum class OldNodeVerdict {
    PASS,
    FAIL,
}

data class NodeValidationSample(
    val index: Int,
    val family: String,
    val oldVerdict: OldNodeVerdict,
    val newVerdict: NodeCoreVerdict,
    /** Whitelisted short code only — never URL/UUID/tag text from user data. */
    val reasonCode: String,
    val oldCheckMs: Long,
    val newCheckMs: Long,
    val harnessBuildMs: Long,
) {
    val category: String
        get() =
            when {
                oldVerdict == OldNodeVerdict.PASS && newVerdict == NodeCoreVerdict.UNKNOWN &&
                    reasonCode == "pass" -> "AGREE_PASS"
                // new PASS is modeled as UNKNOWN with reason pass in offline tests;
                // with real core, reasonCode "pass" means check succeeded.
                oldVerdict == OldNodeVerdict.PASS && reasonCode == "pass" -> "AGREE_PASS"
                oldVerdict == OldNodeVerdict.FAIL && newVerdict == NodeCoreVerdict.NODE_INVALID ->
                    "AGREE_FAIL"
                oldVerdict == OldNodeVerdict.FAIL && reasonCode == "pass" -> "OLD_ONLY_FAIL"
                oldVerdict == OldNodeVerdict.PASS && newVerdict == NodeCoreVerdict.NODE_INVALID ->
                    "NEW_ONLY_NODE"
                newVerdict == NodeCoreVerdict.HARNESS_INVALID -> "HARNESS"
                newVerdict == NodeCoreVerdict.UNKNOWN && reasonCode != "pass" -> "AMBIGUOUS"
                else -> "OTHER"
            }
}

object NodeValidationCompare {

    /** Exact production old scaffold from [HTTPClient.parseSubscription] validateNode lambda. */
    fun oldScaffoldConfig(node: JSONObject): String {
        val copy = JSONObject(node.toString())
        return JSONObject()
            .put("outbounds", JSONArray().put(copy))
            .put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray()
                            .put(JSONObject().put("type", "local").put("tag", "dns-direct")),
                    ),
            )
            .toString()
    }

    fun newHarnessConfig(node: JSONObject): String = NodeCoreHarness.build(node)

    fun familyOf(node: JSONObject): String {
        val type = node.optString("type")
        if (type == "hysteria2" || type == "hysteria") return "hysteria2"
        if (type == "vless") {
            val transport = node.optJSONObject("transport")?.optString("type").orEmpty()
            if (transport == "xhttp") return "vless-xhttp"
            val reality = node.optJSONObject("tls")?.optJSONObject("reality")
            if (reality?.optBoolean("enabled") == true) return "vless-reality"
            return "vless-other"
        }
        if (type.isBlank() || type == "not-a-real-outbound-type") return "invalid"
        return type
    }

    /**
     * Build sample metadata after external core checks supplied [oldErr]/[newErr]
     * (null = check passed).
     */
    fun sampleFromCheckResults(
        index: Int,
        node: JSONObject,
        oldErr: String?,
        newErr: String?,
        familyControlPassed: Boolean,
        oldCheckMs: Long,
        newCheckMs: Long,
        harnessBuildMs: Long,
    ): NodeValidationSample {
        val family = familyOf(node)
        val oldVerdict = if (oldErr == null) OldNodeVerdict.PASS else OldNodeVerdict.FAIL
        val newVerdict =
            when {
                newErr == null -> NodeCoreVerdict.UNKNOWN // PASS represented via reasonCode
                else ->
                    NodeCoreHarness.classify(
                        newErr,
                        controlPassed = familyControlPassed,
                        expectedHarnessFailure = false,
                    )
            }
        val reasonCode =
            when {
                newErr == null && oldErr == null -> "pass"
                newErr == null && oldErr != null -> "old_fail_new_pass"
                newErr != null && oldErr == null -> mapReason(newErr)
                else -> mapReason(newErr ?: oldErr)
            }
        val effectiveNew =
            if (newErr == null) {
                // Encode PASS without adding a PASS enum (B0 enum kept): use UNKNOWN+pass
                NodeCoreVerdict.UNKNOWN
            } else {
                newVerdict
            }
        return NodeValidationSample(
            index = index,
            family = family,
            oldVerdict = oldVerdict,
            newVerdict = effectiveNew,
            reasonCode = reasonCode,
            oldCheckMs = oldCheckMs,
            newCheckMs = newCheckMs,
            harnessBuildMs = harnessBuildMs,
        )
    }

    fun mapReason(err: String): String {
        val m = err.lowercase()
        return when {
            "default domain resolver not found" in m -> "dns_resolver"
            "unknown outbound type" in m || "unsupported outbound" in m -> "unknown_type"
            "missing required" in m || "required field" in m -> "required_field"
            "invalid" in m && "type" in m -> "invalid_type"
            "dns" in m && "not found" in m -> "dns_resolver"
            else -> "other"
        }
    }

    /** Category helper treating reasonCode pass as new PASS for diff rules. */
    fun diffCategory(sample: NodeValidationSample): String {
        val newPass = sample.reasonCode == "pass" || sample.reasonCode == "old_fail_new_pass"
        val newNode = sample.newVerdict == NodeCoreVerdict.NODE_INVALID && !newPass
        return when {
            sample.oldVerdict == OldNodeVerdict.PASS && newPass -> "AGREE_PASS"
            sample.oldVerdict == OldNodeVerdict.FAIL && newNode -> "AGREE_FAIL"
            sample.oldVerdict == OldNodeVerdict.FAIL && newPass -> "OLD_ONLY_FAIL"
            sample.oldVerdict == OldNodeVerdict.PASS && newNode -> "NEW_ONLY_NODE"
            sample.newVerdict == NodeCoreVerdict.HARNESS_INVALID -> "HARNESS"
            !newPass && sample.newVerdict == NodeCoreVerdict.UNKNOWN -> "AMBIGUOUS"
            else -> "OTHER"
        }
    }

    fun assertNoNewOnlyNode(samples: List<NodeValidationSample>) {
        val bad = samples.filter { diffCategory(it) == "NEW_ONLY_NODE" }
        require(bad.isEmpty()) {
            "NEW_ONLY_NODE at indices=${bad.map { it.index }} (false node rejects)"
        }
    }
}
