package io.github.eslamasabry.oc.ui.kitchat.code

import com.gallatinapps.syntaxmp.role.SyntaxRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * C-019: code is coloured with Flutter's highlighter roles and palette (`KitCodeHighlight._rawStyleFor`,
 * `lib/ui/kit/kit_code_block.dart:222-246`, through highlight.js's grammars in `package:highlight` 0.7).
 */
class CodeHighlightParityTest {
    private val tokenizer = SyntaxMpCodeTokenizer()

    /** The role drawn at the first occurrence of [word] in [code] (null: plain text1). */
    private fun roleAt(code: String, word: String, lang: CodeLanguage): CodeRole? {
        val at = code.indexOf(word)
        require(at >= 0) { "$word not in $code" }
        return tokenizer.tokenize(code, lang).firstOrNull { at >= it.start && at < it.end }?.role
    }

    @Test
    fun `C-019 shell - echo and done are keywords, curl and find stay plain, -type is a keyword`() {
        val sh = "find . -type f -name '*.log'\ncurl -s \"https://example.com\"\nfor f in a b; do echo \"\$f\"; done"
        assertNull(roleAt(sh, "find", CodeLanguage.Bash), "an external command is plain text1, as in Flutter")
        assertNull(roleAt(sh, "curl", CodeLanguage.Bash))
        assertEquals(CodeRole.Keyword, roleAt(sh, "type", CodeLanguage.Bash), "highlight.js reads `type` in -type")
        assertEquals(CodeRole.Keyword, roleAt(sh, "echo", CodeLanguage.Bash))
        assertEquals(CodeRole.Keyword, roleAt(sh, "done", CodeLanguage.Bash))
        assertEquals(CodeRole.Keyword, roleAt(sh, "for", CodeLanguage.Bash))
        assertEquals(CodeRole.String, roleAt(sh, "\"https", CodeLanguage.Bash))
        assertEquals(CodeRole.String, roleAt(sh, "'*.log'", CodeLanguage.Bash))
        assertNull(roleAt(sh, "-s", CodeLanguage.Bash), "an option is plain")
    }

    @Test
    fun `C-019 SyntaxMP roles fold onto highlight-js classes`() {
        fun r(role: SyntaxRole, lang: CodeLanguage = CodeLanguage.TypeScript) = SyntaxMpCodeTokenizer.roleOf(role, lang)
        assertEquals(CodeRole.Keyword, r(SyntaxRole.Keyword.Declaration))
        assertEquals(CodeRole.Keyword, r(SyntaxRole.Type), "built-in type names are highlight.js built_in")
        assertEquals(CodeRole.Keyword, r(SyntaxRole.Constant.Builtin), "true/false/null are highlight.js literal")
        assertEquals(CodeRole.Keyword, r(SyntaxRole.Function.Builtin, CodeLanguage.Python), "print/len are built_in")
        assertEquals(CodeRole.Plain, r(SyntaxRole.Function.Builtin, CodeLanguage.Kotlin), "Kotlin's grammar has none")
        assertEquals(CodeRole.Function, r(SyntaxRole.Function.Declaration), "a declared name is highlight.js title")
        assertEquals(CodeRole.Plain, r(SyntaxRole.Function.Member), "a call is unclassed")
        assertEquals(CodeRole.Plain, r(SyntaxRole.Function))
        assertEquals(CodeRole.Plain, r(SyntaxRole.Operator))
        assertEquals(CodeRole.Plain, r(SyntaxRole.Punctuation))
        assertEquals(CodeRole.Plain, r(SyntaxRole.Variable))
        assertEquals(CodeRole.Type, r(SyntaxRole.Property.Name, CodeLanguage.Json), "a JSON key is highlight.js attr")
        assertEquals(CodeRole.Plain, r(SyntaxRole.Property, CodeLanguage.Kotlin), "a member name is unclassed")
        assertEquals(CodeRole.Type, r(SyntaxRole.Attribute))
        assertEquals(CodeRole.Meta, r(SyntaxRole.Annotation))
        assertEquals(CodeRole.String, r(SyntaxRole.Escape))
        assertEquals(CodeRole.Comment, r(SyntaxRole.Comment))
        assertEquals(CodeRole.Number, r(SyntaxRole.Number))
        assertEquals(CodeRole.Type, SyntaxMpCodeTokenizer.roleOf(SyntaxRole.Variable.Parameter, CodeLanguage.Bash))
    }

    @Test
    fun `C-019 JSON keys are codeType and literals are keywords`() {
        val json = "{ \"ok\": true, \"n\": 12 }"
        assertEquals(CodeRole.Type, roleAt(json, "\"ok\"", CodeLanguage.Json))
        assertEquals(CodeRole.Keyword, roleAt(json, "true", CodeLanguage.Json))
        assertEquals(CodeRole.Number, roleAt(json, "12", CodeLanguage.Json))
    }
}
