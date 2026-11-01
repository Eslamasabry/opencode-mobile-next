package io.github.eslamasabry.oc.core.markdown

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Ported from test/markdown_streaming_test.dart, the streaming cases of test/kit/kit_markdown_test.dart (2, 3, 4) and
 * the perf_chat "ten tail updates" check (plan 09 §14 `TailUpdateTest`), plus the tail re-parse equivalence the
 * pure-Kotlin parser relies on.
 */
class StreamingMarkdownTest {
    private val settled =
        "## Plan\n\n" +
            "Read the **controller** first.\n\n" +
            "```dart\nvoid main() => print(1);\n```\n\n" +
            "- one\n- two\n\n"

    /** Fixtures from the six Flutter Markdown test files, every block kind and the awkward edges. */
    private val corpus =
        listOf(
            settled + "The tail keeps streaming in.",
            "# Title\n\n### Small\n\nSome **bold** and `code` here.\n\n- one\n- two\n\n> quoted\n\n---\n\n[site](https://example.com)",
            "1. Upgrade the router\n\n```\nnpm install router@6\n```\n\n2. Or keep v5\n3. Then test\n\n- a\n- b",
            "1. Collect.\n2. Ask only for details needed to understand\n   impact.\n3. Draft.\n   ```sh\n   gh issue create\n   ```\n",
            "Intro\n| a | b |\n| :--- | ---: |\n| 1 | **2** |\n| x\n\nafter the table",
            "Shots:\n\n![Home](/tmp/home.png)\n\n![Home again](/tmp/home.png)\n![Set](/tmp/set.png)\n\nDone ![x](/a.png) inline.",
            "````markdown\n```dart\nclass A {}\n```\n````\n\n~~~\nopen fence that never closes\n```\nstill code",
            "```choices\n- Yes\n- No\n```\n\n```checklist\n[x] a\n[ ] b\n```\n\n```command\nls -la\n```\nend",
            "Make it **bold** and *ita* and `cod` and ~~str~~ and [label](https://a.dev) then `lib/a.dart:12`.",
            "> one\n> two\nthree\n\n- x\n  - y\n    - z\n\n#no heading\n# Heading *with* marks",
            "a\r\nb\r\n```\r\ncode\r\n```\r\n| a | b |\r\n| --- | --- |\r\n| 1 | 2 |\r\n",
            "<!-- hidden -->\n# Report\n\nAdd `<!-- TODO -->` here\n\n<!--\nmulti\n-->\n\nAfter.\n\n```html\n<!-- keep -->\n```\n",
        )

    private fun stream(text: String, chunks: List<String>, check: Boolean = true): StreamingMarkdown {
        val md = StreamingMarkdown("p")
        var sent = ""
        for (chunk in chunks) {
            md.append(chunk)
            sent += chunk
            if (check) {
                assertEquals(MarkdownBlocks.parse("p", sent, streaming = true), md.blocks, "after \"${sent.takeLast(40)}\"")
            }
        }
        assertEquals(text, md.text)
        return md
    }

    private fun split(text: String, size: Int) = text.chunked(size)

    private fun randomSplit(text: String, random: Random): List<String> {
        val chunks = ArrayList<String>()
        var at = 0
        while (at < text.length) {
            val size = random.nextInt(1, 12)
            chunks += text.substring(at, minOf(text.length, at + size))
            at += size
        }
        return chunks
    }

    @Test
    fun `CHAT-GENUI-02 a streamed reply parses to exactly what the whole text parses to, at every delta`() {
        for (text in corpus) {
            for (chunks in listOf(split(text, 1), split(text, 3), split(text, 7), randomSplit(text, Random(text.length)))) {
                val md = stream(text, chunks)
                md.finish()
                assertEquals(MarkdownBlocks.parse("p", text), md.blocks, text)
            }
        }
    }

    @Test
    fun `CHAT-GENUI-02 a partial line that joins the group before it re-parses that group`() {
        for ((head, more) in listOf(
            "- one\n-" to " two",
            "| a | b |\n| --- | --- |\n|" to " 1 | 2 |",
            "1. one\n  " to " still one",
            "![a](/tmp/a.png)\n\n!" to "[b](/tmp/b.png)",
            "> one\n" to "> two",
        )) {
            val md = StreamingMarkdown("p")
            md.append(head)
            md.append(more)
            assertEquals(MarkdownBlocks.parse("p", head + more, streaming = true), md.blocks, head + more)
        }
    }

    @Test
    fun `CHAT-GENUI-02 random Markdown streamed in random chunks parses like the whole text`() {
        val pieces =
            listOf(
                "- ", "* ", "1. ", "2) ", "  ", "    ", "> ", "# ", "### ", "```", "~~~", "````", "---", "| ", " | ",
                "|---|---|", "| --- | :---: |", "![a](/tmp/a.png)", "![b](https://x.dev/b.png)", "**", "*", "`", "~~",
                "[l](https://a.dev)", "word", "more words", "lib/a.dart:3", "<!-- c -->", "<!--", "-->", "\n", "\n",
                "\n", "\n\n", "\r\n", "choices", "[x] ", "[ ] ",
            )
        val random = Random(42)
        repeat(1_500) { round ->
            val text = buildString { repeat(random.nextInt(1, 30)) { append(pieces[random.nextInt(pieces.size)]) } }
            val md = StreamingMarkdown("p")
            var sent = ""
            for (chunk in randomSplit(text, random)) {
                md.append(chunk)
                sent += chunk
                assertEquals(MarkdownBlocks.parse("p", sent, streaming = true), md.blocks, "round $round: \"$sent\"")
            }
            md.finish()
            assertEquals(MarkdownBlocks.parse("p", text), md.blocks, "round $round finished: \"$text\"")
        }
    }

