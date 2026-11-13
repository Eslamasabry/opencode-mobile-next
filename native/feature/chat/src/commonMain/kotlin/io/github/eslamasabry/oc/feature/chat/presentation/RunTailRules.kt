package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState

/** Where the run stands, as the bottom of the transcript and the message box say it (review #12). */
public enum class RunTail {
    /** No run: the turn's footer says how it ended. */
    Idle,

    /** The run works: the live line says what it does. */
    Working,

    /** A request card waits for the person's decision: the card says so. */
    WaitingForYou,

    /** The person pressed Stop and the run winds down. */
    Stopping,
}

/**
 * One current run state (review #12, C-087/C-020/C-032): at any moment the transcript's tail states the run's state
 * once and the message box agrees with it.
 */
public object RunTailRules {
    /**
     * The run's state from what is known: [busy] a run is going, [stopping] the person's Stop is on its way (or the run
     * stops after this step), [waiting] a request card waits for the person ([waitsForYou]). The table:
     *
     * | State         | The transcript's tail                                                        | The message box                                     |
     * |---------------|------------------------------------------------------------------------------|-----------------------------------------------------|
     * | Idle          | the turn's footer once ("Stopped", "You declined this", …); no live line     | the idle words ("Ask …"), Send, no Stop             |
     * | Working       | the live line: what the turn does ("Editing 1 file", "Thinking"); work line  | "Steer while it works…", running glow, Stop         |
     * |               | with its summary; a sent decision shows on its card ("Sending…")             |                                                     |
     * | WaitingForYou | the card alone ("Needs your decision"); no live line; the work line says     | no running glow (the run is not working); Stop      |
     * |               | only how much work there is ("1 step", "Read 2 files"), never "Waiting"      | stays to end the run instead of answering           |
     * | Stopping      | the live line "Stopping", once; no waiting words; a card the stop retired    | Stop chip reads "Stopping"                          |
     * |               | is the card owner's "No longer needed"                                       |                                                     |
     *
     * Stopping → stopped is one line replaced in place: the live line "Stopping" goes with the run, and the footer's
     * "Stopped" comes in the same frame (live and footer are never drawn for the same run).
     */
    public fun tail(busy: Boolean, stopping: Boolean, waiting: Boolean): RunTail =
        when {
            !busy -> RunTail.Idle
            stopping -> RunTail.Stopping
            waiting -> RunTail.WaitingForYou
            else -> RunTail.Working
        }

    /**
     * Whether a request card still waits on the person: asked and not answered ([CardState.Waiting]), answered but
     * still inside the Undo hold ([CardState.Held]: nothing left the phone), or refused by the server and to be tried
     * again ([CardState.NotAccepted]). Once the decision is sending or sent, or the run that asked was stopped, nothing
     * waits on the person: the run works (or stops) again.
     */
    public fun waitsForYou(card: CardState?): Boolean =
        card is CardState.Waiting || card is CardState.Held || card is CardState.NotAccepted

    /** Whether any of the chat's request cards still waits on the person. */
    public fun waitsForYou(cards: List<CardState>): Boolean = cards.any { waitsForYou(it) }

    /** The live line is drawn only while the run works or stops; a waiting card speaks for itself. */
    public fun liveLineShown(tail: RunTail): Boolean = tail == RunTail.Working || tail == RunTail.Stopping

    /** The message box glows as running only while the run works or stops, never while it waits on the person. */
    public fun boxRunning(tail: RunTail): Boolean = tail == RunTail.Working || tail == RunTail.Stopping
}
