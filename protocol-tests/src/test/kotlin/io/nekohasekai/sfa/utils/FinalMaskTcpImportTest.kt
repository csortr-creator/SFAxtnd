package io.nekohasekai.sfa.utils

import java.net.URLEncoder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinalMaskTcpImportTest {
    private val mask = """{"tcp":[{"type":"header-custom","settings":{"clients":[[{"type":"str","packet":"HELLO\r\n"},{"type":"str","packet":"END\r\n"}]],"servers":[[{"type":"str","packet":"READY\r\n"}]]}}]}"""
    private fun link(value: String = mask, transport: String = "tcp", security: String = "none") =
        "vless://11111111-1111-4111-8111-111111111111@example.org:443?encryption=none&type=$transport&security=$security&fm=${URLEncoder.encode(value, "UTF-8")}#Masked"

    @Test fun literalHeaderSurvivesImportAndNativeConfiguration() {
        val result = SubscriptionContentParser().parse(link())
        assertEquals(1, result.report.imported)
        assertTrue(result.report.warnings.none { it.contains("fm") })
        val nodes = JSONObject(result.config).getJSONArray("outbounds")
        val node = (0 until nodes.length()).map { nodes.getJSONObject(it) }.single { it.has("server") }
        val header = node.getJSONObject("tcp_header")
        assertEquals("HELLO\r\nEND\r\n", header.getJSONArray("clients").getString(0))
        assertEquals("READY\r\n", header.getJSONArray("servers").getString(0))
        java.io.File("build/native-configs").mkdirs()
        java.io.File("build/native-configs/import-finalmask-tcp.json").writeText(result.config)
    }

    @Test fun unsupportedMaskItemsAndCombinationsAreNeverSilentlyDropped() {
        for (bad in listOf(
            mask.replace("header-custom", "sudoku"),
            mask.replace("\"type\":\"str\"", "\"type\":\"hex\""),
            mask.replace("\"packet\":\"HELLO", "\"delay\":10,\"packet\":\"HELLO"),
            """{"tcp":{}}""",
            """{"tcp":[{"type":"header-custom","settings":{"clients":[],"servers":[]}}]}""",
        )) assertTrue(runCatching { SubscriptionContentParser().parse(link(bad)) }.isFailure)
        assertTrue(runCatching { SubscriptionContentParser().parse(link(transport="ws")) }.isFailure)
        assertTrue(runCatching { SubscriptionContentParser().parse(link(security="tls")) }.isFailure)
    }

    @Test fun unmaskedNodeStillImportsWhenOtherNodeHasUnsupportedMask() {
        val result = SubscriptionContentParser().parse(link(mask.replace("header-custom", "sudoku")) +
            "\nvless://11111111-1111-4111-8111-111111111111@example.org:443?encryption=none#Plain")
        assertEquals(2, result.report.received)
        assertEquals(1, result.report.imported)
        assertEquals(ImportIssueCode.UNSUPPORTED_FEATURE, result.report.issues.single().code)
        assertEquals("Masked", result.report.issues.single().name)
    }
}
