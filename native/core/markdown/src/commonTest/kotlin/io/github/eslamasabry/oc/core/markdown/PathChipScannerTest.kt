package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from test/markdown_path_link_test.dart (the logic tests; the chip widget tests belong to the kit). */
class PathChipScannerTest {
    @Test
    fun `B-CHAT-064 anchored and extensioned paths look like files`() {
        for (path in listOf(
            "/tmp/opencode/shots/home-desktop.png",
            "lib/state/connection.dart",
            "lib/state/connection.dart:1146",
            "~/notes/todo.md",
            "./scripts/run.sh",
            "/tmp/rec-stop",
        )) {
            assertTrue(PathChipScanner.looksLikeFilePath(path), path)
        }
    }

    @Test
    fun `B-CHAT-064 non-paths do not`() {
        for (text in listOf(
            "and/or",
            "https://example.com/a.png",
            "flutter test",
            "a/b c/d.txt",
            "foo.dart",
            "x/y",
            "abc",
            "/" + "a".repeat(300),
            "lib/a.verylongext",
        )) {
            assertFalse(PathChipScanner.looksLikeFilePath(text), text)
        }
    }

    @Test
    fun `B-CHAT-064 only a trailing line number is stripped`() {
        assertEquals("lib/a.dart" to 120, PathChipScanner.stripLineSuffix("lib/a.dart:120"))
        assertEquals("lib/a.dart" to null, PathChipScanner.stripLineSuffix("lib/a.dart"))
        assertEquals("/tmp/c:120/x.png" to null, PathChipScanner.stripLineSuffix("/tmp/c:120/x.png"))
    }

    @Test
    fun `B-CHAT-064 inline code that looks like a path carries a path candidate, other code does not`() {
        val inlines = InlineParser.parse("Open `lib/a.dart:12` and run `flutter test`.")
        assertEquals("Open lib/a.dart:12 and run flutter test.", inlines.text)
        val candidates = inlines.spans.filter { it.kind is SpanKind.PathCandidate }
        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(SpanKind.PathCandidate("lib/a.dart", 12), candidate.kind)
        assertEquals("lib/a.dart:12", inlines.text.substring(candidate.start, candidate.end))
        // Both stay code: a candidate is drawn as mono until the engine confirms the file.
        assertEquals(2, inlines.spans.count { it.kind == SpanKind.Code })
    }

    @Test
    fun `B-CHAT-061 a prompt bubble measures words without marks and counts its path chips`() {
        val display = markdownDisplay("See **this** in `lib/a.dart` and [docs](https://a.dev), `ls -la`.")
        assertEquals("See this in lib/a.dart and docs, ls -la.", display.text)
        assertEquals(1, display.pathChips)
    }
}
