package io.nekohasekai.sfa.ktx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DimensTest {

    @Test
    fun testDp2pxf_withNegativeValues() {
        val result = dp2pxf(-10)
        assertTrue(result < 0f)
    }

    @Test
    fun testDp2pxf_withPositiveValues() {
        val result = dp2pxf(10)
        assertTrue(result > 0f)
    }

    @Test
    fun testDp2pxf_withZero() {
        val result = dp2pxf(0)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun testDp2px_withNegativeValues() {
        val result = dp2px(-10)
        assertTrue(result < 0)
    }

    @Test
    fun testDp2px_withPositiveValues() {
        val result = dp2px(10)
        assertTrue(result > 0)
    }

    @Test
    fun testDp2px_withZero() {
        val result = dp2px(0)
        assertEquals(0, result)
    }
}
