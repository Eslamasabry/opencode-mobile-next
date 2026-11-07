package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E20-S10 (owner's phone): the reply's Copy and More floated at the far end with a wide gap, Copy drawn heavier, and a
 * tall gap above them. They now sit as one compact row on the reply's own start edge, glyphs on the text's edge.
 */
@OptIn(ExperimentalTestApi::class)
class ReplyFooterCraftTest {
    private val width = 400.dp

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) < 0.5f, "$what: expected $expected, was $actual")

    private fun androidx.compose.ui.test.ComposeUiTest.footer(rtl: Boolean, copied: () -> Unit = {}, opened: () -> Unit = {}) =
        setContent {
            ChatTestTheme(localeTag = if (rtl) "ar" else "en") {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Column(Modifier.width(width)) { KitReplyFooter("Copy reply", "More", copied, opened) }
                }
            }
        }

    @Test
    fun `E20-S10 Copy and More sit on the reply's start edge, the Copy glyph on the text's edge, targets side by side`() =
        runComposeUiTest {
            footer(rtl = false)
            val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
            val more = onNodeWithContentDescription("More").getBoundsInRoot()
            // The reply text starts at S4; the glyph starts one target inset into its 48 dp target.
            near(Space.S4.dp, copy.left + REPLY_ACTION_INSET.dp, "Copy's glyph lines up with the text's start")
            near(copy.right, more.left, "More follows Copy with no extra gap between the targets")
            assertTrue(more.right < width / 2, "the actions stay on the start edge, not floating at the end")
            near(copy.bottom - copy.top, more.bottom - more.top, "same target size")
        }

    @Test
    fun `E20-S10 in Arabic the actions mirror to the right edge, the reply's start`() = runComposeUiTest {
        footer(rtl = true)
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val more = onNodeWithContentDescription("More").getBoundsInRoot()
        near(width - Space.S4.dp, copy.right - REPLY_ACTION_INSET.dp, "Copy's glyph lines up with the text's start")
        assertTrue(more.right <= copy.left + 0.5.dp && more.left > width / 2, "More follows Copy toward the middle")
    }

    @Test
    fun `E20-S10 the row rises so the glyph sits one kit step under the reply, and both actions still take taps`() =
        runComposeUiTest {
            var copied = 0
            var opened = 0
            footer(rtl = false, copied = { copied++ }, opened = { opened++ })
            val row = onNodeWithTag("kit-reply-actions").getBoundsInRoot()
            val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
            near(row.top - REPLY_ACTION_RISE.dp, copy.top, "the target rises above the footer's top")
            // The reply column's S1 bottom padding + what is left of the inset above the glyph = S2.
            near(Space.S2.dp, Space.S1.dp + (REPLY_ACTION_INSET - REPLY_ACTION_RISE).dp, "glyph gap is the kit's step")
            onNodeWithContentDescription("Copy reply").performClick()
            onNodeWithContentDescription("More").performClick()
            assertEquals(1, copied)
            assertEquals(1, opened)
        }
}
