package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The inline scanner against CommonMark 0.31.2 spec examples (emphasis 350–424, code spans 328–349, backslash escapes
 * 12–33, entities 25–41, links 481–) and GFM strikethrough, plus the chat probes p01–p04, p12, p13, p31 of
 * native/docs/parity/MARKDOWN-EVAL.md. Spans print as tags: `<b>` bold, `<i>` italic, `<s>` strike, `<c>` code,
 * `<a href>` link; equal ranges nest in the order the parser lists them (outer first). "dev:" marks the few places
 * where the chat engine deliberately differs from the spec (see [InlineScan]).
 */
class InlineScanTest {
    private fun fmt(source: String): String {
        val inlines = InlineParser.parse(source)
        val spans = inlines.spans.filter { it.kind !is SpanKind.PathCandidate }
        val out = StringBuilder()
        val open = ArrayList<InlineSpan>()
        fun tag(kind: SpanKind, close: Boolean): String {
            val name =
                when (kind) {
                    SpanKind.Bold -> "b"
                    SpanKind.Italic -> "i"
                    SpanKind.Strike -> "s"
                    SpanKind.Code -> "c"
                    is SpanKind.Link -> if (close) "a" else "a ${kind.url}"
                    else -> "?"
                }
            return if (close) "</$name>" else "<$name>"
        }
        var next = 0
        for (pos in 0..inlines.text.length) {
            while (open.isNotEmpty() && open.last().end == pos) out.append(tag(open.removeAt(open.lastIndex).kind, true))
            while (next < spans.size && spans[next].start == pos) {
                out.append(tag(spans[next].kind, false))
                open += spans[next]
                next++
            }
            if (pos < inlines.text.length) out.append(inlines.text[pos])
        }
        return out.toString()
    }

    private fun check(source: String, expected: String) = assertEquals(expected, fmt(source), source)

    @Test
    fun `cm 350-376 emphasis with asterisks and underscores follows the flanking rules`() {
        check("*foo bar*", "<i>foo bar</i>")
        check("a * foo bar*", "a * foo bar*")
        check("a*\"foo\"*", "a*\"foo\"*")
        check("* a *", "* a *")
        check("_foo bar_", "<i>foo bar</i>")
        check("_ foo bar_", "_ foo bar_")
        check("a_\"foo\"_", "a_\"foo\"_")
        check("foo_bar_", "foo_bar_")
        check("5_6_78", "5_6_78")
        check("пристаням_стремятся_", "пристаням_стремятся_")
        check("aa_\"bb\"_cc", "aa_\"bb\"_cc")
        check("foo-_(bar)_", "foo-<i>(bar)</i>")
        check("_foo*", "_foo*")
        check("*foo bar *", "*foo bar *")
        check("*(*foo)", "*(*foo)")
        check("*(*foo*)*", "<i>(<i>foo</i>)</i>")
        check("_foo bar _", "_foo bar _")
        check("_(_foo)", "_(_foo)")
        check("_(_foo_)_", "<i>(<i>foo</i>)</i>")
        check("_foo_bar", "_foo_bar")
        check("_пристаням_стремятся", "_пристаням_стремятся")
        check("_foo_bar_baz_", "<i>foo_bar_baz</i>")
        check("_(bar)_.", "<i>(bar)</i>.")
    }

    @Test
    fun `cm 377-400 strong emphasis`() {
        check("**foo bar**", "<b>foo bar</b>")
        check("** foo bar**", "** foo bar**")
        check("a**\"foo\"**", "a**\"foo\"**")
        check("__foo bar__", "<b>foo bar</b>")
        check("__ foo bar__", "__ foo bar__")
        check("a__\"foo\"__", "a__\"foo\"__")
        check("foo__bar__", "foo__bar__")
        check("5__6__78", "5__6__78")
        check("__foo, __bar__, baz__", "<b>foo, <b>bar</b>, baz</b>")
        check("foo-__(bar)__", "foo-<b>(bar)</b>")
        check("**foo bar **", "**foo bar **")
        check("**(**foo)", "**(**foo)")
        check("*(**foo**)*", "<i>(<b>foo</b>)</i>")
        check(
            "**Gomphocarpus (*Gomphocarpus physocarpus*, syn. *Asclepias physocarpa*)**",
            "<b>Gomphocarpus (<i>Gomphocarpus physocarpus</i>, syn. <i>Asclepias physocarpa</i>)</b>",
        )
        check("**foo \"*bar*\" foo**", "<b>foo \"<i>bar</i>\" foo</b>")
        check("__foo bar __", "__foo bar __")
        check("__(__foo)", "__(__foo)")
        check("_(__foo__)_", "<i>(<b>foo</b>)</i>")
        check("__foo__bar", "__foo__bar")
    }

