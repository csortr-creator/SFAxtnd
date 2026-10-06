package io.nekohasekai.sfa.compose.util.icons

import androidx.compose.ui.graphics.vector.ImageVector
import io.nekohasekai.sfa.compose.util.ProfileIcon

object MaterialIconsLibrary {
    val categories: List<IconCategory> =
        listOf(
            IconCategory("Action", ActionIcons.icons),
            IconCategory("Alert", AlertIcons.icons),
            IconCategory("Audio & Video", AVIcons.icons),
            IconCategory("Communication", CommunicationIcons.icons),
            IconCategory("Content", ContentIcons.icons),
            IconCategory("Device", DeviceIcons.icons),
            IconCategory("Editor", EditorIcons.icons),
            IconCategory("File", FileIcons.icons),
            IconCategory("Hardware", HardwareIcons.icons),
            IconCategory("Image", ImageIcons.icons),
            IconCategory("Maps", MapsIcons.icons),
            IconCategory("Navigation", NavigationIcons.icons),
            IconCategory("Notification", NotificationIcons.icons),
            IconCategory("Places", PlacesIcons.icons),
            IconCategory("Social", SocialIcons.icons),
            IconCategory("Toggle", ToggleIcons.icons),
        )

    fun getAllIcons(): List<ProfileIcon> = categories.flatMap { it.icons }

    fun getIconById(id: String): ImageVector? = getAllIcons().find { it.id == id }?.icon

    fun getCategoryForIcon(iconId: String): String? {
        categories.forEach { category ->
            if (category.icons.any { it.id == iconId }) {
                return category.name
            }
        }
        return null
    }

    fun searchIcons(query: String): List<ProfileIcon> {
        if (query.isBlank()) return getAllIcons()

        val lowercaseQuery = query.lowercase()
        return getAllIcons().filter {
            it.id.contains(lowercaseQuery) ||
                it.label.lowercase().contains(lowercaseQuery)
        }
    }

    fun getIconsByCategory(categoryName: String): List<ProfileIcon> = categories.find { it.name.equals(categoryName, ignoreCase = true) }?.icons
        ?: emptyList()

    fun getTotalIconCount(): Int = categories.sumOf { it.icons.size }

    fun getCategoryNames(): List<String> = categories.map { it.name }
}
