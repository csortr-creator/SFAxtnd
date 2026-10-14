package io.nekohasekai.sfa.database

import android.os.Build
import androidx.room.Room
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.ProxyService
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Path
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.SettingsKey
import io.nekohasekai.sfa.database.preference.KeyValueDatabase
import io.nekohasekai.sfa.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sfa.ktx.boolean
import io.nekohasekai.sfa.ktx.int
import io.nekohasekai.sfa.ktx.long
import io.nekohasekai.sfa.ktx.map
import io.nekohasekai.sfa.ktx.string
import io.nekohasekai.sfa.ktx.stringSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object Settings {
    @OptIn(DelicateCoroutinesApi::class)
    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH,
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }

    val dataStore = RoomPreferenceDataStore(instance.keyValuePairDao())

    /** Backup/export access; Room DB stays private. */
    internal fun keyValuePairDao() = instance.keyValuePairDao()

    var ruleSetUpdateInterval by dataStore.long(SettingsKey.RULE_SET_UPDATE_INTERVAL) { 24 * 60 * 60 * 1000L }
    var routingBlockIpv6 by dataStore.boolean(SettingsKey.ROUTING_BLOCK_IPV6) { true }
    var tunStack by dataStore.string(SettingsKey.TUN_STACK) { "system" }

    var coreOptionsJson by dataStore.string("core_options") { "{}" }
    var appearanceJson by dataStore.string("appearance") { "{}" }
    var outboundLatencyCache by dataStore.string("outbound_latency_cache") { "{}" }
    var outboundSelections by dataStore.string("outbound_selections") { "{}" }
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)
}
