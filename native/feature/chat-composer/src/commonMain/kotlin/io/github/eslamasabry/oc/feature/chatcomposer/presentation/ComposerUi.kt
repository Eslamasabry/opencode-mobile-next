package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The one button at the end of the field (plan 09 §10.2, §4.3; plan 08 M8-12). It changes only between the mic,
 * Send and the steer forms; the running turn's Stop is a chip at the end of the chip row, so "Send to Stop" is a
 * hand-off between the field and the row, not a morph of this button.
 */
enum class FieldButton {
    /** Nothing typed: the mic (the voice slot). */
    Mic,
    Send,
    /** A turn runs and the prompt joins it (steering, or a server that joins by itself). Never "Queued". */
    AddToTurn,
    /** A turn runs and this agent restarts with a new prompt: the amber "Stop and send" pill. */
    StopAndSend,
    /** Offline: the offline queue sends it. */
    SendWhenOnline,
    /** A turn runs and nothing is known about mid-turn prompts: disabled, "You can send when this reply finishes". */
    CannotSendYet,
    /** The prompt is being got ready: disabled, working. */
    Sending,
    /** Read-only, or nothing typed and no mic. */
    None,
}

/** How the delivery engine should send (plan 09 §4.3). There is no queue choice while a runtime steers. */
enum class SendHow { Start, Steer, JoinTurn, StopThenSend, WhenOnline }

/** The model chip's text before words: a name, or one of the fixed states (plan 09 §10.3). */
sealed interface ModelLabel {
    data class Named(val name: String) : ModelLabel

    /** The catalog loads and no name is known yet. */
    data object Loading : ModelLabel

    /** Nothing signed in before the first send. */
    data object NoModelSignedIn : ModelLabel
}

/** Effort levels read as words (B-CHAT-067); [Other] keeps the server's own word. */
sealed interface EffortWord {
    enum class Known : EffortWord { None, Minimal, Low, Medium, High, ExtraHigh, Max }

    data class Other(val raw: String) : EffortWord
}

/** The chip row, in order: model, approval, then Stop at the trailing end while a reply runs (plan 09 §10.3). */
sealed interface ComposerChip {
    data class Model(val label: ModelLabel, val isNew: Boolean, val effort: EffortWord?) : ComposerChip

    data class Approval(val mode: ApprovalMode) : ComposerChip

    /** [stuck]: the same Stop in the attention tone. */
    data class Stop(val stuck: Boolean) : ComposerChip
}

/** A one-line note in the pill's top row. */
sealed interface ComposerNote {
    /** The send failed; the words are back in the field. Try again resends them. */
    data object SendFailed : ComposerNote

    /** A model-cycle shortcut changed the model: "Next turns in this conversation use {model}." */
    data class NextTurnsModel(val label: ModelLabel) : ComposerNote

    data class ReadOnly(val reason: ReadOnlyReason) : ComposerNote
}

/**
 * A change the field must apply to its own `TextFieldState` (state, not a one-shot flow: plan 09 §6.3). The screen
 * applies it once per [id] and calls `ComposerActions.onEditApplied(id)`.
 */
data class FieldEdit(val id: Long, val change: Change) {
    sealed interface Change {
        /** Insert at the selection, replacing it (an agent `choices` tap, a starter). */
        data class Insert(val text: String) : Change

        /** Replace the whole text and put the caret at [caret] (history browsing, a failed send's words coming back). */
        data class Replace(val text: String, val caret: Int) : Change

        data object Clear : Change
    }
}

/** The composer's whole screen state (plan 03 §19.7 rule 3: no copy, no Compose). */
data class ComposerUi(
    val button: FieldButton = FieldButton.Mic,
    val chips: ImmutableList<ComposerChip> = persistentListOf(),
    /** The running reply's glow (plan 09 §10.2): on while a reply runs, never while a request waits. */
    val glow: Boolean = false,
    val note: ComposerNote? = null,
    /** Counts accepted sends; the screen applies the after-send focus rule ([ComposerKeys.keepFocusAfterSend]) on change. */
    val sends: Int = 0,
    val edit: FieldEdit? = null,
    val fieldEnabled: Boolean = true,
)
