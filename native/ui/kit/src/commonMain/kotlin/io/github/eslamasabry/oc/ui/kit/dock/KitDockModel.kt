package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

/**
 * What one ring of the edge dock says. A feature maps its own dock state onto this; the kit never reads the engine.
 * [Working] has no progress on purpose: a busy conversation without a task list gets no invented percentage.
 */
@Immutable
public sealed interface KitDockRingState {
    /** Busy with a task list: an arc of [done] of [total], advancing on a spring with one small overshoot. */
    @Immutable public data class WorkingWithTasks(val done: Int, val total: Int) : KitDockRingState

    /** Busy, no task list: a calm comet runs round the ring. */
    @Immutable public data object Working : KitDockRingState

    /** Busy and reasoning: the full ring breathes. */
    @Immutable public data object Thinking : KitDockRingState

    /** A request waits for the person: attention ring with a dot, a double pop, the dock leans out. */
    @Immutable public data object NeedsYou : KitDockRingState

    /** The run ended well: the arc closes and a check draws itself, then a dim full ring. */
    @Immutable public data object Finished : KitDockRingState

    /** The last run failed: the failure ink with a "!" mark (never colour alone), one shake, a pulse along the body. */
    @Immutable public data object Failed : KitDockRingState

    /** Nothing running, nothing unseen: a hollow, dim ring. */
    @Immutable public data object Idle : KitDockRingState
}

/** True while the ring's agent is running (the states that offer Stop). */
public val KitDockRingState.running: Boolean
    get() = this is KitDockRingState.WorkingWithTasks || this == KitDockRingState.Working || this == KitDockRingState.Thinking

/** A sub-agent under a ring: a satellite at rest, a mini ring in the swell, an indented row in the list. */
@Immutable
public data class KitDockChild(
    /** Stable key (the sub-agent's session), reported back in [KitDockIntents.onOpenChild]. */
    val key: String,
    /** Short name: its agent when known, else its title. */
    val name: String,
    val state: KitDockRingState,
    /** Counts entries into NeedsYou or Failed; a rise plays the parent's alert once. */
    val pulse: Int = 0,
    /** The last line of its work, for the pulled-out list. */
    val lastLine: String? = null,
)

/** Who said one line of a peek card. */
public enum class KitDockLineRole { Person, Agent, Tool }

/** One short line of a conversation's tail, shown in the peek card. */
@Immutable
public data class KitDockLine(val role: KitDockLineRole, val text: String)

/** One ring: one live conversation. */
@Immutable
public data class KitDockRing(
    /** Stable key (the conversation's reference); every intent reports it. */
    val key: String,
    /** The nickname when the person set one, else the title. */
    val name: String,
    /** The agent's mark id (slug) drawn in the ring's centre; null draws the monogram of [name]. */
    val markId: String?,
    /** The state to draw, sub-agents already rolled in by the model. */
    val state: KitDockRingState,
    /** Counts every entry into NeedsYou or Failed (own or a sub-agent's); a rise plays the one-shot alert. */
    val pulse: Int = 0,
    /** Sub-agents in the model's order (the kit draws alerts first). */
    val children: List<KitDockChild> = emptyList(),
    /** Sub-agents that did not fit the model's cap. */
    val childOverflow: Int = 0,
    /** The child that raised [state] to NeedsYou or Failed, so the words can say "explore needs you". */
    val causedBy: String? = null,
    /**
     * The server's name, only when the host says more than one server is live; then the swell's state line and the
     * list rows name it. Never a badge on the ring at rest.
     */
    val server: String? = null,
    /** The newest line of the conversation, for the pulled-out list. */
    val lastLine: String? = null,
    /** Up to about six short lines of the conversation's tail, for the peek card. */
    val peek: List<KitDockLine> = emptyList(),
    /** Pinned rings stay at the top in the person's order and do not leave when idle (the model's rule). */
    val pinned: Boolean = false,
)

/** Which screen edge the dock sits on. Start and End follow the layout direction (End is the right in LTR). */
public enum class KitDockSide { Start, End }

/** Where the dock lives: its edge and its height (0 = as high as it may go, 1 = as low). Comes from the model. */
@Immutable
public data class KitDockPlacement(val side: KitDockSide = KitDockSide.End, val verticalBias: Float = 0.5f)

/**
 * What the host tells the dock to keep clear of, in dp from the dock area's own edges: the status bar ([top]), the
 * navigation bar plus the keyboard when it is up ([bottom]), cutouts ([start], [end]), and any other spot the dock
 * must never cover, such as the composer's send button ([keepClear], in the dock area's coordinates). The part makes no
 * assumption about what is behind it (it is used on the home screen and in the chat).
 */
