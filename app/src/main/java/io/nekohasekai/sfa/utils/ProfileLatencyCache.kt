package io.nekohasekai.sfa.utils

import java.security.MessageDigest
import org.json.JSONObject

class ProfileLatencyCache(raw: String = "{}") {
    data class Result(val delay: Int, val time: Long)

    private val entries = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    @Synchronized
    fun get(profile: Long, tag: String, fingerprint: String): Result? {
        val entry = entries.optJSONObject(profile.toString())?.optJSONObject(tag) ?: return null
        if (entry.optString("fingerprint") != fingerprint) return null
        val time = entry.optLong("time")
        return if (time > 0) Result(entry.optInt("delay").coerceAtLeast(0), time) else null
    }

    @Synchronized
    fun put(profile: Long, tag: String, fingerprint: String, delay: Int, time: Long) {
        if (time <= 0 || profile < 0) return
        val values = entries.optJSONObject(profile.toString()) ?: JSONObject()
        values.put(
            tag,
            JSONObject()
                .put("fingerprint", fingerprint)
                .put("delay", delay.coerceAtLeast(0))
                .put("time", time),
        )
        entries.put(profile.toString(), values)
    }

    @Synchronized
    fun retain(profile: Long, fingerprints: Map<String, String>) {
        val values = entries.optJSONObject(profile.toString()) ?: return
        values.keys().asSequence().toList().forEach { tag ->
            if (values.optJSONObject(tag)?.optString("fingerprint") != fingerprints[tag])
                values.remove(tag)
        }
    }

    @Synchronized fun encode(): String = entries.toString()

    companion object {
        fun fingerprints(config: String): Map<String, String> {
            val outbounds = JSONObject(config).optJSONArray("outbounds") ?: return emptyMap()
            return (0 until outbounds.length())
                .mapNotNull { index ->
                    val outbound = outbounds.optJSONObject(index) ?: return@mapNotNull null
                    val tag = outbound.optString("tag")
                    if (tag.isBlank()) return@mapNotNull null
                    val hash =
                        MessageDigest.getInstance("SHA-256")
                            .digest(canonical(outbound).toByteArray(Charsets.UTF_8))
                            .joinToString("") { "%02x".format(it) }
                    tag to hash
                }
                .toMap()
        }

        private fun canonical(value: Any?): String =
            when (value) {
                is JSONObject ->
                    value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
                        JSONObject.quote(it) + ":" + canonical(value.get(it))
                    }
                is org.json.JSONArray ->
                    (0 until value.length()).joinToString(",", "[", "]") {
                        canonical(value.get(it))
                    }
                is String -> JSONObject.quote(value)
                else -> value.toString()
            }
    }
}
