package io.github.eslamasabry.oc.core.model

/**
 * A model's name in words, the one home of the rule (port of Dart `modelNameFromId`,
 * `lib/domain/model_display_name.dart`, B-KIT-057): "claude-sonnet-5" reads "Sonnet 5",
 * "claude-opus-4-1-20250805" reads "Opus 4.1", "gpt-6-sol" reads "GPT-6 Sol". Runtime prefixes
 * (`kilo/google/x`) drop. The composer chip and the reply footer used to carry their own copies.
 */
public object ModelDisplayName {
    private val DATE_TAIL = Regex("""[-@_](\d{8}|\d{4}-\d{2}-\d{2})$""")
    private val LATEST_TAIL = Regex("-latest$", RegexOption.IGNORE_CASE)
    private val SPLIT = Regex("""[-_\s]+""")
    private val NUMBER = Regex("""^\d+(\.\d+)*$""")
    private val SHORT_NUMBER = Regex("""^\d{1,2}$""")
    private val LETTER_DIGIT = Regex("""^[a-z]\d""")
    private val ACRONYMS = setOf("gpt", "glm", "qwq")
    private val BRACKET_TAIL = Regex("""\[([^\[\]]*)\]$""")
    private val CONTEXT_TAG = Regex("""^(\d{1,4})([kKmM])$""")

    /**
     * The name a list shows: the name the server or agent reports, unless it is blank or only the
     * id again, then the id read as words (Flutter `pickers_choices.dart` `_choiceName`).
     */
    public fun of(modelId: String, reported: String?): String {
        val name = reported?.trim().orEmpty()
        return if (name.isEmpty() || name == modelId.trim()) fromId(modelId) else name
    }

    /**
     * Different from Flutter `modelNameFromId` on purpose (owner, 2026-11-11, "it's showing raw names"): a bracket tag
     * at the end of the id is a variant of the same model, not part of its version. Flutter's rule has no case for it
     * and reads `claude-opus-4-6[1m]` as "Opus 4 6[1m]"; here it reads "Opus 4.6 · 1M context". A context-size tag
     * (`[1m]`, `[200k]`) names the context; any other tag follows the name in plain parentheses ("Sonnet 5 (beta)").
     * The words stay as the model's maker names the variant: like the model name itself, they are not translated.
     */
    public fun fromId(modelId: String): String {
        var id = modelId.trim()
        val slash = id.lastIndexOf('/')
        if (slash >= 0) id = id.substring(slash + 1)
        val tag = BRACKET_TAIL.find(id)
        if (tag != null) {
            val base = id.substring(0, tag.range.first).trim()
            val variant = tag.groupValues[1].trim()
            if (base.isNotEmpty()) {
                val name = words(base, modelId)
                if (variant.isEmpty()) return name
                val context = CONTEXT_TAG.matchEntire(variant)
                return if (context != null)
                    "$name · ${context.groupValues[1]}${context.groupValues[2].uppercase()} context"
                else "$name ($variant)"
            }
        }
        return words(id, modelId)
    }

    /** Flutter `modelNameFromId` from the release-date step on, for an id without its runtime prefix. */
    private fun words(stripped: String, modelId: String): String {
        var id = stripped
        // Release dates and moving tags say nothing a person picks by.
        id = id.replaceFirst(DATE_TAIL, "").replaceFirst(LATEST_TAIL, "")
        val tokens = id.split(SPLIT).filter { it.isNotEmpty() }.toMutableList()
        // The Claude family is named by its tier ("Sonnet 5"), as its makers do.
        if (tokens.size > 1 && tokens.first().lowercase() == "claude") tokens.removeAt(0)
        if (tokens.isEmpty()) return modelId.trim()

        val words = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val lower = token.lowercase()
            when {
                lower in ACRONYMS -> {
                    // "gpt-6" → "GPT-6": an acronym keeps its hyphen to the version.
                    val next = tokens.getOrNull(i + 1)
                    if (next != null && next.first().isDigit()) {
                        val (text, end) = joinVersion(tokens, i + 1)
                        words += "${lower.uppercase()}-$text"
                        i = end
                        continue
                    }
                    words += lower.uppercase()
                }
                NUMBER.matches(token) -> {
                    val (text, end) = joinVersion(tokens, i)
                    words += text
                    i = end
                    continue
                }
                // "o3", "o4" stay as their makers write them; "k2", "v4" are tags.
                LETTER_DIGIT.containsMatchIn(token) ->
                    words +=
                        if (lower.startsWith('o')) lower
                        else lower.replaceFirstChar { it.uppercaseChar() }
                else -> words += token.replaceFirstChar { it.uppercaseChar() }
            }
            i++
        }
        return words.joinToString(" ")
    }

    /**
     * Short number runs are one version ("4-1" → "4.1"); a token starting with digits ("4o",
     * "5.3") stays as written.
     */
    private fun joinVersion(tokens: List<String>, start: Int): Pair<String, Int> {
        val first = tokens[start]
        if (!NUMBER.matches(first)) return first to start + 1
        val parts = mutableListOf(first)
        var end = start + 1
        while (end < tokens.size && SHORT_NUMBER.matches(tokens[end]) && '.' !in parts.last()) {
            parts += tokens[end]
            end++
        }
        return parts.joinToString(".") to end
    }
}
