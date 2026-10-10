package io.nekohasekai.sfa.bg

/**
 * Pure helpers for R2b start intent resolution (unit-testable without Android Settings).
 */
object SwitchStartResolver {
    /** Intent target > 0 wins; otherwise fall back to persisted selection. */
    fun resolveProfileId(intentTargetProfileId: Long, settingsSelectedProfileId: Long): Long =
        if (intentTargetProfileId > 0L) intentTargetProfileId else settingsSelectedProfileId

    /** Intent switch id > 0 wins; otherwise use current generation. */
    fun resolveRequestId(intentSwitchRequestId: Long, currentSwitchRequestId: Long): Long =
        if (intentSwitchRequestId > 0L) intentSwitchRequestId else currentSwitchRequestId

    /**
     * Whether Settings.selectedProfile should be written after a start attempt.
     * Only on successful mark of an explicit target profile.
     */
    fun shouldPersistSelectedAfterLoad(
        targetProfileId: Long,
        markLoadedSucceeded: Boolean,
        requestIdStillCurrent: Boolean,
    ): Boolean = markLoadedSucceeded && targetProfileId > 0L && requestIdStillCurrent
}