    @Test
    fun `CHAT-GENUI-02 ten tail updates re-parse only the tail and rebuild no settled block`() {
        val md = StreamingMarkdown("p")
        md.append(settled + "The tail")
        val heading = md.blocks.first { it is Block.Heading }
        val code = md.blocks.first { it is Block.Code }
        val items = md.blocks.filterIsInstance<Block.ListItem>()
        val parsesBefore = md.markdownParses
        repeat(10) { md.append(" more") }
        assertEquals(10, md.tailReparses)
        assertEquals(parsesBefore, md.markdownParses)
        assertSame(heading, md.blocks.first { it is Block.Heading })
        assertSame(code, md.blocks.first { it is Block.Code })
        items.zip(md.blocks.filterIsInstance<Block.ListItem>()).forEach { (a, b) -> assertSame(a, b) }
        assertEquals("P \"The tail" + " more".repeat(10) + "\"", dump(md.blocks.last()))
    }

    @Test
    fun `CHAT-GENUI-02 a delta hands back settled blocks as the same instances and only the tail as new`() {
        val md = StreamingMarkdown("p")
        val first = md.append(settled + "The tail")
        val second = md.append(" keeps")
        // Every block but the growing paragraph comes back as the very instance published before.
        assertEquals(first.all.size, second.all.size)
        first.all.dropLast(1).zip(second.all.dropLast(1)).forEach { (a, b) -> assertSame(a, b) }
        assertTrue(second.stable.isNotEmpty())
        assertNotSame(first.all.last(), second.all.last())
        assertEquals("P \"The tail keeps\"", dump(second.tail.last()))
        assertEquals(md.blocks, second.all)
    }

    @Test
    fun `CHAT-GENUI-02 an edited earlier block still re-renders and unchanged blocks keep their instances`() {
        val md = StreamingMarkdown("p")
        md.append(settled + "The tail")
        val code = md.blocks.first { it is Block.Code }
        md.replaceAll(settled.replace("## Plan", "## New plan") + "The tail")
        assertEquals("H2 \"New plan\"", dump(md.blocks.first()))
        // Every block after the edit moved by 4 characters, so it is a new block with a new key...
        assertNotSame(code, md.blocks.first { it is Block.Code })
        // ...while an edit after it keeps it.
        val kept = md.blocks.first { it is Block.Code }
        md.replaceAll(md.text.replace("The tail", "A new tail"))
        assertSame(kept, md.blocks.first { it is Block.Code })
    }

    @Test
    fun `CHAT-GENUI-17 a fence that closes switches from open to closed`() {
        val md = StreamingMarkdown("p")
        md.append("```dart\nfinal a = 1;")
        assertTrue((md.blocks.single() as Block.Code).open)
        md.append("\n```")
        val closed = md.blocks.single() as Block.Code
        assertFalse(closed.open)
        assertEquals("final a = 1;\n", closed.copyText)
    }

    @Test
    fun `CHAT-GENUI-17 a long open fence grows without parsing until a line could close it`() {
        val md = StreamingMarkdown("p")
        md.append("Intro\n\n```kotlin\n")
        val intro = md.blocks.first()
        val body = StringBuilder()
        for (n in 1..400) {
            val line = "val x$n = $n\n"
            body.append(line)
            md.append(line)
        }
        val reparsesAtEnd = md.tailReparses
        assertTrue(md.openFenceAppends >= 399, "fast path used ${md.openFenceAppends} times")
        assertTrue(reparsesAtEnd <= 2, "tail re-parsed $reparsesAtEnd times")
        assertSame(intro, md.blocks.first())
        assertEquals(MarkdownBlocks.parse("p", md.text, streaming = true), md.blocks)
        md.append("```")
        assertFalse((md.blocks.last() as Block.Code).open)
        assertEquals(body.toString(), (md.blocks.last() as Block.Code).text + "\n")
    }

    @Test
    fun `CHAT-GENUI-02 CR and LF split across two chunks read as one line break`() {
        val md = StreamingMarkdown("p")
        md.append("```\r")
        md.append("\ncode\r")
        md.append("\n```\r")
        md.append("\nafter")
        md.finish()
        assertEquals(MarkdownBlocks.parse("p", "```\r\ncode\r\n```\r\nafter"), md.blocks)
        assertEquals(listOf("C[] \"code\"", "P \"after\""), dump(md.blocks))
        assertEquals("code\r\n", (md.blocks.first() as Block.Code).copyText)
    }

    @Test
    fun `CHAT-GENUI-21 the end of streaming draws the exact text and keeps blocks that did not differ`() {
        val md = StreamingMarkdown("p")
        md.append("Make it **bold")
        assertEquals("Make it bold", drawn(md.blocks))
        md.finish()
        assertEquals("Make it **bold", drawn(md.blocks))

        val exact = StreamingMarkdown("p")
        exact.append("Make it **bold**")
        val before = exact.blocks.single()
        exact.finish()
        assertSame(before, exact.blocks.single())
    }

    @Test
    fun `CHAT-GENUI-16 text with an HTML comment block parses in full and still streams right`() {
        val text = corpus.last()
        val md = stream(text, split(text, 5))
        assertTrue(md.markdownParses > 1)
        assertFalse(drawn(md.blocks).contains("hidden"))
    }

    @Test
    fun `CHAT-GENUI-20 an agent block appears only once its fence closes`() {
        val md = StreamingMarkdown("p")
        md.append("```choices\nA\nB")
        assertTrue(md.blocks.single() is Block.Code)
        md.append("\n```")
        assertEquals("A choices A,B", dump(md.blocks.single()))
    }
}
