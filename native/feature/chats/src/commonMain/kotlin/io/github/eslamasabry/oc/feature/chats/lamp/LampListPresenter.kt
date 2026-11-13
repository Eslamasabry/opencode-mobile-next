package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockListHidden
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListRow
import io.github.eslamasabry.oc.engine.attention.dock.DockListState
import io.github.eslamasabry.oc.engine.attention.dock.DockListSubAgent
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestWords
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import kotlinx.collections.immutable.toImmutableList

/** What the presenter reads besides the list: the person's choices and the holds in flight. */
public data class LampInputs(
    val nowMs: Long,
    /** Permission replies in their undo window, by request id (the shared hold). */
    val held: Map<String, HomeHeldReply> = emptyMap(),
    val stops: Map<ConversationRef, StopPhase> = emptyMap(),
    /** Blocks the person folded (true) or opened (false); a block not in the map follows its children. */
    val folds: Map<ConversationRef, Boolean> = emptyMap(),
    /** Conversations whose sub-agents were seen running at the same time while the panel lived. */
    val overlapSeen: Set<ConversationRef> = emptySet(),
    val editing: Boolean = false,
    val place: LampPlace = LampPlace(),
    val notice: LampNotice? = null,
)

/**
 * The list's brain as a pure function: [DockListModel] (plus which conversation is open, which the model already says)
 * to [LampUi]. No clock of its own, no copy, no Compose.
 */
public object LampListPresenter {
    /** Above this many sub-agents the five most urgent show, the rest are counted. */
    public const val CHILD_CAP: Int = 5

    public fun present(model: DockListModel, input: LampInputs): LampUi {
        val open = model.rows.firstOrNull { it.isOpen }
        val openTitle = open?.name
        val shown = model.rows.filterNot { it.isOpen }
        val pinnedOrder = shown.filter { it.pinned }.map { it.ref }

        val entries = ArrayList<Pair<LampGroupKind, LampRowUi>>()
        for (row in shown) entries += groupOf(row) to rowOf(row, input, pinnedOrder)
        // The open conversation's working or waiting sub-agents stand as rows of their own.
        if (open != null) {
            for (child in open.subAgents) {
                val group =
                    when (child.state) {
                        DockListState.NeedsYou -> LampGroupKind.NeedsYou
                        DockListState.Working -> LampGroupKind.Working
                        else -> continue
                    }
                entries += group to subAgentRowOf(child, openTitle, input)
            }
        }

        val groups =
            LampGroupKind.entries.mapNotNull { kind ->
                val rows = entries.filter { it.first == kind }.map { it.second }
                if (rows.isEmpty()) null else LampGroupUi(kind, sorted(kind, rows).toImmutableList())
            }
        val scrollTo =
            groups.flatMap { it.rows }.firstNotNullOfOrNull { r -> r.tree?.children?.firstOrNull { it.scrollIntoView }?.key }
        val flat = groups.flatMap { it.rows }
        return LampUi(
            openTitle = openTitle,
            groups = groups.toImmutableList(),
            hidden = model.hidden.takeIf { it.isNotEmpty() }?.let { LampHiddenUi(it.map(::hiddenOf).toImmutableList()) },
            more = model.more,
            allCount = model.rows.size + model.more,
            empty = groups.isEmpty(),
            editing = input.editing,
            face = faceOf(flat),
            scrollToKey = scrollTo,
            place = input.place,
            notice = input.notice,
        )
    }

    /** A pinned row is "Pinned" unless it needs you or failed: urgency is never buried in a calm group. */
    private fun groupOf(row: DockListRow): LampGroupKind =
        when {
            row.state == DockListState.NeedsYou -> LampGroupKind.NeedsYou
            row.state == DockListState.Failed -> LampGroupKind.Failed
            row.pinned -> LampGroupKind.Pinned
            row.state == DockListState.Working -> LampGroupKind.Working
            row.state == DockListState.Done -> LampGroupKind.Done
            else -> LampGroupKind.Recent
        }

    /** Pins keep the person's order, the urgent groups the dock's; what is done or idle is newest first. */
    private fun sorted(kind: LampGroupKind, rows: List<LampRowUi>): List<LampRowUi> =
        if (kind == LampGroupKind.Done || kind == LampGroupKind.Recent) {
            rows.withIndex().sortedWith(compareByDescending<IndexedValue<LampRowUi>> { it.value.lastActivityMs ?: Long.MIN_VALUE }.thenBy { it.index }).map { it.value }
        } else {
            rows
        }

    private fun keyOf(ref: ConversationRef): String = "${ref.profileId.raw}/${ref.sessionId.raw}"

    private fun markOf(state: DockListState): LampState =
        when (state) {
            DockListState.NeedsYou -> LampState.NeedsYou
            DockListState.Failed -> LampState.Failed
            DockListState.Working -> LampState.Working
            DockListState.Done -> LampState.Done
            DockListState.Idle -> LampState.Idle
        }

