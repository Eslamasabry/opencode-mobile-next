package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What one source holds now. Reading it never starts a request. */
public data class SourceFeed(
    /** All rows, sub-agents included; the merged feed filters. */
    val items: ImmutableList<ChatFeedItem> = persistentListOf(),
    val acrossProjects: Boolean = true,
    val loading: Boolean = false,
    val complete: Boolean = true,
    /** A live read completed since start. Until then the source's saved rows paint in its place. */
    val loaded: Boolean = true,
    val projects: ImmutableList<ProjectSummary> = persistentListOf(),
)

/** One source of conversations: the connected OpenCode, one Paseo folder, or another server (04 §12.10). */
public interface ChatFeedSource {
    public val feed: StateFlow<SourceFeed>

    /** A fresh read. Partial failure is a `Failed` outcome; the source keeps what it had. */
    public suspend fun refresh(): Outcome<Unit>
}

/**
 * A source in one owner context. [id] is a stable routing key (`opencode`, `paseo:<directory>`, `profile:<id>`);
 * [label] is app-authored. [saved] are this source's saved rows (from [SavedChatFeed]) painted until it has [SourceFeed.loaded].
 */
public class NamedChatFeedSource(
    public val id: String,
    public val label: String,
    public val source: ChatFeedSource,
    saved: List<ChatFeedItem> = emptyList(),
) {
    /** Saved rows never claim to be running and are never "Done": that is only known live. */
    public val saved: ImmutableList<ChatFeedItem> =
        saved.map { it.copy(status = ChatStatus.Idle, finishedUnseen = false, sourceId = id) }.toImmutableList()
}

public data class ChatFeedRoute(val sourceId: String, val sessionId: String, val directory: String, val agentId: AgentId)

/** One source as the merged feed sees it. */
public data class SourceCheck(
    val items: ImmutableList<ChatFeedItem>,
    val acrossProjects: Boolean,
    val loading: Boolean,
    val complete: Boolean,
    /** Rows shown are the saved ones (the live read has not completed yet). */
    val showingSaved: Boolean,
)

/** The `paseo:<directory>` sources: at most [MAX] folders, in the order wanted. */
public object PaseoFeedSources {
    public const val MAX: Int = 4

    public fun sourceId(directory: String): String = "paseo:$directory"

    public fun wanted(desired: List<String>): ImmutableList<String> = desired.distinct().take(MAX).toImmutableList()
}

/**
 * The one Conversations list (AGENTS-BACKENDS-05; port of `MergedChatFeed`, `lib/domain/merged_chat_feed.dart`).
 * A read-only projection: sources keep their transport and storage. Refreshes coalesce, failures stay partial per
 * source, a source's saved rows paint until its live read lands (then live rows replace them, source by source),
 * and [dispose] fences late results without touching shared children.
 *
 * [items] (all top-level rows) and [revision] change only when visible content changes (B-CHAT-014).
 */
