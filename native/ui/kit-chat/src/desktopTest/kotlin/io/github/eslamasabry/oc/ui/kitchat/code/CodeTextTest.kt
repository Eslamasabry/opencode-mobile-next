package io.github.eslamasabry.oc.ui.kitchat.code

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from `test/kit/kit_code_block_prompt_test.dart` and `kit_code_block_breaks_test.dart` (B-CHAT-055). */
class CodeTextTest {
    private fun starts(text: String) = commandLineStarts(text.split('\n'))

    private val zw = "​"

    @Test
    fun `B-CHAT-055 separate commands each start a line`() {
        assertEquals(listOf(true, true, true), starts("cd app\nflutter pub get\nflutter test"))
    }

    @Test
    fun `B-CHAT-055 a heredoc body and its terminator continue the command`() {
        val script = "cat > /tmp/a.py <<'EOF'\nimport os\ndef f():\n    return 1\nEOF\npython3 /tmp/a.py"
        assertEquals(listOf(true, false, false, false, false, true), starts(script))
    }

    @Test
    fun `B-CHAT-055 a dash heredoc strips tabs and a here-string opens nothing`() {
        assertEquals(listOf(true, false, false, true), starts("cat <<-END\n\tbody\n\tEND\necho done"))
        assertEquals(listOf(true, true), starts("read -r A B <<< \"x y\"\necho \$A"))
    }

    @Test
    fun `B-CHAT-055 a trailing backslash, pipe or double ampersand continues the command`() {
        assertEquals(listOf(true, false, true), starts("docker run \\\n  --rm image\nls"))
        assertEquals(listOf(true, false, true), starts("cat log |\n  grep error\nls"))
        assertEquals(listOf(true, false, true), starts("make &&\n  make install\nls"))
    }

    @Test
    fun `B-CHAT-055 an open quote or bracket continues the command`() {
        assertEquals(listOf(true, false, true), starts("echo \"one\ntwo\"\nls"))
        assertEquals(listOf(true, false, false, true), starts("python3 -c '\nprint(1)\n'\nls"))
        assertEquals(listOf(true, false, false, true), starts("x=\$(\n  date\n)\nls"))
    }

    @Test
    fun `B-CHAT-055 the body of a for or if block continues it`() {
        assertEquals(listOf(true, false, false, true), starts("for f in *.txt; do\n  wc -l \"\$f\"\ndone\nls"))
        assertEquals(listOf(true, false, false, true), starts("if [ -f a ]; then\n  rm a\nfi\nls"))
    }

    @Test
    fun `B-CHAT-055 a comment or a quoted heredoc mark opens nothing`() {
        assertEquals(listOf(true, true, true), starts("echo \"a <<EOF\"\nls # (\nls"))
    }

    @Test
    fun `B-CHAT-055 break chances come after punctuation and never inside a word`() {
        val line = "val text = intent.getStringExtra(Intent.EXTRA_TEXT)"
        val out = codeBreakable(line)
        assertEquals(line, out.replace(zw, ""))
        assertTrue("intent.${zw}getStringExtra($zw" in out)
        assertTrue("Intent.${zw}EXTRA_TEXT" in out)
        assertFalse("EXTRA_$zw" in out, "_ is part of a word")
    }

    @Test
    fun `B-CHAT-055 runs and closers stay whole and plain words are untouched`() {
        assertEquals("a::${zw}b == c && d // e", codeBreakable("a::b == c && d // e"))
        assertEquals("foo()", codeBreakable("foo()"))
        assertEquals("list[${zw}0].${zw}first", codeBreakable("list[0].first"))
        assertEquals("test/${zw}offline_queue_test.dart", codeBreakable("test/offline_queue_test.dart"))
        assertEquals("a.${zw}b.c", codeBreakable("a.b.c"))
        assertEquals("plain words only", codeBreakable("plain words only"))
        assertEquals("", codeBreakable(""))
    }

    @Test
    fun `B-CHAT-055 a copied selection loses the break chances again`() {
        val line = "val text = intent.getStringExtra(Intent.EXTRA_TEXT)"
        assertEquals(line, stripCodeBreaks(codeBreakable(line)))
    }

    @Test
    fun `B-CHAT-056 fence hints map to grammars and unknown hints stay plain`() {
        assertEquals(CodeLanguage.Bash, CodeLanguage.of("sh"))
        assertEquals(CodeLanguage.Bash, CodeLanguage.of(" Console "))
        assertEquals(CodeLanguage.TypeScript, CodeLanguage.of("ts"))
        assertEquals(CodeLanguage.Kotlin, CodeLanguage.of("gradle"))
        assertEquals(CodeLanguage.Css, CodeLanguage.of("scss"))
        assertEquals(CodeLanguage.Xml, CodeLanguage.of("svg"))
        assertEquals(CodeLanguage.Diff, CodeLanguage.of("patch"))
        assertEquals(CodeLanguage.Plain, CodeLanguage.of("brainfuck"))
        assertEquals(CodeLanguage.Plain, CodeLanguage.of(null))
        assertEquals(CodeLanguage.Kotlin, CodeLanguage.of("kotlin {title=\"a.kt\"}"), "only the first word of an info string")
        assertEquals(CodeLanguage.Dart, CodeLanguage.forPath("lib/main.dart"))
        assertEquals(CodeLanguage.Dockerfile, CodeLanguage.forPath("app/Dockerfile"))
        assertEquals(CodeLanguage.Plain, CodeLanguage.forPath("README"))
        // Every Flutter alias resolves (the alias table is ported whole).
        val flutter = "dart js javascript jsx ts typescript tsx py python rb ruby sh bash shell zsh console json yaml yml " +
            "toml ini html xml svg css scss sql go rust rs kotlin kt java swift c h cc cpp c++ cs csharp php diff patch " +
            "gradle dockerfile makefile proto md markdown"
        flutter.split(' ').forEach { assertTrue(CodeLanguage.of(it) != CodeLanguage.Plain, "alias $it") }
    }
}
