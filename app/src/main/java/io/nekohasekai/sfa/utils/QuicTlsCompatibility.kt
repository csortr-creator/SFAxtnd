package io.nekohasekai.sfa.utils

import org.json.JSONObject

/** TCP uTLS cannot provide the standard TLS config required by QUIC. */
internal object QuicTlsCompatibility {
    fun normalize(node: JSONObject): JSONObject {
        val type = node.optString("type")
        if (type !in setOf("hysteria", "hysteria2", "tuic")) return node
        val tls = node.optJSONObject("tls") ?: return node
        val utls = tls.optJSONObject("utls") ?: return node
        if (!utls.optBoolean("enabled")) return node
        val fingerprint = utls.optString("fingerprint").lowercase()
        require(type == "hysteria2" && fingerprint == "chrome") {
            "Unsupported QUIC uTLS fingerprint"
        }
        require(!node.optBoolean("disable_chrome_parrot")) {
            "Unsupported conflicting Hysteria2 Chrome fingerprint options"
        }
        // Hysteria2 has its own QUIC Chrome parrot; keep all certificate/SNI options.
        tls.remove("utls")
        node.put("disable_chrome_parrot", false)
        return node
    }
}
