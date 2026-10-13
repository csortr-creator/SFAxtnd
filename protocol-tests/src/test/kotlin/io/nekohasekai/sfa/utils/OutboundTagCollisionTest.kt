package io.nekohasekai.sfa.utils

import java.io.File
import java.net.URLEncoder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OutboundTagCollisionTest {
    private fun config(names: List<String>): JSONObject {
        val links = names.joinToString("\n") {
            "ss://aes-128-gcm:password@example.org:8388#${URLEncoder.encode(it, "UTF-8").replace("+", "%20")}"
        }
        val result = SubscriptionContentParser().parse(links)
        assertEquals(names.size, result.report.imported)
        assertTrue(result.report.issues.isEmpty())
        return JSONObject(result.config)
    }

    private fun assertSelectable(root: JSONObject, count: Int) {
        val outbounds = root.getJSONArray("outbounds")
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
        assertEquals("Every outbound must have a distinct tag", tags.size, tags.toSet().size)
        val selector = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == SubscriptionRouting.NORMAL_SELECTOR_TAG }
        val members = selector.getJSONArray("outbounds")
        val choices = (0 until members.length()).map { members.getString(it) }
        assertEquals(count + 1, choices.size)
        assertEquals(choices.size, choices.toSet().size)
        assertTrue(choices.all { it in tags })
        assertEquals("direct", choices.last())
    }

    @Test fun explicitSuffixCannotCollideWithGeneratedSuffix() {
        val root = config(listOf("A", "A", "A (1)", "A"))
        // Emitted before assertions: native check also reproduces the old duplicate-tag failure.
        File("build/native-configs").mkdirs()
        File("build/native-configs/import-tag-collision.json").writeText(root.toString())
        assertSelectable(root, 4)
    }

    @Test fun generatedNamesCannotCollideWithReservedTags() {
        val names = listOf("direct", "direct (1)", "block", "block (1)",
            SubscriptionRouting.NORMAL_SELECTOR_TAG, "${SubscriptionRouting.NORMAL_SELECTOR_TAG} (1)")
        assertSelectable(config(names), names.size)
    }

    @Test fun suffixAlreadyPresentBeforeDuplicateIsAlsoSafe() {
        assertSelectable(config(listOf("A (1)", "A", "A", "A (2)", "A")), 5)
    }
}
