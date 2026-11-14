package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit 1, item 2: a table cell broke "write-tool proof" as "write-to / ol proof". A cell breaks at spaces (and after a
 * hyphen where the text layout allows it) and a word is split only when it alone is wider than its column; a table
 * wider than its window scrolls sideways instead of squeezing its words.
 */
@OptIn(ExperimentalTestApi::class)
class MarkdownTableWordsTest {
    private val actions = KitMarkdownActions(onLink = {})

    private val table =
        """
        | File | Size | Note |
        | --- | --- | --- |
        | proof.txt | 41 B | write-tool proof |
        | rivers-300-lines.txt | 25.3 KB | 300 river facts |
        """.trimIndent() + "\n"

    @Test
    fun `the widest word of a cell is the run between spaces`() {
        val word = kitMdWidestWord(AnnotatedString("  write-tool  proof ")) { it.length }
        assertEquals("write-tool".length, word)
        assertEquals(0, kitMdWidestWord(AnnotatedString("   ")) { it.length })
    }

    @Test
    fun `in a narrow window no cell breaks inside a word`() = runComposeUiTest {
        setContent {
            ChatTestTheme {
                Box(Modifier.width(240.dp)) { KitMarkdown(KitMdSource.blocks("t", table, false), actions) }
            }
        }
        waitForIdle()
        val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)).fetchSemanticsNodes()
        var checked = 0
        for (node in nodes) {
            val layout =
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
                    val out = mutableListOf<TextLayoutResult>()
                    get(out)
                    out.firstOrNull()
                } ?: continue
            val text = layout.layoutInput.text.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line, visibleEnd = false)
                val before = text[end - 1]
                val after = text[end]
                assertTrue(
                    before.isWhitespace() || after.isWhitespace(),
                    "\"$text\" breaks inside a word at $end (${text.substring(0, end)} | ${text.substring(end)})",
                )
            }
            checked++
        }
        assertTrue(checked >= 9, "read the layout of every cell, saw $checked")
    }
}
