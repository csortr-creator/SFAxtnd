package io.nekohasekai.sfa.models

data class RoutingRule(
    val name: String = "",
    val domain: String = "",
    val domainSuffix: String = "",
    val domainKeyword: String = "",
    val ipCidr: String = "",
    val port: String = "",
    val sourceIpCidr: String = "",
    val sourcePort: String = "",
    val packageName: String = "",
    val ruleSet: String = "",
    val network: String = "",
    val protocol: String = "",
    val wifiSsid: String = "",
    val wifiBssid: String = "",
    val clashMode: String = "",
    val outbound: String = OUTBOUND_PROXY,
    val dnsRule: Boolean = false,
    val type: Type = Type.DOMAIN,
    val value: String = "",
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

    fun displayTitle(): String {
        if (name.isNotBlank()) return name
        listOf(domain, domainSuffix, domainKeyword, ipCidr, packageName, ruleSet, value)
            .firstOrNull { it.isNotBlank() }
            ?.let { return it }
        return "Правило"
    }

    fun displaySubtitle(): String {
        val parts = buildList {
            if (domain.isNotBlank()) add("domain")
            if (domainSuffix.isNotBlank()) add("suffix")
            if (domainKeyword.isNotBlank()) add("keyword")
            if (ipCidr.isNotBlank()) add("ip")
            if (port.isNotBlank()) add("port")
            if (packageName.isNotBlank()) add("app")
            if (ruleSet.isNotBlank()) add("srs")
            if (network.isNotBlank()) add(network)
            if (protocol.isNotBlank()) add(protocol)
            if (wifiSsid.isNotBlank()) add("wifi")
            if (dnsRule) add("dns")
            if (value.isNotBlank() && domain.isBlank()) add(type.name.lowercase())
        }
        val match = parts.joinToString(" · ").ifBlank { "—" }
        return "$match → $outbound"
    }
}
