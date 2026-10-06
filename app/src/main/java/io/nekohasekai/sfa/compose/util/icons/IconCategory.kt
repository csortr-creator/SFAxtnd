package io.nekohasekai.sfa.compose.util.icons

import io.nekohasekai.sfa.compose.util.ProfileIcon

data class IconCategory(val name: String, val icons: List<ProfileIcon>) {
    val size: Int get() = icons.size
}
