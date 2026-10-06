package io.nekohasekai.sfa.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLifecycleObserver : DefaultLifecycleObserver {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
    private val _isScreenOn = MutableStateFlow(false)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()
    private val _isDeviceIdle = MutableStateFlow(false)
    val isDeviceIdle: StateFlow<Boolean> = _isDeviceIdle.asStateFlow()
    private val _isUiActive = MutableStateFlow(false)
    val isUiActive: StateFlow<Boolean> = _isUiActive.asStateFlow()

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshPowerState(context)
            }
        }

    private fun refreshPowerState(context: Context) {
        val powerManager = context.getSystemService<PowerManager>()!!
        _isScreenOn.value = powerManager.isInteractive
        _isDeviceIdle.value =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager.isDeviceIdleMode
        refreshUiState()
    }

    private fun refreshUiState() {
        _isUiActive.value =
            PowerUsagePolicy.uiActive(_isForeground.value, _isScreenOn.value, _isDeviceIdle.value)
    }

    fun register(context: Context) {
        refreshPowerState(context)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                }
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
        refreshUiState()
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
        refreshUiState()
    }
}