@Immutable
public data class KitDockInsets(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    val keepClear: List<DpRect> = emptyList(),
)

/** Everything the dock draws. Order, pins, names and placement are the model's; the kit only reports intents. */
@Immutable
public data class KitDockModel(
    val rings: List<KitDockRing>,
    /** Conversations removed from the dock that are still live; the list shows them under "N hidden" with a way back. */
    val hidden: List<KitDockRing> = emptyList(),
    /** The ring of the conversation on screen. */
    val current: String? = null,
    /** The sub-agent on screen, when one is ([current] is then its parent). */
    val currentChild: String? = null,
    /** Tucked to the nub of state dots. */
    val tucked: Boolean = false,
    val placement: KitDockPlacement = KitDockPlacement(),
    /**
     * Whether the dock offers a new conversation: the round "+" at the strip's foot and the "New conversation" row in
     * the list. A screen with its own button for it (the conversations home) passes false: the strip then has no foot,
     * and with no rings the dock draws nothing at all and takes no touches. Default true.
     */
    val offersNew: Boolean = true,
)

/**
 * The intents the dock reports. It performs nothing itself: the host acts and feeds a new [KitDockModel] back.
 * Every method has an empty default so a host implements only what it supports.
 */
@Stable
public interface KitDockIntents {
    /** Switch to the conversation of ring [key]. */
    public fun onOpen(key: String) {}

    /** Open sub-agent [childKey] of ring [key]. */
    public fun onOpenChild(key: String, childKey: String) {}

    /** Allow the request ring [key] waits on (its own or the sub-agent's that raised it). */
    public fun onAllow(key: String) {}

    /** Deny that request. */
    public fun onDeny(key: String) {}

    /** Stop the run of ring [key]. */
    public fun onStop(key: String) {}

    /** Take ring [key] out of the dock (it stays live and is listed under "hidden"). */
    public fun onRemove(key: String) {}

    /** Bring a hidden conversation back into the dock. */
    public fun onRestore(key: String) {}

    /** Save a nickname; an empty [name] clears it. */
    public fun onRename(key: String, name: String) {}

    /** The round button at the dock's foot. */
    public fun onNewConversation() {}

    /** The person tucked the dock to the nub, or brought it back. */
    public fun onTuckChange(tucked: Boolean) {}

    /** Move the ring at index [from] to index [to] (indices into [KitDockModel.rings]). */
    public fun onReorder(from: Int, to: Int) {}

    /** Pin or unpin ring [key]. */
    public fun onPinChange(key: String, pinned: Boolean) {}

    /** The person moved the dock: its edge and its height. */
    public fun onPlacementChange(side: KitDockSide, verticalBias: Float) {}
}

/**
 * The dock's words. The host supplies them from its string resources (the kit holds no copy of its own here), so the
 * Sonnet builder wires them to existing keys. Frame text: capped at 1.3 x by the kit.
 */
@Stable
public interface KitDockWords {
    /** Read by assistive technology for the whole dock ("Running conversations"). */
    public val dockLabel: String
    public fun working(): String
    public fun workingTasks(done: Int, total: Int): String
    public fun thinking(): String
    public fun needsYou(): String
    public fun finished(): String
    public fun failed(): String
    public fun idle(): String
    /** "explore needs you". */
    public fun childNeedsYou(child: String): String
    /** "cache failed". */
    public fun childFailed(child: String): String
    /** "3 sub-agents". */
    public fun subAgents(count: Int): String
    /** "on Mac mini". */
    public fun onServer(server: String): String
    /** The words joined in a state line: "Working · 3 of 7". */
    public val separator: String get() = " · "
    public val allow: String
    public val deny: String
    public val stop: String
    public val rename: String
    public val remove: String
    public val open: String
    public val pin: String
    public val unpin: String
    public val restore: String
    public val newConversation: String
    /** The field's label while renaming. */
    public val nickname: String
    /** "Pinned", the words beside the pin line in the list. */
    public val pinnedLabel: String
    /** "2 hidden". */
    public fun hidden(count: Int): String
    /** "Move the dock", the grip's label. */
    public val moveDock: String
    /** "Show the dock", the nub's label. */
    public val showDock: String
    /** Action names for assistive technology: they name their target ("Allow Bank API", "Open explore"). */
    public fun actionOn(action: String, target: String): String
}