    @Test
    fun `cm 401-424 nested emphasis and the rule of three`() {
        check("*foo [bar](/url)*", "<i>foo <a /url>bar</a></i>")
        check("_foo __bar__ baz_", "<i>foo <b>bar</b> baz</i>")
        check("_foo _bar_ baz_", "<i>foo <i>bar</i> baz</i>")
        check("*foo *bar**", "<i>foo <i>bar</i></i>")
        check("*foo **bar** baz*", "<i>foo <b>bar</b> baz</i>")
        check("***foo** bar*", "<i><b>foo</b> bar</i>")
        check("*foo **bar***", "<i>foo <b>bar</b></i>")
        check("*foo **bar *baz* bim** bam*", "<i>foo <b>bar <i>baz</i> bim</b> bam</i>")
        check("**foo **bar baz**", "**foo <b>bar baz</b>")
        check("*foo *bar baz*", "*foo <i>bar baz</i>")
        check("*[bar*](/url)", "*<a /url>bar*</a>")
        check("_foo [bar_](/url)", "_foo <a /url>bar_</a>")
        check("*a `*`*", "<i>a <c>*</c></i>")
        check("_a `_`_", "<i>a <c>_</c></i>")
        check("***strong emph***", "<b><i>strong emph</i></b>")
        check("***strong** in emph*", "<i><b>strong</b> in emph</i>")
        check("***emph* in strong**", "<b><i>emph</i> in strong</b>")
    }

    @Test
    fun `chat probes p01 p02 p31, stars and underscores between word characters stay literal`() {
        check("2 * 3 * 4 = 24", "2 * 3 * 4 = 24")
        check("a*b*c", "a*b*c")
        check("2*3*4", "2*3*4")
        check("x**2 + y**2", "x**2 + y**2")
        check("see __init__.py and os.__name__", "see __init__.py and os.__name__")
        check("my_var_name and snake_case_here", "my_var_name and snake_case_here")
        check("_italic_ and __bold__", "<i>italic</i> and <b>bold</b>")
        check("*a **b** c*", "<i>a <b>b</b> c</i>")
        check("**Note**: it works", "<b>Note</b>: it works")
        check("**重点**内容", "<b>重点</b>内容")
    }

    @Test
    fun `dev, the spec's intraword asterisk emphasis is literal in chat`() {
        check("foo*bar*", "foo*bar*")
        check("5*6*78", "5*6*78")
        check("*foo*bar", "*foo*bar")
        check("foo**bar**", "foo**bar**")
        check("**foo**bar", "**foo**bar")
    }

    @Test
    fun `cm 328-349 code spans of any backtick length`() {
        check("`foo`", "<c>foo</c>")
        check("`` foo ` bar ``", "<c>foo ` bar</c>")
        check("` `` `", "<c>``</c>")
        check("`  ``  `", "<c> `` </c>")
        check("` a`", "<c> a</c>")
        check("`foo``bar``", "`foo<c>bar</c>")
        check("`foo\\`bar`", "<c>foo\\</c>bar`")
        check("``foo`bar``", "<c>foo`bar</c>")
        check("*foo`*`", "*foo<c>*</c>")
        check("```not closed", "```not closed")
        check("`a` and ``b``", "<c>a</c> and <c>b</c>")
    }

    @Test
    fun `cm 12-33 backslash escapes`() {
        check("\\*not emphasized*", "*not emphasized*")
        check("\\[not a link](/foo)", "[not a link](/foo)")
        check("\\`not code`", "`not code`")
        check("\\\\*emphasis*", "\\<i>emphasis</i>")
        check("\\→\\A\\a\\ \\3\\φ\\«", "\\→\\A\\a\\ \\3\\φ\\«")
        check("\\!\\\"\\#\\\$\\%\\&\\'\\(\\)\\*\\+\\,\\-\\.\\/", "!\"#\$%&'()*+,-./")
        check("\\:\\;\\<\\=\\>\\?\\@\\[\\\\\\]\\^\\_\\`\\{\\|\\}\\~", ":;<=>?@[\\]^_`{|}~")
        check("`` \\[\\` ``", "<c>\\[\\`</c>")
        check("trailing \\", "trailing \\")
        check("__init__\\.py", "<b>init</b>.py")
    }

