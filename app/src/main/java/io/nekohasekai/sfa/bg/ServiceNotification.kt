package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.preference.PreferenceDataStore
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.NotificationTitle
import io.nekohasekai.sfa.utils.NotificationUpdateGate
import io.nekohasekai.sfa.utils.PowerUsagePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ServiceNotification(private val service: Service) :
    CommandClient.Handler, OnPreferenceDataStoreChangeListener {
    companion object {
        private const val notificationId = 1
        private const val notificationChannel = "service"
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        fun checkPermission(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }
            return Application.notification.areNotificationsEnabled()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dynamicNotification = MutableStateFlow(Settings.dynamicNotification)
    private val updates = NotificationUpdateGate()
    private var polling = false
    private var titleMode = readTitleMode()
    private var contentText = ""
    private val commandClient =
        CommandClient(
            scope,
            CommandClient.ConnectionType.Status,
            this,
            localOnly = true,
            statusIntervalMillis = 3000L,
        )
    private var started = false
    private var profileName = ""
    private var activeGroups = emptyList<NotificationTitle.Group>()
    private val groupClient =
        CommandClient(
            scope,
            CommandClient.ConnectionType.Groups,
            object : CommandClient.Handler {
                override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
                    val snapshot = newGroups.map { NotificationTitle.Group(it.tag, it.selected) }
                    scope.launch {
                        if (started) {
                            activeGroups = snapshot
                            refreshTitle()
                        }
                    }
                }
            },
            localOnly = true,
        )

    private fun readTitleMode() =
        runCatching { JSONObject(Settings.appearanceJson).optString("notificationTitle", "group") }
            .getOrDefault("group")

    private fun title() = NotificationTitle.resolve(titleMode, profileName, activeGroups)

    private fun refreshTitle() {
        val currentTitle = title()
        if (updates.changed(currentTitle, contentText)) {
            Application.notificationManager.notify(
                notificationId,
                notificationBuilder
                    .setContentTitle(currentTitle)
                    .setContentText(contentText)
                    .build(),
            )
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        scope.launch {
            if (started) {
                dynamicNotification.value = Settings.dynamicNotification
                if (key == "appearance") {
                    titleMode = readTitleMode()
                    refreshTitle()
                }
            }
        }
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(service, notificationChannel)
            .setShowWhen(false)
            .setOngoing(true)
            .setContentTitle("sing-box")
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_menu)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(service, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    flags,
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                addAction(
                    NotificationCompat.Action.Builder(
                            0,
                            service.getText(R.string.stop),
                            PendingIntent.getBroadcast(
                                service,
                                0,
                                Intent(Action.SERVICE_CLOSE).setPackage(service.packageName),
                                flags,
                            ),
                        )
                        .build()
                )
            }
    }

    fun show(lastProfileName: String, @StringRes contentTextId: Int) {
        profileName = lastProfileName
        titleMode = readTitleMode()
        contentText = service.getString(contentTextId)
        if (contentTextId == R.string.status_starting) activeGroups = emptyList()
        updates.changed(title(), contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Application.notification.createNotificationChannel(
                NotificationChannel(
                    notificationChannel,
                    "Service Notifications",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        service.startForeground(
            notificationId,
            notificationBuilder
                .setContentTitle(title())
                .setContentText(service.getString(contentTextId))
                .build(),
        )
    }

    suspend fun start() =
        withContext(Dispatchers.Main.immediate) {
            if (started || !checkPermission()) return@withContext
            started = true
            dynamicNotification.value = Settings.dynamicNotification
            Settings.dataStore.registerChangeListener(this@ServiceNotification)
            groupClient.connect()
            scope.launch {
                combine(
                        AppLifecycleObserver.isScreenOn,
                        AppLifecycleObserver.isDeviceIdle,
                        dynamicNotification,
                    ) { screenOn, idle, dynamic ->
                        PowerUsagePolicy.notificationActive(dynamic, screenOn, idle)
                    }
                    .distinctUntilChanged()
                    .collect { active ->
                        polling = active
                        if (active) commandClient.connect() else commandClient.disconnect()
                    }
            }
        }

    override fun updateStatus(status: StatusMessage) {
        val content =
            Libbox.formatBytes(status.uplink) +
                "/s ↑\t" +
                Libbox.formatBytes(status.downlink) +
                "/s ↓"
        scope.launch {
            if (started && polling) {
                contentText = content
                refreshTitle()
            }
        }
    }

    fun close() {
        started = false
        polling = false
        scope.coroutineContext.cancelChildren()
        updates.reset()
        Settings.dataStore.unregisterChangeListener(this)
        groupClient.disconnect()
        activeGroups = emptyList()
        commandClient.disconnect()
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}
