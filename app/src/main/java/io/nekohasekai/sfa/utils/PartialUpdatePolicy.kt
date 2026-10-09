package io.nekohasekai.sfa.utils

/**
 * B3-3a/b: pure decision model for applying a partially imported subscription.
 */
enum class UpdateTrigger {
    FIRST_IMPORT,
    MANUAL_UPDATE,
    AUTO_UPDATE,
}

enum class StatsReliability {
    COMPLETE,
    INCOMPLETE,
    UNKNOWN,
}

enum class PolicyDecision {
    APPLY,
    REJECT_KEEP_LKG,
    AWAIT_CONFIRMATION,
    REJECT_FIRST,
}

data class PartialUpdateInput(
    val trigger: UpdateTrigger,
    val received: Int,
    val imported: Int,
    val rejected: Int,
    val reliability: StatsReliability,
    val hadPreviousConfig: Boolean,
)

object PartialUpdatePolicy {
    /**
     * No percentage threshold. Contradictory COMPLETE counters are treated as UNKNOWN
     * (not coerced into APPLY via coerceAtLeast).
     */
    fun decide(input: PartialUpdateInput): PolicyDecision {
        val received = input.received
        val imported = input.imported
        val rejected = input.rejected

        val reliability =
            when {
                input.reliability == StatsReliability.UNKNOWN -> StatsReliability.UNKNOWN
                received < 0 || imported < 0 || rejected < 0 -> StatsReliability.UNKNOWN
                imported > received -> StatsReliability.UNKNOWN
                rejected > received -> StatsReliability.UNKNOWN
                input.reliability == StatsReliability.COMPLETE &&
                    received > 0 &&
                    imported + rejected != received -> StatsReliability.UNKNOWN
                else -> input.reliability
            }

        if (imported <= 0) {
            return if (input.hadPreviousConfig || input.trigger != UpdateTrigger.FIRST_IMPORT) {
                PolicyDecision.REJECT_KEEP_LKG
            } else {
                PolicyDecision.REJECT_FIRST
            }
        }

        return when (reliability) {
            StatsReliability.COMPLETE ->
                decideComplete(input.trigger, received, imported, rejected)
            StatsReliability.INCOMPLETE,
            StatsReliability.UNKNOWN,
            -> decideUncertain(input.trigger, input.hadPreviousConfig, imported)
        }
    }

    private fun decideComplete(
        trigger: UpdateTrigger,
        received: Int,
        imported: Int,
        rejected: Int,
    ): PolicyDecision {
        val isFull = received > 0 && imported == received && rejected == 0
        if (isFull) return PolicyDecision.APPLY
        return when (trigger) {
            UpdateTrigger.FIRST_IMPORT -> PolicyDecision.APPLY
            UpdateTrigger.AUTO_UPDATE -> PolicyDecision.REJECT_KEEP_LKG
            UpdateTrigger.MANUAL_UPDATE -> PolicyDecision.AWAIT_CONFIRMATION
        }
    }

    private fun decideUncertain(
        trigger: UpdateTrigger,
        hadPrevious: Boolean,
        imported: Int,
    ): PolicyDecision {
        if (imported <= 0) {
            return if (hadPrevious) PolicyDecision.REJECT_KEEP_LKG else PolicyDecision.REJECT_FIRST
        }
        return when (trigger) {
            UpdateTrigger.FIRST_IMPORT -> PolicyDecision.APPLY
            UpdateTrigger.AUTO_UPDATE ->
                if (hadPrevious) PolicyDecision.REJECT_KEEP_LKG else PolicyDecision.APPLY
            UpdateTrigger.MANUAL_UPDATE ->
                if (hadPrevious) PolicyDecision.AWAIT_CONFIRMATION else PolicyDecision.APPLY
        }
    }

    /**
     * - null → UNKNOWN
     * - URI/Clash/Xray accept() path: COMPLETE when imported+rejected == received
     * - Native sing-box JSON: proxy outbound count, no per-node skips → COMPLETE when consistent
     * - contradictory counters → UNKNOWN
     */
    internal fun reliabilityOf(report: SubscriptionImportReport?): StatsReliability {
        if (report == null) return StatsReliability.UNKNOWN
        val received = report.received
        val imported = report.imported
        val rejected = report.issues.size
        if (received < 0 || imported < 0 || rejected < 0) return StatsReliability.UNKNOWN
        if (imported > received || rejected > received) return StatsReliability.UNKNOWN
        if (received > 0 && imported + rejected != received) return StatsReliability.UNKNOWN
        return StatsReliability.COMPLETE
    }
}

data class PendingImportCandidate(
    val profileId: Long,
    val expectedRevision: Long,
    val contentHash: String,
    val received: Int,
    val imported: Int,
)
