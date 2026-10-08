package io.nekohasekai.sfa.utils

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeOutcomeTest {

    @Test
    fun defaultProbeUrlIsGstaticGenerate204() {
        assertEquals("https://www.gstatic.com/generate_204", ProbeOutcome.DEFAULT_PROBE_URL)
    }

    @Test
    fun successOutcome() {
        val o = ProbeOutcome.success(85)
        assertTrue(o.isSuccess)
        assertEquals(ProbeStatus.SUCCESS, o.status)
        assertEquals(85, o.delayMs)
    }

    @Test
    fun classifiesUnknownHostAsDns() {
        val o = ProbeOutcome.fromThrowable(UnknownHostException("no such host: example.test"))
        assertEquals(ProbeStatus.DNS_ERROR, o.status)
        assertEquals(0, o.delayMs)
    }

    @Test
    fun classifiesSocketTimeout() {
        val o = ProbeOutcome.fromThrowable(SocketTimeoutException("timeout"))
        assertEquals(ProbeStatus.TIMEOUT, o.status)
    }

    @Test
    fun classifiesDeadlineExceededMessage() {
        val o =
            ProbeOutcome.fromThrowable(
                RuntimeException("dns: exchange failed for example.test: context deadline exceeded"),
            )
        assertEquals(ProbeStatus.TIMEOUT, o.status)
    }

    @Test
    fun classifiesHandshakeKeywords() {
        val o = ProbeOutcome.fromThrowable(javax.net.ssl.SSLHandshakeException("handshake failed"))
        assertEquals(ProbeStatus.HANDSHAKE_ERROR, o.status)
    }

    @Test
    fun unknownMapsToProbeErrorWithDetail() {
        val o = ProbeOutcome.fromThrowable(IllegalStateException("outbound not found: x"))
        assertEquals(ProbeStatus.PROBE_ERROR, o.status)
        assertTrue(o.detail.contains("outbound not found"))
    }

    @Test
    fun latencyCacheStoresStatus() {
        val cache = ProfileLatencyCache()
        cache.put(1L, "node", "fp", 0, 1000L, ProbeStatus.DNS_ERROR, "no such host")
        val r = cache.get(1L, "node", "fp")!!
        assertEquals(ProbeStatus.DNS_ERROR, r.status)
        assertEquals(0, r.delay)
        assertEquals("", r.detail)
        assertFalse(cache.encode().contains("no such host"))
    }
}
