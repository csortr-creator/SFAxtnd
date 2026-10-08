package io.nekohasekai.sfa.utils

/**
 * Result of a single outbound URLTest probe.
 *
 * Core method (sing-box urltest): TCP dial via outbound + HTTP HEAD to
 * [DEFAULT_PROBE_URL] (when URL is empty). HTTP status codes are **not** checked by
 * core — a 403/404/405 after a successful response still yields SUCCESS with delay.
 * Failures return an error from libbox; Kotlin must not discard it as delay=0 only.
 */
enum class ProbeStatus {
    UNTESTED,
    TESTING,
    SUCCESS,
    TIMEOUT,
    DNS_ERROR,
    HANDSHAKE_ERROR,
    HTTP_ERROR,
    PROBE_ERROR,
    CANCELLED,
}

data class ProbeOutcome(
    val status: ProbeStatus,
    val delayMs: Int = 0,
    /** Short, non-sensitive diagnostic for logs/UI (no credentials). */
    val detail: String = "",
) {
    val isSuccess: Boolean
        get() = status == ProbeStatus.SUCCESS && delayMs > 0

    companion object {
        /** Default URL when libbox/sing-box receives an empty test URL. */
        const val DEFAULT_PROBE_URL = "https://www.gstatic.com/generate_204"

        fun success(delayMs: Int) =
            ProbeOutcome(ProbeStatus.SUCCESS, delayMs.coerceAtLeast(0), "")

        fun untested() = ProbeOutcome(ProbeStatus.UNTESTED)

        /**
         * Map a libbox/platform throwable to a category.
         * Prefer exception **types** and stable English substrings from the Go/mobile stack;
         * do not rely on localized UI strings.
         */
        fun fromThrowable(error: Throwable): ProbeOutcome {
            if (error is java.util.concurrent.CancellationException ||
                error is kotlinx.coroutines.CancellationException
            ) {
                return ProbeOutcome(ProbeStatus.CANCELLED, detail = "cancelled")
            }
            val chain = generateSequence(error) { it.cause }.toList()
            val typeNames = chain.joinToString(" ") { it.javaClass.name }
            val messages =
                chain.mapNotNull { it.message?.lowercase() }.joinToString(" ")

            fun has(vararg tokens: String) =
                tokens.any { t -> typeNames.contains(t, ignoreCase = true) || messages.contains(t) }

            val status =
                when {
                    // Timeouts first: core often reports "dns: exchange failed … deadline exceeded"
                    // which is a timeout on the DNS exchange, not NXDOMAIN.
                    has(
                        "SocketTimeoutException",
                        "TimeoutException",
                        "deadline exceeded",
                        "context deadline",
                        "i/o timeout",
                        "timed out",
                    ) || (has("timeout") && !has("UnknownHostException")) -> ProbeStatus.TIMEOUT
                    has(
                        "UnknownHostException",
                        "NoAddressAssociatedWithHostname",
                        "no such host",
                        "no address associated",
                        "dns lookup",
                        "android_getaddrinfo",
                    ) || (has("dns") && has("fail", "error", "exchange", "resolve")) ->
                        ProbeStatus.DNS_ERROR
                    has(
                        "SSLException",
                        "SSLHandshakeException",
                        "CertificateException",
                        "TLS",
                        "handshake",
                        "certificate",
                        "x509",
                        "reality",
                    ) -> ProbeStatus.HANDSHAKE_ERROR
                    has("UnexpectedResponse", "HTTP status", "http error", "bad response") ->
                        ProbeStatus.HTTP_ERROR
                    else -> ProbeStatus.PROBE_ERROR
                }

            val detail =
                (error.message ?: error.javaClass.simpleName)
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .take(160)
            return ProbeOutcome(status, delayMs = 0, detail = detail)
        }
    }
}
