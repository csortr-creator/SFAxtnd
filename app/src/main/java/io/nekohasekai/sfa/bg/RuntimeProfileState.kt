package io.nekohasekai.sfa.bg

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local view of which profile config the VPN runtime last accepted.
 * Not a substitute for Status.Started and does not imply network reachability.
 *
 * Safe diagnostics only: profile ids and short content fingerprints — no URLs/keys/JSON.
 *
 * R2a: [tryMarkLoaded] applies only when [requestId] is still the latest switch id
 * ([currentSwitchRequestId]), discarding stale start completions after supersede/timeout.
 */
object RuntimeProfileState {
    private val switchIds = AtomicLong(0L)

    @Volatile
    var loadedProfileId: Long = -1L
        private set

    /** First 16 hex chars of SHA-256 of the runtime config string last accepted. */
    @Volatile
    var loadedConfigFingerprint: String = ""
        private set

    /** Monotonic id for switch/start attempts. */
    fun nextSwitchRequestId(): Long = switchIds.incrementAndGet()

    fun currentSwitchRequestId(): Long = switchIds.get()

    /**
     * Accept a successful start only if [requestId] is still the current switch generation.
     * @return true if loaded state was updated; false if the completion is stale/superseded.
     */
    fun tryMarkLoaded(requestId: Long, profileId: Long, runtimeConfig: String): Boolean {
        if (requestId != currentSwitchRequestId()) {
            return false
        }
        markLoaded(profileId, runtimeConfig)
        return true
    }

    fun markLoaded(profileId: Long, runtimeConfig: String) {
        loadedProfileId = profileId
        loadedConfigFingerprint = fingerprint(runtimeConfig)
    }

    fun clear() {
        loadedProfileId = -1L
        loadedConfigFingerprint = ""
    }

    fun fingerprint(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(content.toByteArray(Charsets.UTF_8))
        return dig.take(8).joinToString("") { b -> "%02x".format(b) }
    }

    fun selectedMatchesLoaded(selectedProfileId: Long): Boolean =
        loadedProfileId != -1L && loadedProfileId == selectedProfileId
}
