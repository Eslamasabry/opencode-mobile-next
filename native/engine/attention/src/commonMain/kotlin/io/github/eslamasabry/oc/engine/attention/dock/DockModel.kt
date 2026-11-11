package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap

/**
 * What one ring of the edge dock says. One state per conversation, derived (never stored) from the facts the app
 * already has. [Working] has no progress on purpose: a busy session without a task list gets no invented percentage.
 */
public sealed interface DockState {
    /** Busy and the agent keeps a task list: [done] of [total] (cancelled tasks are not counted). */
    public data class WorkingWithTasks(val done: Int, val total: Int) : DockState

    /** Busy, no task list. */
    public data object Working : DockState

    /** Busy and the newest part of the reply is reasoning. Only shown when there is no task list. */
    public data object Thinking : DockState

    /** A permission, question or form waits for the person. Stays until it is answered. */
    public data object NeedsYou : DockState

    /** Went idle after working, the run did not fail, and the person has not opened the conversation since. */
    public data object Finished : DockState

    /** The last run failed and the person has not opened the conversation since. */
    public data object Failed : DockState

    /** Nothing running, nothing unseen. */
    public data object Idle : DockState
}

/** One ring. [name] is what to draw under it: the nickname when the person set one, else the session title. */
public data class DockEntry(
    val ref: ConversationRef,
    val title: String?,
    val nickname: String?,
    /** The agent id (mark key) the session runs, when the server said. */
    val agent: String?,
    val state: DockState,
    /**
     * Counts every time this ring entered NeedsYou or Failed. The UI plays its one-shot blip when the number goes up,
     * so it never has to diff states itself.
     */
    val pulse: Int,
    /**
     * The conversation's own state before sub-agents are rolled in; [state] is the one to draw. They differ only when a
     * sub-agent raises the ring (see [causedBy]) or works while this conversation is idle between steps.
     */
    val ownState: DockState = state,
    /** The sub-agent that raised [state] to NeedsYou or Failed, so the UI can say "explore needs you". Null otherwise. */
    val causedBy: ConversationRef? = null,
    /** Sub-agents this conversation spawned (sessions whose parent is this one), in creation order, capped. */
    val children: ImmutableList<DockChild> = persistentListOf(),
    /** Sub-agents that did not fit [DockModel.childCap]. */
    val childOverflow: Int = 0,
    /** This conversation's own pending requests, oldest first (a sub-agent's are on its [DockChild]). */
    val requests: ImmutableList<DockRequestRef> = persistentListOf(),
    /** False for the conversation on screen (or the one whose sub-agent is on screen). */
    val removable: Boolean = true,
    /** Pinned rings stay at the top, in the person's order, and do not leave the dock when idle. */
    val pinned: Boolean = false,
    /** The server this conversation lives on (the saved profile's name), when the feed knows it. */
    val serverName: String? = null,
) {
    val name: String? get() = nickname ?: title
}

/** Which screen edge the dock sits on. */
public enum class DockSide { Start, End }

/**
 * Where the dock sits: a device-wide choice, not per profile. [bias] is the vertical position of its centre, 0 (top)
 * to 1 (bottom). The default is the end edge, a little below the middle.
 */
public data class DockPlacement(val side: DockSide = DockSide.End, val bias: Float = DEFAULT_BIAS) {
    public companion object {
        public const val DEFAULT_BIAS: Float = 0.58f

        /** Clamps [bias] into 0..1; a value that is not a number reads as the default. */
        public fun of(side: DockSide, bias: Float): DockPlacement =
            DockPlacement(side, if (bias.isNaN()) DEFAULT_BIAS else bias.coerceIn(0f, 1f))
    }
}

public enum class DockRequestKind { Permission, Question, Form }

/**
 * A request waiting on the person, as the stream and the reconcile read named it: enough for the UI to call the
 * existing answer path later (the conversation ref is on the entry or child that carries it).
 */
public data class DockRequestRef(val kind: DockRequestKind, val id: String) {
    internal val key: String get() = "${kind.name.lowercase()}:$id"

    internal companion object {
        fun parse(key: String): DockRequestRef? {
            val kind = DockRequestKind.entries.firstOrNull { key.startsWith(it.name.lowercase() + ":") } ?: return null
            return DockRequestRef(kind, key.substringAfter(':'))
        }
    }
}

/** One sub-agent under a ring. Never a ring of its own. [name] is its agent when known, else its title. */
public data class DockChild(
    val ref: ConversationRef,
    val title: String?,
    val agent: String?,
    val state: DockState,
    val pulse: Int,
    /** The requests this sub-agent waits on (NeedsYou), oldest first. */
    val requests: ImmutableList<DockRequestRef> = persistentListOf(),
    /** False only for the sub-agent on screen. */
    val removable: Boolean = true,
    val serverName: String? = null,
) {
    val name: String? get() = agent ?: title
}

/** A conversation the person removed that is still live (running, waiting or unseen). It can be brought back. */
public data class DockHidden(
    val ref: ConversationRef,
    val title: String?,
    val nickname: String?,
    val agent: String?,
    val state: DockState,
    /** The ring this sub-agent belongs to; null for a conversation that is a ring itself. */
    val parent: ConversationRef?,
) {
    val name: String? get() = nickname ?: title
}