    @Test
    fun `cm 25-41 entities decode to their character, never to markup`() {
        check("&nbsp; &amp; &copy;", "\u00A0 & ©")
        check("&#35; &#1234; &#992; &#0;", "# Ӓ Ϡ \uFFFD")
        check("&#X22; &#XD06; &#xcab;", "\" ആ ಫ")
        check("&nbsp &x; &#; &#x; &#87654321; &#abcdef0; &ThisIsNotDefined; &hi?;", "&nbsp &x; &#; &#x; &#87654321; &#abcdef0; &ThisIsNotDefined; &hi?;")
        check("&#42;foo&#42;", "*foo*")
        check("`f&ouml;&ouml;`", "<c>f&ouml;&ouml;</c>")
        check("&lt;b&gt;", "<b>")
        check("&#128512;", "😀")
        check("A &amp; B &amp;amp; C", "A & B &amp; C")
    }

    @Test
    fun `cm 481-540 links, targets with parentheses and titles`() {
        check("[link](/uri \"title\")", "<a /uri>link</a>")
        check("[link](/uri)", "<a /uri>link</a>")
        check("[link](foo(and(bar)))", "<a foo(and(bar))>link</a>")
        check("[link](foo\\(and\\(bar\\))", "<a foo(and(bar)>link</a>")
        check("[link](<foo(and(bar)>)", "<a foo(and(bar)>link</a>")
        check("[link](foo\\)\\:)", "<a foo):>link</a>")
        check("[link](#fragment)", "<a #fragment>link</a>")
        check("[link](foo(and(bar))", "[link](foo(and(bar))")
        check("[link](/url 'title')", "<a /url>link</a>")
        check("[link](/url (title))", "<a /url>link</a>")
        check("[link [foo [bar]]](/uri)", "<a /uri>link [foo [bar]]</a>")
        check("[link] bar](/uri)", "[link] bar](/uri)")
        check("[link [bar](/uri)", "[link <a /uri>bar</a>")
        check("[foo *bar](baz*)", "<a baz*>foo *bar</a>")
        check("[**bold** and `code`](/u)", "<a /u><b>bold</b> and <c>code</c></a>")
        check("[a](https://en.wikipedia.org/wiki/Foo_(bar))", "<a https://en.wikipedia.org/wiki/Foo_(bar)>a</a>")
        check("see [x](/a) and [y](/b).", "see <a /a>x</a> and <a /b>y</a>.")
        check("[ ] todo and [x] done", "[ ] todo and [x] done")
        check("a[0] and b[1](c)", "a[0] and b<a c>1</a>")
        check("[](/x) and [a]()", "[](/x) and [a]()")
    }

    @Test
    fun `p04 and gfm autolinks, addresses with parentheses and angle brackets`() {
        check("https://en.wikipedia.org/wiki/Foo_(bar)", "<a https://en.wikipedia.org/wiki/Foo_(bar)>https://en.wikipedia.org/wiki/Foo_(bar)</a>")
        check("(see https://x.dev/a_b)", "(see <a https://x.dev/a_b>https://x.dev/a_b</a>)")
        check("go to https://x.dev/a.", "go to <a https://x.dev/a>https://x.dev/a</a>.")
        check("https://x.dev/a_(b).", "<a https://x.dev/a_(b)>https://x.dev/a_(b)</a>.")
        check("**https://x.dev**", "<a https://x.dev><b>https://x.dev</b></a>")
        check("<https://x.dev/a?b=1>", "<a https://x.dev/a?b=1>https://x.dev/a?b=1</a>")
        check("<span>raw</span> and <b>x</b>", "<span>raw</span> and <b>x</b>")
        check("<https://x.dev and <b>", "<<a https://x.dev>https://x.dev</a> and <b>")
        check("http://", "http://")
        check("xhttps://a.dev", "xhttps://a.dev")
        check("https://a.dev/x?y=1&z=2, then", "<a https://a.dev/x?y=1&z=2>https://a.dev/x?y=1&z=2</a>, then")
        check("a_b https://a.dev/a_b_c d", "a_b <a https://a.dev/a_b_c>https://a.dev/a_b_c</a> d")
    }

