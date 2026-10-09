package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Import-time legacy dns.fakeip migration (sing-box 1.11/1.12 semantics → 1.14).
 * Pure JSON transform; no DNS rules/servers invented; no documentation default ranges.
 */
internal object DnsFakeIpMigration {

    fun migrate(dns: JSONObject, warnings: MutableList<String>) {
        val knownFakeIpKeys = setOf("enabled", "inet4_range", "inet6_range")
        var enabled = false
        var inet4: String? = null
        var inet6: String? = null
        var hadObject = false

        if (dns.has("fakeip")) {
            hadObject = true
            val raw = dns.remove("fakeip")
            if (raw is JSONObject) {
                val unknown =
                    raw.keys().asSequence().map { it.toString() }.filter { it !in knownFakeIpKeys }.toList()
                if (unknown.isNotEmpty()) {
                    throw LegacyDnsFakeIpIncompatibleException(
                        "LEGACY_DNS_FAKEIP_INCOMPATIBLE: unknown field(s): ${unknown.joinToString(",")}",
                    )
                }
                enabled = readEnabled(raw)
                inet4 = raw.optString("inet4_range").takeIf { it.isNotEmpty() }
                inet6 = raw.optString("inet6_range").takeIf { it.isNotEmpty() }
            } else if (raw != null && raw != JSONObject.NULL) {
                throw LegacyDnsFakeIpIncompatibleException(
                    "LEGACY_DNS_FAKEIP_INCOMPATIBLE: unexpected fakeip value type",
                )
            }
        }

        val servers = dns.optJSONArray("servers") ?: JSONArray().also { dns.put("servers", it) }
        var legacyAddressCount = 0
        var modernFakeIpCount = 0
        for (i in 0 until servers.length()) {
            val server = servers.optJSONObject(i) ?: continue
            when {
                server.optString("type") == "fakeip" -> modernFakeIpCount++
                isLegacyFakeIpAddressServer(server) -> legacyAddressCount++
            }
        }

        if (hadObject && !enabled) {
            if (legacyAddressCount > 0) {
                throw LegacyDnsFakeIpIncompatibleException(
                    "LEGACY_DNS_FAKEIP_INCOMPATIBLE: enabled=false with legacy address fakeip server",
                )
            }
            return
        }

        if (hadObject && enabled) {
            var converted = 0
            for (i in 0 until servers.length()) {
                val server = servers.optJSONObject(i) ?: continue
                if (!isLegacyFakeIpAddressServer(server)) continue
                convertLegacyFakeIpAddressServer(server, inet4, inet6)
                converted++
            }
            if (converted == 0 && modernFakeIpCount == 0) {
                val note =
                    "Legacy dns.fakeip was enabled but no fakeip DNS server was configured; " +
                        "object removed without adding servers or rules"
                if (note !in warnings) warnings.add(note)
            } else if (converted == 0 && modernFakeIpCount > 0) {
                val note =
                    "Legacy dns.fakeip removed; existing type=fakeip server ranges left unchanged"
                if (note !in warnings) warnings.add(note)
            }
            return
        }

        if (legacyAddressCount > 0) {
            for (i in 0 until servers.length()) {
                val server = servers.optJSONObject(i) ?: continue
                if (!isLegacyFakeIpAddressServer(server)) continue
                convertLegacyFakeIpAddressServer(server, null, null)
            }
            val note = "Legacy address=fakeip converted to type=fakeip without range defaults"
            if (note !in warnings) warnings.add(note)
        }
    }

    private fun readEnabled(raw: JSONObject): Boolean {
        if (!raw.has("enabled")) return false
        return when (val v = raw.get("enabled")) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            else -> false
        }
    }

    private fun isLegacyFakeIpAddressServer(server: JSONObject): Boolean {
        if (server.optString("type") == "fakeip") return false
        val addr = server.optString("address").trim()
        if (addr == "fakeip" || addr.startsWith("fakeip://")) return true
        return server.optString("type") == "udp" && server.optString("server") == "fakeip"
    }

    private fun convertLegacyFakeIpAddressServer(
        server: JSONObject,
        inet4: String?,
        inet6: String?,
    ) {
        server.put("type", "fakeip")
        server.remove("address")
        if (server.optString("server") == "fakeip") server.remove("server")
        if (inet4 != null) server.put("inet4_range", inet4)
        if (inet6 != null) server.put("inet6_range", inet6)
    }
}
