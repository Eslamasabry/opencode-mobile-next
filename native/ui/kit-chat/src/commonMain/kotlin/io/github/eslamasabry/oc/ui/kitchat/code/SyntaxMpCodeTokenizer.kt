package io.github.eslamasabry.oc.ui.kitchat.code

import com.gallatinapps.syntaxmp.role.SyntaxRole
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The first [CodeTokenizer] (plan 09 §9.2 decision): SyntaxMP 0.3.0, pure common Kotlin, Apache-2.0. This is the only
 * file that imports SyntaxMP, so swapping the library touches nothing else. SyntaxMP already catches its own grammar
 * failures (empty spans); the extra guard keeps the seam total whatever a future version does.
 */
public class SyntaxMpCodeTokenizer : CodeTokenizer {
    private val engine by lazy { SyntaxTokenizer() }

    override fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken> {
        val grammar = language.grammar ?: return persistentListOf()
        if (code.isEmpty()) return persistentListOf()
        val spans =
            try {
                engine.tokenize(code, grammar)
            } catch (_: Exception) {
                return persistentListOf()
            }
        val out = ArrayList<CodeToken>(spans.size)
        var last = 0
        for (span in spans) {
            val start = span.start.coerceIn(last, code.length)
            val end = span.endExclusive.coerceIn(start, code.length)
            if (end <= start) continue
            val role = roleOf(span.role, language) { code.substring(start, end) }
            if (role == CodeRole.Plain) continue
            val prev = out.lastOrNull()
            if (prev != null && prev.role == role && prev.end == start) {
                out[out.size - 1] = prev.copy(end = end) // adjacent same-role spans draw as one
            } else {
                out += CodeToken(start, end, role)
            }
            last = end
        }
        return out.toImmutableList()
    }

    internal companion object {
        /**
         * C-019: SyntaxMP's role tree folded onto the kit's code roles so a block reads like Flutter's, which colours
         * through highlight.js classes (`KitCodeHighlight._rawStyleFor`, `lib/ui/kit/kit_code_block.dart:222-246`):
         * keyword / built_in / literal / type / tag are [CodeRole.Keyword]; string / regexp / symbol are
         * [CodeRole.String]; comment is [CodeRole.Comment]; number, attr, variable, title, function, meta and the like
         * are the codeType family ([CodeRole.Number], [CodeRole.Type], [CodeRole.Function], [CodeRole.Meta]); anything
         * highlight.js leaves unclassed (calls, member names, operators, punctuation) is [CodeRole.Plain]. [lexeme]
         * is the span's text, read only where the role alone cannot say (shell words).
         */
        fun roleOf(
            role: SyntaxRole,
            language: CodeLanguage = CodeLanguage.Plain,
            lexeme: () -> String = { "" },
        ): CodeRole {
            val path = role.value
            if (language == CodeLanguage.Bash) shellRole(path, lexeme)?.let { return it }
            return when {
                path.startsWith("markup.diff.addition") -> CodeRole.Addition
                path.startsWith("markup.diff.deletion") -> CodeRole.Deletion
                path.startsWith("markup.diff.hunk") || path.startsWith("markup.diff.header") -> CodeRole.Meta
                path == "function.declaration" -> CodeRole.Function // highlight.js `title`
                path == "function.macro" -> CodeRole.Meta
                // highlight.js marks built-in calls (`print`, `len`) as built_in, except Kotlin's grammar.
                path == "function.builtin" -> if (language == CodeLanguage.Kotlin) CodeRole.Plain else CodeRole.Keyword
                path == "constant.color" -> CodeRole.Number
                path == "constant.atom" -> CodeRole.String // highlight.js `symbol`
                else ->
                    when (path.substringBefore('.')) {
                        // SyntaxMP's `type` is built-in type names (`int`, `String`, `List`): highlight.js built_in.
                        "keyword", "tag", "type" -> CodeRole.Keyword
                        "constant" -> CodeRole.Keyword // highlight.js `literal` (true, false, null)
                        "string", "escape" -> CodeRole.String
                        "number" -> CodeRole.Number
                        "comment" -> CodeRole.Comment
                        "attribute" -> CodeRole.Type // highlight.js `attr` / `attribute`
                        "annotation" -> CodeRole.Meta
                        // Keys of config files are highlight.js `attr`; a member name in code is unclassed.
                        "property" -> if (language in keyedLanguages) CodeRole.Type else CodeRole.Plain
                        else -> CodeRole.Plain // function calls, variables, operators, punctuation
                    }
            }
        }

        private val keyedLanguages =
            setOf(CodeLanguage.Json, CodeLanguage.Yaml, CodeLanguage.Toml, CodeLanguage.Ini, CodeLanguage.Css)

        /**
         * Shell words the way highlight.js's bash grammar reads them: only its keyword, literal and built_in lists are
         * coloured (`echo`, `done`, the `type` in `-type`), so an external command (`curl`, `find`) or an argument
         * stays plain; `$NAME` is a variable (codeType). Null: not a word, the general mapping decides.
         */
        private fun shellRole(path: String, lexeme: () -> String): CodeRole? =
            when {
                path == "variable.parameter" -> CodeRole.Type
                path.startsWith("keyword") || path.startsWith("function") || path == "variable" -> {
                    val word = lexeme().trimStart('-')
                    if (word in BASH_WORDS) CodeRole.Keyword else CodeRole.Plain
                }
                else -> null
            }

        /**
         * highlight.js bash (the grammar `package:highlight` 0.7 ships, which Flutter's code block uses): its
         * `keyword`, `literal` and `built_in` lists, one set, since all three draw in the keyword colour.
         */
        internal val BASH_WORDS: Set<String> =
            (
                "if then else elif fi for while in do done case esac function true false " +
                    "break cd continue eval exec exit export getopts hash pwd readonly return shift test times trap " +
                    "umask unset alias bind builtin caller command declare echo enable help let local logout mapfile " +
                    "printf read readarray source type typeset ulimit unalias set shopt autoload bg bindkey bye cap " +
                    "chdir clone comparguments compcall compctl compdescribe compfiles compgroups compquote comptags " +
                    "comptry compvalues dirs disable disown echotc echoti emulate fc fg float functions getcap getln " +
                    "history integer jobs kill limit log noglob popd print pushd pushln rehash sched setcap setopt " +
                    "stat suspend ttyctl unfunction unhash unlimit unsetopt vared wait whence where which zcompile " +
                    "zformat zftp zle zmodload zparseopts zprof zpty zregexparse zsocket zstyle ztcp"
            ).split(' ').toSet()
    }
}
