package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
class SubscriptionRoutingTest {

    @RunWith(Parameterized::class)
    class DetectModeTest(private val input: String, private val expectedMode: SubscriptionRouting.Mode) {

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: detectMode(\"{0}\") = {1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    // Happy paths
                    arrayOf("white-list", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("whitelist", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("white list", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("обход белых списков", SubscriptionRouting.Mode.WHITELIST_BYPASS),

                    // Case insensitivity
                    arrayOf("White-List", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("WHITELIST", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("WHITE LIST", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("ОБХОД БЕЛЫХ СПИСКОВ", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("wHiTeLiSt", SubscriptionRouting.Mode.WHITELIST_BYPASS),

                    // Containing text
                    arrayOf("proxy whitelist bypass", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("my-white-list-server", SubscriptionRouting.Mode.WHITELIST_BYPASS),
                    arrayOf("  обход белых списков  ", SubscriptionRouting.Mode.WHITELIST_BYPASS),

                    // Normal mode fallback
                    arrayOf("normal", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("black-list", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("random string", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("   ", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("white", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("list", SubscriptionRouting.Mode.NORMAL),
                    arrayOf("обход", SubscriptionRouting.Mode.NORMAL)
                )
            }
        }

        @Test
        fun testDetectMode() {
            assertEquals(expectedMode, SubscriptionRouting.detectMode(input))
        }
    }

    @RunWith(Parameterized::class)
    class IsWhitelistBypassTagTest(private val input: String, private val expected: Boolean) {

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: isWhitelistBypassTag(\"{0}\") = {1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    // Happy paths
                    arrayOf("white list", true),
                    arrayOf("whitelist", true),
                    arrayOf("white-list", true),

                    // Combined words
                    arrayOf("обход белых", true),
                    arrayOf("обход белый", true),
                    arrayOf("bypass white", true),
                    arrayOf("bypass list", true),

                    // Case insensitivity
                    arrayOf("White List", true),
                    arrayOf("WHITELIST", true),
                    arrayOf("WHITE-LIST", true),
                    arrayOf("ОБХОД БЕЛ", true),
                    arrayOf("BYPASS WHITE", true),

                    // Leading/trailing spaces inside trim()
                    arrayOf("  whitelist  ", true),
                    arrayOf(" bypass list ", true),

                    // False conditions
                    arrayOf("normal tag", false),
                    arrayOf("white", false),
                    arrayOf("list", false),
                    arrayOf("bypass", false),
                    arrayOf("обход", false),
                    arrayOf("бел", false),
                    arrayOf("", false),
                    arrayOf("   ", false)
                )
            }
        }

        @Test
        fun testIsWhitelistBypassTag() {
            assertEquals(expected, SubscriptionRouting.isWhitelistBypassTag(input))
        }
    }
}
