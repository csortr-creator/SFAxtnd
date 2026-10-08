package io.nekohasekai.sfa.utils

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorPresentationTest {

    private fun assertNoSecrets(vararg texts: String) {
        for (t in texts) {
            assertFalse(t.contains("://"))
            assertFalse(t.contains("SECRET"))
            assertFalse(t.contains("token="))
            assertFalse(t.contains("user:pass"))
            assertFalse(t.contains("access_token"))
        }
    }

    @Test
    fun tlsHandshakeTimeoutCategory() {
        val e =
            IOException(
                """Get "https://cdn.example/sub/SECRET_PATH?token=tok123": net/http: TLS handshake timeout""",
            )
        val safe = NetworkErrorPresentation.classify(e)
        assertEquals(NetworkErrorKind.TLS_TIMEOUT, safe.kind)
        assertEquals("NET_TLS_TIMEOUT", safe.code)
        assertNoSecrets(safe.code, NetworkErrorPresentation.logCode(e))
    }

    @Test
    fun nestedCauseWithUserInfoDoesNotLeak() {
        val inner =
            IOException("""Get "https://user:pass@host.example/v1/x": connection refused""")
        val outer = RuntimeException("wrapper", inner)
        val safe = NetworkErrorPresentation.classify(outer)
        assertEquals(NetworkErrorKind.CONNECTION, safe.kind)
        assertNoSecrets(safe.code)
    }

    @Test
    fun unknownHostIsDns() {
        val e = UnknownHostException("Unable to resolve host \"evil.example\": No address associated with hostname")
        // message has no :// — extractSafeHint returns null; type UnknownHostException still wins
        val safe = NetworkErrorPresentation.classify(e)
        assertEquals(NetworkErrorKind.DNS, safe.kind)
    }

    @Test
    fun socketTimeoutIsTimeout() {
        val e = SocketTimeoutException("timeout")
        assertEquals(NetworkErrorKind.TIMEOUT, NetworkErrorPresentation.classify(e).kind)
    }

    @Test
    fun connectExceptionIsConnection() {
        val e = ConnectException("Connection refused")
        assertEquals(NetworkErrorKind.CONNECTION, NetworkErrorPresentation.classify(e).kind)
    }

    @Test
    fun ambiguousUrlOnlyMessageIsUnknown() {
        val e = IOException("https://h.example/a?access_token=abc#frag")
        val safe = NetworkErrorPresentation.classify(e)
        assertEquals(NetworkErrorKind.UNKNOWN, safe.kind)
        assertEquals("NET_UNKNOWN", safe.code)
        assertNoSecrets(safe.code)
    }

    @Test
    fun extractSafeHintDropsUrlBodies() {
        val hint =
            NetworkErrorPresentation.extractSafeHint(
                """Get "https://x/SECRET": net/http: TLS handshake timeout""",
            )
        assertTrue(hint != null && "tls handshake timeout" in hint!!)
        assertFalse(hint!!.contains("SECRET"))
        assertFalse(hint.contains("://"))
        assertEquals(
            null,
            NetworkErrorPresentation.extractSafeHint("https://x/SECRET?token=1"),
        )
    }

    @Test
    fun logCodeNeverContainsUrlMaterial() {
        val e =
            IOException(
                """Get "https://panel.example/api/v1/client/subscribe?token=PRIVATE": EOF""",
            )
        val code = NetworkErrorPresentation.logCode(e)
        assertTrue(code.startsWith("NET_"))
        assertNoSecrets(code)
    }
}
