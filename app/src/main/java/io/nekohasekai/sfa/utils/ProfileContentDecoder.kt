package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import org.json.JSONObject

internal data class DecodedProfileContent(
    val content: ProfileContent,
    val report: SubscriptionImportReport?,
)

object ProfileContentDecoder {
    fun decode(data: ByteArray): ProfileContent = decodeWithReport(data).content

    internal fun decodeWithReport(data: ByteArray): DecodedProfileContent {
        val text = data.toString(Charsets.UTF_8).trim()
        if (!SubscriptionContentParser.supports(text))
            return DecodedProfileContent(Libbox.decodeProfileContent(data), null)
        val result = HTTPClient().use { it.parseSubscription(text) }
        Libbox.checkConfig(result.config)
        val name =
            JSONObject(result.config).getJSONArray("outbounds").let { outbounds ->
                (0 until outbounds.length())
                    .asSequence()
                    .map { outbounds.getJSONObject(it) }
                    .firstOrNull { it.has("server") }
                    ?.optString("tag") ?: "Imported servers"
            }
        val content =
            ProfileContent().apply {
                this.name = name
                type = Libbox.ProfileTypeLocal
                this.config = result.config
            }
        return DecodedProfileContent(content, result.report)
    }
}
