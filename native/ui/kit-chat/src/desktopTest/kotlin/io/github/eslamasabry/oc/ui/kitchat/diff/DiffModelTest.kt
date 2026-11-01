package io.github.eslamasabry.oc.ui.kitchat.diff

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-CHAT-058, the model half: ported from test/kit/kit_diff_view_test.dart group "1. parser" plus the patch rules of
 * plan 09 §9.3 (`\ No newline at end of file`, preambles, multi-file patches) and totality.
 */
class DiffModelTest {
    private val twoHunks =
        "--- a/lib/a.dart\n" +
            "+++ b/lib/a.dart\n" +
            "@@ -10,3 +10,4 @@\n" +
            " context ten\n" +
            "-old eleven\n" +
            "+new eleven\n" +
            "+new twelve\n" +
            " context twelve\n" +
            "@@ -60,2 +61,2 @@\n" +
            " context sixty\n" +
            "-old sixty-one\n" +
            "+new sixty-two\n"

    private fun numbered(n: Int, prefix: String = "line"): List<String> = (1..n).map { "$prefix $it" }

    /** 60 lines with line 31 changed (Dart `_sixty`). */
    private fun sixty(): DiffFile {
        val before = numbered(60)
        val after = before.toMutableList().also { it[30] = "changed line 31" }
        return DiffModel.fromBeforeAfter("notes.txt", before.joinToString("\n"), after.joinToString("\n"))
    }

    /** [changes] single-line changes, 20 lines apart (Dart `_spaced`). */
    private fun spaced(path: String, changes: Int): DiffFile {
        val before = numbered(changes * 20 + 10, path)
        val after = before.toMutableList()
        for (c in 0 until changes) after[10 + c * 20] = "changed $path ${c + 1}"
        return DiffModel.fromBeforeAfter(path, before.joinToString("\n"), after.joinToString("\n"))
    }

    private fun DiffFile.lines(): List<DiffLine> = segments.filterIsInstance<DiffLine>()

    @Test
    fun `B-CHAT-058 a patch parses into counts, line numbers and hunk lines`() {
        val file = DiffModel.parseFilePatch("lib/a.dart", twoHunks)
        assertEquals(3, file.added)
        assertEquals(2, file.removed)
        assertEquals(2, file.changeCount)
        val lines = file.lines()
        val hunks = lines.filter { it.kind == DiffLineKind.Hunk }
        assertEquals(2, hunks.size)
        assertEquals(10, hunks.first().newNo)
        val removed = lines.first { it.text == "old eleven" }
        assertEquals(DiffLineKind.Removed to 11, removed.kind to removed.oldNo)
        val added = lines.first { it.text == "new twelve" }
        assertEquals(DiffLineKind.Added to 12, added.kind to added.newNo)
        val kept = lines.first { it.text == "context twelve" }
        assertEquals(12 to 13, kept.oldNo to kept.newNo)
        // The skipped lines before and between hunks are count-only gaps.
        val gaps = file.segments.filterIsInstance<DiffGap>()
        assertEquals(listOf(9 to null, 47 to null), gaps.map { it.count to it.lines })
    }

    @Test
    fun `B-CHAT-058 diff --git, --- and +++ preambles are dropped`() {
        val patch = "diff --git a/lib/a.dart b/lib/a.dart\nindex 1234567..89abcde 100644\n$twoHunks"
        val file = DiffModel.parseFilePatch("lib/a.dart", patch)
        val texts = file.lines().filter { it.kind != DiffLineKind.Hunk }.map { it.text }
        assertTrue(texts.none { it.contains("a/lib") || it.contains("b/lib") || it.startsWith("index") }, "$texts")
        assertEquals(DiffLineKind.Hunk, (file.segments[1] as DiffLine).kind)
        assertEquals(5 + 3, texts.size) // 3 context + 2 removed + 3 added
    }

    @Test
    fun `B-CHAT-058 no newline at end of file is a note on the line above, not a line`() {
        val patch = "@@ -1,1 +1,1 @@\n-old\n\\ No newline at end of file\n+new\n\\ No newline at end of file\n"
        val file = DiffModel.parseFilePatch("a.txt", patch)
        assertEquals(1 to 1, file.added to file.removed)
        val lines = file.lines().filter { it.kind != DiffLineKind.Hunk }
        assertEquals(listOf("old", "new"), lines.map { it.text })
        assertTrue(lines.all { it.noNewlineAtEnd })
        val plain = DiffModel.parseFilePatch("a.txt", "@@ -1 +1 @@\n-old\n+new\n").lines()
        assertTrue(plain.none { it.noNewlineAtEnd })
    }

    @Test
    fun `B-CHAT-058 unchanged text folds keeping 3 context lines around a change`() {
        val file = sixty()
        assertEquals(Triple(1, 1, 1), Triple(file.added, file.removed, file.changeCount))
        val first = file.segments.first() as DiffGap
        assertEquals(27, first.count)
        assertEquals(27, first.lines?.size)
        assertEquals(
            listOf("line 28", "line 29", "line 30", "line 31", "changed line 31", "line 32", "line 33", "line 34"),
            file.lines().map { it.text },
        )
        assertEquals(26, (file.segments.last() as DiffGap).count)
        assertEquals(60 + 1, file.totalLines)
    }

