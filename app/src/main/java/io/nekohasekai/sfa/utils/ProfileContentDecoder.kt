package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import org.json.JSONObject

object ProfileContentDecoder {
    fun decode(data: ByteArray): ProfileContent {
        val text = data.toString(Charsets.UTF_8).trim()
        if (!ProxyLinkParser.isShareLink(text)) return Libbox.decodeProfileContent(data)
        val config = HTTPClient().use { it.processSubscriptionContent(text) }
        Libbox.checkConfig(config)
        val name = JSONObject(config).getJSONArray("outbounds").let { outbounds ->
            (0 until outbounds.length()).asSequence().map { outbounds.getJSONObject(it) }
                .firstOrNull { it.has("server") }?.optString("tag") ?: "Imported servers"
        }
        return ProfileContent().apply {
            this.name = name
            type = Libbox.ProfileTypeLocal
            this.config = config
        }
    }
}
