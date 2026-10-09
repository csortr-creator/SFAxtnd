package io.nekohasekai.sfa.utils

/**
 * Structure of the subscription overflow menu (UI-1a).
 * Delete stays in the model for layout/regression, but is not actionable until UI-1d.
 */
object ProfileOverflowMenuModel {
    enum class PrimaryAction {
        Edit,
        Share,
        Delete,
    }

    enum class ShareAction {
        SaveFile,
        ShareFile,
        SaveJson,
        ShareJson,
        ShareUrl,
        ShareQrs,
    }

    /** Top-level actions in order. */
    fun primaryActions(): List<PrimaryAction> =
        listOf(PrimaryAction.Edit, PrimaryAction.Share, PrimaryAction.Delete)

    /**
     * Share/export actions nested under «Поделиться».
     * [isRemote] controls whether Share URL is offered.
     */
    fun shareActions(isRemote: Boolean): List<ShareAction> {
        val base =
            listOf(
                ShareAction.SaveFile,
                ShareAction.ShareFile,
                ShareAction.SaveJson,
                ShareAction.ShareJson,
            )
        return if (isRemote) {
            base + ShareAction.ShareUrl + ShareAction.ShareQrs
        } else {
            base + ShareAction.ShareQrs
        }
    }

    /** Delete is visible but not enabled until safe backend (UI-1b/c/d). */
    const val DELETE_ENABLED: Boolean = true
}
