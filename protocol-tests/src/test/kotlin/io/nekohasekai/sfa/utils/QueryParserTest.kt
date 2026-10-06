package io.nekohasekai.sfa.utils

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParserTest {

    @Test
    fun parseQueryParams_emptyOrNull() {
        assertTrue(ProxyLinkParser.query(null).isEmpty())
        assertTrue(ProxyLinkParser.query("").isEmpty())
    }

    @Test
    fun parseQueryParams_basic() {
        val result = ProxyLinkParser.query("key1=value1&key2=value2")
        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
    }

    @Test
    fun parseQueryParams_urlEncoded() {
        val result = ProxyLinkParser.query("key1=value%201&key2=%D1%82%D0%B5%D1%81%D1%82&%D0%BA%D0%BB%D1%8E%D1%87=%D0%B7%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5")
        assertEquals(3, result.size)
        assertEquals("value 1", result["key1"])
        assertEquals("тест", result["key2"]) // URL decoded
        assertEquals("значение", result["ключ"]) // URL decoded key
    }

    @Test
    fun parseQueryParams_invalidPairsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.query("key1=value1&invalid_pair&=value2&key3=value3")
        }
    }

    @Test
    fun parseQueryParams_emptyValue() {
        val result = ProxyLinkParser.query("key1=&key2=value2")
        assertEquals(2, result.size)
        assertEquals("", result["key1"])
        assertEquals("value2", result["key2"])
    }

    @Test
    fun parseQueryParams_duplicateKeysRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyLinkParser.query("key1=value1&key1=value2")
        }
    }
}
