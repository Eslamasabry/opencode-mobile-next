package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * All conversations (07 §5 B12; Dart `GlobalSessionsScreen`). Reads every connected server page by page, newest
 * first; search, project, Running and Archived filters; rename, archive (where the server can), delete with an
 * undo window, and a select mode that acts on several rows at once. Every action goes to the row's own server.
 */
@AssistedInject
public class ChatsAllViewModel(
    @Assisted private val route: ChatsRoute.All,
    private val nav: AppNavigator,
    private val connections: ConversationServers,
    private val appScope: AppCoroutineScope,
) : ViewModel(), ChatsAllActions {
    private val model =
        MutableStateFlow(
            AllModel(
                running = route.filter == RUNNING,
                project = route.filter?.takeIf { it.startsWith("/") },
            )
        )
    private var servers: List<ConversationServer> = emptyList()
    private var generation = 0
    /** The servers whose live runs this page follows, per reload; a new value stops following the old ones. */
    private val followed = MutableStateFlow<Pair<Int, List<ConversationServer>>>(0 to emptyList())

    public val state: StateFlow<AllConversationsUi> =
        model
            .map { AllConversationsRules.build(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AllConversationsRules.build(model.value))

    init {
        viewModelScope.launch { connections.servers.collect { adopt(it) } }
        viewModelScope.launch {
            followed.collectLatest { (run, list) -> list.map { server -> flow<Unit> { follow(server, run) } }.merge().collect() }
        }
    }

    private fun adopt(live: ImmutableList<ConversationServer>) {
        val before = servers.map { it.profile to it.gateway }
        val after = live.map { it.profile to it.gateway }
        servers = live
        model.update { m ->
            m.copy(
                servers = live.map { ServerName(it.profile, it.name) },
                known = true,
                archiveCapable =
                    live.filter { it.operations != null && it.gateway.capabilities.value.sessionArchive }
                        .map { it.profile }
                        .toSet(),
            )
        }
        if (before != after) reload()
    }

    private fun reload() {
        val run = ++generation
        model.update { it.copy(sources = emptyMap(), loadingMore = false) }
        followed.value = run to servers
        servers.forEach { server -> viewModelScope.launch { firstPage(server, run) } }
    }

    /**
     * Running follows the server's live run events (Flutter rebuilds on controller changes). The stream is volatile:
     * after every (re)connect the statuses are read again, never replayed.
     */
    private suspend fun follow(server: ConversationServer, run: Int) {
        server.gateway.events.all.collect { envelope ->
            if (run != generation) return@collect
            when (val event = envelope.event) {
                is GatewayEvent.RunStarted -> setRunning(server.profile) { it + event.sessionId }
                is GatewayEvent.RunRetrying -> setRunning(server.profile) { it + event.sessionId }
                is GatewayEvent.RunEnded -> setRunning(server.profile) { it - event.sessionId }
                GatewayEvent.Connected -> {
                    val statuses = server.gateway.sessionStatuses()
                    if (run != generation || statuses !is Outcome.Ok) return@collect
                    val running = statuses.value.filterValues(AllConversationsRules::isRunning).keys
                    setRunning(server.profile) { running }
                }
                else -> Unit
            }
        }
    }

    private fun setRunning(profile: ProfileId, change: (Set<SessionId>) -> Set<SessionId>) {
        model.update { cur ->
            val src = cur.sources[profile] ?: return@update cur
            cur.copy(sources = cur.sources + (profile to src.copy(running = change(src.running))))
        }
    }

    private suspend fun firstPage(server: ConversationServer, run: Int) {
        val page = server.gateway.sessionPage(limit = AllConversationsRules.PAGE)
        val statuses = server.gateway.sessionStatuses()
        if (run != generation) return
        val running =
            (statuses as? Outcome.Ok)?.value?.filterValues(AllConversationsRules::isRunning)?.keys.orEmpty()
        val source =
            when (page) {
                is Outcome.Ok -> SourcePages(page.value.items, page.value.nextCursor, loaded = true, running = running)
                is Outcome.Failed -> SourcePages(loaded = true, failure = page.failure)
            }
        model.update { it.copy(sources = it.sources + (server.profile to source)) }
    }

    override fun loadMore() {
        val m = model.value
        if (m.loadingMore) return
        val run = generation
        val waiting = servers.filter { m.sources[it.profile]?.cursor != null }
        if (waiting.isEmpty()) return
        model.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            waiting.forEach { server ->
                val cursor = model.value.sources[server.profile]?.cursor ?: return@forEach
                val page = server.gateway.sessionPage(cursor = cursor, limit = AllConversationsRules.PAGE)
                if (run != generation) return@launch
                model.update { cur ->
                    val old = cur.sources[server.profile] ?: SourcePages(loaded = true)
                    val next =
                        when (page) {
                            is Outcome.Ok ->
                                old.copy(
                                    sessions = AllConversationsRules.append(old.sessions, page.value.items),
                                    cursor = page.value.nextCursor,
                                    moreFailure = null,
                                )
                            // The cursor stays, so Try again asks for the same page (visible Try again, B12).
                            is Outcome.Failed -> old.copy(moreFailure = page.failure)
                        }
                    val read = if (page is Outcome.Ok) 1 else 0
                    cur.copy(sources = cur.sources + (server.profile to next), loadedPages = cur.loadedPages + read)
                }
            }
            if (run == generation) model.update { it.copy(loadingMore = false) }
        }
    }

    override fun retry(): Unit = reload()

    override fun back() {
        commitDelete()
        nav.back()
    }

    override fun search(query: String): Unit = model.update { it.copy(query = query) }

    override fun toggleRunning(): Unit = model.update { it.copy(running = !it.running) }

    override fun toggleArchived(): Unit = model.update { it.copy(archived = !it.archived, selected = emptySet()) }

    override fun chooseProject(directory: String): Unit = model.update {
        it.copy(project = if (it.project == directory) null else directory)
    }

    override fun clearFilters(): Unit = model.update { it.copy(query = "", running = false, project = null) }

    override fun tapRow(key: String) {
        val m = model.value
        if (m.selecting) {
            model.update {
                it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
            }
            return
        }
        val (profile, session) = AllConversationsRules.find(m, key) ?: return
        commitDelete()
        nav.openChat(ConversationRef(profile, session.id))
    }

    override fun startSelecting(key: String?): Unit = model.update {
        it.copy(selecting = true, selected = if (key != null) setOf(key) else emptySet())
    }

    override fun stopSelecting(): Unit = model.update { it.copy(selecting = false, selected = emptySet()) }

    override fun rename(key: String) {
        val m = model.value
        val (_, session) = AllConversationsRules.find(m, key) ?: return
        val initial = SessionTitles.text(m.titles[key] ?: session.title)
        model.update { it.copy(renaming = RenameUi(key, initial)) }
    }

    override fun renameSelected() {
        val only = AllConversationsRules.build(model.value).rows.filter { it.selected }.singleOrNull() ?: return
        rename(only.key)
    }

    override fun finishRename(title: String?) {
        val asked = model.value.renaming ?: return
        model.update { it.copy(renaming = null) }
        val wanted = title?.trim().orEmpty()
        if (wanted.isEmpty() || wanted == asked.initial) return
        val server = serverOf(asked.key) ?: return
        val session = AllConversationsRules.find(model.value, asked.key)?.second ?: return
        viewModelScope.launch {
            when (val done = server.gateway.renameSession(session.id, wanted)) {
                is Outcome.Ok -> model.update { it.copy(titles = it.titles + (asked.key to wanted)) }
                is Outcome.Failed ->
                    model.update { it.copy(problem = AllProblem(AllProblemKind.Rename, done.failure)) }
            }
        }
    }

    override fun delete(key: String): Unit = hide(listOf(key))

    override fun deleteSelected() {
        val keys = AllConversationsRules.build(model.value).rows.filter { it.selected }.map { it.key }
        if (keys.isNotEmpty()) hide(keys)
    }

    /** One undo offer at a time: a new delete sends the earlier one first (B-KIT undo: a new offer replaces). */
    private fun hide(keys: List<String>) {
        commitDelete()
        // Each row keeps its own session, so a reload inside the window cannot lose what the DELETE needs.
        val doomed =
            keys.mapNotNull { k -> AllConversationsRules.find(model.value, k)?.let { (_, s) -> PendingDelete(k, s) } }
        if (doomed.isEmpty()) return
        model.update {
            it.copy(
                pending = doomed,
                undoId = it.undoId + 1,
                selecting = false,
                selected = emptySet(),
                problem = null,
            )
        }
    }

    override fun undoDelete(): Unit = model.update { it.copy(pending = emptyList()) }

    override fun commitDelete() {
        val doomed = model.value.pending
        if (doomed.isEmpty()) return
        // The row's server as it is now (a reconnect may have replaced its gateway); a server that is gone has no
        // rows left to hide, so nothing stays hidden without its DELETE.
        val targets = doomed.mapNotNull { p -> serverOf(p.key)?.let { Triple(p.key, it, p.session.id) } }
        // The rows stay hidden while the deletes are sent; a refused one comes back with its reason.
        model.update { it.copy(pending = emptyList(), hiddenSent = it.hiddenSent + targets.map { t -> t.first }) }
        // The app scope, so leaving the page inside the window still sends what the person deleted.
        appScope.launch {
            targets.forEach { (k, server, id) ->
                when (val done = server.gateway.deleteSession(id)) {
                    is Outcome.Ok ->
                        model.update { cur ->
                            val profile = server.profile
                            val src = cur.sources[profile]
                            cur.copy(
                                hiddenSent = cur.hiddenSent - k,
                                sources =
                                    if (src == null) cur.sources
                                    else cur.sources + (profile to src.copy(sessions = src.sessions.filterNot { it.id == id })),
                            )
                        }
                    is Outcome.Failed ->
                        model.update {
                            it.copy(hiddenSent = it.hiddenSent - k, problem = AllProblem(AllProblemKind.Delete, done.failure))
                        }
                }
            }
        }
    }

    override fun archive(key: String): Unit = archiveAll(listOf(key))

    override fun archiveSelected() {
        val keys = AllConversationsRules.build(model.value).rows.filter { it.selected && it.canArchive }.map { it.key }
        if (keys.isNotEmpty()) archiveAll(keys)
    }

    private fun archiveAll(keys: List<String>) {
        model.update { it.copy(selecting = false, selected = emptySet(), problem = null) }
        viewModelScope.launch {
            keys.forEach { k ->
                val server = serverOf(k) ?: return@forEach
                val ops = server.operations ?: return@forEach
                val session = AllConversationsRules.find(model.value, k)?.second ?: return@forEach
                when (val done = ops.archiveSession(session.id)) {
                    is Outcome.Ok -> model.update { it.copy(archivedNow = it.archivedNow + k) }
                    is Outcome.Failed ->
                        model.update { it.copy(problem = AllProblem(AllProblemKind.Archive, done.failure)) }
                }
            }
        }
    }

    override fun dismissProblem(): Unit = model.update { it.copy(problem = null) }

    override fun openServers() {
        nav.open(ServersRoute.Home)
    }

    private fun serverOf(key: String): ConversationServer? {
        val profile: ProfileId = AllConversationsRules.profileOf(key)
        return servers.firstOrNull { it.profile == profile }
    }

    override fun onCleared() {
        commitDelete()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatsRoute.All): ChatsAllViewModel
    }

    private companion object {
        const val RUNNING = "running"
    }
}
