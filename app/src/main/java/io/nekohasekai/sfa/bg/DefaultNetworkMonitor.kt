package io.nekohasekai.sfa.bg

import android.net.Network
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.NetworkInterface

object DefaultNetworkMonitor {

    var defaultNetwork: Network? = null
    private var listener: InterfaceUpdateListener? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkJob: Job? = null

    suspend fun start() {
        DefaultNetworkListener.start(this) {
            defaultNetwork = it
            checkDefaultInterfaceUpdate(it)
        }
        defaultNetwork = withTimeout(10000L) { DefaultNetworkListener.get() }
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    suspend fun stop() {
        checkJob?.cancel()
        checkJob = null
        defaultNetwork = null
        DefaultNetworkListener.stop(this)
    }

    suspend fun require(): Network {
        val network = defaultNetwork
        if (network != null) {
            return network
        }
        return DefaultNetworkListener.get()
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        checkJob?.cancel()
        val currentListener = listener ?: return
        checkJob = scope.launch {
            if (newNetwork != null) {
                for (times in 0 until 10) {
                    val linkProperties = Application.connectivity.getLinkProperties(newNetwork)
                    if (linkProperties == null) {
                        delay(100)
                        continue
                    }
                    var interfaceIndex: Int
                    try {
                        interfaceIndex = NetworkInterface.getByName(linkProperties.interfaceName).index
                    } catch (e: Exception) {
                        delay(100)
                        continue
                    }
                    currentListener.updateDefaultInterface(linkProperties.interfaceName, interfaceIndex, false, false)
                    return@launch
                }
            } else {
                currentListener.updateDefaultInterface("", -1, false, false)
            }
        }
    }
}
