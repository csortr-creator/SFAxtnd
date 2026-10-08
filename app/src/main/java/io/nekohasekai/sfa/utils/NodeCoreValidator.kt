package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import org.json.JSONObject

/**
 * B2 production entry: Android [Libbox.checkConfig] + [NodeCoreValidationEngine].
 */
internal object NodeCoreValidator {
    private val engine =
        ThreadLocal.withInitial {
            NodeCoreValidationEngine { content -> Libbox.checkConfig(content) }
        }

    fun beginBatch() {
        engine.get().beginBatch()
    }

    fun endBatch() {
        engine.get().endBatch()
    }

    fun validateOrThrow(node: JSONObject) {
        engine.get().validateOrThrow(node)
    }

    fun evaluate(node: JSONObject): NodeCoreVerdict = engine.get().evaluate(node)
}
