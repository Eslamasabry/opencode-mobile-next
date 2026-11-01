package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Ordering and projects (Dart `compareChatFeedItems`, `MergedChatFeed.projectSummaries`). Pure. */
public object ChatFeedRules {
    private fun rank(status: ChatStatus): Int =
        when (status) {
            ChatStatus.NeedsYou -> 0
            ChatStatus.Running -> 1
            else -> 2
        }

    /** Needs you, then running, then the rest; each newest first; ties by identity (B-NAV-030). */
    public val order: Comparator<ChatFeedItem> =
        compareBy<ChatFeedItem> { rank(it.status) }.thenByDescending { it.lastActivityMs }.thenBy { it.identity }

    /** Real projects (never other folders, never sub-agent rows), most recently active first. */
    public fun projects(items: List<ChatFeedItem>): List<ProjectSummary> =
        items
            .filter { !it.inOtherFolder && !it.isSubagent && it.directory != null }
            .groupBy { it.directory!! }
            .map { (dir, rows) ->
                ProjectSummary(
                    directory = dir,
                    name = rows.first().projectName,
                    chatCount = rows.size,
                    runningCount = rows.count { it.status == ChatStatus.Running },
                    lastActivityMs = rows.maxOf { it.lastActivityMs },
                )
            }
            .sortedWith(compareByDescending<ProjectSummary> { it.lastActivityMs }.thenBy { it.directory })
}

/**
 * The merged Conversations feed (17 §1.6; Dart `MergedChatFeed`). Slice 2 builds it with the OpenCode source only;
 * Paseo and side sources join in slice 3 without changing this class. It follows its sources from [scope] until
 * [close]; it never closes a source (sources may be shared, B-STATE-029). The [snapshot] revision moves only when
 * visible content changes (B-CHAT-014).
 */
public class ChatFeed(private val sources: List<ChatFeedSource>, scope: CoroutineScope) {
    private var closed = false
    private val mutable = MutableStateFlow(merge(sources.map { it.current() }, previous = null))
    public val snapshot: StateFlow<ChatFeedSnapshot> = mutable.asStateFlow()

    private val follow =
        scope.launch {
            if (sources.isEmpty()) return@launch
            combine(sources.map { it.changes }) { it.toList() }.collect { states ->
                if (!closed) mutable.value = merge(states, mutable.value)
            }
        }

    /** Asks every source for a fresh list; a closed feed refuses (B-STATE-029). Partial failures stay per source. */
    public suspend fun refresh(): Outcome<Unit> {
        if (closed) return Failure.Refused(RefusalReason.ProfileClosing).failed()
        sources.forEach { it.refresh() }
        return Unit.ok()
    }

    /** Stops following the sources and refuses later reads. Shared sources are left running. */
    public fun close() {
        closed = true
        follow.cancel()
    }

    private fun merge(states: List<FeedSourceState>, previous: ChatFeedSnapshot?): ChatFeedSnapshot {
        val items = states.flatMap { it.items }.distinctBy { it.identity }.sortedWith(ChatFeedRules.order).toImmutableList()
        val incomplete =
            sources.zip(states)
                .filter { (_, state) -> state.failed || !state.complete }
                .map { (source, _) -> source.label ?: source.id }
                .toImmutableList()
        val next =
            ChatFeedSnapshot(
                items = items,
                revision = previous?.revision ?: 0,
                loading = items.isEmpty() && states.any { it.loading },
                complete = incomplete.isEmpty(),
                incompleteSources = incomplete,
                acrossProjects = states.all { it.acrossProjects },
            )
        return if (previous == null || next == previous) next else next.copy(revision = previous.revision + 1)
    }
}
