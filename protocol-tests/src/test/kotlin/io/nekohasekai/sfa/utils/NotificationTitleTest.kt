package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTitleTest {
    @Test fun subscriptionModeKeepsNameAndServerModeFollowsNestedSelections() {
        val groups = listOf(NotificationTitle.Group("proxy", "auto"), NotificationTitle.Group("auto", "Poland"))
        assertEquals("Geodema", NotificationTitle.resolve("group", "Geodema", groups))
        assertEquals("Poland", NotificationTitle.resolve("server", "Geodema", groups))
        assertEquals("Germany", NotificationTitle.resolve("server", "Geodema", groups.map {
            if (it.tag == "auto") it.copy(selected = "Germany") else it
        }))
    }
    @Test fun missingSelectionAndCyclesFallBackToSubscription() {
        assertEquals("Geodema", NotificationTitle.resolve("server", "Geodema", emptyList()))
        assertEquals("Geodema", NotificationTitle.resolve("server", "Geodema", listOf(
            NotificationTitle.Group("a", "b"), NotificationTitle.Group("b", "a"))))
        assertEquals("SFAxtnd", NotificationTitle.resolve("server", "", emptyList()))
    }
}
