package io.nekohasekai.sfa.utils

import org.json.JSONObject

/** TCP uTLS cannot provide the standard TLS config required by QUIC. */
internal object QuicTlsCompatibility {
    const val XRAY_HYSTERIA_NOTE =
        "Hysteria2: параметр fp/fingerprint из ссылки или Xray JSON не задаёт отпечаток QUIC в Xray. " +
            "Сохранены отдельные настройки QUIC, SNI и проверки сертификата."
    /**
     * Xray's Hysteria dialer calls GetTLSConfig (crypto/tls), not UClient.
     * Its tlsSettings.fingerprint/share-link fp has no effect on the QUIC hello.
     * QUIC Chrome parrot is controlled independently by finalmask.quicParams.
     * Do not apply this source-specific rule to native sing-box or Clash uTLS.
     */
    fun fromXrayHysteria(node: JSONObject): JSONObject {
        require(node.optString("type") == "hysteria2")
        node.optJSONObject("tls")?.remove("utls")
        if (!node.has("disable_chrome_parrot")) node.put("disable_chrome_parrot", false)
        return node
    }

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