    @Test
    fun `B-CHAT-058 after-only text is an added file and before-only a deleted one`() {
        val file = DiffModel.fromBeforeAfter("new.txt", null, "a\nb")
        assertEquals(DiffFileStatus.Added, file.status)
        assertEquals(2 to 0, file.added to file.removed)
        assertEquals(DiffFileStatus.Added, DiffModel.fromBeforeAfter("new.txt", "", "a").status)
        assertEquals(DiffFileStatus.Deleted, DiffModel.fromBeforeAfter("old.txt", "a", null).status)
        assertEquals(DiffFileStatus.Modified, DiffModel.fromBeforeAfter("x.txt", "", "a", DiffFileStatus.Modified).status)
    }

    @Test
    fun `B-CHAT-058 separate changes are found, not one block`() {
        assertEquals(3, spaced("a", 3).changeCount)
    }

    @Test
    fun `B-CHAT-058 above the cell limit the diff falls back to one block and keeps prefix and suffix`() {
        val middle = 1001 // odd: the last middle line differs, so (1001 + 1)² cells is over the 1,000,000 limit
        val before = listOf("head") + (1..middle).map { "a $it" } + listOf("tail")
        val after = listOf("head") + (1..middle).map { if (it % 2 == 0) "a $it" else "b $it" } + listOf("tail")
        val file = DiffModel.fromBeforeAfter("big.txt", before.joinToString("\n"), after.joinToString("\n"))
        assertEquals(1, file.changeCount)
        assertEquals(middle to middle, file.added to file.removed)
        val lines = file.lines()
        assertEquals("head", lines.first().text)
        assertEquals(DiffLineKind.Context, lines.first().kind)
        assertEquals("tail", lines.last().text)
        assertEquals(DiffLineKind.Context, lines.last().kind)
        // Under the limit the same shape is compared line by line.
        val small = DiffModel.fromBeforeAfter("s.txt", before.take(200).joinToString("\n"), after.take(200).joinToString("\n"))
        assertTrue(small.changeCount > 1)
    }

    @Test
    fun `B-CHAT-058 hunks report the line ranges they cover on each side`() {
        val hunks = DiffModel.parseFilePatch("lib/a.dart", twoHunks).hunks
        assertEquals(2, hunks.size)
        assertEquals(DiffHunk(oldStart = 10, oldEnd = 12, newStart = 10, newEnd = 13, headerSegment = 1), hunks[0])
        assertEquals(60 to 61, hunks[1].oldStart to hunks[1].oldEnd)
        assertEquals(61 to 62, hunks[1].newStart to hunks[1].newEnd)
    }

    @Test
    fun `B-CHAT-058 display rows say gaps with their place and hunk ranges once`() {
        val rows = DiffModel.displayRows(DiffModel.parseFilePatch("lib/a.dart", twoHunks)).rows
        // A fold line already says where a hunk starts: no hunk header after a gap.
        assertTrue(rows.none { it is DiffRow.HunkHeader })
        val gaps = rows.filterIsInstance<DiffRow.Gap>()
        assertEquals(listOf(9 to DiffGapPosition.Above, 47 to DiffGapPosition.Between), gaps.map { it.hidden to it.position })

        val atTop = DiffModel.displayRows(DiffModel.parseFilePatch("a.txt", "@@ -1,3 +1,4 @@\n a\n-b\n+c\n+d\n e\n")).rows
        assertEquals(DiffRow.HunkHeader(oldStart = 1, oldEnd = 3, newStart = 1, newEnd = 4, segment = 0), atTop.first())

        val folded = DiffModel.displayRows(sixty()).rows
        assertEquals(DiffGapPosition.Above, (folded.first() as DiffRow.Gap).position)
        assertEquals(DiffGapPosition.Below, (folded.last() as DiffRow.Gap).position)
    }

    @Test
    fun `B-CHAT-058 the cap counts only lines and reports what it cut`() {
        val big = DiffModel.fromBeforeAfter("big.txt", null, numbered(3200).joinToString("\n"))
        val capped = DiffModel.displayRows(big, maxLines = 400)
        assertEquals(400, capped.rows.count { it is DiffRow.Line })
        assertEquals(400 to 3200, capped.shownLines to capped.totalLines)
        assertTrue(capped.cut)
        val whole = DiffModel.displayRows(sixty(), maxLines = 12)
        assertFalse(whole.cut)
        assertEquals(8, whole.shownLines)
        assertEquals(2, whole.rows.count { it is DiffRow.Gap }) // gaps take no line from the cap
    }

