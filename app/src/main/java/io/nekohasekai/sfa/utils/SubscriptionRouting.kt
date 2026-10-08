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

    fun apply(root: JSONObject, mode: Mode, blockIpv6: Boolean = true) {
        val route = ensureRoute(root)
        // route.default_domain_resolver requires a matching dns.servers[].tag (sing-box 1.14+).
        // Panel sing-box JSON (e.g. without a dns block) would otherwise fail checkConfig with
        // "default domain resolver not found: dns-direct".
        ensureDefaultDomainResolverAvailable(root)
        ensureRuleSet(route)

        when (mode) {
            Mode.NORMAL -> applyNormalRouting(root, blockIpv6)
            Mode.WHITELIST_BYPASS -> applyWhitelistRouting(root, blockIpv6)
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

    /**
     * Ensures [route.default_domain_resolver] resolves to an existing DNS server tag.
     * If missing, registers a `local` server under that tag (import-time safe bootstrap).
     */
    internal fun ensureDefaultDomainResolverAvailable(root: JSONObject) {
        val route = root.optJSONObject("route") ?: return
        val tag = defaultDomainResolverTag(route) ?: return
        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
        val servers = dns.optJSONArray("servers") ?: JSONArray().also { dns.put("servers", it) }
        for (i in 0 until servers.length()) {
            val server = servers.optJSONObject(i) ?: continue
            if (server.optString("tag") == tag) return
        }
        servers.put(
            JSONObject()
                .put("type", "local")
                .put("tag", tag),
        )
        dns.put("servers", servers)
        root.put("dns", dns)
    }

    private fun defaultDomainResolverTag(route: JSONObject): String? {
        if (!route.has("default_domain_resolver")) return null
        return when (val value = route.get("default_domain_resolver")) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is JSONObject -> value.optString("server").trim().takeIf { it.isNotEmpty() }
            else -> null
        }
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

    private fun applyNormalRouting(root: JSONObject, blockIpv6: Boolean) {
        val route = ensureRoute(root)
        val oldRules = route.optJSONArray("rules") ?: JSONArray()

        val rules = JSONArray()
        appendSystemRules(rules, oldRules)
        appendNormalDirectRules(rules, blockIpv6)

        route.put("rules", rules)
        route.put("final", NORMAL_SELECTOR_TAG)
        root.put("route", route)
    }

    private fun applyWhitelistRouting(root: JSONObject, blockIpv6: Boolean) {
        val route = ensureRoute(root)
        val oldRules = route.optJSONArray("rules") ?: JSONArray()

        val rules = JSONArray()
        appendSystemRules(rules, oldRules)
        appendWhitelistDirectRules(rules, blockIpv6)

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

    private fun appendCommonDirectRules(rules: JSONArray, blockIpv6: Boolean) {
        // Only settings-driven / essential system rules — no hard-coded RU/apps/domains.
        // User rules come from UserRoutingConfig (Маршруты in the app).
        if (blockIpv6) {
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

    private fun appendNormalDirectRules(rules: JSONArray, blockIpv6: Boolean) {
        appendCommonDirectRules(rules, blockIpv6)
    }

    private fun appendWhitelistDirectRules(rules: JSONArray, blockIpv6: Boolean) {
        appendCommonDirectRules(rules, blockIpv6)
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
