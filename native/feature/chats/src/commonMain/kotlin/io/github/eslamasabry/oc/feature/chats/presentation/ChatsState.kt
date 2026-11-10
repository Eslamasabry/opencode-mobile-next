package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersonaId
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

    /** A1: how many conversations "Recent" shows; the rest are one tap away on All conversations. */
    public const val RECENT_ROWS: Int = 5

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
                    otherFolders = filters.otherFolders,
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
        val briefUi = if (filters.identities == null) briefUi(brief, roots, nowMs) else null
        // A row the return brief already shows is not repeated under it.
        val briefKeys = briefUi?.rows.orEmpty().mapTo(hashSetOf()) { it.key }
        val listed = visible.filterNot { it.identity in briefKeys }
        // A1: everything working or waiting on the person, uncapped, in the feed's order (needs you, then running,
        // newest first); then the five latest of the rest.
        val (live, rest) = listed.partition { it.status == ChatStatus.NeedsYou || it.status == ChatStatus.Running }
        val rightNow = live.sortedWith(ChatFeedRules.order).map { row(it, nowMs) }
        val latest =
            rest.sortedWith(compareByDescending<ChatFeedItem> { it.lastActivityMs }.thenBy { it.identity })
                .take(RECENT_ROWS)
                .map { row(it, nowMs) }
        return ChatsUi(
            phase = phase,
            rightNow = rightNow.toImmutableList(),
            recent = latest.toImmutableList(),
            showProjects = filters.projectDirectory == null && visible.distinctBy { it.projectName }.size > 1,
            runningShown = visible.count { it.status == ChatStatus.Running },
            showAgents = agentsDiffer(rightNow + latest + briefUi?.rows.orEmpty()),
            needsYou = roots.count { it.status == ChatStatus.NeedsYou },
            running = roots.count { it.status == ChatStatus.Running },
            projects = chips.toImmutableList(),
            filters = filters,
            brief = briefUi,
            incomplete = feed.incompleteSources,
            settingsAvailable = settingsAvailable,
            projectChoices = projects.toImmutableList(),
            projectNeedsYou =
                roots
                    .filter { it.status == ChatStatus.NeedsYou }
                    .mapNotNull { it.directory }
                    .toImmutableSet(),
            allCount = roots.size,
            otherFoldersCount = roots.count { it.inOtherFolder },
            otherFoldersSelected = filters.otherFolders,
            inbox =
                feed.items
                    .filter { it.status == ChatStatus.NeedsYou }
                    .sortedWith(
                        compareBy<ChatFeedItem> { it.lastActivityMs }.thenBy { it.identity }
                    )
                    .map(::row)
                    .toImmutableList(),
            finished =
                roots
                    .filter { it.finishedUnseen && it.status == ChatStatus.Idle }
                    .sortedByDescending { it.lastActivityMs }
                    .map(::row)
                    .toImmutableList(),
        )
    }

    /** Whether [rows] come from more than one agent, told apart by mark and name (P-021). */
    public fun agentsDiffer(rows: List<ConversationRowUi>): Boolean =
        rows.distinctBy { it.agentMark to it.agentName }.size > 1

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
            updatedAtMs = item.lastActivityMs.takeIf { it > 0 },
            canOpen = item.canReopen,
            agentMark = AgentPersona.of(item.agentId).markSlug,
            agentName = agentName(item),
            server = item.sourceLabel,
            directory = item.directory?.takeIf { it.isNotBlank() },
            status =
                when (item.status) {
                    ChatStatus.NeedsYou -> RowStatus.NeedsYou
                    ChatStatus.Running -> RowStatus.Running
                    ChatStatus.Failed -> RowStatus.Failed
                    ChatStatus.Idle -> if (item.finishedUnseen) RowStatus.Done else RowStatus.None
                },
            ago = RelativeAge.of(item.lastActivityMs, nowMs),
            running = item.running,
            permission = item.permission,
            preview = item.preview.takeIf { it.isNotBlank() }?.let(HomePreviewRules::oneLine),
        )

    /**
     * E04-S05, Flutter `_agentLabel`: a known agent by its maker's name (whatever the connection calls it), another
     * by the name its connection gave; null when it has none, and the screen says "Another agent" in the person's
     * language. Never the conversation's title.
     */
    private fun agentName(item: ChatFeedItem): String? =
        brandName(AgentPersona.of(item.agentId).id) ?: item.agentLabel?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * E04-S05: the rows that can be archived: every top-level conversation, OpenCode or an agent's (Paseo), whose
     * server says it archives ([capable]: `sessionArchive` with operations). Codex says it does not, so its rows
     * are never offered Archive. Sub-agent runs belong to their parent and are never archived alone.
     */
    public fun archivable(
        items: List<ChatFeedItem>,
        capable: Set<io.github.eslamasabry.oc.core.common.ids.ProfileId>,
    ): kotlinx.collections.immutable.ImmutableSet<String> =
        items.filter { !it.isSubagent && it.profileId in capable }.mapTo(hashSetOf()) { it.identity }.toImmutableSet()

    /** Product names are proper nouns, not copy: the same in every language. */
    private fun brandName(id: AgentPersonaId): String? =
        when (id) {
            AgentPersonaId.ClaudeCode -> "Claude Code"
            AgentPersonaId.Codex -> "Codex"
            AgentPersonaId.Gemini -> "Gemini"
            AgentPersonaId.Antigravity -> "Antigravity"
            AgentPersonaId.Kilo -> "Kilo Code"
            AgentPersonaId.Cline -> "Cline"
            AgentPersonaId.OpenCode -> "OpenCode"
            AgentPersonaId.Other -> null
        }

    /**
     * Brief rows are the feed rows of the sessions it names (same profile); nothing when it has
     * nothing to say.
     */
    private fun briefUi(brief: ReturnBrief, roots: List<ChatFeedItem>, nowMs: Long): BriefUi? {
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
                    // With its time, like the Today rows under it (P-021).
                    row(it, nowMs).let { r ->
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
