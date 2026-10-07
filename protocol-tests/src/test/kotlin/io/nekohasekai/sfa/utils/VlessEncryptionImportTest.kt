package io.nekohasekai.sfa.utils

import java.io.File
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VlessEncryptionImportTest {
    private val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
    private val uuid = "11111111-1111-4111-8111-111111111111"

    @Test
    fun nativeEncryptionAndXhttpSurviveShareLinkImport() {
        for (mode in listOf("0rtt", "1rtt")) {
            val encryption = "mlkem768x25519plus.native.$mode.$key"
            val result =
                SubscriptionContentParser()
                    .parse(
                        "vless://$uuid@example.org:443?type=xhttp&encryption=$encryption#encrypted"
                    )
            val node = nodes(result).single()
            assertEquals(encryption, node.getString("encryption"))
            assertEquals("xhttp", node.getJSONObject("transport").getString("type"))
            assertEquals(1, result.report.imported)
            File("build/native-configs").mkdirs()
            File("build/native-configs/import-vless-encryption-$mode.json").writeText(result.config)
        }
    }

    @Test
    fun xrayAndClashPreserveEncryption() {
        val encryption = "mlkem768x25519plus.native.0rtt.$key"
        val xray =
            """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.org","port":443,"users":[{"id":"$uuid","encryption":"$encryption"}]}]}}]}"""
        val clash =
            """{"proxies":[{"name":"encrypted","type":"vless","server":"example.org","port":443,"uuid":"$uuid","encryption":"$encryption"}]}"""
        for (content in listOf(xray, clash)) {
            val result = SubscriptionContentParser().parse(content)
            assertEquals(encryption, nodes(result).single().getString("encryption"))
        }
    }

    @Test
    fun invalidKeysAndUnsupportedProfilesAreSkippedWithoutLeakingKeys() {
        val bad =
            listOf(
                "mlkem768x25519plus.random.0rtt.$key",
                "mlkem768x25519plus.native.0rtt.bad",
                "mlkem768x25519plus.native.0rtt.$key.$key",
            )
        for (encryption in bad) {
            val result =
                SubscriptionContentParser()
                    .parse(
                        "vless://$uuid@example.org:443?encryption=none#valid\nvless://$uuid@example.org:443?encryption=$encryption#invalid"
                    )
            assertEquals(1, result.report.imported)
            assertEquals(1, result.report.issues.size)
            assertFalse(result.report.issues.single().reason.contains(key))
        }
        assertTrue(
            runCatching {
                    ProxyLinkParser.applyVlessEncryption(
                        JSONObject(),
                        "mlkem768x25519plus.native.0rtt.$key",
                        "xtls-rprx-vision",
                    )
                }
                .isFailure
        )
    }

    private fun nodes(result: SubscriptionImportResult): List<JSONObject> {
        val array = JSONObject(result.config).getJSONArray("outbounds")
        return (0 until array.length()).map { array.getJSONObject(it) }.filter { it.has("server") }
    }
}
