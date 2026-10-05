package io.nekohasekai.sfa.utils

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.file.Files

class RuleSetUpdaterTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        tempDir = Files.createTempDirectory("test_files").toFile()
        `when`(mockContext.filesDir).thenReturn(tempDir)
    }

    @Test
    fun testRuleSetTransformation() = runBlocking {
        // Create a dummy remote ruleset JSON
        val root = JSONObject()
        val route = JSONObject()
        val ruleSets = JSONArray()

        val remoteRuleSet = JSONObject().apply {
            put("type", "remote")
            put("tag", "geosite-test")
            put("url", "https://example.com/geosite-test.srs")
            put("download_detour", "direct")
        }
        ruleSets.put(remoteRuleSet)
        route.put("rule_set", ruleSets)
        root.put("route", route)

        val configJson = root.toString()

        // Ensure the directory exists to simulate a "successful" fake download by creating a dummy file directly.
        val ruleSetDir = File(tempDir, "rule_sets")
        ruleSetDir.mkdirs()
        val dummyFile = File(ruleSetDir, "geosite-test.srs")
        dummyFile.writeText("dummy content")

        // Call the updater (with interval -1 to just check if it transforms when file exists)
        val resultJsonString = RuleSetUpdater.ensureRuleSets(mockContext, configJson, -1L)
        val resultJson = JSONObject(resultJsonString)

        val resultRoute = resultJson.getJSONObject("route")
        val resultRuleSets = resultRoute.getJSONArray("rule_set")
        val resultRuleSet = resultRuleSets.getJSONObject(0)

        // Verify transformations
        assertEquals("local", resultRuleSet.getString("type"))
        assertTrue(resultRuleSet.has("path"))
        assertFalse(resultRuleSet.has("url"))
        assertFalse(resultRuleSet.has("download_detour"))
        assertEquals(dummyFile.absolutePath, resultRuleSet.getString("path"))
    }
}
