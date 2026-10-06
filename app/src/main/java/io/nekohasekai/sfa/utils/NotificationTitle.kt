package io.nekohasekai.sfa.utils

object NotificationTitle {
    data class Group(val tag: String, val selected: String)

    fun resolve(mode: String, subscription: String, groups: List<Group>): String {
        val fallback = subscription.ifBlank { "SFAxtnd" }
        if (mode != "server") return fallback
        val byTag = groups.associateBy { it.tag }
        var selected = groups.firstOrNull()?.selected.orEmpty()
        val visited = mutableSetOf<String>()
        while (selected in byTag) {
            if (!visited.add(selected)) return fallback
            selected = byTag.getValue(selected).selected
        }
        return selected.ifBlank { fallback }
    }
}
