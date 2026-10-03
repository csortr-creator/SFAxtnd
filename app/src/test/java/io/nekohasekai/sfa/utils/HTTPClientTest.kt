package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HTTPClientTest {

    @Test
    fun parseQueryParams_emptyOrNull() {
        val client = HTTPClient()
        assertTrue(client.parseQueryParams(null).isEmpty())
        assertTrue(client.parseQueryParams("").isEmpty())
    }

    @Test
    fun parseQueryParams_basic() {
        val client = HTTPClient()
        val result = client.parseQueryParams("key1=value1&key2=value2")
        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
    }

    @Test
    fun parseQueryParams_urlEncoded() {
        val client = HTTPClient()
        val result = client.parseQueryParams("key1=value%201&key2=%D1%82%D0%B5%D1%81%D1%82&%D0%BA%D0%BB%D1%8E%D1%87=%D0%B7%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5")
        assertEquals(3, result.size)
        assertEquals("value 1", result["key1"])
        assertEquals("тест", result["key2"]) // URL decoded
        assertEquals("значение", result["ключ"]) // URL decoded key
    }

    @Test
    fun parseQueryParams_invalidPairsIgnored() {
        val client = HTTPClient()
        // Missing "=", "=" at the beginning (idx == 0)
        val result = client.parseQueryParams("key1=value1&invalid_pair&=value2&key3=value3")
        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value3", result["key3"])
        assertTrue(!result.containsKey("invalid_pair"))
    }

    @Test
    fun parseQueryParams_emptyValue() {
        val client = HTTPClient()
        val result = client.parseQueryParams("key1=&key2=value2")
        assertEquals(2, result.size)
        assertEquals("", result["key1"])
        assertEquals("value2", result["key2"])
    }

    @Test
    fun parseQueryParams_duplicateKeys_lastOneWins() {
        val client = HTTPClient()
        val result = client.parseQueryParams("key1=value1&key1=value2")
        assertEquals(1, result.size)
        assertEquals("value2", result["key1"])
    }
}
