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
            val role = roleOf(span.role)
            val start = span.start.coerceIn(last, code.length)
            val end = span.endExclusive.coerceIn(start, code.length)
            if (role == CodeRole.Plain || end <= start) continue
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
        /** SyntaxMP's role tree folded onto the kit's code roles (root of the dotted path, plus the diff lines). */
        fun roleOf(role: SyntaxRole): CodeRole {
            val path = role.value
            return when {
                path.startsWith("markup.diff.addition") -> CodeRole.Addition
                path.startsWith("markup.diff.deletion") -> CodeRole.Deletion
                path.startsWith("markup.diff.hunk") || path.startsWith("markup.diff.header") -> CodeRole.Meta
                else ->
                    when (path.substringBefore('.')) {
                        "keyword", "tag" -> CodeRole.Keyword
                        "string", "escape" -> CodeRole.String
                        "number", "constant" -> CodeRole.Number
                        "comment" -> CodeRole.Comment
                        "function" -> CodeRole.Function
                        "type", "attribute" -> CodeRole.Type
                        "annotation" -> CodeRole.Meta
                        "punctuation", "operator" -> CodeRole.Punctuation
                        else -> CodeRole.Plain
                    }
            }
        }
    }
}