    private fun requestOf(words: DockRequestWords?, owner: ConversationRef, held: Map<String, HomeHeldReply>): LampRequestUi? {
        words ?: return null
        val answer =
            held[words.id]?.let { LampAnswerUi.Held(it) }
                ?: if (words.kind == DockRequestKind.Permission) LampAnswerUi.AllowDeny else LampAnswerUi.Answer
        return LampRequestUi(words.kind, words.id, words.action, words.target, words.text, owner, answer)
    }

    private fun stopOf(ref: ConversationRef, working: Boolean, stops: Map<ConversationRef, StopPhase>): LampStopUi? =
        when (stops[ref]) {
            StopPhase.Holding -> LampStopUi.Holding
            StopPhase.Stopped -> LampStopUi.Stopped
            null -> if (working) LampStopUi.Button else null
        }

    private fun rowOf(row: DockListRow, input: LampInputs, pinnedOrder: List<ConversationRef>): LampRowUi {
        val group = groupOf(row)
        val age = row.lastActivityMs?.let { RelativeAge.of(it, input.nowMs) }
        val tree = treeOf(row, input)
        val childRaised = row.causedBy?.let { cause -> row.subAgents.any { it.ref == cause } } == true
        val index = pinnedOrder.indexOf(row.ref)
        return LampRowUi(
            key = keyOf(row.ref),
            ref = row.ref,
            title = row.name,
            state = markOf(row.state),
            stateLine = stateLineOf(row, group, age, tree),
            newestLine = row.newestLine,
            request = if (childRaised) null else requestOf(row.request, row.causedBy ?: row.ref, input.held),
            lastActivityMs = row.lastActivityMs,
            age = age,
            server = row.serverName,
            pinned = row.pinned,
            subAgentOf = null,
            tree = tree,
            stop = stopOf(row.ref, row.state == DockListState.Working, input.stops),
            edit =
                if (!input.editing) null
                else LampRowEditUi(row.pinned, canMoveUp = index > 0, canMoveDown = index in 0 until pinnedOrder.lastIndex, canHide = true, isSubAgent = false),
        )
    }

    private fun subAgentRowOf(child: DockListSubAgent, parentTitle: String?, input: LampInputs): LampRowUi {
        val state = markOf(child.state)
        val progress = child.progress
        return LampRowUi(
            key = keyOf(child.ref),
            ref = child.ref,
            title = child.name,
            state = state,
            stateLine =
                when {
                    child.state != DockListState.Working -> null
                    progress != null -> LampStateLine.TasksDone(progress.done, progress.total)
                    child.thinking -> LampStateLine.Thinking
                    else -> null
                },
            newestLine = null,
            request = if (child.state == DockListState.NeedsYou) requestOf(child.request, child.ref, input.held) else null,
            lastActivityMs = null,
            age = null,
            server = null,
            pinned = false,
            subAgentOf = parentTitle,
            tree = null,
            stop = stopOf(child.ref, child.state == DockListState.Working, input.stops),
            edit = if (!input.editing) null else LampRowEditUi(false, false, false, canHide = false, isSubAgent = true),
        )
    }

    private fun stateLineOf(row: DockListRow, group: LampGroupKind, age: RelativeAge?, tree: LampTreeUi?): LampStateLine? {
        val waitingChild = tree?.children?.firstOrNull { it.state == LampChildState.NeedsYou }
        if (row.state == DockListState.NeedsYou && row.causedBy != null && tree != null && waitingChild != null) {
            val waiting = tree.children.count { it.state == LampChildState.NeedsYou }
            val working = tree.children.count { it.state == LampChildState.Working || it.state == LampChildState.Thinking }
            return when {
                tree.kind == LampTreeKind.Sequence -> LampStateLine.PausedAtStep(waitingChild.step, tree.count)
                working > 0 -> LampStateLine.Waiting(waiting, working)
                else -> LampStateLine.WaitingToFinish
            }
        }
        val progress = row.progress
        return when (row.state) {
            DockListState.NeedsYou, DockListState.Failed -> age?.let { LampStateLine.For(it) }
            DockListState.Working ->
                when {
                    progress != null -> LampStateLine.TasksDone(progress.done, progress.total)
                    row.thinking -> LampStateLine.Thinking
                    group == LampGroupKind.Pinned -> LampStateLine.Working
                    else -> null
                }
            DockListState.Done -> if (group == LampGroupKind.Done) null else LampStateLine.Done
            DockListState.Idle -> null
        }
    }

    private fun childState(c: DockListSubAgent): LampChildState =
        when (c.state) {
            DockListState.NeedsYou -> LampChildState.NeedsYou
            DockListState.Failed -> LampChildState.Failed
            DockListState.Working -> if (c.thinking && c.progress == null) LampChildState.Thinking else LampChildState.Working
            DockListState.Done -> LampChildState.Done
            DockListState.Idle -> LampChildState.Idle
        }

