package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Owner decision 2026-11-03: the shade writes counts with the phone's digits, through the shared formatter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NumberDigitsTest {
    private val arabicDigits = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫')
    private val words = NotifyWords(RuntimeEnvironment.getApplication()) { "en" }

    @AfterTest fun unpin() { Numbers.pinned = null }

    @Test
    fun `06-12_1-8 notification counts use the phone's digits, the same text the screens get`() {
        Numbers.pinned = arabicDigits
        assertEquals("٣ need you", words.plural(R.plurals.notify_group_need_you, 3, 3))
        assertEquals("${Numbers.format(1200)} need you", words.plural(R.plurals.notify_group_need_you, 1200, 1200))
        Numbers.pinned = NumberSymbols.Latin
        assertEquals("3 need you", words.plural(R.plurals.notify_group_need_you, 3, 3))
    }
}
