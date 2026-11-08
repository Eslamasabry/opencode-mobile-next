package io.github.eslamasabry.oc.feature.git.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role

/**
 * The agent's own one-line account of its latest turn, for "Write it for me" under the commit message (Flutter
 * chat_top_bar.dart `_turnSummary`, passed to the Git sheet as `summary`). Null: the button is not offered.
 */
public fun interface GitTurnSummary {
    public suspend fun summary(ref: ConversationRef): String?
}

/** No conversation to read: "Write it for me" is not offered. */
public class NoGitTurnSummary : GitTurnSummary {
    override suspend fun summary(ref: ConversationRef): String? = null
}

/** A commit subject's length (Flutter `_turnSummary`: 72 characters). */
internal const val SUMMARY_MAX = 72

/**
 * Flutter `_turnSummary`: the last assistant reply that has words, its first non-empty line with Markdown marks
 * removed, cut at the end of its first sentence, and at most [SUMMARY_MAX] characters (cut on a word past 40, with
 * an ellipsis). Null when the agent said nothing.
 */
public fun turnSummary(messages: List<MessageWithParts>): String? =
    messages.asReversed().firstNotNullOfOrNull { message ->
        message
            .takeIf { it.info.role == Role.Assistant }
            ?.parts
            ?.filter { it.type == PartType.Text && !it.synthetic }
            ?.joinToString("\n") { it.text }
            ?.trim()
            ?.ifEmpty { null }
            ?.let(::firstSentence)
    }

private fun firstSentence(words: String): String? {
    val line = words.split('\n').map { it.replace(MARKS, "").trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    val end = SENTENCE_END.find(line)
    val first = if (end == null) line else line.substring(0, end.range.first + 1)
    if (first.length <= SUMMARY_MAX) return first.ifEmpty { null }
    val cut = first.take(SUMMARY_MAX)
    val space = cut.lastIndexOf(' ')
    return (if (space > WORD_CUT_FROM) cut.substring(0, space) else cut) + "\u2026"
}

/** Flutter's cut keeps a word whole only past this many characters. */
private const val WORD_CUT_FROM = 40

private val MARKS = Regex("[#*`>_]")
private val SENTENCE_END = Regex("[.!?](\\s|$)")
