package io.nekohasekai.sfa.bg

/** Serial selections with one replaceable pending request; no Android or service side effects. */
class ProfileSelectionQueue {
    data class Request(val profileId: Long, val connect: Boolean)

    private var active: Request? = null
    private var pending: Request? = null
    private var activeCancelled = false

    @Synchronized
    fun offer(profileId: Long, serviceWantsConnection: Boolean) {
        pending = Request(
            profileId,
            serviceWantsConnection ||
                (!activeCancelled && active?.connect == true) || pending?.connect == true,
        )
    }

    @Synchronized
    fun take(serviceStable: Boolean): Request? {
        if (!serviceStable || active != null) return null
        val request = pending ?: return null
        pending = null
        active = request
        activeCancelled = false
        return request
    }

    @Synchronized
    fun complete(request: Request) {
        if (active === request) active = null
    }

    @Synchronized
    fun cancelAll() {
        pending = null
        // Retain the active slot until its coroutine has finished cleaning up.
        activeCancelled = true
    }

    /** Serialize dispatch with cancellation; a cancelled request cannot dispatch a start. */
    @Synchronized
    fun runIfCurrent(request: Request, action: () -> Unit): Boolean {
        if (active !== request || activeCancelled) return false
        action()
        return true
    }
}
