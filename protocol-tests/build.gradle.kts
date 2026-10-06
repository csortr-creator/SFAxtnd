plugins {
    kotlin("jvm") version "2.4.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

// Exercise the production parser without loading Android or the native libbox.
sourceSets.main {
    kotlin.srcDir("../app/src/main/java")
    kotlin.include("io/nekohasekai/sfa/utils/ClientSettingsConfig.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ProfileLatencyCache.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ProxyLinkParser.kt")
    kotlin.include("io/nekohasekai/sfa/utils/OutboundProfileState.kt")
    kotlin.include("io/nekohasekai/sfa/utils/UserRoutingConfig.kt")
    kotlin.include("io/nekohasekai/sfa/utils/RoutingPresets.kt")
    kotlin.include("io/nekohasekai/sfa/models/DnsConfig.kt")
    kotlin.include("io/nekohasekai/sfa/models/GeoFileSource.kt")
    kotlin.include("io/nekohasekai/sfa/models/RoutingRule.kt")
}

tasks.test { useJUnit() }
