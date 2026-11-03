package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.search.presentation.ConversationSearchSource
import io.github.eslamasabry.oc.feature.search.presentation.RecentSearchHistory
import io.github.eslamasabry.oc.feature.search.presentation.RecentSearchStore
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversationPage
import io.github.eslamasabry.oc.feature.search.presentation.SearchMatch
import io.github.eslamasabry.oc.feature.search.presentation.SearchMessage
import io.github.eslamasabry.oc.feature.search.presentation.SearchServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageDay
import io.github.eslamasabry.oc.feature.usage.presentation.UsageModel
import io.github.eslamasabry.oc.feature.usage.presentation.UsageProject
import io.github.eslamasabry.oc.feature.usage.presentation.UsageQuery
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageSource
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTimeZone
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTools
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2SearchUsage
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2UsageStatistics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * Unit P12-SEARCH-USAGE: the Android side of Search conversations and Usage and cost. Everything here reads the live
 * connection graphs the preview runtime opened; nothing sends, changes or replays a request.
 */

/** Live servers, shared by both screens. */
@SingleIn(AppScope::class)
@Inject
class SearchUsageServers(registry: ConnectionRegistry, appScope: AppCoroutineScope, private val stores: PreviewStores) {
    private val scope = CoroutineScope(appScope.coroutineContext)

    /** Connected servers by id and saved name, in saved order. */
    val live: StateFlow<List<Pair<ProfileId, String>>> =
        registry.live
            .map { graphs -> graphs.map { (id, graph) -> id to graph.profile.name } }
            .stateIn(scope, SharingStarted.Eagerly, registry.live.value.map { (id, graph) -> id to graph.profile.name })

    val active: StateFlow<ProfileId?> = stores.profiles.activeId

    private val registryRef = registry

    fun gateway(profile: ProfileId): ServerGateway? = registryRef.live.value[profile]?.session?.server
}

private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

