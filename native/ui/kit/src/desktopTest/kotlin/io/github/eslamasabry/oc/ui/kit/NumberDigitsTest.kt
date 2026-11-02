package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.kit.frame.kitJumpLatestLabel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner decision 2026-11-03: screens write counts with the phone's digits, through the shared formatter. */
@OptIn(ExperimentalTestApi::class)
class NumberDigitsTest {
    @AfterTest fun unpin() { Numbers.pinned = null }

    @Test
    fun `B-L10N-012 a screen count uses the phone's digits, the same text the shade gets`() = runComposeUiTest {
        Numbers.pinned = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫')
        var label = ""
        setContent { label = kitJumpLatestLabel(newCount = 3) }
        waitForIdle()
        assertTrue(label.startsWith("٣ "), label)
        assertEquals(false, label.any { it in '0'..'9' }, label)
    }
}
