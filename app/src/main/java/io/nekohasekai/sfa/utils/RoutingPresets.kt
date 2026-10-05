package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.models.RoutingRule

object RoutingPresets {
    const val WHITELIST_DOMAINS = "ru-whitelist-domains"
    const val WHITELIST_IPS = "ru-whitelist-ips"
    val remoteRuleSets = mapOf(
        WHITELIST_DOMAINS to "https://raw.githubusercontent.com/hydraponique/roscomvpn-geosite/master/release/sing-box/whitelist.srs",
        WHITELIST_IPS to "https://raw.githubusercontent.com/hydraponique/roscomvpn-geoip/master/release/sing-box/whitelist.srs",
    )
    fun whitelist(rules: List<RoutingRule>): List<RoutingRule> = rules.filter {
        it.name !in setOf("ru-sites", "ru-ip", "Whitelist · домены", "Whitelist · IP") &&
            it.ruleSet !in remoteRuleSets.keys
    } + listOf(
        RoutingRule(name = "Whitelist · домены", ruleSet = WHITELIST_DOMAINS,
            outbound = RoutingRule.OUTBOUND_DIRECT, dnsRule = true),
        RoutingRule(name = "Whitelist · IP", ruleSet = WHITELIST_IPS,
            outbound = RoutingRule.OUTBOUND_DIRECT),
    )
}
