package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A2-02: Find marks the matches where the text is drawn: prose, table cells and code lines. */
@OptIn(ExperimentalTestApi::class)
class KitFindMarksScreenTest {
    private val actions = KitMarkdownActions(onLink = {})

    /** Each mark's alpha in whole percent (a colour keeps its alpha in 8 bits): 18 is the tint, 38 the current match. */
    private fun ComposeUiTest.alphasOf(source: String, marks: KitFindMarks?): List<Int> {
        setContent {
            ChatTestTheme {
                CompositionLocalProvider(LocalKitFindMarks provides marks) {
                    KitMarkdown(KitMdSource.blocks("t", source, false), actions)
                }
            }
        }
        waitForIdle()
        val layouts =
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
                        val out = mutableListOf<TextLayoutResult>()
                        get(out)
                        out.firstOrNull()
                    }
                }
        return layouts.flatMap { l ->
            l.layoutInput.text.spanStyles.map { it.item.background }.filter { it.alpha > 0f && it.alpha < 1f }.map { (it.alpha * 100).roundToInt() }
        }
    }

    @Test
    fun `A2-02 prose marks every match and the current one is stronger`() = runComposeUiTest {
        val alphas = alphasOf("Total here, and total there, TOTAL again.", KitFindMarks("total", current = 1))
        assertEquals(listOf(18, 38, 18), alphas)
    }

    @Test
    fun `A2-02 a table marks its cells and counts the current match across them`() = runComposeUiTest {
        val table = "| File | Note |\n| --- | --- |\n| total.txt | a total |\n| other.txt | total again |\n"
        val alphas = alphasOf(table, KitFindMarks("total", current = 2))
        assertEquals(3, alphas.size)
        assertEquals(1, alphas.count { it == 38 })
        assertEquals(2, alphas.count { it == 18 })
    }

    @Test
    fun `A2-02 a code block marks the matches in its lines`() = runComposeUiTest {
        val code = "```kotlin\nval total = 1\nval other = total + total\n```\n"
        val alphas = alphasOf(code, KitFindMarks("total", current = 0))
        assertEquals(3, alphas.size)
        assertEquals(1, alphas.count { it == 38 })
    }

    @Test
    fun `A2-02 the person's own bubble marks the matches in their words`() = runComposeUiTest {
        setContent {
            ChatTestTheme {
                CompositionLocalProvider(LocalKitFindMarks provides KitFindMarks("below", current = 0)) {
                    io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble("anything below one, and below two")
                }
            }
        }
        waitForIdle()
        val percents =
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
                        val out = mutableListOf<TextLayoutResult>()
                        get(out)
                        out.firstOrNull()
                    }
                }
                .flatMap { l -> l.layoutInput.text.spanStyles.map { (it.item.background.alpha * 100).roundToInt() }.filter { it in 1..99 } }
        assertEquals(listOf(38, 18), percents)
    }

    @Test
    fun `A2-02 closed Find draws no mark at all`() = runComposeUiTest {
        assertTrue(alphasOf("total total", null).isEmpty())
    }
}
