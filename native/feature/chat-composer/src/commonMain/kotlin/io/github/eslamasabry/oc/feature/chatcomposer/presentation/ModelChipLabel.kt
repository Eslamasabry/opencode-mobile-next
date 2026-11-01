package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A readable stand-in for a model's name made from its id alone, for the moment before the catalog arrives: port of
 * Dart `modelNameFromId` (`lib/domain/model_display_name.dart`, B-KIT-057). "claude-sonnet-5" reads "Sonnet 5",
 * "claude-opus-4-1-20250805" reads "Opus 4.1", "gpt-6-sol" reads "GPT-6 Sol". Runtime prefixes (`kilo/google/x`) drop.
 */
object ModelNames {
    private val DATE_TAIL = Regex("""[-@_](\d{8}|\d{4}-\d{2}-\d{2})$""")
    private val LATEST_TAIL = Regex("-latest$", RegexOption.IGNORE_CASE)
    private val SPLIT = Regex("""[-_\s]+""")
    private val NUMBER = Regex("""^\d+(\.\d+)*$""")
    private val SHORT_NUMBER = Regex("""^\d{1,2}$""")
    private val LETTER_DIGIT = Regex("""^[a-z]\d""")
    private val ACRONYMS = setOf("gpt", "glm", "qwq")

    fun fromId(modelId: String): String {
        var id = modelId.trim()
        val slash = id.lastIndexOf('/')
        if (slash >= 0) id = id.substring(slash + 1)
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
                    words += if (lower.startsWith('o')) lower else lower.replaceFirstChar { it.uppercaseChar() }
                else -> words += token.replaceFirstChar { it.uppercaseChar() }
            }
            i++
        }
        return words.joinToString(" ")
    }

    /** Short number runs are one version ("4-1" → "4.1"); a token starting with digits ("4o", "5.3") stays as written. */
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

/** The model chip's rules (plan 09 §10.3; B-CHAT-066, B-CHAT-067). Never `provider/model` or a raw id. */
object ModelChipLabel {
    private val NEW_FOR = 30.days

    fun label(facts: ModelFacts): ModelLabel {
        val id = facts.modelId?.takeIf { it.isNotBlank() }
        if (id == null) {
            return when {
                !facts.signedIn -> ModelLabel.NoModelSignedIn
                !facts.serverDefaultName.isNullOrBlank() -> ModelLabel.Named(facts.serverDefaultName.trim())
                else -> ModelLabel.Loading
            }
        }
        facts.catalogName?.trim()?.takeIf { it.isNotEmpty() && !facts.catalogLoading }?.let { return ModelLabel.Named(it) }
        // While the models are read (or when the catalog lacks the model), a name seen before, else one made from the id.
        val known = facts.knownName?.trim()?.takeIf { it.isNotEmpty() }
        return ModelLabel.Named(known ?: ModelNames.fromId(id))
    }

    /** Released in the last 30 days; a date in the future is wrong, not new. */
    fun isNew(released: Instant?, now: Instant): Boolean {
        if (released == null) return false
        val age = now - released
        return !age.isNegative() && age < NEW_FOR
    }

    fun effort(id: String): EffortWord =
        when (id.trim().lowercase()) {
            "none" -> EffortWord.Known.None
            "minimal" -> EffortWord.Known.Minimal
            "low" -> EffortWord.Known.Low
            "medium" -> EffortWord.Known.Medium
            "high" -> EffortWord.Known.High
            "xhigh" -> EffortWord.Known.ExtraHigh
            "max" -> EffortWord.Known.Max
            else -> EffortWord.Other(id)
        }

    fun chip(facts: ModelFacts, now: Instant): ComposerChip.Model =
        ComposerChip.Model(
            label = label(facts),
            isNew = facts.modelId != null && isNew(facts.released, now),
            effort = facts.effort?.takeIf { facts.effortIsChoice && it.isNotBlank() }?.let(::effort),
        )
}
