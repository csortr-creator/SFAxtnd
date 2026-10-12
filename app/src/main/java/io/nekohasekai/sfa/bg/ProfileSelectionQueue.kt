package io.nekohasekai.sfa.bg

/** Serial selections with one replaceable pending request; no Android or service side effects. */
class ProfileSelectionQueue {
    data class Request(val profileId: Long, val connect: Boolean)

    private var active: Request? = null
    private var pending: Request? = null

    @Synchronized
    fun offer(profileId: Long, serviceWantsConnection: Boolean) {
        pending = Request(profileId, serviceWantsConnection || active?.connect == true || pending?.connect == true)
    }

    @Synchronized
    fun take(serviceStable: Boolean): Request? {
        if (!serviceStable || active != null) return null
        val request = pending ?: return null
        pending = null
        active = request
        return request
    }

    @Synchronized
    fun complete(request: Request) {
        if (active === request) active = null
    }

    @Synchronized
    fun cancelPending() {
        pending = null
    }
}