    @Test
    fun `B-CHAT-058 a multi-file patch splits into files with paths and kinds`() {
        val patch =
            "diff --git a/lib/a.dart b/lib/a.dart\n" +
                "index 1..2 100644\n" +
                twoHunks +
                "diff --git a/new.txt b/new.txt\n" +
                "new file mode 100644\n" +
                "--- /dev/null\n" +
                "+++ b/new.txt\n" +
                "@@ -0,0 +1,2 @@\n" +
                "+one\n" +
                "+two\n" +
                "diff --git a/gone.txt b/gone.txt\n" +
                "deleted file mode 100644\n" +
                "--- a/gone.txt\n" +
                "+++ /dev/null\n" +
                "@@ -1 +0,0 @@\n" +
                "-bye\n" +
                "diff --git a/old.txt b/moved.txt\n" +
                "similarity index 100%\n" +
                "rename from old.txt\n" +
                "rename to moved.txt\n" +
                "diff --git a/logo.png b/logo.png\n" +
                "Binary files a/logo.png and b/logo.png differ\n"
        val files = DiffModel.parsePatch(patch)
        assertEquals(listOf("lib/a.dart", "new.txt", "gone.txt", "moved.txt", "logo.png"), files.map { it.path })
        assertEquals(listOf("Modified", "Added", "Deleted", "Renamed", "Modified"), files.map { it.status.name })
        assertEquals(3 to 2, files[0].added to files[0].removed)
        assertEquals(2 to 0, files[1].added to files[1].removed)
        assertEquals(0 to 1, files[2].added to files[2].removed)
        assertEquals("old.txt", files[3].oldPath)
        assertTrue(files[4].binary)
        // Plain `diff -u` headers without `diff --git`, and a removed line that looks like a header.
        val plain = "--- x.txt\t2026-01-01\n+++ x.txt\t2026-01-02\n@@ -1,2 +1,1 @@\n--- not a header\n++++ still not\n"
        val one = DiffModel.parsePatch(plain)
        assertEquals(listOf("x.txt"), one.map { it.path })
        assertEquals(1 to 1, one[0].added to one[0].removed)
        // A bare hunk with no file header takes the default path.
        assertEquals(listOf("changes.diff"), DiffModel.parsePatch("@@ -1 +1 @@\n-a\n+b\n", "changes.diff").map { it.path })
        assertTrue(DiffModel.parsePatch("").isEmpty())
    }

    @Test
    fun `B-CHAT-058 CRLF text shows without carriage returns`() {
        val patch = DiffModel.parseFilePatch("a.txt", "@@ -1,2 +1,2 @@\r\n keep\r\n-old\r\n+new\r\n")
        assertEquals(listOf("keep", "old", "new"), patch.lines().filter { it.kind != DiffLineKind.Hunk }.map { it.text })
        val texts = DiffModel.fromBeforeAfter("a.txt", "keep\r\nold\r\n", "keep\r\nnew\r\n")
        assertEquals(1 to 1, texts.added to texts.removed)
        assertTrue(texts.lines().none { it.text.contains('\r') })
    }

    @Test
    fun `B-CHAT-058 malformed hunk headers and huge numbers never throw`() {
        val inputs = listOf(
            "@@ -x +y @@\n+a\n",
            "@@ -99999999999999999999 +1 @@\n-a\n+b\n",
            "@@ -2147483647,2147483647 +2147483647,2147483647 @@\n a\n+b\n a\n",
            "@@\n@@ @@\n-\n+\n \n\\\n",
            "+++ \n--- \n",
            "diff --git\n",
        )
        for (input in inputs) {
            val files = DiffModel.parsePatch(input)
            files.forEach { DiffModel.displayRows(it, maxLines = 12) }
            DiffModel.displayRows(DiffModel.parseFilePatch("p", input))
        }
        val file = DiffModel.parseFilePatch("p", "@@ -x +y @@\n+a\n")
        assertNull(file.lines().firstOrNull { it.kind == DiffLineKind.Hunk })
        assertEquals(1, file.added)
    }

    @Test
    fun `B-CHAT-058 any input parses without throwing`() {
        val pieces = listOf(
            "@@ -1,3 +1,4 @@", "@@ -0,0 +1 @@", "@@ -5 +5,0 @@", "@@", "+", "-", " ", "\\ No newline at end of file",
            "diff --git a/x b/y",
            "--- a/x", "+++ b/x", "--- /dev/null", "+++ /dev/null", "rename from a", "rename to b", "new file mode 100644",
            "Binary files a and b differ", "GIT binary patch", "", "\r", "\t", "x", "+added", "-removed", " ctx", "‮evil",
        )
        val random = Random(58)
        repeat(1500) {
            val patch = buildString {
                repeat(random.nextInt(0, 40)) {
                    append(pieces[random.nextInt(pieces.size)])
                    if (random.nextInt(5) == 0) append(random.nextInt(-5, 99999))
                    append(if (random.nextInt(7) == 0) "\r\n" else "\n")
                }
            }
            for (file in DiffModel.parsePatch(patch)) {
                DiffModel.displayRows(file, maxLines = random.nextInt(-2, 20))
                file.changeCount
                file.hunks
            }
            DiffModel.displayRows(DiffModel.parseFilePatch("p", patch))
            val before = if (random.nextBoolean()) null else patch.take(random.nextInt(patch.length + 1))
            val after = if (random.nextInt(4) == 0) null else patch.reversed()
            val texts = DiffModel.fromBeforeAfter("t", before, after)
            DiffModel.displayRows(texts, maxLines = 12)
            texts.hunks
        }
    }
}
