package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceDataStore
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.NotificationTitle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(DelicateCoroutinesApi::class)
class ServiceNotification(
    private val status: MutableLiveData<Status>,
    private val service: Service,
) : BroadcastReceiver(), CommandClient.Handler, OnPreferenceDataStoreChangeListener {
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

    @OptIn(DelicateCoroutinesApi::class)
    private val commandClient =
        CommandClient(GlobalScope, CommandClient.ConnectionType.Status, this, localOnly = true)
    private var receiverRegistered = false
    private var started = false
    private var profileName = ""
    private var activeGroups = emptyList<NotificationTitle.Group>()
    private val groupClient =
        CommandClient(
            GlobalScope,
            CommandClient.ConnectionType.Groups,
            object : CommandClient.Handler {
                override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
                    val snapshot = newGroups.map { NotificationTitle.Group(it.tag, it.selected) }
                    GlobalScope.launch(Dispatchers.Main) {
                        if (started) {
                            activeGroups = snapshot
                            refreshTitle()
                        }
                    }
                }
            },
            localOnly = true,
        )

    private fun title() =
        NotificationTitle.resolve(
            runCatching {
                    JSONObject(Settings.appearanceJson).optString("notificationTitle", "group")
                }
                .getOrDefault("group"),
            profileName,
            activeGroups,
        )

    private fun refreshTitle() {
        Application.notificationManager.notify(
            notificationId,
            notificationBuilder.setContentTitle(title()).build(),
        )
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key == "appearance")
            GlobalScope.launch(Dispatchers.Main) { if (started) refreshTitle() }
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
        if (contentTextId == R.string.status_starting) activeGroups = emptyList()
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

    suspend fun start() {
        if (checkPermission()) {
            started = true
            Settings.dataStore.registerChangeListener(this)
            groupClient.connect()
        }
        if (Settings.dynamicNotification && checkPermission()) {
            commandClient.connect()
            withContext(Dispatchers.Main) { registerReceiver() }
        }
    }

    private fun registerReceiver() {
        service.registerReceiver(
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        receiverRegistered = true
    }

    override fun updateStatus(status: StatusMessage) {
        val content =
            Libbox.formatBytes(status.uplink) +
                "/s ↑\t" +
                Libbox.formatBytes(status.downlink) +
                "/s ↓"
        GlobalScope.launch(Dispatchers.Main) {
            if (started)
                Application.notificationManager.notify(
                    notificationId,
                    notificationBuilder.setContentTitle(title()).setContentText(content).build(),
                )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                commandClient.connect()
            }

            Intent.ACTION_SCREEN_OFF -> {
                commandClient.disconnect()
            }
        }
    }

    fun close() {
        started = false
        Settings.dataStore.unregisterChangeListener(this)
        groupClient.disconnect()
        activeGroups = emptyList()
        commandClient.disconnect()
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (receiverRegistered) {
            service.unregisterReceiver(this)
            receiverRegistered = false
        }
    }
}
