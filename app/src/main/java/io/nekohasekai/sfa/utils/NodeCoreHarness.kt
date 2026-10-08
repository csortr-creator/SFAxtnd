package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * B0 — Isolated per-node core validation harness (tests / diagnostics only).
 *
 * Used by production [NodeCoreValidator] for per-node core checks (B2).
 * Builds a minimal sing-box 1.14.2-shaped config so [Libbox.checkConfig] / `sing-box check`
 * can validate a single outbound without the full subscription profile.
 *
 * DNS/route tags mirror [SubscriptionContentParser] generated profiles (`dns-direct`,
 * `dns-remote`, `default_domain_resolver`) so domain resolution references resolve.
 */
enum class NodeCoreVerdict {
    /** checkConfig succeeded for this outbound harness. */
    VALID,
    /** Outbound is the likely cause of checkConfig failure (only when control for family passed). */
    NODE_INVALID,
    /** Harness template / DNS-route scaffolding is broken (policy: HARNESS_ERROR — do not auto-skip). */
    HARNESS_INVALID,
    /** Native core not available in this process. */
    CORE_UNAVAILABLE,
    /** Cannot attribute failure safely — do not auto-exclude the node. */
    UNKNOWN,
}

data class NodeCoreFamilyControl(
    val family: String,
    val passed: Boolean,
)

object NodeCoreHarness {

    const val DNS_DIRECT = "dns-direct"
    const val DNS_REMOTE = "dns-remote"
    const val DIRECT_TAG = "direct"

    /**
     * Minimal valid config embedding [node] plus a fixed direct outbound.
     * Does not invent TLS/Reality/XHTTP/encryption fields for the node.
     */
    fun build(node: JSONObject, routeFinal: String = DIRECT_TAG): String {
        val root = baseRoot(includeDns = true, dnsDirectPresent = true)
        val tag = node.optString("tag").ifBlank { "node-under-test" }
        val underTest =
            JSONObject(node.toString()).apply {
                if (!has("tag") || optString("tag").isBlank()) put("tag", tag)
            }
        root.put(
            "outbounds",
            JSONArray()
                .put(underTest)
                .put(JSONObject().put("type", "direct").put("tag", DIRECT_TAG)),
        )
        root.getJSONObject("route").put("final", routeFinal)
        return root.toString()
    }

    /** Intentionally broken harness: route references dns-direct but DNS has no such server. */
    fun buildMissingDnsTag(node: JSONObject): String {
        val root = baseRoot(includeDns = true, dnsDirectPresent = false)
        val underTest =
            JSONObject(node.toString()).apply {
                if (!has("tag") || optString("tag").isBlank()) put("tag", "node-under-test")
            }
        root.put(
            "outbounds",
            JSONArray()
                .put(underTest)
                .put(JSONObject().put("type", "direct").put("tag", DIRECT_TAG)),
        )
        return root.toString()
    }

    fun baseRoot(includeDns: Boolean, dnsDirectPresent: Boolean): JSONObject {
        val root =
            JSONObject()
                .put("log", JSONObject().put("level", "warn"))
        if (includeDns) {
            val servers = JSONArray()
            if (dnsDirectPresent) {
                servers.put(JSONObject().put("type", "local").put("tag", DNS_DIRECT))
            }
            servers.put(
                JSONObject()
                    .put("type", "udp")
                    .put("tag", DNS_REMOTE)
                    .put("server", "1.1.1.1")
                    .put("server_port", 53)
                    .apply {
                        if (dnsDirectPresent) put("domain_resolver", DNS_DIRECT)
                    },
            )
            root.put(
                "dns",
                JSONObject()
                    .put("servers", servers)
                    .put("final", DNS_REMOTE)
                    .put("strategy", "ipv4_only"),
            )
        }
        root.put(
            "route",
            JSONObject()
                .put("final", DIRECT_TAG)
                .put("auto_detect_interface", true)
                .put("rules", JSONArray())
                .apply {
                    if (includeDns && dnsDirectPresent) {
                        put("default_domain_resolver", DNS_DIRECT)
                    } else if (includeDns && !dnsDirectPresent) {
                        // Force HARNESS failure path used in tests.
                        put("default_domain_resolver", DNS_DIRECT)
                    }
                },
        )
        return root
    }

