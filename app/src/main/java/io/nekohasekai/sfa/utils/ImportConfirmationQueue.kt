package io.nekohasekai.sfa.utils

/** Immutable FIFO state; reducers may safely retry without consuming a request. */
class ImportConfirmationQueue<T : Any> private constructor(
    private val entries: List<T>,
    val applying: Boolean,
) {
    constructor() : this(emptyList(), false)

    val current: T? get() = entries.firstOrNull()
    val showDialog: Boolean get() = current != null && !applying

    fun offer(request: T): ImportConfirmationQueue<T> = if (entries.any { it === request }) {
        this
    } else {
        ImportConfirmationQueue(entries + request, applying)
    }

    fun canBegin(request: T): Boolean = current === request && !applying

    fun begin(request: T): ImportConfirmationQueue<T> = if (canBegin(request)) ImportConfirmationQueue(entries, true) else this

    fun complete(request: T): ImportConfirmationQueue<T> = if (current === request) ImportConfirmationQueue(entries.drop(1), false) else this
}
