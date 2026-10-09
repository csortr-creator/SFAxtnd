package io.nekohasekai.sfa.utils

import org.junit.Assert.*
import org.junit.Test

class ProfileOverflowMenuModelTest {
    @Test
    fun primaryMenuIsEditShareDeleteInOrder() {
        assertEquals(
            listOf(
                ProfileOverflowMenuModel.PrimaryAction.Edit,
                ProfileOverflowMenuModel.PrimaryAction.Share,
                ProfileOverflowMenuModel.PrimaryAction.Delete,
            ),
            ProfileOverflowMenuModel.primaryActions(),
        )
        assertTrue(
            "Delete is enabled after UI-1c/1d safe backend",
            ProfileOverflowMenuModel.DELETE_ENABLED,
        )
    }

    @Test
    fun shareSubmenuKeepsAllExportPathsAndUrlOnlyForRemote() {
        val remote = ProfileOverflowMenuModel.shareActions(isRemote = true)
        val local = ProfileOverflowMenuModel.shareActions(isRemote = false)
        assertTrue(remote.contains(ProfileOverflowMenuModel.ShareAction.SaveFile))
        assertTrue(remote.contains(ProfileOverflowMenuModel.ShareAction.ShareFile))
        assertTrue(remote.contains(ProfileOverflowMenuModel.ShareAction.SaveJson))
        assertTrue(remote.contains(ProfileOverflowMenuModel.ShareAction.ShareJson))
        assertTrue(remote.contains(ProfileOverflowMenuModel.ShareAction.ShareUrl))
        assertTrue(remote.contains(ProfileOverflowMenuModel.ShareAction.ShareQrs))
        assertFalse(local.contains(ProfileOverflowMenuModel.ShareAction.ShareUrl))
        assertTrue(local.contains(ProfileOverflowMenuModel.ShareAction.ShareQrs))
        assertEquals(6, remote.size)
        assertEquals(5, local.size)
    }
}
