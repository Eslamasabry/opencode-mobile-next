package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// The Edge light's list, as the panel draws it. No copy and no Compose here: every sentence is a typed token that the
// screen turns into the string keys named `edgeLight*` in `:ui:words`.

/** The groups of the list, in the order the presenter lists them: the most urgent first (the panel fills from the bottom). */
public enum class LampGroupKind { Pinned, NeedsYou, Failed, Working, Done, Recent }

/** What a row or a sub-agent is doing, for its mark. */
public enum class LampState { NeedsYou, Failed, Working, Done, Idle }

/** The one line under a title that says how it goes. Dropped (null) when it only repeats the group's heading. */
public sealed interface LampStateLine {
    /** "3 of 7 done". */
    public data class TasksDone(val done: Int, val total: Int) : LampStateLine

    public data object Thinking : LampStateLine

    public data object Working : LampStateLine

    public data object Done : LampStateLine

    /** "for 4m": how long it has been stuck (the time of its last activity; shown for needs-you and failed only). */
    public data class For(val age: RelativeAge) : LampStateLine

    /** "1 waiting for you · 2 still working" (parallel sub-agents). */
    public data class Waiting(val waiting: Int, val working: Int) : LampStateLine

    /** "Waiting for your answer to finish": only the waiting sub-agent holds the run. */
    public data object WaitingToFinish : LampStateLine

    /** "Paused at step 3 of 5 until you answer" (sub-agents that ran one after another). */
    public data class PausedAtStep(val step: Int, val total: Int) : LampStateLine
}

/** What a sub-agent's one line says about it. */
public enum class LampChildState { Working, Thinking, NeedsYou, Failed, Done, Idle }

/** The small fact at the end of a sub-agent line; the data carries only task progress. */
public data class LampFact(val done: Int, val total: Int)

/** Sub-agents that run at the same time (a trunk with branches) or one after another (one trunk). */
public enum class LampTreeKind { Parallel, Sequence }

/** What the person can do about a waiting request, as the row shows it. */
public sealed interface LampAnswerUi {
    /** A permission: Allow once and Deny (the 3-second hold starts on a tap). */
    public data object AllowDeny : LampAnswerUi

    /** A question or a form: "Answer…" opens the conversation at the request. */
    public data object Answer : LampAnswerUi

    /** The reply is in its undo window: "Allowed · Undo" or "Denied · Undo". */
    public data class Held(val reply: HomeHeldReply) : LampAnswerUi
}

/** A waiting request in words the screen composes ("Asks to edit: wait7.txt", "Asks: ‹question›"). Masked, one line. */
public data class LampRequestUi(
    val kind: DockRequestKind,
    val requestId: String,
    /** `run`, `edit`, `read` or `folder`; empty for a question, a form, or when the ask names none. */
    val action: String,
    val target: String,
    val text: String,
    /** The conversation the request belongs to (a sub-agent's own, when it raised it). */
    val owner: ConversationRef,
    val answer: LampAnswerUi,
)

/** The stop square of a running row, its hold, and the receipt after. */
public sealed interface LampStopUi {
    /** The 44 dp stop square, named "Stop ‹title›". */
    public data object Button : LampStopUi

    /** "Stopping ‹title›…" with Undo while the 3 seconds run. */
    public data object Holding : LampStopUi

    /** "Stopped" once it was sent. */
    public data object Stopped : LampStopUi
}

/** One sub-agent line of a conversation's block. */
public data class LampChildUi(
    val key: String,
    val ref: ConversationRef,
    val name: String?,
    val state: LampChildState,
    val fact: LampFact?,
    /** Its own waiting request: opens in place, with Allow once / Deny or Answer…. */
    val request: LampRequestUi?,
    /** True when it waits on you: "Paused until you answer". */
    val paused: Boolean,
    /** The step number among the block's children, 1-based (the words of a sequence use it). */
    val step: Int,
    /** True for the child the panel scrolls into view when it opens (the first one that needs you). */
    val scrollIntoView: Boolean,
    val stop: LampStopUi?,
)

/** A conversation's sub-agents as one tree under its row. */
public data class LampTreeUi(
    val kind: LampTreeKind,
    /** In the order they started, the needing one left in place; at most five. */
    val children: ImmutableList<LampChildUi>,
    /** "+N more": children left out of [children] (the least urgent), plus those the engine did not list. */
    val more: Int,
    /** "+N more inside": levels below the second. The data lists one level, so this is 0 until it reports deeper ones. */
    val moreInside: Int,
    /** All sub-agents of the conversation, for "N sub-agents". */
    val count: Int,
    /** Folded to the parent row: all finished (or the person folded it). Never while one needs you. */
    val folded: Boolean,
)

