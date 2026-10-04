package io.nekohasekai.sfa.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.nekohasekai.sfa.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class ColorUtilsTest {

    @Test
    fun ansiEscapeToSpannable_plainText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "Hello, World!"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals(text, spannable.toString())
        val spans = spannable.getSpans(0, spannable.length, Any::class.java)
        assertEquals(0, spans.size)
    }

    @Test
    fun ansiEscapeToSpannable_boldText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[1mBold Text\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("Bold Text", spannable.toString())
        val spans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
    }

    @Test
    fun ansiEscapeToSpannable_italicText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[3mItalic Text\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("Italic Text", spannable.toString())
        val spans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(Typeface.ITALIC, spans[0].style)
    }

    @Test
    fun ansiEscapeToSpannable_underlineText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[4mUnderline Text\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("Underline Text", spannable.toString())
        val spans = spannable.getSpans(0, spannable.length, UnderlineSpan::class.java)
        assertEquals(1, spans.size)
    }

    @Test
    fun ansiEscapeToSpannable_coloredText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[31mRed Text\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("Red Text", spannable.toString())
        val spans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
    }

    @Test
    fun ansiEscapeToSpannable_multipleStyles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[1;31mBold Red Text\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("Bold Red Text", spannable.toString())
        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        assertEquals(1, styleSpans.size)

        val colorSpans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        assertEquals(1, colorSpans.size)
    }

    @Test
    fun ansiEscapeToSpannable_nestedStyles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[1mBold \u001B[3mItalic\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("Bold Italic", spannable.toString())
        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        assertEquals(2, styleSpans.size)
    }

    @Test
    fun ansiEscapeToSpannable_256color() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "\u001B[120m256 Color\u001B[0m"
        val spannable = ColorUtils.ansiEscapeToSpannable(context, text)

        assertEquals("256 Color", spannable.toString())
        val colorSpans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        assertEquals(1, colorSpans.size)
    }
}
