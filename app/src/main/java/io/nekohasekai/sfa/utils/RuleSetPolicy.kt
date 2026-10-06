package io.nekohasekai.sfa.utils

import org.json.JSONObject

/** Keep native remote updates alive while retaining an offline initial copy. */
object RuleSetPolicy {
    fun configure(entry: JSONObject, cachedPath: String?, intervalMs: Long) {
        if (intervalMs > 0) {
            entry.put("type", "remote").put("update_interval", "${intervalMs}ms")
            entry.remove("path")
            if (cachedPath != null) entry.put("initial_path", cachedPath)
        } else if (cachedPath != null) {
            entry.put("type", "local").put("path", cachedPath)
            listOf("url", "initial_path", "download_detour", "http_client", "update_interval")
                .forEach(entry::remove)
        } else {
            error(
                "Нет сохранённого rule-set для режима без автообновления: ${entry.optString("tag")}"
            )
        }
    }
}
