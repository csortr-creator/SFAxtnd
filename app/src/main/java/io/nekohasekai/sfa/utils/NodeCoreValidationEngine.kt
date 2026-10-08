package io.nekohasekai.sfa.utils

import org.json.JSONObject

/**
 * B2 per-node core validation engine (injectable checkConfig for tests).
 *
 * Skip (throw) **only** on [NodeCoreVerdict.NODE_INVALID] after a green family control.
 * HARNESS / UNKNOWN / CORE_UNAVAILABLE never auto-skip.
 */
internal class NodeCoreNodeInvalidException(
    val reasonCode: String,
) : Exception("node_core_invalid:$reasonCode")

internal class NodeCoreValidationEngine(
    private val checkConfig: (String) -> Unit,
) {
    private val familyControlOk = mutableMapOf<String, Boolean>()

    fun beginBatch() {
        familyControlOk.clear()
    }

    fun endBatch() {
        familyControlOk.clear()
    }

    fun evaluate(node: JSONObject): NodeCoreVerdict {
        val snapshot = node.toString()
        val family = NodeValidationCompare.familyOf(node)
        val controlPassed = familyControlOk.getOrPut(family) { runFamilyControl(family) }
        val harness =
            try {
                NodeCoreHarness.build(node)
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                return NodeCoreVerdict.HARNESS_INVALID
            }
        check(node.toString() == snapshot) { "NodeCore harness must not mutate node JSON" }

        return try {
            checkConfig(harness)
            NodeCoreVerdict.VALID
        } catch (e: UnsatisfiedLinkError) {
            NodeCoreVerdict.CORE_UNAVAILABLE
        } catch (e: NoClassDefFoundError) {
            NodeCoreVerdict.CORE_UNAVAILABLE
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            NodeCoreHarness.classify(
                errorMessage = e.message,
                controlPassed = controlPassed,
                expectedHarnessFailure = false,
            )
        }
    }

    fun validateOrThrow(node: JSONObject) {
        val snapshot = node.toString()
        val family = NodeValidationCompare.familyOf(node)
        val controlPassed = familyControlOk.getOrPut(family) { runFamilyControl(family) }
        val harness =
            try {
                NodeCoreHarness.build(node)
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                return
            }
        check(node.toString() == snapshot) { "NodeCore harness must not mutate node JSON" }
        try {
            checkConfig(harness)
            return
        } catch (e: UnsatisfiedLinkError) {
            return
        } catch (e: NoClassDefFoundError) {
            return
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            val verdict =
                NodeCoreHarness.classify(
                    errorMessage = e.message,
                    controlPassed = controlPassed,
                    expectedHarnessFailure = false,
                )
            if (verdict == NodeCoreVerdict.NODE_INVALID) {
                throw NodeCoreNodeInvalidException(
                    NodeValidationCompare.mapReason(e.message ?: "other"),
                )
            }
            return
        }
    }

    private fun runFamilyControl(family: String): Boolean {
        if (family == "invalid") return false
        val control =
            when (family) {
                "vless-xhttp" -> NodeCoreHarness.controlVlessXhttp()
                "hysteria2" -> NodeCoreHarness.controlHysteria2()
                else -> NodeCoreHarness.controlVlessRealityDomain()
            }
        return try {
            checkConfig(NodeCoreHarness.build(control))
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: NoClassDefFoundError) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
