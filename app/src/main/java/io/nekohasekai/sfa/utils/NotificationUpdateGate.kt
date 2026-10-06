package io.nekohasekai.sfa.utils

class NotificationUpdateGate {
    private var last: Pair<String, String>? = null

    fun changed(title: String, content: String): Boolean {
        val current = title to content
        if (last == current) return false
        last = current
        return true
    }

    fun reset() {
        last = null
    }
}
