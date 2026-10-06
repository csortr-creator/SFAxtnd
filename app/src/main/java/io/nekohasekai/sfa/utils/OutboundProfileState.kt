package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

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

    fun runtimeConfig(content: String, blockIpv6: Boolean, tunStack: String, optionsJson: String = "{}"): String {
        val root = JSONObject(content)
        root.optJSONObject("dns")?.remove("independent_cache")
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
        for (i in 0 until inbounds.length()) {
            inbounds.optJSONObject(i)?.takeIf { it.optString("type") == "tun" }?.put("stack", tunStack)
        }
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray()
        val updated = JSONArray()
        if (blockIpv6) updated.put(JSONObject().put("ip_cidr", JSONArray().put("::/0")).put("action", "reject"))
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val ips = rule.optJSONArray("ip_cidr")
            val ipv6Block = rule.length() == 2 && ips?.length() == 1 && ips.optString(0) == "::/0" &&
                (rule.optString("outbound") == "block" || rule.optString("action") == "reject")
            if (!ipv6Block) updated.put(rule)
        }
        route.put("rules", updated)
        ClientSettingsConfig.applyCore(root, JSONObject(optionsJson.ifBlank { "{}" }))
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
