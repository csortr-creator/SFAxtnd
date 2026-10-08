plugins {
    kotlin("jvm") version "2.4.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.json:json:20240303")
    implementation("org.yaml:snakeyaml:2.5")
    testImplementation("junit:junit:4.13.2")
}

// Exercise the production parser without loading Android or the native libbox.
sourceSets.main {
    kotlin.srcDir("../app/src/main/java")
    kotlin.include("io/nekohasekai/sfa/utils/ParallelTasks.kt")
    kotlin.include("io/nekohasekai/sfa/utils/SubscriptionMetadata.kt")
    kotlin.include("io/nekohasekai/sfa/utils/NotificationTitle.kt")
    kotlin.include("io/nekohasekai/sfa/utils/NotificationUpdateGate.kt")
    kotlin.include("io/nekohasekai/sfa/utils/PowerUsagePolicy.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ClientSettingsConfig.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ProfileLatencyCache.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ProxyLinkParser.kt")
    kotlin.include("io/nekohasekai/sfa/utils/XrayCompatibility.kt")
    kotlin.include("io/nekohasekai/sfa/utils/SubscriptionContentParser.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ForeignSubscriptionParser.kt")
    kotlin.include("io/nekohasekai/sfa/utils/SubscriptionImportReport.kt")
    kotlin.include("io/nekohasekai/sfa/utils/SubscriptionRouting.kt")
    kotlin.include("io/nekohasekai/sfa/utils/ProfileConfigCommit.kt")
    kotlin.include("io/nekohasekai/sfa/utils/OutboundProfileState.kt")
    kotlin.include("io/nekohasekai/sfa/utils/UserRoutingConfig.kt")
    kotlin.include("io/nekohasekai/sfa/utils/RoutingPresets.kt")
    kotlin.include("io/nekohasekai/sfa/utils/RuleSetPolicy.kt")
    kotlin.include("io/nekohasekai/sfa/models/DnsConfig.kt")
    kotlin.include("io/nekohasekai/sfa/models/GeoFileSource.kt")
    kotlin.include("io/nekohasekai/sfa/models/RoutingRule.kt")
}

tasks.test { useJUnit() }
