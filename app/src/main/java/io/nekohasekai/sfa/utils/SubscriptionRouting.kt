package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object SubscriptionRouting {

    const val NORMAL_SELECTOR_TAG = "Выбор сервера"
    const val WHITELIST_SELECTOR_TAG = "Whitelist Bypass"

    enum class Mode {
        NORMAL,
        WHITELIST_BYPASS
    }

    private enum class ServerRoutingType {
        NORMAL,
        WHITELIST_BYPASS
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

    fun isWhitelistBypassTag(tag: String): Boolean {
        return detectServerType(tag) == ServerRoutingType.WHITELIST_BYPASS
    }

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
        val existing = route.optJSONArray("rule_set") ?: JSONArray()
        val existingTags = mutableSetOf<String>()
        for (i in 0 until existing.length()) {
            existing.optJSONObject(i)?.optString("tag")?.let { existingTags.add(it) }
        }

        if ("geosite-category-ru" !in existingTags) {
            existing.put(JSONObject().apply {
                put("tag", "geosite-category-ru")
                put("type", "remote")
                put("format", "binary")
                put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs")
                put("download_detour", NORMAL_SELECTOR_TAG)
            })
        }
        if ("geoip-ru" !in existingTags) {
            existing.put(JSONObject().apply {
                put("tag", "geoip-ru")
                put("type", "remote")
                put("format", "binary")
                put("url", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs")
                put("download_detour", NORMAL_SELECTOR_TAG)
            })
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
            if (WHITELIST_SELECTOR_TAG in availableTags) WHITELIST_SELECTOR_TAG else NORMAL_SELECTOR_TAG
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
        rules.put(JSONObject().apply {
            put("ip_cidr", JSONArray().apply { put("::/0") })
            put("outbound", "block")
        })
        rules.put(JSONObject().apply {
            put("ip_is_private", true)
            put("outbound", "direct")
        })
        rules.put(JSONObject().apply {
            put("package_name", JSONArray().apply {
                put("ru.vk.store")
                put("com.vk.store")
                put("com.android.vending")
                put("com.google.android.gms")
                put("com.google.android.gsf")
            })
            put("outbound", "direct")
        })
        rules.put(JSONObject().apply {
            put("domain_keyword", JSONArray().apply {
                put("tiktok")
                put("mobilelegend")
                put("moonton")
            })
            put("outbound", "direct")
        })
        rules.put(JSONObject().apply {
            put("domain_suffix", JSONArray().apply {
                put("connectivitycheck.gstatic.com")
                put("connectivitycheck.android.com")
                put("clients3.google.com")
                put("msftconnecttest.com")
            })
            put("outbound", "direct")
        })
        rules.put(JSONObject().apply {
            put("protocol", JSONArray().apply { put("bittorrent") })
            put("outbound", "direct")
        })
        rules.put(JSONObject().apply {
            put("rule_set", JSONArray().apply {
                put("geosite-category-ru")
                put("geoip-ru")
            })
            put("outbound", "direct")
        })
        rules.put(JSONObject().apply {
            put("domain_suffix", JSONArray().apply {
                put(".ru"); put(".su"); put(".xn--p1ai"); put(".by"); put(".kz")
                put("xn--90aifd0aza.site")
                put("sberbank.com")
                put("alfa-bank.com")
                put("alfabank.com")
                put("vtb.com")
                put("yandex.net")
                put("yastatic.net")
                put("yastat.net")
                put("vk.me")
                put("vk.cc")
                put("userapi.com")
                put("mradx.net")
                put("ozon.app")
                put("ozonusercontent.com")
                put("ozon.travel")
                put("avito.st")
                put("cdek.shopping")
                put("okko.tv")
                put("okko.sport")
                put("whoosh.bike")
            })
            put("outbound", "direct")
        })
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