public class MergedChatFeed(
    sources: List<NamedChatFeedSource>,
    scope: CoroutineScope,
    public val defaultSourceId: String? = null,
    private val refreshTimeout: Duration = 45.seconds,
) {
    public val sources: ImmutableList<NamedChatFeedSource> = sources.toImmutableList()

    private val failed = MutableStateFlow<PersistentSet<String>>(persistentSetOf())
    private val refreshing = MutableStateFlow(false)
    private var disposed = false
    private var inFlight: Deferred<Unit>? = null
    private val refreshScope = scope

    private val mutableItems = MutableStateFlow<ImmutableList<ChatFeedItem>>(persistentListOf())
    private val mutableRevision = MutableStateFlow(0L)

    /** Top-level rows in list order, keyed by [ChatFeedItem.identity]. */
    public val items: StateFlow<ImmutableList<ChatFeedItem>> = mutableItems.asStateFlow()

    /** Bumped once per visible change; a notification that changes nothing never bumps it. */
    public val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    init {
        val ids = mutableSetOf<String>()
        for (s in this.sources) require(s.id.isNotEmpty() && ids.add(s.id)) { "Feed source IDs must be unique." }
        require(defaultSourceId == null || defaultSourceId in ids) { "The default feed source must be named." }
        require(refreshTimeout > Duration.ZERO) { "Refresh timeout must be positive." }
        recompute()
    }

    private val collector = scope.launch {
        merge(*this@MergedChatFeed.sources.map { it.source.feed }.toTypedArray(), failed, refreshing).collect { recompute() }
    }

    private fun recompute() {
        if (disposed) return
        val next = snapshot(ChatFeedFilter.All).items
        if (next != mutableItems.value) {
            mutableItems.value = next
            mutableRevision.value += 1
        }
    }

    public fun sourceChecks(): ImmutableMap<String, SourceCheck> = sources.associate { it.id to check(it) }.toImmutableMap()

    private fun check(named: NamedChatFeedSource): SourceCheck {
        if (disposed) return SourceCheck(persistentListOf(), acrossProjects = false, loading = false, complete = false, showingSaved = false)
        val feed = named.source.feed.value
        val saved = !feed.loaded
        val rows = if (saved) named.saved else feed.items
        return SourceCheck(
            items = rows.map { it.copy(sourceId = named.id, sourceLabel = named.label) }.toImmutableList(),
            acrossProjects = feed.acrossProjects,
            loading = feed.loading,
            complete = feed.complete && !saved && named.id !in failed.value,
            showingSaved = saved,
        )
    }

    public val acrossProjects: Boolean
        get() = !disposed && sources.isNotEmpty() && sourceChecks().values.all { it.acrossProjects }

    /** The rows for [filter]; pure, reads held state, starts no request. */
    public fun snapshot(filter: ChatFeedFilter = ChatFeedFilter.All): ChatFeedSnapshot {
        if (disposed) return ChatFeedSnapshot(persistentListOf(), acrossProjects = false, complete = false)
        val checks = sources.map { it to check(it) }
        val rows = checks.flatMap { (_, c) -> c.items }.filter(filter::matches).sortedWith(ChatFeedOrder)
        return ChatFeedSnapshot(
            items = rows.toImmutableList(),
            acrossProjects = checks.isNotEmpty() && checks.all { it.second.acrossProjects },
            complete = checks.isNotEmpty() && checks.all { it.second.complete },
            incompleteSources = checks.filter { !it.second.complete }.map { it.first.label }.toImmutableList(),
            // Placeholders only while no source knows a single row: a filter matching nothing keeps its empty state.
            loading = checks.all { it.second.items.isEmpty() } && (refreshing.value || checks.any { it.second.loading }),
        )
    }

    /** Real projects of every source (a failing source never removes another's), most recent first. */
    public fun projectSummaries(): ImmutableList<ProjectSummary> {
        if (disposed) return persistentListOf()
        return sources.flatMap { named ->
            named.source.feed.value.projects.map { it.copy(sourceId = named.id, sourceLabel = named.label) }
        }.filterNot { FeedFolders.isTemporary(it.directory) }
            .sortedWith(
                compareByDescending<ProjectSummary> { it.lastActivityMillis ?: 0L }
                    .thenBy { "${it.sourceId}:${it.directory}" },
            ).toImmutableList()
    }

    /** Refreshes every source; concurrent calls share one read. Never fails: failures stay per source. */
    public suspend fun refresh() {
        if (disposed) return
        val running = inFlight?.takeIf { it.isActive } ?: refreshScope.async { refreshAll() }.also { inFlight = it }
        running.await()
    }

    private suspend fun refreshAll() {
        refreshing.value = true
        try {
            coroutineScope {
                sources.map { named ->
                    async {
                        val result = withTimeoutOrNull(refreshTimeout) {
                            attempt({ Failure.Unexpected() }) { named.source.refresh() }
                        }
                        val ok = result is Outcome.Ok && result.value is Outcome.Ok
                        if (!disposed) failed.value = if (ok) failed.value.remove(named.id) else failed.value.add(named.id)
                    }
                }.awaitAll()
            }
        } finally {
            refreshing.value = false
            inFlight = null
        }
    }

    public fun routeFor(item: ChatFeedItem): Outcome<ChatFeedRoute> {
        val route = ChatFeedRoute(item.sourceId.orEmpty(), item.sessionId, item.directory, item.agentId)
        return when (val source = sourceFor(route)) {
            is Outcome.Ok -> route.ok()
            is Outcome.Failed -> source
        }
    }

    /**
     * The original named source of a held row. Never searches other sources for a colliding id or substitutes
     * another session: anything but exactly one match is a stale route.
     */
    public fun sourceFor(route: ChatFeedRoute): Outcome<ChatFeedSource> {
        val named = named(route.sourceId) ?: return stale()
        val matches = check(named).items.count {
            it.sessionId == route.sessionId && it.directory == route.directory && it.agentId == route.agentId
        }
        return if (matches == 1) named.source.ok() else stale()
    }

    /** The source a new chat starts in: the default, or the only one; several without a default must be chosen. */
    public fun startSource(sourceId: String? = null): Outcome<NamedChatFeedSource> {
        val id = sourceId ?: defaultSourceId ?: sources.singleOrNull()?.id ?: return stale()
        return named(id)?.ok() ?: stale()
    }

    private fun named(id: String): NamedChatFeedSource? = if (disposed) null else sources.firstOrNull { it.id == id }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    /** Stops observing; later reads are empty and incomplete. Children are shared and never disposed here. */
    public fun dispose() {
        if (disposed) return
        disposed = true
        collector.cancel()
    }
}
