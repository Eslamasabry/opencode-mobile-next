package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

@OptIn(ExperimentalTestApi::class)
class ReplyAskTest {
    private val phoneWidth = 360.dp

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 0.51f, "$what: expected $expected, was $actual")

    private fun ComposeUiTest.showFooter(
        rtl: Boolean = false,
        fontScale: Float? = null,
        showAsk: Boolean = true,
        onYes: () -> Unit = {},
        onNo: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) = setContent {
        ChatTestTheme(localeTag = if (rtl) "ar" else "en", fontScale = fontScale) {
            val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                Column(Modifier.width(phoneWidth)) {
                    KitReplyFooter(
                        copyLabel = "Copy reply",
                        moreLabel = "More",
                        onCopy = {},
                        onMore = {},
                        modifier = Modifier.testTag("footer"),
                    ) {
                        if (showAsk) {
                            KitReplyAsk(
                                question = "Enjoying it?",
                                yes = "Yes",
                                no = "No",
                                skip = "Skip",
                                onYes = onYes,
                                onNo = onNo,
                                onSkip = onSkip,
                            )
                        } else {
                            KitReplyMeta("7:01 PM · 12s")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `B-STAR-033 the ask takes the time words' place and the footer keeps its height at 360 dp`() {
        var metaHeight = 0.dp
        var metaCopyLeft = 0.dp
        var metaMoreLeft = 0.dp
        runComposeUiTest {
            showFooter(showAsk = false)
            val footer = onNodeWithTag("footer").getBoundsInRoot()
            metaHeight = footer.bottom - footer.top
            metaCopyLeft = onNodeWithContentDescription("Copy reply").getBoundsInRoot().left
            metaMoreLeft = onNodeWithContentDescription("More").getBoundsInRoot().left
        }
        runComposeUiTest {
            showFooter(showAsk = true)
            onNodeWithTag(REPLY_META_TAG, useUnmergedTree = true).assertDoesNotExist()
            val footer = onNodeWithTag("footer").getBoundsInRoot()
            val ask = onNodeWithTag(REPLY_ASK_TAG).getBoundsInRoot()
            val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
            val more = onNodeWithContentDescription("More").getBoundsInRoot()
            val skip = onNodeWithTag(REPLY_ASK_SKIP_TAG).getBoundsInRoot()
            val askHeight = footer.bottom - footer.top
            near(metaHeight, askHeight, "footer height matches time words (meta=$metaHeight ask=$askHeight)")
            near(metaCopyLeft, copy.left, "Copy stays in the same place")
            near(metaMoreLeft, more.left, "More stays in the same place")
            assertTrue(
                skip.right <= copy.left + 0.5.dp,
                "ask fits before Copy at 360 dp (ask=${ask.right - ask.left}, skipRight=${skip.right}, copyLeft=${copy.left})",
            )
            near(48.dp, ask.bottom - ask.top, "ask stays on one 48 dp line")
        }
    }

    @Test
    fun `B-STAR-034 every answer is a 48 dp tall target that does its own thing`() = runComposeUiTest {
        var yes = 0
        var no = 0
        var skip = 0
        showFooter(onYes = { yes++ }, onNo = { no++ }, onSkip = { skip++ })
        for (tag in listOf(REPLY_ASK_YES_TAG, REPLY_ASK_NO_TAG, REPLY_ASK_SKIP_TAG)) {
            val b = onNodeWithTag(tag).getBoundsInRoot()
            assertTrue(b.bottom - b.top >= 47.5.dp, "$tag must be at least 48 dp tall (was ${b.bottom - b.top})")
        }
        onNodeWithTag(REPLY_ASK_YES_TAG).performClick()
        assertEquals(Triple(1, 0, 0), Triple(yes, no, skip))
        onNodeWithTag(REPLY_ASK_NO_TAG).performClick()
        assertEquals(Triple(1, 1, 0), Triple(yes, no, skip))
        onNodeWithTag(REPLY_ASK_SKIP_TAG).performClick()
        assertEquals(Triple(1, 1, 1), Triple(yes, no, skip))
    }

    @Test
    fun `B-STAR-035 at text 1_3 on 360 dp no word is cut`() = runComposeUiTest {
        showFooter(fontScale = 1.3f)
        val ask = onNodeWithTag(REPLY_ASK_TAG).getBoundsInRoot()
        val question = onNodeWithText("Enjoying it?").getBoundsInRoot()
        val yes = onNodeWithTag(REPLY_ASK_YES_TAG).getBoundsInRoot()
        val no = onNodeWithTag(REPLY_ASK_NO_TAG).getBoundsInRoot()
        val skip = onNodeWithTag(REPLY_ASK_SKIP_TAG).getBoundsInRoot()
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        near(48.dp, ask.bottom - ask.top, "at 1.3 the start line is 48 dp tall")
        assertTrue(ask.bottom <= copy.top + 0.5.dp, "at 1.3 the start line sits above Copy and More")
        assertTrue(question.right <= yes.left + 0.5.dp, "question clear of Yes")
        assertTrue(yes.right <= no.left + 0.5.dp, "Yes clear of No")
        assertTrue(no.right <= skip.left + 0.5.dp, "No clear of Skip")
        assertTrue(
            skip.right <= phoneWidth - Space.S4.dp + 0.5.dp,
            "Skip stays inside the 360 dp row (skipRight=${skip.right})",
        )
    }

    @Test
    fun `B-STAR-036 right to left the ask sits at the right and Copy and More stay at the left`() = runComposeUiTest {
        showFooter(rtl = true)
        val ask = onNodeWithTag(REPLY_ASK_TAG).getBoundsInRoot()
        val copy = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val more = onNodeWithContentDescription("More").getBoundsInRoot()
        assertTrue(more.right < copy.left, "More follows Copy to the left")
        assertTrue(copy.right <= ask.left + 0.5.dp, "ask sits at the right, Copy and More at the left")
        assertTrue(ask.right > phoneWidth / 2, "ask sits on the right side")
    }
}
