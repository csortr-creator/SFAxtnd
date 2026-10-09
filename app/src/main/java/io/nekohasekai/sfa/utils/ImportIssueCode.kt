package io.nekohasekai.sfa.utils

/**
 * B3-1 typed diagnostics for subscription import.
 *
 * Node-level codes go on [SubscriptionImportIssue].
 * Operation-level outcomes (fetch / final checkConfig / persistence) are reported
 * outside the per-node issue list — never as synthetic line=0 issues.
 */
enum class ImportIssueCode {
    /** Malformed single element (e.g. expected object in array). */
    FORMAT_ERROR,
    /** Share-link / field parse failure for one server. */
    PARSE_ERROR,
    /** Critical parameter known to be unsupported. */
    UNSUPPORTED_FEATURE,
    /** NodeCore rejected the node after a green family control. */
    NODE_CORE_INVALID,
    /** Ambiguous or non-classified node failure (safe default). */
    UNKNOWN,
    ;

    companion object {
        fun fromWire(raw: String?): ImportIssueCode {
            if (raw.isNullOrBlank()) return UNKNOWN
            return entries.find { it.name == raw } ?: UNKNOWN
        }
    }
}
