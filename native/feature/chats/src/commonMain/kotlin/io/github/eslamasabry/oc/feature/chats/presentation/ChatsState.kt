package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedFilter
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedRules
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import kotlinx.collections.immutable.toImmutableList

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
    ): ChatsUi {
        val roots = feed.items.filter { !it.isSubagent }
        val projects = ChatFeedRules.projects(roots)
        val recent = projects.take(RECENT_PROJECTS).toMutableList()
        projects.firstOrNull { it.directory == filters.projectDirectory }?.let { if (it !in recent) recent += it }
        val chips = recent.map { ProjectChipUi(it.directory, it.name, it.directory == filters.projectDirectory) }

        val visible =
            ChatFeedFilter(projectDirectory = filters.projectDirectory, running = filters.running, needsYou = filters.needsYou)
                .apply(roots)
                .filter { matches(it, filters.query) }
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
            today = today.map(::row).toImmutableList(),
            earlier = earlier.map(::row).toImmutableList(),
            needsYou = roots.count { it.status == ChatStatus.NeedsYou },
            running = roots.count { it.status == ChatStatus.Running },
            projects = chips.toImmutableList(),
            filters = filters,
            brief = briefUi(brief, roots),
            incomplete = feed.incompleteSources,
            settingsAvailable = settingsAvailable,
        )
    }

    private fun matches(item: ChatFeedItem, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val title = (SessionTitles.present(item.title) as? RowTitle.Text)?.text.orEmpty()
        return title.contains(q, ignoreCase = true) || item.projectName.contains(q, ignoreCase = true)
    }

    public fun row(item: ChatFeedItem): ConversationRowUi =
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
        )

    /** Brief rows are the feed rows of the sessions it names (same profile); nothing when it has nothing to say. */
    private fun briefUi(brief: ReturnBrief, roots: List<ChatFeedItem>): BriefUi? {
        if (brief.isEmpty && !brief.unknown) return null
        val bySession = roots.associateBy { it.sessionId }
        val rows =
            (brief.requests.map { it.session.id } + brief.unreviewed.map { it.session.id })
                .mapNotNull { bySession[it] }
                .map { row(it).let { r -> if (r.status == RowStatus.None) r.copy(status = RowStatus.Done) else r } }
        return BriefUi(rows.toImmutableList(), more = brief.hiddenUnreviewed + brief.hiddenRequests, unknown = brief.unknown)
    }
}
