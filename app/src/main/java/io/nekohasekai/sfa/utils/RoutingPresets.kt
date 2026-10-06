package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.models.RoutingRule
import org.json.JSONArray
import org.json.JSONObject

object RoutingPresets {
    const val WHITELIST_DOMAINS = "ru-whitelist-domains"
    const val WHITELIST_IPS = "ru-whitelist-ips"
    const val RF_DOMAINS = "geosite-rf-direct"
    const val RF_IPS = "geoip-rf-direct"
    val whitelistTags = setOf(WHITELIST_DOMAINS, WHITELIST_IPS)
    val rfTags = setOf(RF_DOMAINS, RF_IPS)
    val remoteRuleSets =
        mapOf(
            WHITELIST_DOMAINS to
                "https://raw.githubusercontent.com/hydraponique/roscomvpn-geosite/master/release/sing-box/whitelist.srs",
            WHITELIST_IPS to
                "https://raw.githubusercontent.com/hydraponique/roscomvpn-geoip/master/release/sing-box/whitelist.srs",
            RF_DOMAINS to
                "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs",
            RF_IPS to "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
        )

    fun tags(raw: String) =
        raw.split(',', ';', '\n').map(String::trim).filter(String::isNotBlank).toSet()

    fun isWhitelist(rule: RoutingRule) = tags(rule.ruleSet).any { it in whitelistTags }

    fun isRf(rule: RoutingRule) =
        rule.name in setOf("ru-sites", "ru-ip", "РФ напрямую") ||
            tags(rule.ruleSet).any { it in rfTags }

    private fun preset(whitelist: Boolean) =
        RoutingRule(
            name = if (whitelist) "Whitelist для России" else "РФ напрямую",
            ruleSet = (if (whitelist) whitelistTags else rfTags).joinToString(","),
            outbound = RoutingRule.OUTBOUND_DIRECT,
            dnsRule = true,
        )

    fun whitelist(rules: List<RoutingRule>) =
        rules.filterNot { isWhitelist(it) || isRf(it) } + preset(true)

    fun rfDirect(rules: List<RoutingRule>) =
        rules.filterNot { isWhitelist(it) || isRf(it) } + preset(false)

    fun migrateRules(rules: JSONArray?): JSONArray? {
        if (rules == null) return null
        val items = (0 until rules.length()).mapNotNull { rules.optJSONObject(it) }
        fun kind(item: JSONObject): String? =
            when {
                item.optString("name") in setOf("Whitelist · домены", "Whitelist · IP") ->
                    "whitelist"
                item.optString("name") in setOf("ru-sites", "ru-ip") -> "rf"
                else -> null
            }
        if (items.none { kind(it) != null }) return rules
        val emitted = mutableSetOf<String>()
        val result = JSONArray()
        for (item in items) {
            val type = kind(item)
            if (type == null) {
                result.put(item)
                continue
            }
            if (!emitted.add(type)) continue
            val old = items.filter { kind(it) == type }
            val next = preset(type == "whitelist")
            result.put(
                JSONObject()
                    .put("name", next.name)
                    .put("ruleSet", next.ruleSet)
                    .put("outbound", next.outbound)
                    .put("dnsRule", true)
                    .put("enabled", old.all { it.optBoolean("enabled", true) })
            )
        }
        return result
    }

    fun migrateConfig(raw: String): String {
        if (raw.isBlank()) return raw
        val root = JSONObject(raw)
        val old = root.optJSONArray("rules") ?: return raw
        val next = migrateRules(old)
        if (next === old) return raw
        root.put("rules", next)
        return root.toString()
    }
}
