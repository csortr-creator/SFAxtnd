package io.nekohasekai.sfa.utils

import org.junit.Assert.*
import org.junit.Test

class ProfileLatencyCacheTest {
    @Test
    fun switchingSubscriptionsDoesNotMixIdenticalNames() {
        val cache = ProfileLatencyCache()
        cache.put(1, "Germany", "a", 120, 1000)
        cache.put(2, "Germany", "b", 240, 2000)
        assertEquals(120, cache.get(1, "Germany", "a")?.delay)
        assertEquals(240, cache.get(2, "Germany", "b")?.delay)
        assertNull(cache.get(2, "Germany", "a"))
    }

    @Test
    fun persistsBothSuccessAndUnavailableResults() {
        val cache = ProfileLatencyCache()
        cache.put(1, "A", "a", 120, 1000)
        cache.put(2, "B", "b", 0, 2000)
        val restored = ProfileLatencyCache(cache.encode())
        assertEquals(ProfileLatencyCache.Result(120, 1000), restored.get(1, "A", "a"))
        assertEquals(ProfileLatencyCache.Result(0, 2000), restored.get(2, "B", "b"))
    }

    @Test
    fun changedOrRemovedServersLoseTheirMeasurements() {
        val cache = ProfileLatencyCache()
        cache.put(1, "A", "old", 120, 1000)
        cache.put(1, "B", "keep", 240, 2000)
        cache.put(1, "C", "removed", 360, 3000)
        cache.retain(1, mapOf("A" to "new", "B" to "keep"))
        assertNull(cache.get(1, "A", "old"))
        assertNull(cache.get(1, "A", "new"))
        assertNull(cache.get(1, "C", "removed"))
        assertEquals(240, cache.get(1, "B", "keep")?.delay)
    }

    @Test
    fun fingerprintsIgnoreJsonFormattingButDetectChangedEndpoint() {
        val a =
            ProfileLatencyCache.fingerprints(
                """{"outbounds":[{"tag":"A","server":"one","tls":{"enabled":true,"server_name":"example.com"}}]}"""
            )
        val b =
            ProfileLatencyCache.fingerprints(
                """{ "outbounds": [ {"tls":{"server_name":"example.com","enabled":true},"server":"one","tag":"A"} ] }"""
            )
        val changed =
            ProfileLatencyCache.fingerprints(
                """{"outbounds":[{"tag":"A","server":"two","tls":{"enabled":true,"server_name":"example.com"}}]}"""
            )
        assertEquals(a, b)
        assertNotEquals(a, changed)
        val cache = ProfileLatencyCache()
        cache.put(1, "A", a.getValue("A"), 120, 1000)
        assertFalse(cache.encode().contains("example.com"))
        assertFalse(cache.encode().contains("one"))
    }

    @Test
    fun invalidStoredJsonDoesNotBlockLoading() {
        assertNull(ProfileLatencyCache("bad json").get(1, "A", "hash"))
    }
}
