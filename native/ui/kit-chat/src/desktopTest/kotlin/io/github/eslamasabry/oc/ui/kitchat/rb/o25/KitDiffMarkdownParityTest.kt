package io.github.eslamasabry.oc.ui.kitchat.rb.o25

import io.github.eslamasabry.oc.ui.kitchat.diff.DiffDisplay
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFile
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLine
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLineKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffMarkdownPatterns
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.diffDisplay
import io.github.eslamasabry.oc.ui.kitchat.diff.diffFenceStates
import io.github.eslamasabry.oc.ui.kitchat.diff.diffMarkdownUnits
import io.github.eslamasabry.oc.ui.kitchat.diff.isMarkdownPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/diff/kit_diff_markdown.dart at 8302de537 (standard §6 step 8). */
class KitDiffMarkdownParityTest {
    private fun added(vararg text: String) = text.mapIndexed { i, t -> DiffLine(t, DiffLineKind.Added, newNo = i + 1) }

    private fun cut(vararg text: String, inFence: Boolean = false) =
        diffMarkdownUnits(added(*text), inFence).map { u -> u.lines.map { it.text } to u.code }

    @Test fun `markdown paths are md, markdown and mdx, any case, by the last path part`() {
        assertTrue(isMarkdownPath("docs/README.MD"))
        assertTrue(isMarkdownPath("a/b.markdown"))
        assertTrue(isMarkdownPath("page.mdx"))
        assertFalse(isMarkdownPath("md/notes.txt"))
        assertFalse(isMarkdownPath("x.mdown"))
    }

    @Test fun `the four line patterns are Dart's`() {
        val p = DiffMarkdownPatterns
        assertTrue(p.fence.containsMatchIn("```kotlin"))
        assertTrue(p.fence.containsMatchIn("   ~~~"))
        assertFalse(p.fence.containsMatchIn("    ```"), "four spaces is an indented code line, not a fence")
        assertFalse(p.fence.containsMatchIn("``"))
        assertTrue(p.heading.containsMatchIn("###### Six"))
        assertTrue(p.heading.containsMatchIn("#"))
        assertFalse(p.heading.containsMatchIn("####### seven"))
        assertFalse(p.heading.containsMatchIn("#hashtag"))
        assertTrue(p.rule.containsMatchIn("  ***  "))
        assertTrue(p.rule.containsMatchIn("___"))
        assertFalse(p.rule.containsMatchIn("-*-"), "a rule repeats one mark")
        assertTrue(p.item.containsMatchIn("  - item"))
        assertTrue(p.item.containsMatchIn("123456789) nine digits"))
        assertFalse(p.item.containsMatchIn("1234567890. ten digits"))
        assertFalse(p.item.containsMatchIn("-no space"))
    }

    @Test fun `a run is cut into headings, paragraphs, items, quotes, tables, rules and blanks`() {
        assertEquals(
            listOf(
                listOf("# Title") to false,
                listOf("Para one", "wraps here") to false,
                listOf("") to false,
                listOf("- a", "  wrapped", "  - nested") to false,
                listOf("- b") to false,
                listOf("> q1", "> q2") to false,
                listOf("| t | u |", "|---|---|") to false,
                listOf("---") to false,
                listOf("#tag is prose", "still prose") to false,
            ),
            cut(
                "# Title", "Para one", "wraps here", "", "- a", "  wrapped", "  - nested", "- b", "> q1", "> q2",
                "| t | u |", "|---|---|", "---", "#tag is prose", "still prose",
            ),
        )
    }

    @Test fun `a paragraph stops at a list item and at any block start`() {
        assertEquals(
            listOf(listOf("words") to false, listOf("1. one") to false, listOf("2) two") to false),
            cut("words", "1. one", "2) two"),
        )
        assertEquals(listOf(listOf("words") to false, listOf("> quote") to false), cut("words", "> quote"))
    }

    @Test fun `fences stay code to their close, or to the end of the run`() {
        assertEquals(
            listOf(listOf("text") to false, listOf("```", "code", "```") to true, listOf("after") to false),
            cut("text", "```", "code", "```", "after"),
        )
        assertEquals(listOf(listOf("```", "open") to true), cut("```", "open"))
        assertEquals(
            listOf(listOf("inside", "```") to true, listOf("out") to false),
            cut("inside", "```", "out", inFence = true),
        )
    }

    @Test fun `the drawn markdown joins wrapped items and paragraphs, keeps quotes and tables`() {
        fun md(vararg text: String) = diffMarkdownUnits(added(*text), false).single().markdown
        assertEquals("- a wrapped\n  - nested", md("- a ", "  wrapped", "  - nested"))
        assertEquals("one two", md(" one ", " two"))
        assertEquals("> a\n> b", md("> a", "> b"))
        assertEquals("only", md("only"))
    }

    @Test fun `each side keeps its own fence state and an unknown gap resets both`() {
        val fenceAdded = DiffLine("```", DiffLineKind.Added, newNo = 1)
        val removedProse = DiffLine("prose", DiffLineKind.Removed, oldNo = 1)
        val addedCode = DiffLine("code", DiffLineKind.Added, newNo = 2)
        val after = DiffLine("after", DiffLineKind.Context, oldNo = 5, newNo = 9)
        val file =
            DiffFile(
                "a.md",
                kotlinx.collections.immutable.persistentListOf(
                    fenceAdded, removedProse, addedCode, io.github.eslamasabry.oc.ui.kitchat.diff.DiffGap(3), after,
                ),
                2, 1,
            )
        val state = diffFenceStates(file)
        assertEquals(false, state[fenceAdded])
        assertEquals(false, state[removedProse], "an added fence never opens on the old side")
        assertEquals(true, state[addedCode])
        assertEquals(false, state[after], "a gap whose lines are unknown resets both sides")
    }

    @Test fun `formatted rows draw blocks, code and blanks stay source rows`() {
        val file = DiffModel.fromBeforeAfter("a.md", null, "# Head\nOne\ntwo\n\n```\nx\n```")
        val rows = DiffModel.displayRows(file).rows
        val display = diffDisplay(rows, formatted = true, split = false, diffFenceStates(file), file)
        assertEquals(
            listOf("md:# Head", "md:One two", "src:", "src:```", "src:x", "src:```"),
            display.map {
                when (it) {
                    is DiffDisplay.Markdown -> "md:${it.unit.markdown}"
                    is DiffDisplay.Source -> "src:${(it.row as io.github.eslamasabry.oc.ui.kitchat.diff.DiffRow.Line).line.text}"
                    is DiffDisplay.Pair -> "pair"
                }
            },
        )
    }

    @Test fun `a gap's revealed lines are a run of their own`() {
        val gapLines = kotlinx.collections.immutable.persistentListOf(
            DiffLine("hidden one", DiffLineKind.Context, 2, 2),
        )
        val first = DiffLine("first", DiffLineKind.Context, 1, 1)
        val file =
            DiffFile(
                "a.md",
                kotlinx.collections.immutable.persistentListOf(first, io.github.eslamasabry.oc.ui.kitchat.diff.DiffGap(1, gapLines)),
                0, 0,
            )
        val rows =
            listOf(
                io.github.eslamasabry.oc.ui.kitchat.diff.DiffRow.Line(first, 0),
                io.github.eslamasabry.oc.ui.kitchat.diff.DiffRow.Line(gapLines[0], 1),
            )
        val display = diffDisplay(rows, formatted = true, split = false, diffFenceStates(file), file)
        assertEquals(2, display.size, "Dart draws the gap's shown lines apart from the paragraph above")
    }
}