    private fun urgency(s: LampChildState): Int =
        when (s) {
            LampChildState.NeedsYou -> 0
            LampChildState.Failed -> 1
            LampChildState.Working, LampChildState.Thinking -> 2
            LampChildState.Done -> 3
            LampChildState.Idle -> 4
        }

    private fun treeOf(row: DockListRow, input: LampInputs): LampTreeUi? {
        if (row.subAgents.isEmpty() && row.subAgentsMore == 0) return null
        val mapped =
            row.subAgents.mapIndexed { i, c ->
                val state = childState(c)
                LampChildUi(
                    key = keyOf(c.ref),
                    ref = c.ref,
                    name = c.name,
                    state = state,
                    fact = c.progress?.let { LampFact(it.done, it.total) },
                    request = if (c.state == DockListState.NeedsYou) requestOf(c.request, c.ref, input.held) else null,
                    paused = state == LampChildState.NeedsYou,
                    step = i + 1,
                    scrollIntoView = false,
                    stop = stopOf(c.ref, c.state == DockListState.Working, input.stops),
                )
            }
        // More than the cap: the most urgent stay, still in the order they started.
        val kept =
            if (mapped.size <= CHILD_CAP) mapped
            else {
                val keep = mapped.withIndex().sortedWith(compareBy({ urgency(it.value.state) }, { it.index })).take(CHILD_CAP).map { it.index }.toSet()
                mapped.filterIndexed { i, _ -> i in keep }
            }
        val firstNeeding = kept.firstOrNull { it.state == LampChildState.NeedsYou }
        val children = kept.map { if (it === firstNeeding) it.copy(scrollIntoView = true) else it }
        val allFinished = mapped.all { it.state == LampChildState.Done || it.state == LampChildState.Idle } && row.subAgentsMore == 0
        val anyNeeds = mapped.any { it.state == LampChildState.NeedsYou }
        val folded = if (anyNeeds) false else input.folds[row.ref] ?: allFinished
        return LampTreeUi(
            kind = LampTrees.kindOf(row.subAgents, row.ref in input.overlapSeen),
            children = children.toImmutableList(),
            more = mapped.size - kept.size + row.subAgentsMore,
            moreInside = 0,
            count = mapped.size + row.subAgentsMore,
            folded = folded,
        )
    }

    private fun hiddenOf(h: DockListHidden): LampHiddenItemUi = LampHiddenItemUi(h.ref, h.name, markOf(h.state))

    private fun faceOf(rows: List<LampRowUi>): LampFaceUi {
        val needs = rows.count { it.state == LampState.NeedsYou }
        val failed = rows.count { it.state == LampState.Failed }
        val running = rows.filter { it.state == LampState.Working }
        val runMarks =
            running.map { r ->
                val p = r.stateLine as? LampStateLine.TasksDone
                LampFaceMark(LampMarkKind.Working, p?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total })
            }.withIndex().sortedWith(compareByDescending<IndexedValue<LampFaceMark>> { it.value.progress ?: -1f }.thenBy { it.index }).map { it.value }
        val marks =
            (List(needs) { LampFaceMark(LampMarkKind.NeedsYou, null) } + List(failed) { LampFaceMark(LampMarkKind.Failed, null) } + runMarks)
                .take(MAX_MARKS)
        return LampFaceUi(needs + failed, marks.toImmutableList(), needs, failed, running.size)
    }

    public const val MAX_MARKS: Int = 3
}

/** Parallel or sequence: what the data can tell (see the slice report). */
public object LampTrees {
    private fun live(c: DockListSubAgent) = c.state == DockListState.Working || c.state == DockListState.NeedsYou

    /** True when two or more sub-agents are running or waiting at the same moment: they are parallel, for good. */
    public fun overlaps(children: List<DockListSubAgent>): Boolean = children.count(::live) >= 2

    /**
     * Sequence only when nothing says otherwise: two or more sub-agents, exactly one running or waiting now, every
     * finished one listed before it (they started earlier), and no overlap seen while the panel lived. Anything else,
     * including no evidence at all, is parallel: a trunk that claims no order.
     */
    public fun kindOf(children: List<DockListSubAgent>, overlapSeen: Boolean): LampTreeKind {
        if (overlapSeen || children.size < 2) return LampTreeKind.Parallel
        val liveIndexes = children.indices.filter { live(children[it]) }
        if (liveIndexes.size != 1) return LampTreeKind.Parallel
        val at = liveIndexes.single()
        val finishedAfter = children.drop(at + 1).any { it.state == DockListState.Done || it.state == DockListState.Failed || it.state == DockListState.Idle }
        return if (finishedAfter) LampTreeKind.Parallel else LampTreeKind.Sequence
    }
}
