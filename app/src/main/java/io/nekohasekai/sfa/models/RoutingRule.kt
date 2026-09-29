package io.nekohasekai.sfa.models

/**
 * A single user-defined routing rule.
 *
 * The rule describes what traffic should be matched and
 * which outbound should handle the matched traffic.
 */
data class RoutingRule(
    val type: Type = Type.DOMAIN,
    val value: String = "",
    val outbound: String = OUTBOUND_DIRECT,
) {

    enum class Type {
        DOMAIN,
        DOMAIN_SUFFIX,
        DOMAIN_KEYWORD,
        GEOSITE,
        IP_CIDR,
        GEOIP,
        PACKAGE_NAME,
        PROTOCOL,
    }

    companion object {
        const val OUTBOUND_DIRECT = "direct"
        const val OUTBOUND_PROXY = "proxy"
        const val OUTBOUND_BLOCK = "block"
    }
}
