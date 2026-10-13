package io.nekohasekai.sfa.ktx

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Parcelize
data class DummyParcelable(
    val id: Int,
    val name: String,
    val isActive: Boolean
) : Parcelable

@RunWith(RobolectricTestRunner::class)
class RoomTest {

    @Test
    fun testMarshallAndUnmarshall() {
        val original = DummyParcelable(id = 42, name = "Test Item", isActive = true)
        val bytes = original.marshall()
        assertTrue("Marshalled byte array should not be empty", bytes.isNotEmpty())

        val restored = bytes.unmarshall(DummyParcelable.CREATOR::createFromParcel)
        assertEquals("Restored object should match original", original, restored)
        assertNotEquals("Restored object should be a different instance", System.identityHashCode(original), System.identityHashCode(restored))
    }
}
