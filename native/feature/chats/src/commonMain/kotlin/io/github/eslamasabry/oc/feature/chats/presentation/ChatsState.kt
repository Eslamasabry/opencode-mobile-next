package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedFilter
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedRules
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** Pure: feed + brief + filters → [ChatsUi]. The ViewModel only combines flows into this. */
public object ChatsState {
    /** Recent projects shown as chips (the selected project is always among them). */
    public const val RECENT_PROJECTS: Int = 4

    public fun build(
        feed: ChatFeedSnapshot,
        brief: ReturnBrief,
        filters: ChatsFilters,
        startOfTodayMs: Long,
        settingsAvailable: Boolean,
        nowMs: Long = 0L,
    ): ChatsUi {
        val roots = feed.items.filter { !it.isSubagent }
        val projects = ChatFeedRules.projects(roots)
        val recent = projects.take(RECENT_PROJECTS).toMutableList()
        projects
            .firstOrNull { it.directory == filters.projectDirectory }
            ?.let { if (it !in recent) recent += it }
        val chips = recent.map {
            ProjectChipUi(it.directory, it.name, it.directory == filters.projectDirectory)
        }

        val visible =
            ChatFeedFilter(
                    projectDirectory = filters.projectDirectory,
                    running = filters.running,
                    needsYou = filters.needsYou,
                )
                .apply(roots)
                .filter {
                    matches(it, filters.query) &&
                        (filters.identities == null || it.identity in filters.identities)
                }
        val phase =
            when {
                roots.isEmpty() && feed.loading -> ChatsPhase.Opening
                roots.isEmpty() -> ChatsPhase.Empty
                visible.isEmpty() -> ChatsPhase.NoMatch
                else -> ChatsPhase.List
            }
        val (today, earlier) = visible.partition { it.lastActivityMs >= startOfTodayMs }
        return ChatsUi(
            phase = phase,
            today = today.map { row(it, nowMs) }.toImmutableList(),
            earlier = earlier.map { row(it, nowMs) }.toImmutableList(),
            needsYou = roots.count { it.status == ChatStatus.NeedsYou },
            running = roots.count { it.status == ChatStatus.Running },
            projects = chips.toImmutableList(),
            filters = filters,
            brief = if (filters.identities == null) briefUi(brief, roots) else null,
            incomplete = feed.incompleteSources,
            settingsAvailable = settingsAvailable,
        )
    }

    private fun matches(item: ChatFeedItem, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val title = (SessionTitles.present(item.title) as? RowTitle.Text)?.text.orEmpty()
        return title.contains(q, ignoreCase = true) ||
            item.projectName.contains(q, ignoreCase = true)
    }

    public fun row(item: ChatFeedItem, nowMs: Long = 0L): ConversationRowUi =
        ConversationRowUi(
            key = item.identity,
            ref = item.ref,
            title = SessionTitles.present(item.title),
            project = item.projectName,
            status =
                when (item.status) {
                    ChatStatus.NeedsYou -> RowStatus.NeedsYou
                    ChatStatus.Running -> RowStatus.Running
                    ChatStatus.Failed -> RowStatus.Failed
                    ChatStatus.Idle -> if (item.finishedUnseen) RowStatus.Done else RowStatus.None
                },
            ago = RelativeAge.of(item.lastActivityMs, nowMs),
        )

    /**
     * Brief rows are the feed rows of the sessions it names (same profile); nothing when it has
     * nothing to say.
     */
    private fun briefUi(brief: ReturnBrief, roots: List<ChatFeedItem>): BriefUi? {
        if (brief.isEmpty && !brief.unknown && brief.hiddenUnreviewed + brief.hiddenRequests == 0)
            return null
        // A session id is local to a source and folder. Unknown scope or an ambiguous match fails
        // closed.
        fun match(session: Session): ChatFeedItem? {
            val scope = brief.scope ?: return null
            return roots.singleOrNull {
                it.locationKey == scope.location &&
                    it.profileId == scope.profileId &&
                    it.sourceId == scope.sourceId &&
                    it.sessionId == session.id &&
                    it.directory == session.directory
            }
        }
        val requests = brief.requests.filter { match(it.session) != null }
        val runs = brief.unreviewed.filter { match(it.session) != null }
        val rows =
            (requests.map { it.session } + runs.map { it.session })
                .mapNotNull(::match)
                .distinctBy { it.identity }
                .map {
                    row(it).let { r ->
                        if (r.status == RowStatus.None) r.copy(status = RowStatus.Done) else r
                    }
                }
        val overflowKeys =
            (brief.overflowRequests.map { it.session } + brief.overflowRuns.map { it.session })
                .mapNotNull(::match)
                .map { it.identity }
                .toImmutableSet()
        return BriefUi(
            rows = rows.toImmutableList(),
            more = brief.hiddenUnreviewed + brief.hiddenRequests,
            unknown =
                brief.unknown ||
                    requests.size != brief.requests.size ||
                    runs.size != brief.unreviewed.size,
            acknowledgement =
                brief.copy(
                    requests = requests.toImmutableList(),
                    unreviewed = runs.toImmutableList(),
                    hiddenRequests = 0,
                    hiddenUnreviewed = 0,
                    overflowRuns = persistentListOf(),
                    overflowRequests = persistentListOf(),
                ),
            overflowKeys = overflowKeys,
        )
    }
}