public enum class DockPresence {
    /** Nothing to offer at all (no live, pinned, open or recent conversation): no dock, no nub. */
    Hidden,

    /** Tucked to the thin edge nub: quiet (fewer than two live and no unanswered failure or request), or tucked. */
    Nub,

    /** Out: two or more live, or an unanswered failure or request, and the person has not tucked it. */
    Out,
}

/** The one thing the UI draws. [overflow] conversations did not fit in [DockModel.cap] rings. */
public data class DockUi(
    val entries: ImmutableList<DockEntry> = persistentListOf(),
    val presence: DockPresence = DockPresence.Hidden,
    val overflow: Int = 0,
    /** The ring of the conversation on screen; for a sub-agent on screen this is its parent. */
    val open: ConversationRef? = null,
    /** The sub-agent on screen, when one is; [open] is then its parent. */
    val openChild: ConversationRef? = null,
    val tucked: Boolean = false,
    /** Entries that are not Idle once sub-agents are counted. Idle recent rings are not live. */
    val liveCount: Int = 0,
    /** Removed conversations that are still live, for the "N hidden" list. */
    val hidden: ImmutableList<DockHidden> = persistentListOf(),
    /** True when the live rings come from more than one server: the UI then says which server each is on. */
    val severalServers: Boolean = false,
    val placement: DockPlacement = DockPlacement(),
) {
    val hiddenCount: Int get() = hidden.size
}

/** How a run ended, as far as the dock cares. */
public enum class DockRunEnd {
    /** Finished or went idle: the ring shows Finished until seen. */
    Succeeded,

    /** The run failed: the ring shows Failed until seen. */
    Failed,

    /** The person (or the server) stopped it: back to Idle without a celebration. */
    Stopped,
}

/** One row of a server's conversation list (the home list's saved rows), for the dock's recent rings. */
public data class DockRecent(
    val session: SessionId,
    val title: String?,
    /** Last activity the server reported, epoch ms: the newer, the earlier it is offered. */
    val lastActivityMs: Long,
    /** Set for a sub-agent: never offered as a ring of its own. */
    val parent: SessionId? = null,
)

/** Everything that can change the dock. The reducer is [DockModel.reduce]; nothing else mutates it. */
public sealed interface DockEvent {
    /** Fills in the title and agent of a conversation the dock already tracks. Never adds one. */
    public data class Meta(val ref: ConversationRef, val title: String?, val agent: String?, val parent: SessionId? = null) : DockEvent

    public data class RunStarted(val ref: ConversationRef) : DockEvent

    public data class RunEnded(val ref: ConversationRef, val end: DockRunEnd) : DockEvent

    /** True while the newest part of the reply is reasoning. */
    public data class Reasoning(val ref: ConversationRef, val active: Boolean) : DockEvent

    /** The agent's task list. A [total] of zero or less means no list. */
    public data class Tasks(val ref: ConversationRef, val done: Int, val total: Int) : DockEvent

    public data class RequestOpened(val ref: ConversationRef, val requestId: String) : DockEvent

    public data class RequestSettled(val ref: ConversationRef, val requestId: String) : DockEvent

    /** A reconcile read of every waiting request of one server: replaces what the dock believed about it. */
    public data class RequestsRead(val profile: ProfileId, val bySession: Map<SessionId, Set<String>>) : DockEvent

    /** A reconcile read of the busy sessions of one server. */
    public data class StatusesRead(val profile: ProfileId, val busy: Set<SessionId>) : DockEvent

    /** The session record says its last run failed. Only a conversation sitting in the dock as Finished is raised by it (the
     * run end event said idle, the record knows better); one that is not in the dock, or was seen, is never brought back. */
    public data class LastRunFailed(val ref: ConversationRef, val failed: Boolean) : DockEvent

    public data class Deleted(val ref: ConversationRef) : DockEvent

    public data class ProfileGone(val profile: ProfileId) : DockEvent

    /** The connection to a server closed: its conversations leave the dock (nothing is known about them now). Names stay. */
    public data class ProfileOffline(val profile: ProfileId) : DockEvent

    /**
     * A server's conversation list (what the home list holds): the recent conversations the dock offers as quiet rings
     * when fewer than [DockModel.cap] are live. Merged with what the dock heard live; the newer activity wins.
     */
    public data class Recent(val profile: ProfileId, val rows: List<DockRecent>) : DockEvent

    /** The saved nicknames of one profile, replacing what the model held for it. */
    public data class Nicknames(val profile: ProfileId, val names: Map<SessionId, String>) : DockEvent

    /** One nickname changed; null clears it. */
    public data class NicknameSet(val ref: ConversationRef, val name: String?) : DockEvent

    /** The conversation on screen, or null when none is. Opening a conversation marks it seen. */
    public data class Opened(val ref: ConversationRef?) : DockEvent

    /** Clears Finished or Failed without opening the conversation. */
    public data class Seen(val ref: ConversationRef) : DockEvent

    /** The person removed this ring (or sub-agent). Ignored for the conversation on screen. */
    public data class Remove(val ref: ConversationRef) : DockEvent

    /** Brings a removed conversation back (the Undo, or a pick from the hidden list). */
    public data class Restore(val ref: ConversationRef) : DockEvent

    /** The removed set saved for one profile, merged into the model after a restart. */
    public data class RemovedLoaded(val profile: ProfileId, val sessions: List<SessionId>) : DockEvent

