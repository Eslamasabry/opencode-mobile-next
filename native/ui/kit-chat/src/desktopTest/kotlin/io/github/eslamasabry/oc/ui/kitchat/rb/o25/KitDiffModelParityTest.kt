package io.github.eslamasabry.oc.ui.kitchat.rb.o25

import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFileStatus
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffGap
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLine
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLineKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/diff/kit_diff_model.dart at 8302de537 (standard §6 step 8). */
class KitDiffModelParityTest {
    @Test fun `constants are Dart's`() {
        assertEquals(3, DiffModel.VISIBLE_CONTEXT) // :195
        assertEquals(1_000_000, DiffModel.LCS_CELL_LIMIT) // :200
        assertEquals(2000, DiffModel.HIGHLIGHT_LINE_LIMIT) // :564
        assertEquals(1000, DiffModel.HIGHLIGHT_LINE_CHARS) // :567
        assertEquals(4.5, DiffModel.LEGIBLE_CONTRAST) // :652
        assertEquals(.2f, DiffModel.SELECTION_ALPHA) // :624
        assertEquals(listOf("Context", "Added", "Removed", "Hunk"), DiffLineKind.entries.map { it.name }) // :4
        assertEquals(listOf("Modified", "Added", "Deleted", "Renamed"), DiffFileStatus.entries.map { it.name }) // :27
    }

    @Test fun `before-after status: null before is added, null after deleted, an empty before is modified`() {
        assertEquals(DiffFileStatus.Added, DiffModel.fromBeforeAfter("a", null, "x").status)
        assertEquals(DiffFileStatus.Added, DiffModel.fromBeforeAfter("a", null, "").status)
        assertEquals(DiffFileStatus.Deleted, DiffModel.fromBeforeAfter("a", "x", null).status)
        // kit_diff_model.dart:146-152: only a null before makes an added file.
        assertEquals(DiffFileStatus.Modified, DiffModel.fromBeforeAfter("a", "", "x").status)
        assertEquals(DiffFileStatus.Renamed, DiffModel.fromBeforeAfter("a", "", "x", DiffFileStatus.Renamed).status)
    }

    @Test fun `texts keep the after text and patches keep the patch`() {
        assertEquals("b\n", DiffModel.fromBeforeAfter("a", "a\n", "b\n").fullText)
        assertNull(DiffModel.fromBeforeAfter("a", "a\n", "b\n").patch)
        val patch = "@@ -1 +1 @@\n-a\n+b"
        assertEquals(patch, DiffModel.parseFilePatch("a", patch).patch)
        val binary = DiffModel.parseFilePatch("p.png", "Binary files a/p.png and b/p.png differ")
        assertTrue(binary.binary)
        assertEquals("Binary files a/p.png and b/p.png differ", binary.patch)
    }

    @Test fun `patch numbers, gaps and the no-newline note`() {
        val file = DiffModel.parseFilePatch("a", "--- a/a\n+++ b/a\n@@ -10,2 +10,3 @@\n ctx\n-old\n+new\n+more\n\\ No newline at end of file")
        val first = file.segments.first()
        assertEquals(DiffGap(9), first, "the 9 lines before the hunk fold, count only")
        val lines = file.segments.filterIsInstance<DiffLine>()
        assertEquals(DiffLineKind.Hunk, lines[0].kind)
        assertEquals(listOf(10 to 10, 11 to null, null to 11, null to 12), lines.drop(1).map { it.oldNo to it.newNo })
        assertEquals(2 to 1, file.added to file.removed)
    }

    @Test fun `context folds keep three lines by a change, none at the file's ends`() {
        val before = (1..20).joinToString("\n") { "l$it" }
        val after = before.replace("l10", "L10")
        val file = DiffModel.fromBeforeAfter("a", before, after)
        val shape = file.segments.map { if (it is DiffGap) "gap${it.count}" else (it as DiffLine).kind.name[0].toString() }
        assertEquals(listOf("gap6", "C", "C", "C", "R", "A", "C", "C", "C", "gap7"), shape)
        // A run that would hide a single line is kept whole.
        val short = DiffModel.fromBeforeAfter("a", "a\nb\nc\nd\ne", "a\nb\nc\nd\nE")
        assertFalse(short.segments.any { it is DiffGap }, "four lines above a change: three kept, so the fourth stays too")
    }

    @Test fun `the index counts lines, numbers, changes, the longest line and every line`() {
        val file = DiffModel.fromBeforeAfter("a", "one\ntwo\nthree", "one\nTWO and more\nthree")
        assertEquals(4, file.totalLines)
        assertEquals(3, file.maxLineNumber)
        assertEquals(1, file.changeCount)
        assertEquals("TWO and more", file.longest)
        assertEquals(4, file.allLines.size)
        val hunk = file.hunks.single()
        // A hunk without an @@ line spans every line it shows, context included (:419-429).
        assertEquals(listOf(1, 3, 1, 3), listOf(hunk.oldStart, hunk.oldEnd, hunk.newStart, hunk.newEnd))
    }

    @Test fun `highlighting: json lines use javascript, empty or long lines and big or binary files stay plain`() {
        assertEquals("javascript", DiffModel.lineGrammar("json"))
        assertEquals("dart", DiffModel.lineGrammar("dart"))
        assertNull(DiffModel.lineGrammar(null))
        assertFalse(DiffModel.highlightsLine(""))
        assertTrue(DiffModel.highlightsLine("x".repeat(1000)))
        assertFalse(DiffModel.highlightsLine("x".repeat(1001)))
        assertTrue(DiffModel.fromBeforeAfter("a.kt", "a", "b").highlightable)
        assertFalse(DiffModel.parseFilePatch("p", "GIT binary patch").highlightable)
    }

    @Test fun `an oversized middle falls back to one removed-then-added block`() {
        val before = (1..1001).joinToString("\n") { "a$it" }
        val after = (1..1001).joinToString("\n") { "b$it" }
        val file = DiffModel.fromBeforeAfter("a", before, after)
        val kinds = file.segments.filterIsInstance<DiffLine>().map { it.kind }
        assertEquals(List(1001) { DiffLineKind.Removed } + List(1001) { DiffLineKind.Added }, kinds)
    }
}
