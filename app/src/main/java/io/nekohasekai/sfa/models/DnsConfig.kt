package io.nekohasekai.sfa.models

data class DnsConfig(
    val servers: List<DnsServer> = defaultServers,
    val strategy: Strategy = Strategy.AUTO,
    val cacheEnabled: Boolean = true,
    val independentCache: Boolean = false,
    val reverseMapping: Boolean = false,
    val finalServer: String = "",
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

        val defaultServers = emptyList<DnsServer>()
    }
}

data class DnsServer(
    val tag: String,
    val address: String,
    val detour: String? = null,
)