    /** Says whose child a session is. Comes from a [DockChildSource]; the model does not care which provider said it. */
    public data class Link(val child: ConversationRef, val parent: SessionId) : DockEvent

    /** Pins a ring (not a sub-agent) at the end of the pinned list. */
    public data class Pin(val ref: ConversationRef) : DockEvent

    public data class Unpin(val ref: ConversationRef) : DockEvent

    /** Moves pinned [from] to the place of pinned [to]. Both must be pinned. */
    public data class Reorder(val from: ConversationRef, val to: ConversationRef) : DockEvent

    /**
     * The pins saved for one profile, in the person's order. Also says the profile is connected: pinned rings of a
     * server that is not connected are not drawn.
     */
    public data class PinsLoaded(val profile: ProfileId, val sessions: List<SessionId>) : DockEvent

    /** The saved server's name, for entries to carry. Blank clears it. */
    public data class ServerName(val profile: ProfileId, val name: String?) : DockEvent

    public data class SetPlacement(val placement: DockPlacement) : DockEvent

    public data object Tuck : DockEvent

    public data object Untuck : DockEvent
}

/**
 * The edge dock's brain: a pure reducer from [DockEvent]s to a [DockUi]. No clock, no I/O, no Compose.
 *
 * Rules (the owner's notes, `edge-dock/NOTES.md`; "always available" is the owner's decision of 2026-11-11):
 * - In the dock: every conversation that is not Idle, the pinned ones, and the open one (always, highlighted, not
 *   removable). When fewer than [cap] places are taken, the rest are filled with the most recently active Idle
 *   conversations the dock knows ([DockEvent.Recent] rows and conversations it heard work, ask or open), root
 *   conversations only, never a removed one, most recent first. An idle ring is quiet (no progress, no alert) and a
 *   tap opens it. Idle fillers never take a place a live, pinned or open ring needs: above [cap] there are none, and
 *   [DockUi.overflow] counts only those. At most [MAX_RECENT] recent conversations are remembered; a deleted one, and
 *   every one of a server that disconnected or was deleted, is forgotten.
 * - The order is stable: a conversation keeps its place until it leaves; a newcomer goes last (fillers that join
 *   together go most recent first). A state change never reshuffles. Above [cap] rings the ones that matter most stay
 *   (pinned, NeedsYou, Failed, working, Finished), still in their stable order, and [DockUi.overflow] counts the rest.
 * - The dock is never hidden for lack of live conversations. [DockPresence.Out] needs two or more live (anything but
 *   Idle), or an unanswered failure or request; otherwise it is [DockPresence.Nub] (the thin edge nub). The person can
 *   tuck it, and a NEW failure or request untucks it; with nothing live the tuck is forgotten. [DockPresence.Hidden]
 *   only when there is nothing at all to offer (the app hides it too when the person turned the dock off).
 * - Removing (`DockEvent.Remove`): the ring (or sub-agent) leaves at once and stays out while nothing new happens. It
 *   comes back by itself only when it needs the person or fails AFTER the removal (a rise in its `pulse`, or a
 *   sub-agent's for a removed parent), or when the person opens it. Plain working or finished does not bring it back. A removed parent takes its sub-agents with it; a removed sub-agent hides only itself.
 *   The conversation on screen cannot be removed; opening a removed conversation brings it back. A removed idle ring
 *   stays out and the next most recent takes its place. The set drops a conversation when the dock forgets it (its
 *   record leaves and it is no longer a recent conversation) or it is deleted, and keeps at most [MAX_REMOVED].
 *   Removed-but-live ones are listed in [DockUi.hidden].
 * - Pinned rings (`Pin`, `Unpin`, `Reorder`) come first in the person's order, stay when idle (while their server is
 *   connected) and honour Remove. Unpinned rings keep the stable order after them. At most [MAX_PINNED]; a deleted
 *   conversation leaves the list.
 * - Opening a conversation (or [DockEvent.Seen]) clears its Finished and Failed. A run that ends in the open
 *   conversation is seen at once. A seen conversation is Idle (at most a quiet recent ring), so the session record
 *   cannot raise an old failure again.
 */
public class DockModel private constructor(private val s: Snapshot) {
    public val cap: Int get() = s.cap

    public val childCap: Int get() = s.childCap

    public val ui: DockUi by lazy(LazyThreadSafetyMode.NONE) { s.toUi() }

    /** The removed set, oldest first, parents and sub-agents alike. The controller saves it. */
    public val removedRefs: List<ConversationRef> get() = s.removed

    /** The pinned rings in the person's order. The controller saves it. */
    public val pinnedRefs: List<ConversationRef> get() = s.pinned

    public fun reduce(event: DockEvent): DockModel {
        val next = s.step(event)
        if (next === s) {
            // A full read of the requests that changes no record still settles the removed set: a removed conversation
            // the server no longer reports leaves it (a saved set meeting the first read after a restart).
            if (event !is DockEvent.RequestsRead) return this
            val pruned = s.pruneRemoved(s, event)
            return if (pruned === s) this else DockModel(pruned)
        }
        val settled = next.noted(s, event).unmuteAlerts(s).settled()
        return DockModel(settled.pruneRemoved(s, event))
    }

    private enum class Outcome { None, Finished, Failed }