/** One conversation (or a working or waiting sub-agent of the open conversation) in a group. */
public data class LampRowUi(
    val key: String,
    val ref: ConversationRef,
    /** Null when the conversation has no name yet: the screen says "New conversation". */
    val title: String?,
    val state: LampState,
    val stateLine: LampStateLine?,
    val newestLine: String?,
    /** The request the row asks about, when it raised it itself (a sub-agent's is inside [tree]). */
    val request: LampRequestUi?,
    /** The home's time words come from this: `RelativeAge.of(lastActivityMs, now)`. */
    val lastActivityMs: Long?,
    val age: RelativeAge?,
    val server: String?,
    val pinned: Boolean,
    /** For a sub-agent of the open conversation: that conversation's title ("Sub-agent of ‹title›"). */
    val subAgentOf: String?,
    val tree: LampTreeUi?,
    val stop: LampStopUi?,
    /** In Edit mode: pin, rename, hide, and the drag handle with Move up / Move down of a pinned row. */
    val edit: LampRowEditUi?,
)

public data class LampRowEditUi(
    val pinned: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    /** A conversation on screen cannot be hidden; the list never shows it, so this is true for every shown row. */
    val canHide: Boolean,
    /** A sub-agent row has no pin, rename or hide. */
    val isSubAgent: Boolean,
)

public data class LampGroupUi(val kind: LampGroupKind, val rows: ImmutableList<LampRowUi>)

/** "Hidden · n · Show again". */
public data class LampHiddenUi(val items: ImmutableList<LampHiddenItemUi>) {
    val count: Int get() = items.size
}

public data class LampHiddenItemUi(val ref: ConversationRef, val title: String?, val state: LampState)

/** One mark on the face: who needs you, then failed, then runs. */
public enum class LampMarkKind { NeedsYou, Failed, Working }

/** [progress] is done/total (0..1) for a run with a task list; null draws the dashed ring. Null for needs-you and failed. */
public data class LampFaceMark(val kind: LampMarkKind, val progress: Float?)

/** The light itself: the number, up to three marks, and the words TalkBack speaks. */
public data class LampFaceUi(
    /** Needs you + failed. Zero draws no number. */
    val number: Int,
    val marks: ImmutableList<LampFaceMark>,
    val needs: Int,
    val failed: Int,
    val working: Int,
) {
    /** Nothing needs you, nothing failed, nothing runs: the small "‹" tab. */
    val quiet: Boolean get() = needs == 0 && failed == 0 && working == 0
}

/** Side of the screen; Start is the reading-start edge. */
public enum class LampSide { Start, End }

public enum class LampHeight { Low, Middle, High }

/** Where the light sits: one of six spots, saved per device. */
public data class LampPlace(val side: LampSide = LampSide.End, val height: LampHeight = LampHeight.Low)

/** One thing told after an act, one at a time. */
public sealed interface LampNotice {
    /** "Hidden from this list · ‹title› · Undo" for 8 seconds. */
    public data class Hidden(val ref: ConversationRef, val title: String?) : LampNotice

    public data class StopFailed(val ref: ConversationRef, val title: String?) : LampNotice

    public data class AnswerFailed(val ref: ConversationRef, val title: String?) : LampNotice

    public data class RenameFailed(val ref: ConversationRef, val title: String?) : LampNotice

    /** "Edge light off. Settings › Other conversations turns it back on." */
    public data object TurnedOff : LampNotice
}

/** Everything the panel draws. */
public data class LampUi(
    /** The open conversation's title for "You are in “‹title›”"; null when it has none or is not known. */
    val openTitle: String?,
    /** Pinned?, Needs you, Failed, Working, Done, Recent: only groups that have rows. */
    val groups: ImmutableList<LampGroupUi> = persistentListOf(),
    val hidden: LampHiddenUi? = null,
    /** Conversations the list did not take: "‹n› more in All conversations". */
    val more: Int = 0,
    /** The footer's "All conversations · n": every conversation shown plus [more]. */
    val allCount: Int = 0,
    /** No row anywhere: "No other conversations yet." */
    val empty: Boolean = true,
    val editing: Boolean = false,
    val face: LampFaceUi = LampFaceUi(0, persistentListOf(), 0, 0, 0),
    /** Key of the sub-agent that needs you first: the panel scrolls it into view when it opens. */
    val scrollToKey: String? = null,
    val place: LampPlace = LampPlace(),
    val notice: LampNotice? = null,
)
