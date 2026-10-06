package io.nekohasekai.sfa.utils

object PowerUsagePolicy {
    fun uiActive(foreground: Boolean, screenOn: Boolean, deviceIdle: Boolean) =
        foreground && screenOn && !deviceIdle

    fun notificationActive(dynamic: Boolean, screenOn: Boolean, deviceIdle: Boolean) =
        dynamic && screenOn && !deviceIdle
}
