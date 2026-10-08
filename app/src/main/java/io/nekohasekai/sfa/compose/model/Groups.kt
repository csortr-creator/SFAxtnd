package io.nekohasekai.sfa.compose.model

import androidx.compose.runtime.Immutable
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItem
import io.nekohasekai.libbox.OutboundGroupItemIterator

@Immutable
data class Group(
    val tag: String,
    val type: String,
    val displayType: String,
    val selectable: Boolean,
    val selected: String,
    val isExpand: Boolean,
    val items: List<GroupItem>,
) {
    constructor(item: OutboundGroup) : this(
        item.tag,
        item.type,
        Libbox.proxyDisplayType(item.type),
        item.selectable,
        item.selected,
        item.isExpand,
        item.items.toList().map { GroupItem(it) },
    )
}

@Immutable
data class GroupItem(
    val tag: String,
    val type: String,
    val displayType: String,
    val urlTestTime: Long,
    val urlTestDelay: Int,
    /** Probe classification; empty for online-history items without Kotlin-side status. */
    val probeStatus: String = "",
    val probeDetail: String = "",
) {
    constructor(item: OutboundGroupItem) : this(
        item.tag,
        item.type,
        Libbox.proxyDisplayType(item.type),
        item.urlTestTime,
        item.urlTestDelay,
        probeStatus = if (item.urlTestTime > 0L && item.urlTestDelay > 0) "SUCCESS"
        else if (item.urlTestTime > 0L) "PROBE_ERROR"
        else "",
        probeDetail = "",
    )
}

internal fun OutboundGroupItemIterator.toList(): List<OutboundGroupItem> {
    val list = mutableListOf<OutboundGroupItem>()
    while (hasNext()) {
        list.add(next())
    }
    return list
}
