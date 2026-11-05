@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o49

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.mediumLocalDateTime
import io.github.eslamasabry.oc.ui.kit.text.kitDateTime
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.test.*

class DateTimeTest {
    private val at = Instant.parse("2026-09-05T12:00:00Z").toEpochMilli()

    @Test
    fun `a full date remains present and the local day follows the time zone`() {
        val utc = mediumLocalDateTime(at, Locale.US, TimeZone.getTimeZone("UTC"))
        assertEquals("Sep 5, 2026 12:00 PM", utc.replace('\u202f', ' ').replace('\u00a0', ' '))
        val nextDay = mediumLocalDateTime(at, Locale.US, TimeZone.getTimeZone("GMT+13:00"))
        assertEquals("Sep 6, 2026 1:00 AM", nextDay.replace('\u202f', ' ').replace('\u00a0', ' '))
    }

    @Test
    fun `a full date uses the requested locale instead of a fixed English order`() {
        val french = mediumLocalDateTime(at, Locale.FRANCE, TimeZone.getTimeZone("UTC"))
        assertTrue(french.startsWith("5 sept. 2026"), french)
        assertTrue(french.endsWith("12:00"), french)
    }

    @Test
    fun `the kit forwards the instant and current app locale without a clock read`() =
        runComposeUiTest {
            val calls = mutableListOf<Pair<Long, String>>()
            val platform =
                object : KitPlatform by DesktopKitPlatform() {
                    override fun dateTime(atMs: Long, localeTag: String): String {
                        calls += atMs to localeTag
                        return "formatted account timestamp"
                    }
                }
            setContent {
                KitTheme(platform = platform, localeTag = "ar", stillFrame = true) {
                    kitDateTime(at)?.let { KitText(it) }
                }
            }
            onNodeWithText("formatted account timestamp").assertExists()
            assertTrue(calls.isNotEmpty())
            assertTrue(calls.all { it == at to "ar" })
        }
}
