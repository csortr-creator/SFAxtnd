package io.nekohasekai.sfa.models

data class GeoFileSource(
    val id: String,
    val name: String,
    val description: String,
    val geosite_url: String?,
    val geoip_url: String?,
    val priority: Int = 0
)

object GeoFileSources {
    val SAGERNET = GeoFileSource(
        id = "sagernet",
        name = "SagerNet",
        description = "Official SagerNet geofiling",
        geosite_url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/",
        geoip_url = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/",
        priority = 1
    )

    val V2RAY = GeoFileSource(
        id = "v2ray",
        name = "V2Ray",
        description = "V2Ray geofiling",
        geosite_url = "https://raw.githubusercontent.com/v2fly/domain-list-community/master/",
        geoip_url = "https://raw.githubusercontent.com/v2fly/geoip/master/",
        priority = 2
    )

    val RUNETFREEDOM = GeoFileSource(
        id = "runetfreedom",
        name = "RuNetFreedom",
        description = "Russian anti-censorship geofiling",
        geosite_url = "https://raw.githubusercontent.com/Anonym-tsk/runetfreedom/main/",
        geoip_url = null,
        priority = 3
    )

    val HYDRAPONIQUE = GeoFileSource(
        id = "hydraponique",
        name = "Hydraponique",
        description = "Hybrid geofiling",
        geosite_url = "https://raw.githubusercontent.com/Loyalsoldier/v2ray-rules-dat/release/",
        geoip_url = "https://raw.githubusercontent.com/Loyalsoldier/geoip/release/",
        priority = 4
    )

    val REFILTER = GeoFileSource(
        id = "refilter",
        name = "Refilter",
        description = "Refilter geofiling",
        geosite_url = "https://raw.githubusercontent.com/Refilter/rules/main/",
        geoip_url = null,
        priority = 5
    )

    val METACUBEX = GeoFileSource(
        id = "metacubex",
        name = "MetaCubeX",
        description = "MetaCubeX geofiling",
        geosite_url = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/",
        geoip_url = "https://raw.githubusercontent.com/MetaCubeX/geoip-dat/release/",
        priority = 6
    )

    val ALL = listOf(SAGERNET, V2RAY, RUNETFREEDOM, HYDRAPONIQUE, REFILTER, METACUBEX)
}