    /**
     * Attribute a core validation error. Does **not** auto-exclude nodes when ambiguous.
     *
     * @param controlPassed whether the protocol-family control outbound passed checkConfig
     *   in the same environment; if false, never returns [NodeCoreVerdict.NODE_INVALID].
     */
    fun classify(
        errorMessage: String?,
        controlPassed: Boolean,
        expectedHarnessFailure: Boolean = false,
    ): NodeCoreVerdict {
        if (errorMessage == null) return NodeCoreVerdict.UNKNOWN
        val msg = errorMessage.lowercase()

        if (expectedHarnessFailure) {
            return NodeCoreVerdict.HARNESS_INVALID
        }

        // Structural harness signatures (dns-direct missing, etc.)
        val harnessHints =
            listOf(
                "default domain resolver not found",
                "dns server not found",
                "dns-direct",
                "finder not found",
                "outbound not found: direct",
            )
        // Only treat as harness if message is clearly about resolver/dns scaffolding
        // and not about the outbound protocol itself.
        val looksHarness =
            harnessHints.any { it in msg } &&
                !listOf("unknown outbound type", "missing required field", "decode").any { it in msg }

        if (looksHarness &&
            ("default domain resolver not found" in msg ||
                ("dns" in msg && "not found" in msg && "outbound" !in msg))
        ) {
            return NodeCoreVerdict.HARNESS_INVALID
        }

        // Unambiguous outbound schema/decode failures: NODE even without family control
        // (e.g. unknown type has no protocol family to validate harness against).
        val unambiguousNodeHints =
            listOf(
                "unknown outbound type",
                "unsupported outbound",
                "cannot unmarshal",
            )
        if (unambiguousNodeHints.any { it in msg }) {
            return NodeCoreVerdict.NODE_INVALID
        }

        if (!controlPassed) {
            // Family control failed — ambiguous errors are not trustworthy as NODE.
            return NodeCoreVerdict.UNKNOWN
        }

        // Prefer type/schema style failures for NODE when control is green.
        val nodeHints =
            listOf(
                "missing required",
                "required field",
                "invalid type",
                "json:",
            )
        if (nodeHints.any { it in msg }) {
            return NodeCoreVerdict.NODE_INVALID
        }

        // Ambiguous protocol/TLS errors with green control → still UNKNOWN (do not auto-exclude).
        return NodeCoreVerdict.UNKNOWN
    }

    // --- Synthetic control outbounds (no real user secrets) ---

    fun controlVlessRealityDomain(): JSONObject =
        JSONObject()
            .put("type", "vless")
            .put("tag", "ctrl-vless-reality-domain")
            .put("server", "example.org")
            .put("server_port", 443)
            .put("uuid", "11111111-1111-4111-8111-111111111111")
            .put(
                "tls",
                JSONObject()
                    .put("enabled", true)
                    .put("server_name", "www.example.com")
                    .put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome"))
                    .put(
                        "reality",
                        JSONObject()
                            .put("enabled", true)
                            .put("public_key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                            .put("short_id", "abcd"),
                    ),
            )

    fun controlVlessRealityIp(): JSONObject =
        controlVlessRealityDomain()
            .put("tag", "ctrl-vless-reality-ip")
            .put("server", "203.0.113.10")

    fun controlVlessXhttp(): JSONObject =
        JSONObject()
            .put("type", "vless")
            .put("tag", "ctrl-vless-xhttp")
            .put("server", "example.org")
            .put("server_port", 443)
            .put("uuid", "11111111-1111-4111-8111-111111111111")
            .put(
                "transport",
                JSONObject()
                    .put("type", "xhttp")
                    .put("path", "/")
                    .put("mode", "auto"),
            )
            .put(
                "tls",
                JSONObject()
                    .put("enabled", true)
                    .put("server_name", "www.example.com")
                    .put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome")),
            )

    fun controlHysteria2(): JSONObject =
        JSONObject()
            .put("type", "hysteria2")
            .put("tag", "ctrl-hysteria2")
            .put("server", "203.0.113.20")
            .put("server_port", 443)
            .put("password", "fixture-password")
            .put(
                "tls",
                JSONObject()
                    .put("enabled", true)
                    .put("server_name", "www.example.com")
                    .put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome")),
            )

    fun controlVlessWithDomainResolver(): JSONObject =
        controlVlessRealityDomain()
            .put("tag", "ctrl-vless-domain-resolver")
            .put("domain_resolver", DNS_DIRECT)

    fun invalidUnknownType(): JSONObject =
        JSONObject()
            .put("type", "not-a-real-outbound-type")
            .put("tag", "bad-unknown-type")
            .put("server", "example.org")
            .put("server_port", 443)

    fun invalidWrongFieldType(): JSONObject =
        JSONObject()
            .put("type", "vless")
            .put("tag", "bad-field-type")
            .put("server", "example.org")
            .put("server_port", "not-a-number")
            .put("uuid", "11111111-1111-4111-8111-111111111111")
}
