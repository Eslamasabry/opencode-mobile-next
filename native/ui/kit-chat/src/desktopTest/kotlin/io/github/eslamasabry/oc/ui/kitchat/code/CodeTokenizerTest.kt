package io.github.eslamasabry.oc.ui.kitchat.code

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The tokenizer seam (plan 09 §9.2): SyntaxMP first, plain fallback, the 48-entry cache and the 20,000-char limit. */
class CodeTokenizerTest {
    private class Counting(private val inner: CodeTokenizer) : CodeTokenizer {
        var calls = 0

        override fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken> {
            calls++
            return inner.tokenize(code, language)
        }
    }

    @Test
    fun `B-CHAT-056 SyntaxMP colours keywords, strings and comments in Kotlin, shell, JSON and TypeScript`() {
        val t = SyntaxMpCodeTokenizer()
        fun roles(code: String, lang: CodeLanguage) = t.tokenize(code, lang).associate { code.substring(it.start, it.end) to it.role }
        val kt = roles("val name = \"x\" // note\nfun f() = 1", CodeLanguage.Kotlin)
        assertEquals(CodeRole.Keyword, kt["val"])
        assertEquals(CodeRole.String, kt["\"x\""])
        assertEquals(CodeRole.Comment, kt["// note"])
        assertEquals(CodeRole.Keyword, kt["fun"])
        val sh = roles("echo \"hi\" # done", CodeLanguage.Bash)
        assertEquals(CodeRole.String, sh["\"hi\""])
        assertEquals(CodeRole.Comment, sh["# done"])
        val json = roles("{ \"ok\": true, \"n\": 12 }", CodeLanguage.Json)
        assertEquals(CodeRole.Number, json["12"])
        val ts = roles("const a: string = 'x';", CodeLanguage.TypeScript)
        assertEquals(CodeRole.Keyword, ts["const"])
    }

    @Test
    fun `B-CHAT-056 a diff colours added and removed lines`() {
        val code = "@@ -1,2 +1,2 @@\n-old\n+new\n same"
        val tokens = SyntaxMpCodeTokenizer().tokenize(code, CodeLanguage.Diff)
        val byRole = tokens.groupBy({ it.role }, { code.substring(it.start, it.end) })
        assertTrue(byRole[CodeRole.Addition].orEmpty().any { "new" in it }, "added: $byRole")
        assertTrue(byRole[CodeRole.Deletion].orEmpty().any { "old" in it }, "removed: $byRole")
    }

    @Test
    fun `B-CHAT-056 an unknown language and an oversize source fall back to plain`() {
        val inner = Counting(SyntaxMpCodeTokenizer())
        val cached = CachedCodeTokenizer(inner)
        assertEquals(persistentListOf(), cached.tokenize("val a = 1", CodeLanguage.Plain))
        val big = "val a = 1\n".repeat(2_001) // 20,010 chars
        assertEquals(persistentListOf(), cached.tokenize(big, CodeLanguage.Kotlin))
        assertEquals(0, inner.calls, "plain and oversize never reach the grammar")
        assertTrue(cached.tokenize("val a = 1", CodeLanguage.Kotlin).isNotEmpty())
    }

    @Test
    fun `B-CHAT-056 tokens are cached per language and source, 48 entries, oldest out`() {
        val inner = Counting(SyntaxMpCodeTokenizer())
        val cached = CachedCodeTokenizer(inner)
        val first = cached.tokenize("val a = 1", CodeLanguage.Kotlin)
        assertSame(first, cached.tokenize("val a = 1", CodeLanguage.Kotlin))
        assertEquals(1, inner.calls)
        cached.tokenize("val a = 1", CodeLanguage.Java) // another language is another entry
        assertEquals(2, inner.calls)
        repeat(48) { cached.tokenize("val x$it = 1", CodeLanguage.Kotlin) }
        cached.tokenize("val a = 1", CodeLanguage.Kotlin) // evicted by 48 newer entries
        assertEquals(51, inner.calls)
    }

    @Test
    fun `tokens are total, in order, inside the source and never overlap`() {
        val t = CachedCodeTokenizer(SyntaxMpCodeTokenizer())
        val rnd = Random(56)
        val alphabet = "abc xyz 01 \"'`{}()[]<>#/*\\\n\t;:=+-$@!%^&|?.,~​ا😀"
        repeat(2_000) {
            val code = String(CharArray(rnd.nextInt(0, 120)) { alphabet[rnd.nextInt(alphabet.length)] })
            val lang = CodeLanguage.entries[rnd.nextInt(CodeLanguage.entries.size)]
            var last = 0
            for (tok in t.tokenize(code, lang)) {
                assertTrue(tok.start >= last && tok.end > tok.start && tok.end <= code.length, "$lang $tok in ${code.length}")
                last = tok.end
            }
        }
    }
}
