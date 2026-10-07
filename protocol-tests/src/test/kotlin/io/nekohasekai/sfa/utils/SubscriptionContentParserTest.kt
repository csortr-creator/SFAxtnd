package io.nekohasekai.sfa.utils

import java.io.File
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionContentParserTest {
    private val valid = "ss://aes-128-gcm:password@example.org:8388#Good"

    private fun encoded(text: String) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())

    @Test
    fun plaintext2022AndIpv6AreNotDecodedAsBase64() {
        val password = Base64.getEncoder().encodeToString(ByteArray(32) { 42 })
        val node =
            ProxyLinkParser.shadowsocks(
                "ss://2022-blake3-aes-256-gcm:$password@[2001:db8::1]:8443#2022"
            )
        assertEquals("2022-blake3-aes-256-gcm", node.getString("method"))
        assertEquals(password, node.getString("password"))
        assertEquals("2001:db8::1", node.getString("server"))
        assertEquals(8443, node.getInt("server_port"))
        val config =
            SubscriptionContentParser()
                .parse("ss://2022-blake3-aes-256-gcm:$password@example.org:8443")
                .config
        File("build/native-configs").mkdirs()
        File("build/native-configs/import-ss2022.json").writeText(config)
    }

    @Test
    fun sip002Base64PreservesLiteralPercentAndPlus() {
        val node =
            ProxyLinkParser.shadowsocks(
                "ss://${encoded("aes-128-gcm:a+%20:b")}@example.org:8388#A+B"
            )
        assertEquals("a+%20:b", node.getString("password"))
        assertEquals("A+B", node.getString("tag"))
    }

    @Test
    fun plaintextPercentEncodingAndLegacyWholeLinkWork() {
        assertEquals(
            "a+b:c@d",
            ProxyLinkParser.shadowsocks("ss://aes-128-gcm:a%2Bb%3Ac%40d@example.org:8388")
                .getString("password"),
        )
        val legacy =
            ProxyLinkParser.shadowsocks("ss://${encoded("aes-128-gcm:a@b@example.org:8388")}")
        assertEquals("a@b", legacy.getString("password"))
    }

    @Test
    fun pluginIsPreserved() {
        val node =
            ProxyLinkParser.shadowsocks(
                "$valid".substringBefore('#') +
                    "?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dexample.org"
            )
        assertEquals("obfs-local", node.getString("plugin"))
        assertEquals("obfs=http;obfs-host=example.org", node.getString("plugin_opts"))
    }

    @Test
    fun mixedSubscriptionReportsFailuresWithOriginalLineNumbers() {
        val result =
            SubscriptionContentParser()
                .parse(
                    "# ignored\n$valid\nss://broken@example.org:8388#Bad\nsocks://secret@example.org:443#Unknown"
                )
        assertEquals(3, result.report.received)
        assertEquals(1, result.report.imported)
        assertEquals(listOf(3, 4), result.report.issues.map { it.line })
        assertFalse(result.report.displayText().contains("secret"))
        assertFalse(result.report.displayText().contains("://"))
    }

    @Test
    fun nativeValidationFailureDoesNotDiscardValidNeighboursOrExposeCredentials() {
        val result =
            SubscriptionContentParser(
                    validateNode = { node ->
                        if (node.getString("tag") == "Rejected")
                            error("invalid password: confidential")
                    }
                )
                .parse("$valid\n${valid.replace("Good", "Rejected")}")
        assertEquals(1, result.report.imported)
        assertEquals(1, result.report.issues.size)
        assertFalse(result.report.displayText().contains("confidential"))
    }

    @Test
    fun realityWithoutPublicKeyIsRejectedInsteadOfDowngraded() {
        val result =
            SubscriptionContentParser()
                .parse(
                    "$valid\nvless://11111111-1111-4111-8111-111111111111@example.org:443?security=reality#Broken"
                )
        assertEquals(1, result.report.imported)
        assertTrue(result.report.issues.single().reason.contains("public key"))
    }

    @Test
    fun unknownConnectionParametersAreReported() {
        val result =
            SubscriptionContentParser()
                .parse(
                    "$valid\nvless://11111111-1111-4111-8111-111111111111@example.org:443?security=tls&sni=example.org&newEncryption=secret#Unsupported"
                )
        // A+: optional unknown query keys are PARTIAL (warning), not a hard reject.
        assertTrue(result.report.issues.isEmpty())
        assertTrue(
            result.report.warnings.any { it.contains("newEncryption") && it.contains("Частичная") },
        )
        assertFalse(result.report.displayText().contains("secret"))
    }


    @Test
    fun invalidPortsAndEncryptionMethodsAreRejected() {
        for (link in
            listOf(
                "ss://aes-128-gcm:p@example.org:broken",
                "ss://aes-128-gcm:p@example.org:0",
                "ss://garbage:p@example.org:8388",
            )) {
            val result = SubscriptionContentParser().parse("$valid\n$link")
            assertEquals(1, result.report.imported)
            assertEquals(1, result.report.issues.size)
        }
    }

    @Test
    fun base64SubscriptionSupportsMissingPadding() {
        val result = SubscriptionContentParser().parse(encoded("$valid\n$valid"))
        assertEquals(2, result.report.imported)
        val outbounds = JSONObject(result.config).getJSONArray("outbounds")
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun emptyOrEntirelyInvalidSubscriptionsFailClearly() {
        for (content in listOf("", "ss://broken@example.org:8388")) {
            try {
                SubscriptionContentParser().parse(content)
                fail("Expected failure")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message.orEmpty().contains("Нет пригодных серверов"))
            }
        }
    }

    @Test
    fun reportsRoundTripWithoutCredentials() {
        val file = File.createTempFile("import-report", ".json")
        try {
            val report =
                SubscriptionContentParser().parse("$valid\nss://broken@example.org:8388#Bad").report
            report.save(file.path)
            assertEquals(report, SubscriptionImportReport.read(file.path))
            assertFalse(File("${file.path}.import.json").readText().contains("password"))
        } finally {
            File("${file.path}.import.json").delete()
            file.delete()
        }
    }

    @Test
    fun credentialsAreSeparatedBeforeQueryPathsContainingAtSigns() {
        val result =
            SubscriptionContentParser()
                .parse(
                    "vless://11111111-1111-4111-8111-111111111111@example.org:443?security=tls&type=ws&path=/user@example#Valid"
                )
        assertEquals(1, result.report.imported)
        val nodes = JSONObject(result.config).getJSONArray("outbounds")
        val node =
            (0 until nodes.length())
                .map { nodes.getJSONObject(it) }
                .first { it.optString("type") == "vless" }
        assertEquals("/user@example", node.getJSONObject("transport").getString("path"))
    }

    @Test
    fun alternateIdentifiersSupportedByTheCoreArePreserved() {
        val result = SubscriptionContentParser().parse("vless://custom-user@example.org:443#User")
        val nodes = JSONObject(result.config).getJSONArray("outbounds")
        assertEquals(
            "custom-user",
            (0 until nodes.length())
                .map { nodes.getJSONObject(it) }
                .first { it.optString("type") == "vless" }
                .getString("uuid"),
        )
    }

    @Test
    fun duplicateParametersAreNotSilentlyOverwritten() {
        val result =
            SubscriptionContentParser()
                .parse("$valid\nvless://user@example.org:443?security=tls&security=none")
        assertEquals(1, result.report.issues.size)
        assertTrue(result.report.issues.single().reason.contains("duplicate"))
    }
    @Test fun nativeConfigurationReferencesAreValidatedAsAWhole() {
        val node = ProxyLinkParser.shadowsocks(valid)
        val selector = JSONObject().put("type", "selector").put("tag", "Custom").put("outbounds", org.json.JSONArray().put("Good"))
        val array = org.json.JSONArray().put(selector).put(node)
        val result = SubscriptionContentParser(validateNode = { error("Must not validate dependent JSON outbounds in isolation") }).parse(array.toString())
        assertTrue(result.report.issues.isEmpty())
        File("build/native-configs").mkdirs()
        File("build/native-configs/import-json-array.json").writeText(result.config)
    }

}
