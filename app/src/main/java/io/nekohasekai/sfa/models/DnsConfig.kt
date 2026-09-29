package io.nekohasekai.sfa.models

/**
 * User-configurable DNS settings.
 *
 * The actual sing-box DNS configuration will be generated
 * from this model at a later stage.
 */
data class DnsConfig(
    val servers: List<DnsServer> = defaultServers,
    val strategy: Strategy = Strategy.AUTO,
    val cacheEnabled: Boolean = true,
    val independentCache: Boolean = false,
    val reverseMapping: Boolean = false,
    val finalServer: String = DEFAULT_DIRECT_SERVER,
) {

    enum class Strategy {
        AUTO,
        PREFER_IPV4,
        PREFER_IPV6,
        IPV4_ONLY,
        IPV6_ONLY,
    }

    companion object {
        const val DEFAULT_DIRECT_SERVER = "dns-direct"
        const val DEFAULT_PROXY_SERVER = "dns-proxy"

        val defaultServers = listOf(
            DnsServer(
                tag = DEFAULT_DIRECT_SERVER,
                address = "local",
                detour = "direct",
            ),
            DnsServer(
                tag = DEFAULT_PROXY_SERVER,
                address = "https://dns.google/dns-query",
                detour = "Выбор сервера",
            ),
        )
    }
}

/**
 * A DNS upstream used by the application.
 */
data class DnsServer(
    val tag: String,
    val address: String,
    val detour: String? = null,
)
