package io.nekohasekai.sfa.utils

object PowerUsagePolicy {
    fun uiActive(foreground: Boolean, screenOn: Boolean, deviceIdle: Boolean) =
        foreground && screenOn && !deviceIdle

    fun notificationActive(dynamic: Boolean, screenOn: Boolean, deviceIdle: Boolean) =
        dynamic && screenOn && !deviceIdle

    fun bytesPerSecond(bytes: Long, intervalMillis: Long): Long =
        (bytes.toDouble() * 1000.0 / intervalMillis.coerceAtLeast(250L)).toLong().coerceAtLeast(0L)
}
