package io.nekohasekai.sfa.terminal

data class GhosttyCustomConfig(
    val raw: String = "",
) {
    companion object {
        fun parse(text: String): GhosttyCustomConfig = GhosttyCustomConfig(raw = text)
    }
}