private const val PAGE = 50

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ConversationSearchSource>())
@Inject
class AndroidConversationSearch(private val shared: SearchUsageServers, appScope: AppCoroutineScope) :
    ConversationSearchSource {
    private val scope = CoroutineScope(appScope.coroutineContext)

    override val servers: StateFlow<ImmutableList<SearchServer>> =
        shared.live
            .map { list -> list.map { (id, name) -> SearchServer(id, name) }.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    override val active: StateFlow<ProfileId?> = shared.active

    override suspend fun conversations(
        profile: ProfileId,
        query: String,
        cursor: String?,
    ): Outcome<SearchConversationPage> {
        val gateway = shared.gateway(profile) ?: return stale()
        // The server's own title search across every project (OC2); a server that cannot is read page by page here.
        when (val global = Oc2SearchUsage.searchSessions(gateway, query, cursor, PAGE)) {
            null -> Unit
            is Outcome.Ok ->
                return SearchConversationPage(
                    global.value.items.map { row(it.session, it.projectDirectory) }.toImmutableList(),
                    global.value.nextCursor,
                    titlesMatched = query.isNotBlank(),
                    allProjects = true,
                ).ok()
            is Outcome.Failed ->
                if (global.failure !is Failure.NotFound && global.failure !is Failure.Unavailable) return global
        }
        return when (val page = gateway.sessionPage(cursor, PAGE)) {
            is Outcome.Failed -> page
            is Outcome.Ok ->
                SearchConversationPage(
                    page.value.items.filter { it.parentId == null }.map { row(it, it.directory) }.toImmutableList(),
                    page.value.nextCursor,
                    titlesMatched = false,
                    allProjects = false,
                ).ok()
        }
    }

    override suspend fun recentMessages(
        profile: ProfileId,
        session: SessionId,
        limit: Int,
    ): Outcome<ImmutableList<SearchMessage>> {
        val gateway = shared.gateway(profile) ?: return stale()
        return when (val page = gateway.messagePage(session, null, limit)) {
            is Outcome.Failed -> page
            is Outcome.Ok ->
                page.value.items
                    .map { message ->
                        SearchMessage(
                            message.info.id,
                            fromYou = message.info.role == Role.User,
                            text =
                                message.parts
                                    .filter { it.type == PartType.Text && !it.synthetic }
                                    .joinToString("\n") { it.text },
                        )
                    }
                    .filter { it.text.isNotBlank() }
                    .toImmutableList()
                    .ok()
        }
    }

    private fun row(session: Session, directory: String?): SearchConversation =
        SearchConversation(session.id, session.title, directory, session.time?.updatedMs)
}

/**
 * App-wide recent searches (`oc.search.recent`, a string list). Not per server: the words are the person's own.
 * Start-up restoration and every change share one lock ([RecentSearchHistory]), so no history is lost to a race.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<RecentSearchStore>())
@Inject
class AndroidRecentSearches(stores: PreviewStores, appScope: AppCoroutineScope, dispatchers: AppDispatchers) :
    RecentSearchStore by RecentSearchHistory(
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("recent-searches")),
        warm = { stores.warm() },
        load = { (stores.prefs.get(RECENT_KEY) as? PrefValue.StrList)?.value ?: emptyList() },
        save = { next ->
            stores.prefs.commit(stores.lanes.app) {
                if (next.isEmpty()) remove(RECENT_KEY) else putStringList(RECENT_KEY, next)
            }
        },
    )

private const val RECENT_KEY = "oc.search.recent"

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<UsageSource>())
@Inject
class AndroidUsageSource(private val shared: SearchUsageServers, appScope: AppCoroutineScope) : UsageSource {
    private val scope = CoroutineScope(appScope.coroutineContext)

    override val servers: StateFlow<ImmutableList<UsageServer>> =
        shared.live
            .map { list -> list.map { (id, name) -> UsageServer(id, name) }.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    override val active: StateFlow<ProfileId?> = shared.active

    override suspend fun projects(profile: ProfileId): Outcome<ImmutableList<UsageProject>> {
        val gateway = shared.gateway(profile) ?: return stale()
        // OC2: the current project first, then those of recent conversations (Oc2SearchUsage.projectChoices).
        val seen: List<Pair<String?, String?>> =
            when (val choices = Oc2SearchUsage.projectChoices(gateway, 100)) {
                is Outcome.Ok -> choices.value.map { it.id to it.directory }
                is Outcome.Failed -> return choices
                null ->
                    when (val page = gateway.sessionPage(null, 100)) {
                        is Outcome.Failed -> return page
                        is Outcome.Ok -> page.value.items.map { it.projectId?.raw to it.directory }
                    }
            }
        return seen
            .mapNotNull { (raw, directory) ->
                val id = raw?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                UsageProject(id, SearchMatch.projectName(directory) ?: id)
            }
            .distinctBy { it.id }
            .toImmutableList()
            .ok()
    }

    override suspend fun statistics(profile: ProfileId, query: UsageQuery): Outcome<UsageReport> {
        val gateway = shared.gateway(profile) ?: return stale()
        val result =
            Oc2SearchUsage.usageStatistics(gateway, query.toWire())
                ?: return Failure.Unavailable(FeatureId(Oc2SearchUsage.USAGE)).failed()
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok -> report(result.value).ok()
        }
    }

    private fun tokens(t: Oc2UsageStatistics.Tokens) = UsageTokens(t.input, t.output, t.reasoning, t.cacheRead, t.cacheWrite)

    private fun report(s: Oc2UsageStatistics): UsageReport =
        UsageReport(
            fromMs = s.from,
            toMs = s.to,
            sessions = s.sessions,
            subagents = s.subagents,
            prompts = s.prompts,
            steps = s.steps,
            activeDays = s.activeDays,
            streak = s.streak,
            cost = s.cost,
            tokens = tokens(s.tokens),
            tools = s.tools?.let { UsageTools(it.calls, it.succeeded, it.failed, it.unfinished) },
            models =
                s.models
                    .map { UsageModel(it.providerID, it.modelID, it.variant, it.steps, tokens(it.tokens), it.cost) }
                    .toImmutableList(),
            activity = s.activity.map { UsageDay(it.date, it.steps) }.toImmutableList(),
        )
}

/** The device's IANA time zone id; blank reads as unreadable (never replaced by UTC). */
@ContributesBinding(AppScope::class, binding = binding<UsageTimeZone>())
@Inject
class AndroidUsageTimeZone : UsageTimeZone {
    override fun id(): String? = java.util.TimeZone.getDefault().id?.takeIf { it.isNotBlank() }
}
