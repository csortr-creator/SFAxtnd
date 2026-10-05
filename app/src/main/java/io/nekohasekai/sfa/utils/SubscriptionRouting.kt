package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object SubscriptionRouting {

    const val NORMAL_SELECTOR_TAG = "Выбор сервера"
    const val WHITELIST_SELECTOR_TAG = "Whitelist Bypass"

    enum class Mode {
        NORMAL,
        WHITELIST_BYPASS,
    }

    private enum class ServerRoutingType {
        NORMAL,
        WHITELIST_BYPASS,
    }

    fun detectMode(content: String): Mode {
        val normalized = content.lowercase(Locale.ROOT)
        return when {
            normalized.contains("white-list") -> Mode.WHITELIST_BYPASS
            normalized.contains("whitelist") -> Mode.WHITELIST_BYPASS
            normalized.contains("white list") -> Mode.WHITELIST_BYPASS
            normalized.contains("обход белых списков") -> Mode.WHITELIST_BYPASS
            else -> Mode.NORMAL
        }
    }

    fun isWhitelistBypassTag(tag: String): Boolean = detectServerType(tag) == ServerRoutingType.WHITELIST_BYPASS

    private fun detectServerType(tag: String): ServerRoutingType {
        val normalized = tag.lowercase(Locale.ROOT).trim()
        return when {
            normalized.contains("обход") && normalized.contains("бел") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("white list") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("whitelist") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("white-list") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("bypass") && (normalized.contains("white") || normalized.contains("list")) -> ServerRoutingType.WHITELIST_BYPASS
            else -> ServerRoutingType.NORMAL
        }
    }

    fun apply(root: JSONObject, mode: Mode) {
        val route = ensureRoute(root)
        ensureRuleSet(route)

        when (mode) {
            Mode.NORMAL -> applyNormalRouting(root)
            Mode.WHITELIST_BYPASS -> applyWhitelistRouting(root)
        }
    }

    private fun ensureRoute(root: JSONObject): JSONObject {
        val route = root.optJSONObject("route") ?: JSONObject().also {
            root.put("route", it)
        }
        if (route.optJSONArray("rules") == null) {
            route.put("rules", JSONArray())
        }
        if (!route.has("default_domain_resolver")) {
            route.put("default_domain_resolver", "dns-direct")
        }
        if (!route.has("auto_detect_interface")) {
            route.put("auto_detect_interface", true)
        }
        return route
    }

    private fun ensureRuleSet(route: JSONObject) {
        // Do not inject hard-coded rule_sets. Only normalize existing remote sets.
        val existing = route.optJSONArray("rule_set") ?: return
        for (i in 0 until existing.length()) {
            val item = existing.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val url = item.optString("url").trim()
            if (type == "remote" || url.startsWith("http://") || url.startsWith("https://")) {
                if (!item.has("download_detour")) {
                    item.put("download_detour", "direct")
                }
            }
        }
        route.put("rule_set", existing)
    }

    private fun applyNormalRouting(root: JSONObject) {
        val route = ensureRoute(root)
        val oldRules = route.optJSONArray("rules") ?: JSONArray()

        val rules = JSONArray()
        appendSystemRules(rules, oldRules)
        appendNormalDirectRules(rules)

        route.put("rules", rules)
        route.put("final", NORMAL_SELECTOR_TAG)
        root.put("route", route)
    }

    private fun applyWhitelistRouting(root: JSONObject) {
        val route = ensureRoute(root)
        val oldRules = route.optJSONArray("rules") ?: JSONArray()

        val rules = JSONArray()
        appendSystemRules(rules, oldRules)
        appendWhitelistDirectRules(rules)

        route.put("rules", rules)

        val availableTags = getOutboundTags(root)
        route.put(
            "final",
            if (WHITELIST_SELECTOR_TAG in availableTags) WHITELIST_SELECTOR_TAG else NORMAL_SELECTOR_TAG,
        )
        root.put("route", route)
    }

    private fun appendSystemRules(target: JSONArray, source: JSONArray) {
        var hasSniff = false
        var hasHijackDns = false

        for (i in 0 until source.length()) {
            val rule = source.optJSONObject(i) ?: continue
            val action = rule.optString("action")
            val protocol = rule.optString("protocol")

            if (action == "sniff") hasSniff = true
            if (action == "hijack-dns" || protocol == "dns") hasHijackDns = true

            target.put(JSONObject(rule.toString()))
        }

        if (!hasSniff) {
            target.put(0, JSONObject().apply { put("action", "sniff") })
        }
        if (!hasHijackDns) {
            val dnsRule = JSONObject().apply {
                put("protocol", "dns")
                put("action", "hijack-dns")
            }
            target.put(if (hasSniff) 1 else 1, dnsRule)
        }
    }

    private fun appendCommonDirectRules(rules: JSONArray) {
        // Only settings-driven / essential system rules — no hard-coded RU/apps/domains.
        // User rules come from UserRoutingConfig (Маршруты in the app).
        if (io.nekohasekai.sfa.database.Settings.routingBlockIpv6) {
            rules.put(
                JSONObject().apply {
                    put("ip_cidr", JSONArray().apply { put("::/0") })
                    put("outbound", "block")
                },
            )
        }
        rules.put(
            JSONObject().apply {
                put("ip_is_private", true)
                put("outbound", "direct")
            },
        )
    }

    private fun appendNormalDirectRules(rules: JSONArray) {
        appendCommonDirectRules(rules)
    }

    private fun appendWhitelistDirectRules(rules: JSONArray) {
        appendCommonDirectRules(rules)
    }

    private fun getOutboundTags(root: JSONObject): Set<String> {
        val tags = mutableSetOf<String>()
        val outbounds = root.optJSONArray("outbounds") ?: return tags
        for (i in 0 until outbounds.length()) {
            val tag = outbounds.optJSONObject(i)?.optString("tag")?.trim()
            if (!tag.isNullOrBlank()) tags.add(tag)
        }
        return tags
    }
}
