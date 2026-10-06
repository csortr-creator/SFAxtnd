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
    val enabled: Boolean = true,
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
        if (name.isNotBlank())
            return when (name) {
                "ru-sites" -> "Домены .ru"
                "ru-ip" -> "Российские IP-адреса"
                "ads" -> "Рекламные сайты"
                else -> name
            }
        listOf(domain, domainSuffix, domainKeyword, ipCidr, packageName, ruleSet, value)
            .firstOrNull { it.isNotBlank() }
            ?.let {
                return it
            }
        return "Правило"
    }

    fun displaySubtitle(): String {
        val parts = buildList {
            if (!enabled) add("Выключено")
            if (domain.isNotBlank()) add("Домен")
            if (domainSuffix.isNotBlank()) add("Домены")
            if (domainKeyword.isNotBlank()) add("Часть домена")
            if (ipCidr.isNotBlank()) add("IP-адреса")
            if (port.isNotBlank()) add("Порты")
            if (packageName.isNotBlank()) add("Приложения")
            if (ruleSet.isNotBlank()) add("Готовый список")
            if (network.isNotBlank()) add(network)
            if (protocol.isNotBlank()) add(protocol)
            if (wifiSsid.isNotBlank()) add("Сеть Wi-Fi")
            if (dnsRule) add("DNS")
            if (value.isNotBlank() && domain.isBlank())
                add(
                    when (type) {
                        Type.DOMAIN -> "Домен"
                        Type.DOMAIN_SUFFIX -> "Домены"
                        Type.DOMAIN_KEYWORD -> "Часть домена"
                        Type.IP_CIDR -> "IP-адреса"
                        Type.GEOSITE,
                        Type.GEOIP -> "Готовый список"
                        Type.PACKAGE_NAME -> "Приложения"
                        Type.PROTOCOL -> "Протокол"
                    }
                )
        }
        val match = parts.joinToString(" · ").ifBlank { "—" }
        val destination =
            when (outbound) {
                OUTBOUND_DIRECT -> "Напрямую"
                OUTBOUND_BLOCK -> "Блокировать"
                OUTBOUND_PROXY -> "Через VPN"
                else -> outbound
            }
        return "$match · $destination"
    }
}
