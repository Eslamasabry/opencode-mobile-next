package io.github.eslamasabry.oc.engine.requests.questions

import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** How a question is presented (plan 09 §5.4, map CHAT-GENUI-47). */
public enum class QuestionPresentation {
    /** One prompt with one answer: options in place on the card, a tap sends (S2-REQ's one-question card). */
    InPlace,

    /** One prompt with several answers: "Answer" opens the request sheet (every option + Send). */
    RequestSheet,

    /** Two or more prompts, long options, or several answers plus own words: the full question sheet. */
    FullSheet,
}

/** One prompt's answer in progress. [chosen] are choice labels; [own] is the "Something else" text. */
public data class PromptDraft(
    val chosen: ImmutableList<String> = persistentListOf(),
    val own: String = "",
    /** A single-answer prompt whose "Something else" field is open (its text replaces any chosen option). */
    val ownOpen: Boolean = false,
)

/** Why Send cannot go yet ("says why it cannot send yet", STATE-8). One sentence each in `:ui:words`. */
public sealed interface QuestionBlock {
    /** A prompt that is not optional has no answer. [index] is the prompt to point at. */
    public data class Unanswered(val index: Int) : QuestionBlock

    /** A value the prompt does not offer and it takes no own words: never sent (B-CHAT-072). */
    public data class NotOffered(val index: Int) : QuestionBlock
}

/**
 * The question sheet's rules (port of Dart `question_sheet.dart` / `connection/questions.dart`; B-CHAT-072, map
 * CHAT-GENUI-47). Answers are string lists in prompt order (`answerQuestion(id, List<List<String>>)`): chosen labels in
 * the order the prompt offers them, then the trimmed own words. Typed questions are never permissions and nothing here
 * approves anything.
 */
public object QuestionSheet {
    /** An option longer than this pushes a question to the full sheet (it no longer fits a card row). */
    public const val LONG_OPTION: Int = 80

    public fun presentation(question: PendingQuestion): QuestionPresentation {
        val prompts = question.prompts
        val prompt = prompts.singleOrNull() ?: return QuestionPresentation.FullSheet
        return when {
            prompt.choices.any { it.label.length > LONG_OPTION } -> QuestionPresentation.FullSheet
            prompt.multiple && prompt.custom -> QuestionPresentation.FullSheet
            prompt.multiple -> QuestionPresentation.RequestSheet
            else -> QuestionPresentation.InPlace
        }
    }

    public fun emptyDraft(question: PendingQuestion): ImmutableList<PromptDraft> =
        question.prompts.map { PromptDraft() }.toImmutableList()

    /** Taps an option. Single answer: replaces the choice and clears own words. Several: toggles, own words kept. */
    public fun choose(prompt: QuestionPrompt, draft: PromptDraft, label: String): PromptDraft =
        if (prompt.multiple) {
            val next = if (label in draft.chosen) draft.chosen - label else draft.chosen + label
            draft.copy(chosen = prompt.choices.map { it.label }.filter { it in next }.toImmutableList())
        } else {
            PromptDraft(chosen = persistentListOf(label), own = "", ownOpen = false)
        }

    /** Types own words. Single answer: they replace the chosen option. Several: they join the chosen ones. */
    public fun write(prompt: QuestionPrompt, draft: PromptDraft, text: String): PromptDraft =
        if (prompt.multiple) draft.copy(own = text) else PromptDraft(chosen = persistentListOf(), own = text, ownOpen = true)

    /** The answer of one prompt, as sent. */
    public fun answerOf(prompt: QuestionPrompt, draft: PromptDraft): ImmutableList<String> {
        val own = draft.own.trim().takeIf { prompt.custom && it.isNotEmpty() }
        return when {
            !prompt.multiple && draft.ownOpen -> listOfNotNull(own)
            else -> draft.chosen + listOfNotNull(own)
        }.toImmutableList()
    }

    /** Null when Send may go; otherwise the first reason, pointing at its prompt. */
    public fun block(question: PendingQuestion, drafts: List<PromptDraft>): QuestionBlock? {
        question.prompts.forEachIndexed { i, prompt ->
            val draft = drafts.getOrNull(i) ?: PromptDraft()
            val offered = prompt.choices.map { it.label }.toSet()
            if (draft.chosen.any { it !in offered }) return QuestionBlock.NotOffered(i)
            if (!prompt.optional && answerOf(prompt, draft).isEmpty()) return QuestionBlock.Unanswered(i)
        }
        return null
    }

    /** Every prompt's answer in prompt order, or null while [block] says it cannot send. */
    public fun answers(question: PendingQuestion, drafts: List<PromptDraft>): ImmutableList<ImmutableList<String>>? {
        if (block(question, drafts) != null) return null
        return question.prompts.mapIndexed { i, p -> answerOf(p, drafts.getOrNull(i) ?: PromptDraft()) }.toImmutableList()
    }
}
