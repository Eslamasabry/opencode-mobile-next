package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReplyAskTest {
    private val phoneWidth = 360.dp

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 0.51f, "$what: expected $expected, was $actual")

    private class Taps(
        val onYes: () -> Unit = {},
        val onNo: () -> Unit = {},
        val onSkip: () -> Unit = {},
    )

    private data class AskCopy(
        val locale: String,
        val question: String,
        val yes: String,
        val no: String,
        val skip: String,
    )

    private data class AfterCopy(
        val locale: String,
        val note: String,
        val action: String,
        val star: Boolean,
    )

    private val english = AskCopy("en", "Enjoying it?", "Yes", "No", "Skip")

    private fun ComposeUiTest.showFooter(
        rtl: Boolean = false,
        fontScale: Float? = null,
        showAsk: Boolean = true,
        taps: Taps = Taps(),
        copy: AskCopy = english,
    ) = setContent {
        val locale = if (rtl) "ar" else copy.locale
        ChatTestTheme(localeTag = locale, fontScale = fontScale) {
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
                                question = copy.question,
                                yes = copy.yes,
                                no = copy.no,
                                skip = copy.skip,
                                onYes = taps.onYes,
                                onNo = taps.onNo,
                                onSkip = taps.onSkip,
                            )
                        } else {
                            KitReplyMeta("7:01 PM · 12s")
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.showAfterFooter(after: AfterCopy, fontScale: Float) = setContent {
        ChatTestTheme(localeTag = after.locale, fontScale = fontScale) {
            Column(Modifier.width(phoneWidth)) {
                KitReplyFooter(
                    copyLabel = "Copy reply",
                    moreLabel = "More",
                    onCopy = {},
                    onMore = {},
                    modifier = Modifier.testTag("footer"),
                ) {
                    KitReplyAskAfter(
                        note = after.note,
                        action = after.action,
                        onAction = {},
                        star = after.star,
                    )
                }
            }
        }
    }

    private fun assertWordUnclipped(node: SemanticsNodeInteraction, row: DpRect, label: String) {
        val sem = node.fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val get = sem.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(get != null && get(results) && results.isNotEmpty(), "$label: missing text layout")
        val layout = results.first()
        val wide =
            (0 until layout.lineCount).any {
                layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f
            }
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        assertFalse(layout.hasVisualOverflow, "$label: visual overflow")
        assertFalse(layout.multiParagraph.didExceedMaxLines, "$label: exceeded maxLines")
        assertFalse(ellipsized, "$label: ellipsized")
        assertFalse(wide, "$label: line wider than box")
        val box = node.getBoundsInRoot()
        assertTrue(box.right > box.left && box.bottom > box.top, "$label: zero box ($box)")
        assertInside(box, row, label)
    }

    private fun assertInside(box: DpRect, container: DpRect, label: String) {
        val msg = "$label: box=$box outside row=$container"
        assertTrue(box.left >= container.left - 0.5.dp, msg)
        assertTrue(box.right <= container.right + 0.5.dp, msg)
        assertTrue(box.top >= container.top - 0.5.dp, msg)
        assertTrue(box.bottom <= container.bottom + 0.5.dp, msg)
    }

    private fun assertDisjoint(boxes: List<Pair<String, DpRect>>, locale: String) {
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                val (aName, a) = boxes[i]
                val (bName, b) = boxes[j]
                val clear =
                    a.right <= b.left + 0.5.dp ||
                        b.right <= a.left + 0.5.dp ||
                        a.bottom <= b.top + 0.5.dp ||
                        b.bottom <= a.top + 0.5.dp
                assertTrue(clear, "$locale: $aName ($a) overlaps $bName ($b)")
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
            val widthMsg =
                "ask fits before Copy at 360 dp (ask=${ask.right - ask.left}, " +
                    "skipRight=${skip.right}, copyLeft=${copy.left})"
            assertTrue(skip.right <= copy.left + 0.5.dp, widthMsg)
            near(48.dp, ask.bottom - ask.top, "ask stays on one 48 dp line")
        }
    }

    @Test
    fun `B-STAR-034 every answer is a 48 dp tall target that does its own thing`() = runComposeUiTest {
        var yes = 0
        var no = 0
        var skip = 0
        showFooter(taps = Taps(onYes = { yes++ }, onNo = { no++ }, onSkip = { skip++ }))
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

    private fun checkAskAtLargeScale(copy: AskCopy) = runComposeUiTest {
        showFooter(fontScale = 1.3f, copy = copy)
        val ask = onNodeWithTag(REPLY_ASK_TAG).getBoundsInRoot()
        val copyBtn = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val askHeight = ask.bottom - ask.top
        assertTrue(ask.left >= Space.S4.dp - 0.5.dp, "${copy.locale}: ask starts inside gutter")
        assertTrue(
            ask.right <= phoneWidth - Space.S4.dp + 0.5.dp,
            "${copy.locale}: ask ends inside gutter (right=${ask.right})",
        )
        assertTrue(ask.bottom <= copyBtn.top + 0.5.dp, "${copy.locale}: ask sits above Copy and More")
        if (copy.locale == "en") {
            near(48.dp, askHeight, "en at 1.3 fits on one 48 dp line")
        } else {
            assertTrue(
                askHeight >= 47.5.dp && askHeight <= 96.5.dp,
                "${copy.locale}: ask is 1 or 2 rows tall (was $askHeight)",
            )
        }
        for (word in listOf(copy.question, copy.yes, copy.no, copy.skip)) {
            assertWordUnclipped(onNodeWithText(word, useUnmergedTree = true), ask, "${copy.locale} '$word'")
        }
        for (tag in listOf(REPLY_ASK_YES_TAG, REPLY_ASK_NO_TAG, REPLY_ASK_SKIP_TAG)) {
            val b = onNodeWithTag(tag).getBoundsInRoot()
            assertTrue(b.bottom - b.top >= 47.5.dp, "${copy.locale} $tag is 48 dp tall")
            assertInside(b, ask, "${copy.locale} $tag")
        }
        assertDisjoint(
            listOf(
                "question" to onNodeWithText(copy.question, useUnmergedTree = true).getBoundsInRoot(),
                "yes" to onNodeWithTag(REPLY_ASK_YES_TAG).getBoundsInRoot(),
                "no" to onNodeWithTag(REPLY_ASK_NO_TAG).getBoundsInRoot(),
                "skip" to onNodeWithTag(REPLY_ASK_SKIP_TAG).getBoundsInRoot(),
            ),
            copy.locale,
        )
    }

    private fun checkAfterAtLargeScale(after: AfterCopy) = runComposeUiTest {
        showAfterFooter(after, fontScale = 1.3f)
        val row = onNodeWithTag(REPLY_ASK_AFTER_TAG).getBoundsInRoot()
        val copyBtn = onNodeWithContentDescription("Copy reply").getBoundsInRoot()
        val rowHeight = row.bottom - row.top
        assertTrue(row.left >= Space.S4.dp - 0.5.dp, "${after.locale}: after starts inside gutter")
        assertTrue(row.right <= phoneWidth - Space.S4.dp + 0.5.dp, "${after.locale}: after inside gutter")
        assertTrue(row.bottom <= copyBtn.top + 0.5.dp, "${after.locale}: after sits above Copy and More")
        assertTrue(rowHeight in 47.5.dp..96.5.dp, "${after.locale}: after is 1 or 2 rows tall (was $rowHeight)")
        val noteNode = onNodeWithText(after.note, useUnmergedTree = true)
        assertWordUnclipped(noteNode, row, "${after.locale} '${after.note}'")
        assertWordUnclipped(
            onNodeWithText(after.action, useUnmergedTree = true),
            row,
            "${after.locale} '${after.action}'",
        )
        val actionBox = onNodeWithTag(REPLY_ASK_ACTION_TAG).getBoundsInRoot()
        assertTrue(actionBox.bottom - actionBox.top >= 47.5.dp, "${after.locale}: action is 48 dp tall")
        assertInside(actionBox, row, "${after.locale} action")
        assertDisjoint(listOf("note" to noteNode.getBoundsInRoot(), "action" to actionBox), after.locale)
    }

    @Test
    fun `B-STAR-035 at text 1_3 on 360 dp no word is cut`() {
        val shipped =
            listOf(
                english,
                AskCopy("vi", "Bạn thích chứ?", "Có", "Không", "Bỏ qua"),
                AskCopy("id", "Menyukainya?", "Ya", "Tidak", "Lewati"),
                AskCopy("ru", "Нравится?", "Да", "Нет", "Пропустить"),
                AskCopy("fr", "Vous aimez\u00A0?", "Oui", "Non", "Passer"),
                AskCopy("ja", "気に入りましたか？", "はい", "いいえ", "スキップ"),
            )
        shipped.forEach(::checkAskAtLargeScale)
        val longestAfter =
            listOf(
                AfterCopy("id", "Terima kasih.", "Beri bintang di GitHub", star = true),
                AfterCopy("fr", "Merci.", "Mettre une étoile sur GitHub", star = true),
                AfterCopy("ru", "Спасибо.", "Поставить звезду на GitHub", star = true),
                AfterCopy("ja", "ありがとうございます。", "GitHubでスターを付ける", star = true),
                AfterCopy("es", "Lo sentimos.", "Informar de un problema", star = false),
            )
        longestAfter.forEach(::checkAfterAtLargeScale)
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
