package io.nekohasekai.sfa.models

data class GeoFileSource(
    val id: String,
    val name: String,
    val description: String,
    val geosite_url: String?,
    val geoip_url: String?,
    val priority: Int = 0,
)

object GeoFileSources {
    val SAGERNET = GeoFileSource(
        id = "sagernet",
        name = "SagerNet",
        description = "rule-set .srs (geosite / geoip)",
        geosite_url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set",
        geoip_url = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set",
        priority = 1,
    )

    val ALL = listOf(SAGERNET)

    fun find(id: String): GeoFileSource? = ALL.find { it.id == id }
}
