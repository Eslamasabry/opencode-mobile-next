package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
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
 * C-003 (parity audit 2026-11-07, Flutter kit_turn.dart:667-737): the reply footer puts Copy then More at the **end**,
 * 24 dp glyphs in 48 dp targets 8 dp apart, the last glyph's ink 24 dp before the turn's edge (clear of the edge light), and the meta caption at the
 * start. Native had them at the start, 20 dp, no gap, no caption, raised into the reply.
 */
@OptIn(ExperimentalTestApi::class)
class ReplyFooterCraftTest {
    private var width = 400.dp

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 0.51f, "$what: expected $expected, was $actual")

    private fun ComposeUiTest.footer(
        rtl: Boolean = false,
        meta: String? = null,
        fontScale: Float? = null,
        copied: () -> Unit = {},
        opened: (() -> Unit)? = {},
    ) = setContent {
        ChatTestTheme(localeTag = if (rtl) "ar" else "en", fontScale = fontScale) {
            val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column(Modifier.width(width)) {
                    KitReplyFooter("Copy reply", "More", copied, opened) { meta?.let { KitReplyMeta(it) } }
                }
            }
        }
    }

    @Test
    fun `C-003 Copy then More sit at the end, 8 dp apart, More's ink ending on the turn's edge`() = runComposeUiTest {
        footer()
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val more = onNodeWithContentDescription("More").getBoundsInRoot()
        near(48.dp, copy.right - copy.left, "Copy keeps a 48 dp target")
        near(48.dp, more.right - more.left, "More keeps a 48 dp target")
        near(Space.S2.dp, more.left - copy.right, "8 dp between the two targets")
        assertTrue(copy.left > width / 2, "the actions sit at the end, not the start")
        // The glyph is 24 dp centred in the target; the dots' ink stands 18.75 % in from the glyph's end.
        val moreInkEnd = more.right - REPLY_ACTION_INSET.dp - (REPLY_ACTION_GLYPH * MORE_INK_END).dp
        near(width - Space.S4.dp - REPLY_ACTIONS_EDGE_INSET.dp, moreInkEnd, "More's ink ends 24 dp before the turn's edge")
        assertEquals(24, REPLY_ACTION_GLYPH, "the action glyphs are 24 dp (AppIconography.actionSize)")
    }

    @Test
    fun `C-003 in Arabic the actions mirror to the left end, More's ink on the left edge`() = runComposeUiTest {
        footer(rtl = true)
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val more = onNodeWithContentDescription("More").getBoundsInRoot()
        assertTrue(more.right < copy.left, "More follows Copy toward the end (left)")
        near(Space.S2.dp, copy.left - more.right, "8 dp between the two targets")
        val moreInkEnd = more.left + REPLY_ACTION_INSET.dp + (REPLY_ACTION_GLYPH * MORE_INK_END).dp
        near(Space.S4.dp + REPLY_ACTIONS_EDGE_INSET.dp, moreInkEnd, "More's ink ends 24 dp before the turn's edge")
    }

    @Test
    fun `C-003 the meta caption sits at the start of the footer row, the actions at its end`() = runComposeUiTest {
        footer(meta = "7:01 PM · Parity model · 150 tok")
        val meta = onNodeWithTag(REPLY_META_TAG, useUnmergedTree = true).getBoundsInRoot()
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        near(Space.S4.dp, meta.left, "the meta starts on the reply's text edge")
        assertTrue(meta.right <= copy.left, "the meta stays clear of the actions")
        val metaMid = (meta.top + meta.bottom) / 2
        val copyMid = (copy.top + copy.bottom) / 2
        near(copyMid, metaMid, "meta and actions share one line")
    }

    @Test
    fun `C-003 from 1_3x text the meta moves above the actions, which stay at the end`() = runComposeUiTest {
        footer(meta = "7:01 PM · Parity model · 150 tok", fontScale = 1.3f)
        val meta = onNodeWithTag(REPLY_META_TAG, useUnmergedTree = true).getBoundsInRoot()
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        assertTrue(meta.bottom <= copy.top + 0.5.dp, "the meta sits above the actions")
        assertTrue(copy.left > width / 2, "the actions stay at the end")
    }

    @Test
    fun `C-003 the row no longer rises into the reply, and both actions take taps`() = runComposeUiTest {
        var copied = 0
        var opened = 0
        footer(copied = { copied++ }, opened = { opened++ })
        val row = onNodeWithTag("kit-reply-actions").getBoundsInRoot()
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        near(row.top, copy.top, "the target starts at the footer's top: 4 dp under the reply plus the 12 dp inset")
        near(0.dp, row.top, "nothing pulls the row above its own slot")
        onNodeWithContentDescription("Copy reply").performClick()
        onNodeWithContentDescription("More").performClick()
        assertEquals(1, copied)
        assertEquals(1, opened)
    }

    @Test
    fun `C-003 with Copy alone, Copy's own ink ends on the edge`() = runComposeUiTest {
        footer(opened = null)
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val inkEnd = copy.right - REPLY_ACTION_INSET.dp - (REPLY_ACTION_GLYPH * COPY_INK_END).dp
        near(width - Space.S4.dp - REPLY_ACTIONS_EDGE_INSET.dp, inkEnd, "Copy's ink ends 24 dp before the turn's edge")
    }

    /** KC1: the buttons stand clear of the light's touch box (24 dp from the physical edge) at every width and text size. */
    @Test
    fun `KC1 the buttons always stand clear of the light's touch box, at 360 and 412 dp and 1_3x text, both directions`() {
        assertEquals(24, REPLY_ACTIONS_EDGE_INSET, "the token is 24 dp (Space.S6)")
        for (w in listOf(360, 412)) for (scale in listOf(1f, 1.3f)) for (rtl in listOf(false, true)) {
            width = w.dp
            runComposeUiTest {
                footer(rtl = rtl, meta = "7:01 PM · Parity model · 150 tok", fontScale = scale)
                val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
                val more = onNodeWithContentDescription("More").getBoundsInRoot()
                val ink = (REPLY_ACTION_INSET + REPLY_ACTION_GLYPH * MORE_INK_END).dp
                val lightBox = 24.dp
                val what = "w=$w scale=$scale rtl=$rtl"
                if (!rtl) {
                    near(width - Space.S4.dp - REPLY_ACTIONS_EDGE_INSET.dp, more.right - ink, "$what More's ink")
                    assertTrue(width - (more.right - ink) >= lightBox + 16.dp - 0.51.dp, "$what clear of the touch box")
                } else {
                    near(Space.S4.dp + REPLY_ACTIONS_EDGE_INSET.dp, more.left + ink, "$what More's ink")
                    assertTrue(more.left + ink >= lightBox + 16.dp - 0.51.dp, "$what clear of the touch box")
                }
                val meta = onNodeWithTag(REPLY_META_TAG, useUnmergedTree = true).getBoundsInRoot()
                if (scale < 1.3f) assertTrue(if (!rtl) meta.right <= copy.left else meta.left >= copy.right, "$what meta clear of the buttons")
            }
        }
    }
}
