package io.nekohasekai.sfa.ktx

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class DimensTest {

    private val density: Float
        get() = Resources.getSystem().displayMetrics.density

    @Test
    fun dp2pxf_zero_returnsZero() {
        val result = dp2pxf(0)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun dp2pxf_positiveValue_returnsCalculatedFloat() {
        val dp = 10
        val expected = dp * density
        val result = dp2pxf(dp)
        assertEquals(expected, result, 0.001f)
    }

    @Test
    fun dp2pxf_negativeValue_returnsCalculatedFloat() {
        val dp = -5
        val expected = dp * density
        val result = dp2pxf(dp)
        assertEquals(expected, result, 0.001f)
    }

    @Test
    fun dp2px_zero_returnsZero() {
        val result = dp2px(0)
        assertEquals(0, result)
    }

    @Test
    fun dp2px_positiveValue_returnsCeiledInt() {
        val dp = 10
        val expected = ceil(dp * density).toInt()
        val result = dp2px(dp)
        assertEquals(expected, result)
    }

    @Test
    fun dp2px_negativeValue_returnsCeiledInt() {
        val dp = -5
        val expected = ceil(dp * density).toInt()
        val result = dp2px(dp)
        assertEquals(expected, result)
    }
}