    private data class Tracked(
        val ref: ConversationRef,
        val seq: Long,
        val title: String? = null,
        val agent: String? = null,
        val busy: Boolean = false,
        val thinking: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val requests: Set<String> = emptySet(),
        val outcome: Outcome = Outcome.None,
        val pulse: Int = 0,
        val parent: SessionId? = null,
    ) {
        fun requestRefs(): ImmutableList<DockRequestRef> = requests.mapNotNull { DockRequestRef.parse(it) }.toImmutableList()

        fun state(): DockState =
            when {
                requests.isNotEmpty() -> DockState.NeedsYou
                busy && total > 0 -> DockState.WorkingWithTasks(done.coerceIn(0, total), total)
                busy && thinking -> DockState.Thinking
                busy -> DockState.Working
                outcome == Outcome.Failed -> DockState.Failed
                outcome == Outcome.Finished -> DockState.Finished
                else -> DockState.Idle
            }
    }

    private data class Snapshot(
        val cap: Int,
        val childCap: Int,
        /** Removed conversations, oldest first. A removed parent hides its sub-agents; a removed sub-agent hides itself. */
        val removed: List<ConversationRef> = emptyList(),
        val pinned: List<ConversationRef> = emptyList(),
        /** Profiles whose connection is live (their pins were loaded). */
        val online: Set<ProfileId> = emptySet(),
        val servers: Map<ProfileId, String> = emptyMap(),
        val placement: DockPlacement = DockPlacement(),
        val tracked: Map<ConversationRef, Tracked> = emptyMap(),
        val names: Map<ConversationRef, String> = emptyMap(),
        val order: List<ConversationRef> = emptyList(),
        val open: ConversationRef? = null,
        val tucked: Boolean = false,
        val nextSeq: Long = 0,
        /** Titles and agents heard before a conversation joined the dock, so a newcomer arrives with its name. */
        val known: PersistentMap<ConversationRef, Triple<String?, String?, SessionId?>> = persistentMapOf(),
        /**
         * Recent conversations and their last activity (listed epoch ms, or one past the newest for activity heard
         * live, which is "now"). Root conversations only, at most [MAX_RECENT]: the pool idle rings are offered from.
         */
        val recent: Map<ConversationRef, Long> = emptyMap(),
    ) {
        /** The record for [ref], created when it is missing. */
        private fun withTracked(ref: ConversationRef, change: (Tracked) -> Tracked): Snapshot {
            val (base, next) = tracked[ref]?.let { it to nextSeq } ?: (Tracked(ref, nextSeq, known[ref]?.first, known[ref]?.second, parent = known[ref]?.third) to nextSeq + 1)
            return copy(tracked = tracked + (ref to change(base)), nextSeq = next)
        }

        private fun ifTracked(ref: ConversationRef, change: (Tracked) -> Tracked): Snapshot =
            if (ref in tracked) copy(tracked = tracked + (ref to change(tracked.getValue(ref)))) else this

        /** A new failure or request: the ring pulses and a tucked dock comes back out. */
        private fun alerted(t: Tracked): Tracked = if (t.ref == open) t else t.copy(pulse = t.pulse + 1)

        private fun untuckFor(ref: ConversationRef): Snapshot = if (ref == open) this else copy(tucked = false)

        private fun ended(t: Tracked, end: DockRunEnd): Tracked {
            val base = t.copy(busy = false, thinking = false)
            val onScreen = t.ref == open
            return when (end) {
                DockRunEnd.Failed ->
                    if (onScreen) base.copy(outcome = Outcome.None) else alerted(base).copy(outcome = Outcome.Failed)
                DockRunEnd.Stopped -> base
                // A success never replaces a failure the person has not seen.
                DockRunEnd.Succeeded ->
                    when {
                        onScreen -> base.copy(outcome = Outcome.None)
                        base.outcome == Outcome.Failed -> base
                        t.busy -> base.copy(outcome = Outcome.Finished)
                        else -> base
                    }
            }
        }

        private fun seen(ref: ConversationRef): Snapshot =
            ifTracked(ref) { it.copy(outcome = Outcome.None) }

        fun step(e: DockEvent): Snapshot =
            when (e) {
                is DockEvent.Meta -> remember(e).ifTracked(e.ref) { it.copy(title = e.title ?: it.title, agent = e.agent ?: it.agent, parent = e.parent ?: it.parent) }
                    .let { if (e.parent != null && e.ref in it.recent) it.copy(recent = it.recent - e.ref) else it }
                is DockEvent.RunStarted ->
                    withTracked(e.ref) { it.copy(busy = true, thinking = false, outcome = Outcome.None) }
                is DockEvent.RunEnded ->
                    if (e.ref !in tracked && e.end != DockRunEnd.Failed) {
                        this
                    } else {
                        withTracked(e.ref) { ended(it, e.end) }.let { if (e.end == DockRunEnd.Failed) it.untuckFor(e.ref) else it }
                    }
                is DockEvent.Reasoning ->
                    if ((tracked[e.ref]?.thinking ?: false) == e.active) this else withTracked(e.ref) { it.copy(thinking = e.active) }
                is DockEvent.Tasks ->
                    withTracked(e.ref) {
                        if (e.total > 0) it.copy(done = e.done, total = e.total) else it.copy(done = 0, total = 0)
                    }
                is DockEvent.RequestOpened -> {
                    val before = tracked[e.ref]?.requests.orEmpty()
                    withTracked(e.ref) {
                        val next = it.copy(requests = it.requests + e.requestId)
                        if (before.isEmpty()) alerted(next) else next
                    }.let { if (before.isEmpty()) it.untuckFor(e.ref) else it }
                }
                is DockEvent.RequestSettled -> ifTracked(e.ref) { it.copy(requests = it.requests - e.requestId) }
                is DockEvent.RequestsRead -> readRequests(e)
                is DockEvent.StatusesRead -> readStatuses(e)
                is DockEvent.LastRunFailed ->
                    if (e.failed) {
                        val t = tracked[e.ref]
                        if (t != null && !t.busy && t.outcome == Outcome.Finished && e.ref != open) {
                            ifTracked(e.ref) { alerted(it).copy(outcome = Outcome.Failed) }.untuckFor(e.ref)
                        } else this
                    } else this
                is DockEvent.Deleted -> copy(pinned = pinned.filter { it != e.ref }, removed = removed.filter { it != e.ref }, known = known.remove(e.ref), tracked = tracked.filter { (r, t) -> r != e.ref && !(r.profileId == e.ref.profileId && t.parent == e.ref.sessionId) }, names = names - e.ref, open = open.takeIf { it != e.ref }, recent = recent - e.ref)
                is DockEvent.ProfileGone ->
                    copy(
                        removed = removed.filter { it.profileId != e.profile },
                        pinned = pinned.filter { it.profileId != e.profile },
                        online = online - e.profile,
                        servers = servers - e.profile,
                        known = known.filterKeys { it.profileId != e.profile }.toPersistentMap(),
                        tracked = tracked.filterKeys { it.profileId != e.profile },
                        names = names.filterKeys { it.profileId != e.profile },
                        open = open.takeIf { it?.profileId != e.profile },
                        recent = recent.filterKeys { it.profileId != e.profile },
                    )
                is DockEvent.ProfileOffline ->
                    copy(tracked = tracked.filterKeys { it.profileId != e.profile }, online = online - e.profile, recent = recent.filterKeys { it.profileId != e.profile })
                is DockEvent.Recent -> listed(e)
                is DockEvent.Nicknames ->
                    copy(names = names.filterKeys { it.profileId != e.profile } + e.names.mapKeys { ConversationRef(e.profile, it.key) })
                is DockEvent.NicknameSet -> copy(names = if (e.name == null) names - e.ref else names + (e.ref to e.name))
                is DockEvent.Opened -> {
                    val moved = copy(open = e.ref)
                    if (e.ref == null) {
                        moved
                    } else {
                        // Opening a conversation shows its sub-agents' results: their Finished marks clear too, their
                        // Failed and NeedsYou ones stay until that sub-agent itself is opened.
                        val parent = e.ref
                        val cleared = moved.copy(
                            tracked = moved.tracked.mapValues { (r, t) ->
                                if (r.profileId == parent.profileId && t.parent == parent.sessionId && t.outcome == Outcome.Finished) t.copy(outcome = Outcome.None) else t
                            },
                        )
                        cleared.withTracked(parent) { it }.seen(parent).reopened(parent)
                    }
                }
                is DockEvent.Seen -> seen(e.ref)
                is DockEvent.Pin ->
                    if (e.ref in pinned || pinned.size >= MAX_PINNED || e.ref !in tracked || rootOf(e.ref) != e.ref) this else copy(pinned = pinned + e.ref)
                is DockEvent.Unpin -> if (e.ref in pinned) copy(pinned = pinned - e.ref) else this
                is DockEvent.Reorder ->
                    if (e.from == e.to || e.from !in pinned || e.to !in pinned) {
                        this
                    } else {
                        val l = pinned.toMutableList()
                        l.remove(e.from)
                        l.add(pinned.indexOf(e.to), e.from)
                        copy(pinned = l)
                    }
                is DockEvent.PinsLoaded -> {
                    val loaded = e.sessions.map { ConversationRef(e.profile, it) }
                    copy(pinned = (loaded + pinned.filter { it.profileId != e.profile || it !in loaded }).distinct().take(MAX_PINNED), online = online + e.profile)
                }
                is DockEvent.ServerName ->
                    if (e.name.isNullOrBlank()) copy(servers = servers - e.profile) else if (servers[e.profile] == e.name) this else copy(servers = servers + (e.profile to e.name))
                is DockEvent.SetPlacement -> if (placement == e.placement) this else copy(placement = e.placement)
                is DockEvent.Remove -> remove(e.ref)
                is DockEvent.Restore -> if (e.ref in removed) copy(removed = removed - e.ref) else this
                is DockEvent.RemovedLoaded -> {
                    val loaded = e.sessions.map { ConversationRef(e.profile, it) }
                    val merged = (loaded + removed).distinct().takeLast(MAX_REMOVED)
                    if (merged == removed) this else copy(removed = merged)
                }
                is DockEvent.Link ->
                    copy(known = known.put(e.child, (known[e.child] ?: Triple(null, null, null)).copy(third = e.parent)), recent = recent - e.child)
                        .ifTracked(e.child) { it.copy(parent = e.parent) }
                DockEvent.Tuck -> copy(tucked = true)
                DockEvent.Untuck -> copy(tucked = false)
            }

        /** A server's conversation list: titles are remembered, root conversations join the recent pool. */
        private fun listed(e: DockEvent.Recent): Snapshot {
            var next = this
            val merged = LinkedHashMap(recent)
            for (row in e.rows) {
                val ref = ConversationRef(e.profile, row.session)
                next = next.remember(DockEvent.Meta(ref, row.title, null, row.parent)).ifTracked(ref) { it.copy(title = row.title ?: it.title) }
                if (row.parent == null) merged[ref] = maxOf(merged[ref] ?: Long.MIN_VALUE, row.lastActivityMs)
            }
            return next.copy(recent = bounded(merged))
        }

        /**
         * Activity heard live makes a conversation the most recent one: a run, a request or a task list that changed a
         * record, or the person opening it. Counted for the ring it belongs to (a sub-agent's parent).
         */
        fun noted(prev: Snapshot, e: DockEvent): Snapshot {
            val refs: Collection<ConversationRef> =
                when (e) {
                    is DockEvent.Opened -> listOfNotNull(e.ref)
                    is DockEvent.RunStarted, is DockEvent.RunEnded, is DockEvent.Reasoning, is DockEvent.Tasks,
                    is DockEvent.RequestOpened, is DockEvent.RequestSettled, is DockEvent.RequestsRead,
                    is DockEvent.StatusesRead, is DockEvent.LastRunFailed ->
                        tracked.filter { (r, t) -> prev.tracked[r] != t }.keys
                    else -> return this
                }
            if (refs.isEmpty()) return this
            var stamp = recent.values.maxOrNull() ?: 0L
            val next = LinkedHashMap(recent)
            for (root in refs.map { rootOf(it) }.distinct()) {
                next.remove(root)
                next[root] = ++stamp
            }
            return copy(recent = bounded(next))
        }

        private fun bounded(pool: Map<ConversationRef, Long>): Map<ConversationRef, Long> =
            if (pool.size <= MAX_RECENT) pool
            else pool.entries.sortedByDescending { it.value }.take(MAX_RECENT).associate { it.key to it.value }

        private fun readRequests(e: DockEvent.RequestsRead): Snapshot {
            var next = this
            for (t in tracked.values) {
                if (t.ref.profileId != e.profile) continue
                val read = e.bySession[t.ref.sessionId].orEmpty()
                if (read != t.requests) next = next.reconcileRequests(t.ref, read)
            }
            for ((session, ids) in e.bySession) {
                val ref = ConversationRef(e.profile, session)
                if (ids.isNotEmpty() && ref !in next.tracked) next = next.reconcileRequests(ref, ids)
            }
            return next
        }

        private fun reconcileRequests(ref: ConversationRef, read: Set<String>): Snapshot {
            val before = tracked[ref]?.requests.orEmpty()
            if (read.isEmpty()) return ifTracked(ref) { it.copy(requests = emptySet()) }
            return withTracked(ref) { t -> t.copy(requests = read).let { if (before.isEmpty()) alerted(it) else it } }
                .let { if (before.isEmpty()) it.untuckFor(ref) else it }
        }

        private fun readStatuses(e: DockEvent.StatusesRead): Snapshot {
            var next = this
            for (t in tracked.values) {
                if (t.ref.profileId != e.profile) continue
                val busyNow = t.ref.sessionId in e.busy
                if (busyNow && !t.busy) {
                    next = next.withTracked(t.ref) { it.copy(busy = true, outcome = Outcome.None) }
                } else if (!busyNow && t.busy) {
                    // The end was missed (the stream is volatile); the server says idle, so it finished.
                    next = next.ifTracked(t.ref) { ended(it, DockRunEnd.Succeeded) }
                }
            }
            for (session in e.busy) {
                val ref = ConversationRef(e.profile, session)
                if (ref !in next.tracked) next = next.withTracked(ref) { it.copy(busy = true) }
            }
            return next
        }

        private fun remember(e: DockEvent.Meta): Snapshot {
            val old = known[e.ref]
            val value = Triple(e.title ?: old?.first, e.agent ?: old?.second, e.parent ?: old?.third)
            if (old == value) return this
            val next = if (e.ref in known) known.remove(e.ref).put(e.ref, value) else known.put(e.ref, value)
            return copy(known = if (next.size > KNOWN_LIMIT) next.remove(next.keys.first()) else next)
        }

        private fun canRemove(ref: ConversationRef): Boolean {
            val on = open ?: return true
            return ref != on && ref != rootOf(on)
        }

        private fun remove(ref: ConversationRef): Snapshot {
            if (ref in removed || ref !in tracked || !canRemove(ref)) return this
            // A removed parent takes its sub-agents with it, so their own entries are subsumed.
            val kept = removed.filter { it == ref || rootOf(it) != ref }
            return copy(removed = (kept + ref).takeLast(MAX_REMOVED))
        }

        /** True while [ref], or the conversation it belongs to, is removed. */
        private fun hiddenRef(ref: ConversationRef): Boolean = ref in removed || rootOf(ref) in removed

        /** Opening a removed conversation brings it (and the ring it hangs under) back: the open one is always shown. */
        fun reopened(ref: ConversationRef): Snapshot {
            val back = listOf(ref, rootOf(ref)).filter { it in removed }
            return if (back.isEmpty()) this else copy(removed = removed - back.toSet())
        }

        /**
         * A new failure or request after the removal brings the conversation back: an alert is never hidden. Seen as a
         * rise in a record's pulse across one step.
         */
        fun unmuteAlerts(prev: Snapshot): Snapshot {
            if (removed.isEmpty()) return this
            val back = mutableSetOf<ConversationRef>()
            for ((ref, t) in tracked) if (t.pulse > (prev.tracked[ref]?.pulse ?: 0)) {
                back += ref
                back += rootOf(ref)
            }
            return if (back.any { it in removed }) copy(removed = removed.filter { it !in back }) else this
        }

        /**
         * The removed set only holds conversations that are still around: it drops one when its record leaves and it is
         * not a recent conversation either (a removed idle ring stays out while it would otherwise be offered).
         */
        fun pruneRemoved(prev: Snapshot, event: DockEvent): Snapshot {
            if (removed.isEmpty()) return this
            fun kids(of: Snapshot, r: ConversationRef) = of.tracked.values.filter { it.parent == r.sessionId && it.ref.profileId == r.profileId }
            val gone =
                removed.filter { r ->
                    // A removed parent stays while a hidden sub-agent is still live.
                    r !in tracked && r !in recent && kids(this, r).none { it.state() != DockState.Idle } &&
                        (r in prev.tracked || r in prev.recent || kids(prev, r).isNotEmpty() ||
                            (event is DockEvent.RequestsRead && r.profileId == event.profile))
                }
            return if (gone.isEmpty()) this else copy(removed = removed - gone.toSet())
        }

        private fun parentOf(ref: ConversationRef): ConversationRef? =
            (tracked[ref]?.parent ?: known[ref]?.third)?.let { ConversationRef(ref.profileId, it) }

        /** The top-level conversation a sub-agent belongs to (itself when it has no parent). */
        fun rootOf(ref: ConversationRef): ConversationRef {
            var at = ref
            repeat(MAX_DEPTH) { at = parentOf(at)?.takeIf { it != at } ?: return at }
            return at
        }

        /**
         * Drops Idle records that nothing needs, adds back the parent of every kept sub-agent (a parent idle between
         * steps stays while a child lives), fills free places with recent idle conversations, recomputes membership and
         * keeps the stable order.
         */
        fun settled(): Snapshot {
            val pinnedLive = pinned.filter { it.profileId in online && it !in removed }
            val kept = tracked.filter { (ref, t) -> t.state() != DockState.Idle || ref == open || ref in pinnedLive }
            var seq = nextSeq
            val needed = LinkedHashMap<ConversationRef, Tracked>()
            for (ref in kept.keys) {
                if (hiddenRef(ref)) continue
                val root = rootOf(ref)
                if (root !in kept && root !in needed) {
                    needed[root] = tracked[root] ?: Tracked(root, seq++, known[root]?.first, known[root]?.second)
                }
            }
            for (ref in pinnedLive) {
                if (ref !in kept && ref !in needed) needed[ref] = tracked[ref] ?: Tracked(ref, seq++, known[ref]?.first, known[ref]?.second)
            }
            val core = kept + needed
            val coreBase = copy(tracked = core, nextSeq = seq)
            val openRoot = open?.let { coreBase.rootOf(it) }
            val coreMembers =
                core.values.filter {
                    coreBase.rootOf(it.ref) == it.ref && it.ref !in removed &&
                        (coreBase.view(it).state != DockState.Idle || it.ref in pinnedLive || it.ref == openRoot)
                }
            // Free places take the most recent idle conversations; they never push a live, pinned or open ring out.
            val room = cap - coreMembers.size
            val coreIds = coreMembers.map { it.ref }.toSet()
            val fillers =
                if (room <= 0) {
                    emptyList()
                } else {
                    recent.entries.sortedByDescending { it.value }.map { it.key }
                        .filter { it !in coreIds && !hiddenRef(it) && rootOf(it) == it }
                        .take(room)
                        .map { ref -> tracked[ref] ?: Tracked(ref, seq++, known[ref]?.first, known[ref]?.second) }
                }
            val all = core + fillers.associateBy { it.ref }
            val base = copy(tracked = all, nextSeq = seq)
            val live = coreMembers.filter { base.view(it).state != DockState.Idle }
            val members = coreMembers + fillers
            val ids = members.map { it.ref }.toSet()
            // Newcomers go last: live, pinned and open ones by arrival, then idle fillers most recent first.
            val newcomers =
                coreMembers.filter { it.ref !in order }.sortedBy { it.seq }.map { it.ref } +
                    fillers.map { it.ref }.filter { it !in order }
            val nextOrder = order.filter { it in ids } + newcomers
            return base.copy(order = nextOrder, tucked = tucked && live.isNotEmpty())
        }

        /** A root with its sub-agents rolled in. */
        private class View(val root: Tracked, val kids: List<Tracked>, val state: DockState, val causedBy: ConversationRef?)

        private fun view(root: Tracked, includeRemoved: Boolean = false): View {
            val kids =
                tracked.values
                    .filter { it.ref != root.ref && rootOf(it.ref) == root.ref && (includeRemoved || it.ref !in removed) }
                    .sortedBy { it.seq }
            val own = root.state()
            val needs = kids.firstOrNull { it.state() == DockState.NeedsYou }
            val failed = kids.firstOrNull { it.state() == DockState.Failed }
            val working = kids.any { k -> k.state().let { it is DockState.WorkingWithTasks || it == DockState.Working || it == DockState.Thinking } }
            return when {
                own == DockState.NeedsYou -> View(root, kids, own, null)
                needs != null -> View(root, kids, DockState.NeedsYou, needs.ref)
                own == DockState.Failed -> View(root, kids, own, null)
                failed != null -> View(root, kids, DockState.Failed, failed.ref)
                (own == DockState.Idle || own == DockState.Finished) && working -> View(root, kids, DockState.Working, null)
                own == DockState.Idle && kids.any { it.state() != DockState.Idle } -> View(root, kids, DockState.Finished, null)
                else -> View(root, kids, own, null)
            }
        }

        private fun rank(state: DockState, onScreen: Boolean): Int {
            val base =
                when (state) {
                    DockState.NeedsYou -> 0
                    DockState.Failed -> 1
                    is DockState.WorkingWithTasks, DockState.Working, DockState.Thinking -> 2
                    DockState.Finished -> 3
                    DockState.Idle -> 4
                }
            return if (onScreen) minOf(base, 2) else base
        }

        fun toUi(): DockUi {
            val openRoot = open?.let { rootOf(it) }
            val pinnedMembers = pinned.filter { it in order }.mapNotNull { tracked[it] }
            val rest = order.filter { it !in pinned }.mapNotNull { tracked[it] }
            val members = (pinnedMembers + rest).map { view(it) }
            val shown =
                if (members.size <= cap) {
                    members
                } else {
                    val keep =
                        members.withIndex()
                            .sortedWith(compareBy({ if (it.value.root.ref in pinned) -1 else rank(it.value.state, it.value.root.ref == openRoot) }, { it.index }))
                            .take(cap).map { it.index }.toSet()
                    members.filterIndexed { i, _ -> i in keep }
                }
            val entries =
                shown.map { v ->
                    val kept =
                        if (v.kids.size <= childCap) {
                            v.kids
                        } else {
                            val keep =
                                v.kids.withIndex()
                                    .sortedWith(compareBy({ rank(it.value.state(), it.value.ref == open) }, { it.index }))
                                    .take(childCap).map { it.index }.toSet()
                            v.kids.filterIndexed { i, _ -> i in keep }
                        }
                    val r = v.root
                    DockEntry(
                        ref = r.ref,
                        title = r.title,
                        nickname = names[r.ref],
                        agent = r.agent,
                        state = v.state,
                        pulse = r.pulse + v.kids.sumOf { it.pulse },
                        ownState = r.state(),
                        causedBy = v.causedBy,
                        children =
                            kept.map {
                                DockChild(it.ref, it.title, it.agent, it.state(), it.pulse, it.requestRefs(), it.ref != open, servers[it.ref.profileId])
                            }.toImmutableList(),
                        childOverflow = v.kids.size - kept.size,
                        requests = r.requestRefs(),
                        removable = r.ref != openRoot,
                        pinned = r.ref in pinned,
                        serverName = servers[r.ref.profileId],
                    )
                }
            val live = members.count { it.state != DockState.Idle }
            val alert = members.any { it.state == DockState.NeedsYou || it.state == DockState.Failed }
            // Never hidden for lack of live conversations: quiet is the nub, nothing at all to offer is hidden.
            val presence =
                when {
                    members.isEmpty() -> DockPresence.Hidden
                    !tucked && (live >= 2 || alert) -> DockPresence.Out
                    else -> DockPresence.Nub
                }
            val hidden =
                removed.mapNotNull { ref ->
                    val t = tracked[ref] ?: return@mapNotNull null
                    val state = if (rootOf(ref) == ref) view(t, includeRemoved = true).state else t.state()
                    if (state == DockState.Idle) {
                        null
                    } else {
                        DockHidden(ref, t.title, names[ref], t.agent, state, rootOf(ref).takeIf { it != ref })
                    }
                }
            return DockUi(
                entries.toImmutableList(), presence, members.size - shown.size, openRoot, open.takeIf { it != openRoot },
                tucked, live, hidden.toImmutableList(),
                severalServers = members.filter { it.state != DockState.Idle }.map { it.root.ref.profileId }.toSet().size > 1,
                placement = placement,
            )
        }
    }

    public companion object {
        /** About seven rings fit the edge before they shrink past a thumb (notes: "more than about 7"). */
        public const val DEFAULT_CAP: Int = 7

        /** Sub-agents kept under one ring; alerts and working ones first, the rest are counted in `childOverflow`. */
        public const val DEFAULT_CHILD_CAP: Int = 4

        private const val KNOWN_LIMIT: Int = 256
        private const val MAX_DEPTH: Int = 8

        /** The most removed conversations kept (and saved) at once; the oldest is forgotten first. */
        public const val MAX_REMOVED: Int = 100

        /** The most pinned rings (the dock has about [DEFAULT_CAP] places; pins may not fill it with idle ones). */
        public const val MAX_PINNED: Int = 5

        /** The most recent conversations remembered to offer as idle rings; the least recent is forgotten first. */
        public const val MAX_RECENT: Int = 20

        public fun empty(cap: Int = DEFAULT_CAP, childCap: Int = DEFAULT_CHILD_CAP): DockModel {
            require(cap >= 1 && childCap >= 1) { "the dock needs at least one ring" }
            return DockModel(Snapshot(cap, childCap).settled())
        }
    }
}
