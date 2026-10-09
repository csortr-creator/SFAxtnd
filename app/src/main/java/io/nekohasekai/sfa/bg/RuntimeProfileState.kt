package io.nekohasekai.sfa.bg

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local view of which profile config the VPN runtime last accepted.
 * Not a substitute for Status.Started and does not imply network reachability.
 *
 * Safe diagnostics only: profile ids and short content fingerprints — no URLs/keys/JSON.
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

    /** Monotonic id for switch/start attempts (diagnostics / future stale guards). */
    fun nextSwitchRequestId(): Long = switchIds.incrementAndGet()

    fun currentSwitchRequestId(): Long = switchIds.get()

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
