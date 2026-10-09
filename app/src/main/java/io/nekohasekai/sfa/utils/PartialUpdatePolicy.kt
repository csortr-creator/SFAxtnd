package io.nekohasekai.sfa.utils

/**
 * B3-3a: pure decision model for applying a partially imported subscription.
 *
 * Not wired to production paths — callers (B3-3b/c) must invoke [decide] and honor the result.
 *
 * Statistics reliability:
 * - [StatsReliability.COMPLETE]: received/imported/rejected are trustworthy (URI list path).
 * - [StatsReliability.INCOMPLETE]: some formats under-report issues; do not treat imported==received
 *   as proof of zero rejections.
 * - [StatsReliability.UNKNOWN]: no usable counters.
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
    /** Commit the new config. */
    APPLY,
    /** Do not commit; keep previous file when present. */
    REJECT_KEEP_LKG,
    /** Manual path only: hold candidate until user confirms. */
    AWAIT_CONFIRMATION,
    /** First import with nothing usable — do not create profile. */
    REJECT_FIRST,
}

/**
 * Inputs are plain integers and flags — no Throwables, URLs, or secrets.
 *
 * [rejected] should match issues.size when known; may be 0 when reliability is not COMPLETE.
 * [hadPreviousConfig] reflects an existing LKG file / profile (not merely Room row intent).
 */
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
     * Decide whether to apply the candidate config.
     *
     * Rules (COMPLETE reliability):
     * - imported <= 0 → REJECT_FIRST / REJECT_KEEP_LKG
     * - full (imported == received, rejected == 0, received > 0) → APPLY
     * - partial (imported < received or rejected > 0) + FIRST → APPLY
     * - partial + AUTO → REJECT_KEEP_LKG
     * - partial + MANUAL → AWAIT_CONFIRMATION
     *
     * INCOMPLETE / UNKNOWN: never treat as "full"; FIRST still APPLY if imported > 0;
     * AUTO → REJECT_KEEP_LKG if hadPrevious; MANUAL → AWAIT_CONFIRMATION if hadPrevious
     * else APPLY when imported > 0.
     *
     * No percentage threshold — provider shrink with full import (10/10) is APPLY.
     */
    fun decide(input: PartialUpdateInput): PolicyDecision {
        val received = input.received.coerceAtLeast(0)
        val imported = input.imported.coerceAtLeast(0)
        val rejected = input.rejected.coerceAtLeast(0)

        if (imported <= 0) {
            return if (input.hadPreviousConfig || input.trigger != UpdateTrigger.FIRST_IMPORT) {
                PolicyDecision.REJECT_KEEP_LKG
            } else {
                PolicyDecision.REJECT_FIRST
            }
        }

        return when (input.reliability) {
            StatsReliability.COMPLETE -> decideComplete(input.trigger, received, imported, rejected)
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
        val isPartial = !isFull

        if (isFull) return PolicyDecision.APPLY

        // Partial with at least one imported node
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
        // Cannot prove full import — conservative for updates with LKG.
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
}

/**
 * Contract sketch for B3-3c stale-safe confirmation (not implemented here).
 *
 * A pending candidate must capture the profile revision at prepare time.
 * [ProfileConfigCommit] generation tokens bump on every beginOperation — that alone is not a
 * durable "successful change" revision. B3-3c should introduce an explicit per-profile
 * content/revision counter incremented only after a successful file commit (or metadata-ok path),
 * store it on the pending holder together with content hash, and under the per-profile Mutex on
 * confirm:
 * 1. reject if tombstone / Room missing;
 * 2. reject if current revision != pending.expectedRevision;
 * 3. beginOperation + commit only if still matching;
 * so an older dialog cannot overwrite a newer successful update.
 */
data class PendingImportCandidate(
    val profileId: Long,
    val expectedRevision: Long,
    val contentHash: String,
    val received: Int,
    val imported: Int,
)
