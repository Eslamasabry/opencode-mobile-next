package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.engine.requests.ask.AlwaysGrant
import io.github.eslamasabry.oc.engine.requests.ask.AskView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the chat's request slot shows above the composer (plan 09 §5.1, §6.4): at most one card, the
 * oldest waiting request of this conversation, how many more wait, and the Always allow confirm
 * while it is open. No copy: every word is chosen by the screen from `:ui:words`; server text (a
 * question and its options) is data.
 */
public data class RequestSlotUi(
    val card: RequestCardUi? = null,
    val moreWaiting: Int = 0,
    val confirmAlways: AlwaysConfirmUi? = null,
    val questionSheet: QuestionSheetUi? = null,
)

/**
 * One request card. [waitingSinceMs] is when this phone first showed it (null until it is shown).
 */
public sealed interface RequestCardUi {
    public val id: RequestId
    public val waitingSinceMs: Long?
    public val state: CardState

    /**
     * A permission ask. [ask] is the engine's wording data; [canAlwaysAllow] gates the quiet Always
     * allow action (`capabilities.persistentPermissionGrants && request.canAlwaysAllow`, plan 09
     * §5.3).
     */
    public data class Permission(
        override val id: RequestId,
        override val waitingSinceMs: Long?,
        override val state: CardState,
        val ask: AskView,
        val canAlwaysAllow: Boolean,
    ) : RequestCardUi

    /**
     * A question. One prompt with one answer ([oneAnswer]) puts its [options] on the card and a tap
     * answers; with [custom] "Something else" opens a field ([other]). Longer questions show
     * "Answer", which opens the request sheet.
     */
    public data class Question(
        override val id: RequestId,
        override val waitingSinceMs: Long?,
        override val state: CardState,
        val title: String,
        val question: String,
        val options: ImmutableList<String> = persistentListOf(),
        val oneAnswer: Boolean,
        val custom: Boolean,
        val other: OtherAnswerUi? = null,
    ) : RequestCardUi
}

/**
 * The "Something else" field of a one-answer question; its text is kept per request while the card
 * waits.
 */
public data class OtherAnswerUi(val text: String) {
    val canSend: Boolean
        get() = text.isNotBlank()
}

/** Where the card's answer stands (the collapsed line after a tap, then the receipt; STATE-10). */
public sealed interface CardState {
    public data object Waiting : CardState

    /** Inside the 3 s hold: "Allowed · Undo". */
    public data class Held(val words: AnswerWords) : CardState

    public data class Sending(val words: AnswerWords) : CardState

    /** Left this phone; the card leaves when the request settles. "Sent" is never "Done". */
    public data class Sent(val words: AnswerWords) : CardState

    /** The server refused it: the card says so and offers Try again. */
    public data class NotAccepted(val words: AnswerWords) : CardState
}

/** The words of the collapsed line, as a choice (the screen resolves it). */
public sealed interface AnswerWords {
    public data object Allowed : AnswerWords

    public data object AlwaysAllowed : AnswerWords

    public data object Rejected : AnswerWords

    public data object Dismissed : AnswerWords

    /** The chosen option or typed answer, as the person gave it. */
    public data class Chose(val answer: String) : AnswerWords
}

/**
 * The one confirm Always allow opens (plan 09 §5.3, owner decision 13B): it names what ([grant];
 * null = "these requests") and where (this project). Cancel, a dismissal or an answer that landed
 * meanwhile sends nothing.
 */
public data class AlwaysConfirmUi(val id: RequestId, val grant: AlwaysGrant?)

/** The full question and its screen draft; answers are checked by the engine before dispatch. */
public data class QuestionSheetUi(
    val question: io.github.eslamasabry.oc.core.model.PendingQuestion,
    val drafts: ImmutableList<io.github.eslamasabry.oc.engine.requests.questions.PromptDraft>,
)

public interface QuestionSheetActions {
    public fun chooseSheet(id: RequestId, prompt: Int, label: String)

    public fun writeSheet(id: RequestId, prompt: Int, text: String)

    public fun sendSheet(id: RequestId)

    public fun closeSheet()
}
