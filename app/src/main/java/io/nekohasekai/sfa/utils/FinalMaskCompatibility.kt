package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

/** Maps only wire-equivalent FinalMask subsets into the pinned sing-box core. */
internal object FinalMaskCompatibility {
    fun apply(node: JSONObject, mask: JSONObject) {
        // Failed mapping must not leave partially changed native options behind.
        val mapped = JSONObject(node.toString())
        applyMapped(mapped, mask)
        mapped.keys().forEach { node.put(it, mapped.get(it)) }
    }

    private fun applyMapped(node: JSONObject, mask: JSONObject) {
        for (key in mask.keys()) {
            require(key in setOf("quicParams", "udp", "tcp")) {
                "Unsupported finalmask parameter: $key"
            }
        }
        require(!mask.has("udp") || mask.optJSONArray("udp") != null) {
            "Invalid finalmask UDP mask array"
        }
        mask.optJSONArray("udp")?.takeIf { it.length() > 0 }?.let { masks ->
            require(node.optString("type") == "hysteria2") {
                "Unsupported finalmask UDP transport combination"
            }
            require(masks.length() == 1 && !node.has("obfs")) {
                "Unsupported finalmask UDP mask chain or conflicting obfuscation"
            }
            val entry = masks.getJSONObject(0)
            require(
                entry.keys().asSequence().all { it in setOf("type", "settings") } &&
                    entry.getString("type") == "salamander",
            ) {
                "Unsupported finalmask UDP mask"
            }
            val settings = entry.getJSONObject("settings")
            require(settings.keys().asSequence().all { it == "password" }) {
                "Unsupported finalmask Salamander settings"
            }
            require(
                settings.get("password") is String &&
                    settings.getString("password").toByteArray(Charsets.UTF_8).size >= 4,
            ) {
                "Invalid finalmask Salamander password"
            }
            // Both implementations use 8-byte salt and XOR with BLAKE2b-256(password+salt).
            // Keep proxy authentication separate from the mask password.
            node.put(
                "obfs",
                JSONObject().put("type", "salamander")
                    .put("password", settings.getString("password")),
            )
        }
        require(!mask.has("tcp") || mask.optJSONArray("tcp") != null) {
            "Invalid finalmask TCP mask array"
        }
        mask.optJSONArray("tcp")?.takeIf { it.length() > 0 }?.let { masks ->
            require(
                node.optString("type") == "vless" && !node.has("transport") &&
                    !node.has("tls") &&
                    node.optString("flow").isEmpty(),
            ) {
                "Unsupported finalmask TCP transport combination"
            }
            require(masks.length() == 1 && !node.has("tcp_header")) {
                "Unsupported finalmask TCP mask chain"
            }
            val entry = masks.getJSONObject(0)
            require(
                entry.keys().asSequence().all { it in setOf("type", "settings") } &&
                    entry.getString("type") == "header-custom",
            ) {
                "Unsupported finalmask TCP mask"
            }
            val settings = entry.getJSONObject("settings")
            require(settings.keys().asSequence().all { it in setOf("clients", "servers") }) {
                "Unsupported finalmask TCP header settings"
            }
            val header = JSONObject()
            var bytes = 0
            for (side in listOf("clients", "servers")) {
                val sequences = settings.getJSONArray(side)
                require(sequences.length() <= 32) { "Invalid finalmask TCP header size" }
                val result = JSONArray()
                for (i in 0 until sequences.length()) {
                    val sequence = sequences.getJSONArray(i)
                    val packet = StringBuilder()
                    require(sequence.length() <= 64) { "Invalid finalmask TCP header size" }
                    for (j in 0 until sequence.length()) {
                        val item = sequence.getJSONObject(j)
                        require(
                            item.keys().asSequence().all { it in setOf("type", "packet") } &&
                                item.getString("type") == "str" && item.get("packet") is String,
                        ) {
                            "Unsupported finalmask TCP header item"
                        }
                        val value = item.getString("packet")
                        bytes += value.toByteArray(Charsets.UTF_8).size
                        require(bytes <= 65536) { "Invalid finalmask TCP header size" }
                        packet.append(value)
                    }
                    result.put(packet.toString())
                }
                header.put(side, result)
            }
            require(bytes > 0) { "Invalid empty finalmask TCP header" }
            node.put("tcp_header", header)
        }
        require(!mask.has("quicParams") || mask.optJSONObject("quicParams") != null) {
            "Invalid finalmask QUIC object"
        }
        val quic = mask.optJSONObject("quicParams") ?: return
        require(node.getString("type") == "hysteria2") {
            "Unsupported finalmask QUIC options for proxy type"
        }
        for (key in quic.keys()) {
            require(
                key in setOf("congestion", "debug", "bbrProfile", "disablePathMTUDiscovery", "disableChromeParrot", "udpHop"),
            ) {
                "Unsupported finalmask QUIC parameter: $key"
            }
        }
        require(quic.optString("congestion") in setOf("", "bbr")) {
            "Unsupported finalmask congestion control"
        }
        require(!quic.has("debug") || quic.get("debug") == false) {
            "Unsupported finalmask debug mode"
        }
        quic
            .optString("bbrProfile")
            .takeIf { it.isNotBlank() }
            ?.let {
                require(it in setOf("standard", "conservative", "aggressive")) {
                    "Invalid BBR profile"
                }
                node.put("bbr_profile", it)
            }
        if (quic.has("disablePathMTUDiscovery")) {
            require(quic.get("disablePathMTUDiscovery") is Boolean) {
                "Invalid QUIC path MTU setting"
            }
            node.put("disable_path_mtu_discovery", quic.getBoolean("disablePathMTUDiscovery"))
        }
        if (quic.has("disableChromeParrot")) {
            require(quic.get("disableChromeParrot") is Boolean) {
                "Invalid QUIC Chrome parrot setting"
            }
            node.put("disable_chrome_parrot", quic.getBoolean("disableChromeParrot"))
        }
        require(!quic.has("udpHop") || quic.optJSONObject("udpHop") != null) {
            "Invalid finalmask port hopping object"
        }
        quic.optJSONObject("udpHop")?.let {
            require(it.keys().asSequence().all { key -> key in setOf("ports", "interval") }) {
                "Unsupported finalmask port hopping parameter"
            }
            val ports =
                ProxyLinkParser.endpoint("[" + node.getString("server") + "]:" + it.getString("ports")).ports
            require(!node.has("server_ports")) { "Invalid conflicting port hopping options" }
            node.put("server_ports", JSONArray(ports))
            if (it.has("interval")) {
                val interval = it.getDouble("interval")
                require(interval.isFinite() && interval > 0) { "Invalid port hopping interval" }
                require(!node.has("hop_interval")) { "Invalid conflicting port hopping interval" }
                // Prefer integer seconds when the value is whole (sing-box duration style).
                val rendered =
                    if (interval == interval.toLong().toDouble()) {
                        "${interval.toLong()}s"
                    } else {
                        "${interval}s"
                    }
                node.put("hop_interval", rendered)
            }
        }
    }
}
