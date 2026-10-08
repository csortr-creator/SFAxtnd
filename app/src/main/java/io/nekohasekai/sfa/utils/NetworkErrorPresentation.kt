package io.nekohasekai.sfa.utils

/**
 * Safe classification of subscription **fetch** failures.
 *
 * Never exposes [Throwable.message] or cause-chain text to UI, logcat, reports, or Settings.
 * Classification prefers exception types and fixed diagnostic phrases that do not embed URLs.
 */
enum class NetworkErrorKind {
    TLS_TIMEOUT,
    TLS,
    DNS,
    TIMEOUT,
    HTTP_AUTH,
    HTTP_STATUS,
    CONNECTION,
    UNKNOWN,
}

data class SafeNetworkError(
    val kind: NetworkErrorKind,
    /** Stable machine code for logs only, e.g. NET_TLS_TIMEOUT. */
    val code: String,
)

object NetworkErrorPresentation {

    fun classify(error: Throwable): SafeNetworkError {
        val typeBlob =
            generateSequence(error) { it.cause }
                .map { it.javaClass.name }
                .joinToString(" ")
                .lowercase()

        val hintBlob =
            generateSequence(error) { it.cause }
                .mapNotNull { extractSafeHint(it.message) }
                .joinToString(" ")
                .lowercase()

        val kind =
            when {
                "tls handshake timeout" in hintBlob -> NetworkErrorKind.TLS_TIMEOUT
                hasType(typeBlob, "sslhandshakeexception", "sslexception") ||
                    (hasHint(hintBlob, "tls", "ssl", "handshake", "certificate", "x509") &&
                        "timeout" !in hintBlob) -> NetworkErrorKind.TLS
                hasType(typeBlob, "unknownhostexception") ||
                    hasHint(hintBlob, "no such host", "no address associated") ->
                    NetworkErrorKind.DNS
                hasType(typeBlob, "sockettimeoutexception", "timeoutexception") ||
                    hasHint(hintBlob, "deadline exceeded", "i/o timeout") ||
                    ("timeout" in hintBlob && "tls handshake timeout" !in hintBlob) ->
                    NetworkErrorKind.TIMEOUT
                hasHint(
                    hintBlob,
                    "status 401",
                    "status 403",
                    " 401",
                    " 403",
                    "unauthorized",
                    "forbidden",
                ) -> NetworkErrorKind.HTTP_AUTH
                hasHint(
                    hintBlob,
                    "status 404",
                    "status 500",
                    "status 502",
                    "status 503",
                    " 404",
                    " 500",
                ) -> NetworkErrorKind.HTTP_STATUS
                hasType(typeBlob, "connectexception") ||
                    hasHint(
                        hintBlob,
                        "connection refused",
                        "connection reset",
                        "network is unreachable",
                        "enotconn",
                    ) -> NetworkErrorKind.CONNECTION
                else -> NetworkErrorKind.UNKNOWN
            }

        return SafeNetworkError(kind, code = "NET_${kind.name}")
    }

    fun logCode(error: Throwable): String = classify(error).code

    /**
     * From a message that may embed a subscription URL, keep only known fixed phrases
     * or nothing. Never returns URL material.
     */
    internal fun extractSafeHint(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val lower = message.lowercase()
        val embedsUrl =
            "://" in message || "?" in message || "@" in message || "get \"" in lower
        val known =
            listOf(
                "tls handshake timeout",
                "handshake failure",
                "handshake timeout",
                "certificate",
                "x509",
                "deadline exceeded",
                "i/o timeout",
                "no such host",
                "no address associated",
                "connection refused",
                "connection reset",
                "network is unreachable",
                "unauthorized",
                "forbidden",
                "status 401",
                "status 403",
                "status 404",
                "status 500",
                "status 502",
                "status 503",
            )
        val found = known.filter { it in lower }
        if (found.isNotEmpty()) return found.joinToString(" ")
        if (embedsUrl) return null
        return null
    }

    private fun hasType(blob: String, vararg tokens: String) = tokens.any { it in blob }

    private fun hasHint(blob: String, vararg tokens: String) = tokens.any { it in blob }
}
