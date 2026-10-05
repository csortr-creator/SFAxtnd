package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

/** Pure config transformations shared by the offline list, probes and VPN startup. */
object OutboundProfileState {
    fun withSelections(content: String, selections: String): String {
        val root = JSONObject(content)
        val selected = JSONObject(selections.ifBlank { "{}" })
        val outbounds = root.optJSONArray("outbounds") ?: return content
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            if (outbound.optString("type") != "selector") continue
            val tag = selected.optString(outbound.optString("tag"))
            val members = outbound.optJSONArray("outbounds") ?: continue
            if ((0 until members.length()).any { members.optString(it) == tag }) {
                outbound.put("default", tag).put("force_default", true)
            }
        }
        return root.toString()
    }

    fun probeConfig(content: String, tags: Collection<String>): String {
        val original = JSONObject(content)
        val source = original.optJSONArray("outbounds") ?: error("В подписке нет серверов")
        val byTag = (0 until source.length()).mapNotNull { source.optJSONObject(it) }
            .associateBy { it.optString("tag") }
        val wanted = linkedSetOf<String>()
        fun include(tag: String) {
            if (!wanted.add(tag)) return
            val item = byTag[tag] ?: error("Сервер $tag не найден в подписке")
            item.optString("detour").takeIf { it.isNotBlank() }?.let(::include)
            val members = item.optJSONArray("outbounds")
            if (members != null) for (i in 0 until members.length()) include(members.getString(i))
        }
        tags.forEach(::include)
        val outbounds = JSONArray()
        wanted.forEach { tag ->
            val item = JSONObject(byTag.getValue(tag).toString())
            if (item.optString("type") == "urltest") {
                item.put("type", "selector")
                listOf("url", "interval", "tolerance", "idle_timeout").forEach(item::remove)
            }
            item.remove("domain_resolver")
            item.remove("force_default")
            outbounds.put(item)
        }
        return JSONObject()
            .put("outbounds", outbounds)
            .put("dns", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("type", "local").put("tag", "probe-dns"))))
            .put("route", JSONObject().put("default_domain_resolver", "probe-dns").put("auto_detect_interface", true))
            .apply {
                original.optJSONObject("certificate")?.let { put("certificate", it) }
                original.optJSONArray("certificate_providers")?.let { put("certificate_providers", it) }
            }.toString()
    }
}