    @Test
    fun `gfm strikethrough and raw html stays text`() {
        check("~~Hi~~ Hello", "<s>Hi</s> Hello")
        check("This ~~has a", "This ~~has a")
        check("~~ spaced ~~", "~~ spaced ~~")
        check("a ~tilde~ b and ~~~three~~~", "a ~tilde~ b and ~~~three~~~")
        check("~~**both**~~", "<b><s>both</s></b>")
        check("<kbd>Ctrl</kbd>+<br>", "<kbd>Ctrl</kbd>+<br>")
    }

    @Test
    fun `paths in code still become chips and pictures stay in sentences`() {
        val i = InlineParser.parse("open ``lib/a.dart:12`` now")
        assertEquals("open lib/a.dart:12 now", i.text)
        assertEquals(SpanKind.PathCandidate("lib/a.dart", 12), i.spans.single { it.kind is SpanKind.PathCandidate }.kind)
        val pic = InlineParser.parse("See ![Shot](/tmp/a.png) **here**")
        assertEquals("See \uFFFC here", pic.text)
        assertEquals(1, pic.spans.count { it.kind is SpanKind.Picture })
        assertEquals(listOf("here"), pic.spans.filter { it.kind == SpanKind.Bold }.map { pic.text.substring(it.start, it.end) })
        val inside = InlineParser.parse("**see ![Shot](/tmp/a.png)**")
        assertEquals(listOf("see \uFFFC"), inside.spans.filter { it.kind == SpanKind.Bold }.map { inside.text.substring(it.start, it.end) })
    }

    @Test
    fun `unclosed marks stay as written`() {
        for (s in listOf("**bold", "*it", "__b", "`code", "[l](u", "[l", "~~s", "a * b", "**", "_", "~~", "``", "`")) {
            assertEquals(s, InlineParser.parse(s).text, s)
        }
    }
}

/** The last line of a streaming reply: marks the scanner would open are closed, text it would not is left alone. */
class InlineStreamingTest {
    @Test
    fun `an unfinished mark is closed only when it would open`() {
        assertEquals("_ital_", UnfinishedTail.close("_ital"))
        assertEquals("- _item_", UnfinishedTail.close("- _item"))
        assertEquals("*a **b***", UnfinishedTail.close("*a **b"))
        assertEquals("***x***", UnfinishedTail.close("***x"))
        assertEquals("**bold *it***", UnfinishedTail.close("**bold *it"))
        assertEquals("see __init__", UnfinishedTail.close("see __init"))
        for (literal in listOf("a*b", "2 * 3 * 4", "__init__.py", "my_var_", "5 * 6 and 7*8", "x**2 + y**2")) {
            assertEquals(literal, UnfinishedTail.close(literal), literal)
        }
    }

    @Test
    fun `a mark that closes nothing at the end is hidden`() {
        assertEquals("x ", UnfinishedTail.close("x **"))
        assertEquals("foo", UnfinishedTail.close("foo**"))
        assertEquals("2 * 3 ", UnfinishedTail.close("2 * 3 *"))
    }

    @Test
    fun `new inline constructs streamed one character at a time end up as the one-shot parse`() {
        val texts =
            listOf(
                "Use _italic_ and __bold__ but keep __init__.py and a*b*c, 2 * 3 * 4 and x**2.",
                "*a **b** c* then ``code ` tick`` and \\*escaped\\* &amp; &#35;.",
                "See [Foo](https://en.wikipedia.org/wiki/Foo_(bar)) or https://en.wikipedia.org/wiki/Foo_(bar). <https://x.dev/a>",
                "~~gone~~ **[link](https://a.dev \"t\")** `lib/a.dart:3` ![p](/tmp/a.png) end",
            )
        for (text in texts) {
            val md = StreamingMarkdown("p")
            var sent = ""
            for (c in text) {
                md.append(c.toString())
                sent += c
                assertEquals(MarkdownBlocks.parse("p", sent, streaming = true), md.blocks, "after \"$sent\"")
                // While streaming, no half-written mark of the kinds we close shows.
                val drawn = drawn(md.blocks)
                if (sent.endsWith("_ital") || sent.endsWith("__bol") || sent.endsWith("*a **b")) {
                    assertEquals(false, drawn.endsWith("_ital") || drawn.endsWith("__bol"), drawn)
                }
            }
            md.finish()
            assertEquals(MarkdownBlocks.parse("p", text), md.blocks, text)
        }
    }
}
