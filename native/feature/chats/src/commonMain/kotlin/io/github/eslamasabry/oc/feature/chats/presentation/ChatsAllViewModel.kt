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
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * All conversations (07 §5 B12; Dart `GlobalSessionsScreen`). Reads every connected server page by
 * page, newest first; search, project, Running and Archived filters; rename, archive (where the
 * server can), delete with an undo window, and a select mode that acts on several rows at once.
 * Every action goes to the row's own server.
 */
@AssistedInject
public class ChatsAllViewModel(
    @Assisted private val route: ChatsRoute.All,
    private val nav: AppNavigator,
    private val connections: ConversationServers,
    private val appScope: AppCoroutineScope,
    private val pins: ConversationPins = NoConversationPins(),
    private val context: io.github.eslamasabry.oc.engine.settings.context.ContextUsageHistory? =
        null,
    private val previews: ConversationPreviews = NoConversationPreviews(),
    /** Needs-you and unread come from the home feed: the server's session list does not carry them. */
    private val feed: ConversationsFeed = QuietConversationsFeed(),
    private val dispatchers: io.github.eslamasabry.oc.core.common.AppDispatchers? = null,
    private val importFiles: Set<io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportFiles> = emptySet(),
) : ViewModel(), ChatsAllActions, ChatsPinActions {
    private val model =
        MutableStateFlow(
            AllModel(
                running = route.filter == RUNNING,
                project = route.filter?.takeIf { it.startsWith("/") },
            )
        )
    private var servers: List<ConversationServer> = emptyList()
    private var generation = 0
    private val attemptedPins = mutableSetOf<Pair<ProfileId, SessionId>>()
    private val failedPins = mutableSetOf<Pair<ProfileId, SessionId>>()
    private val tombstones = mutableMapOf<ProfileId, MutableSet<SessionId>>()
    private val liveSessions =
        mutableMapOf<
            ProfileId,
            MutableMap<SessionId, io.github.eslamasabry.oc.core.model.Session>,
        >()
    private val liveRunning = mutableMapOf<ProfileId, MutableMap<SessionId, Boolean>>()
    /**
     * The servers whose live runs this page follows, per reload; a new value stops following the
     * old ones.
     */
    private val followed = MutableStateFlow<Pair<Int, List<ConversationServer>>>(0 to emptyList())

    public val state: StateFlow<AllConversationsUi> =
        model
            .map { current ->
                // ANDR-1: a host with pin storage shows the row's visible pin action too.
                val built =
                    AllConversationsRules.build(current)
                        .let { it.copy(nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()) }
                if (!pins.supported) built
                else
                    built.copy(
                        rows = built.rows.map { it.copy(pinAvailable = true) }.toImmutableList()
                    )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                AllConversationsRules.build(model.value),
            )

    init {
        viewModelScope.launch {
            combine(connections.servers, connections.waiting) { live, waiting -> live to waiting }
                .collect { (live, waiting) -> adopt(live, waiting) }
        }
        // Pins stored by another surface: re-read each live server's set.
        viewModelScope.launch {
            pins.changes.collect {
                val now = servers
                model.update { cur ->
                    cur.copy(
                        sources =
                            cur.sources.mapValues { (profile, src) ->
                                now.firstOrNull { it.profile == profile }
                                    ?.let { src.copy(pinned = pins.ids(it)) } ?: src
                            }
                    )
                }
            }
        }
        // Needs you / unread per row, from the home feed (the same facts the home list marks).
        viewModelScope.launch {
            feed.snapshot.collect { snap ->
                val next =
                    snap.items
                        .filter { it.status == ChatStatus.NeedsYou || it.finishedUnseen }
                        .associate {
                            AllConversationsRules.key(it.profileId, it.sessionId) to
                                RowAttention(
                                    needsYou = it.status == ChatStatus.NeedsYou,
                                    unread = it.finishedUnseen && it.status == ChatStatus.Idle,
                                )
                        }
                model.update { it.copy(attention = next) }
            }
        }
        viewModelScope.launch {
            followed.collectLatest { (run, list) ->
                list.map { server -> flow<Unit> { follow(server, run) } }.merge().collect()
            }
        }
    }

    /** Waiting servers whose kept rows are shown (cache-first); they become live sources when they connect. */
    private var waitingNow: List<ServerName> = emptyList()

    private fun adopt(live: ImmutableList<ConversationServer>, waiting: List<ServerName> = waitingNow) {
        val waitingOnly = waiting.filter { w -> live.none { it.profile == w.profile } }
        waitingNow = waitingOnly
        val before = servers.map { it.profile to it.gateway }
        val after = live.map { it.profile to it.gateway }
        live.forEach { server ->
            if (servers.firstOrNull { it.profile == server.profile }?.gateway !== server.gateway) {
                attemptedPins.removeAll { it.first == server.profile }
                failedPins.removeAll { it.first == server.profile }
            }
        }
        servers = live
        model.update { m ->
            val kept =
                waitingOnly.associate { w ->
                    w.profile to
                        (m.sources[w.profile]?.takeIf { it.preview }
                            ?: SourcePages(sessions = previews.read(w.profile), preview = true))
                }
            m.copy(
                sources = m.sources.filterKeys { id -> live.any { it.profile == id } } + kept,
                servers = live.map { ServerName(it.profile, it.name) } + waitingOnly,
                known = true,
                archiveCapable =
                    live
                        .filter {
                            it.operations != null && it.gateway.capabilities.value.sessionArchive
                        }
                        .map { it.profile }
                        .toSet(),
                importCapable =
                    if (dispatchers == null) emptySet()
                    else
                        live
                            .filter {
                                it.gateway.capabilities.value.sessionImportExport &&
                                    it.gateway is io.github.eslamasabry.oc.core.domain.SessionTransferImportGateway
                            }
                            .map { it.profile }
                            .toSet(),
                stealCapable =
                    live
                        .filter {
                            it.operations != null && it.gateway.capabilities.value.sessionSteal
                        }
                        .map { it.profile }
                        .toSet(),
                openFolders =
                    live
                        .mapNotNull { server ->
                            server.gateway.location.value.directory
                                ?.trim()
                                ?.trimEnd('/')
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { server.profile to it }
                        }
                        .toMap(),
            )
        }
        if (before != after) reload()
    }

    private fun reload() {
        val run = ++generation
        model.update {
            it.copy(
                sources =
                    it.sources.mapValues { (_, old) ->
                        old.copy(cursor = null, loaded = false, moreFailure = null)
                    },
                loadingMore = false,
            )
        }
        liveSessions.clear()
        liveRunning.clear()
        model.update { cur ->
            val next = cur.sources.toMutableMap()
            servers.forEach { server ->
                if (next[server.profile]?.sessions.isNullOrEmpty()) {
                    val saved = previews.read(server)
                    if (saved.isNotEmpty())
                        next[server.profile] = SourcePages(sessions = saved, preview = true)
                }
            }
            cur.copy(sources = next)
        }
        followed.value = run to servers
        servers.forEach { server -> viewModelScope.launch { firstPage(server, run) } }
    }

    /**
     * Running follows the server's live run events (Flutter rebuilds on controller changes). The
     * stream is volatile: after every (re)connect the statuses are read again, never replayed.
     */
    private suspend fun follow(server: ConversationServer, run: Int): Unit = coroutineScope {
        launch {
            server.missing.collect { ids ->
                if (run != generation) return@collect
                ids.forEach { id ->
                    if (id !in tombstones[server.profile].orEmpty())
                        removeSession(server.profile, id)
                }
            }
        }
        server.gateway.events.all.collect { envelope ->
            if (run != generation) return@collect
            when (val event = envelope.event) {
                is GatewayEvent.RunStarted -> recordRunning(server.profile, event.sessionId, true)
                is GatewayEvent.RunRetrying -> recordRunning(server.profile, event.sessionId, true)
                is GatewayEvent.RunEnded -> recordRunning(server.profile, event.sessionId, false)
                is GatewayEvent.SessionUpserted -> {
                    if (event.session.id !in tombstones[server.profile].orEmpty()) {
                        liveSessions.getOrPut(server.profile) { mutableMapOf() }[event.session.id] =
                            event.session
                        model.update { cur ->
                            val old = cur.sources[server.profile] ?: SourcePages()
                            cur.copy(
                                sources =
                                    cur.sources +
                                        (server.profile to
                                            old.copy(
                                                sessions =
                                                    merge(
                                                        server.profile,
                                                        old.sessions,
                                                        listOf(event.session),
                                                    )
                                            ))
                            )
                        }
                    }
                }
                is GatewayEvent.SessionDeleted -> removeSession(server.profile, event.sessionId)
                is GatewayEvent.SessionChanged -> {
                    val old =
                        model.value.sources[server.profile]?.sessions?.firstOrNull {
                            it.id == event.sessionId
                        }
                    if (old != null) {
                        val updated =
                            old.copy(
                                title = event.title ?: old.title,
                                directory =
                                    if (event.replacesLocation) event.directory else old.directory,
                            )
                        liveSessions.getOrPut(server.profile) { mutableMapOf() }[updated.id] =
                            updated
                        model.update { cur ->
                            val source = cur.sources[server.profile] ?: return@update cur
                            cur.copy(
                                sources =
                                    cur.sources +
                                        (server.profile to
                                            source.copy(
                                                sessions =
                                                    source.sessions.map {
                                                        if (it.id == updated.id) updated else it
                                                    }
                                            ))
                            )
                        }
                    }
                }
                GatewayEvent.Connected -> {
                    // Reconcile by refetch: run ends missed while disconnected never replay, so
                    // the old live overlay must not override the fresh status read.
                    liveRunning.remove(server.profile)
                    val statuses = server.gateway.sessionStatuses()
                    if (run != generation || statuses !is Outcome.Ok) return@collect
                    val running = statuses.value.filterValues(AllConversationsRules::isRunning).keys
                    setRunning(server.profile) { overlayRunning(server.profile, running) }
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

    private fun recordRunning(profile: ProfileId, id: SessionId, running: Boolean) {
        liveRunning.getOrPut(profile) { mutableMapOf() }[id] = running
        setRunning(profile) { if (running) it + id else it - id }
    }

    private fun overlayRunning(profile: ProfileId, read: Set<SessionId>): Set<SessionId> {
        val result = read.toMutableSet()
        liveRunning[profile]?.forEach { (id, busy) -> if (busy) result += id else result -= id }
        return result - tombstones[profile].orEmpty()
    }

    private fun merge(
        profile: ProfileId,
        old: List<io.github.eslamasabry.oc.core.model.Session>,
        fresh: List<io.github.eslamasabry.oc.core.model.Session>,
    ): List<io.github.eslamasabry.oc.core.model.Session> {
        val rows = old.associateByTo(linkedMapOf()) { it.id }
        fresh.forEach { rows[it.id] = it }
        liveSessions[profile]?.forEach { (id, row) -> rows[id] = row }
        tombstones[profile]?.forEach(rows::remove)
        return rows.values.toList()
    }

    private fun removeSession(profile: ProfileId, id: SessionId) {
        connections.forget(ConversationRef(profile, id))
        tombstones.getOrPut(profile) { mutableSetOf() } += id
        liveSessions[profile]?.remove(id)
        model.update { cur ->
            val old = cur.sources[profile] ?: return@update cur
            cur.copy(
                sources =
                    cur.sources +
                        (profile to
                            old.copy(
                                sessions = old.sessions.filterNot { it.id == id },
                                running = old.running - id,
                            ))
            )
        }
    }

    private suspend fun firstPage(server: ConversationServer, run: Int) {
        val savedScope = previews.scope(server)
        val page = server.gateway.sessionPage(limit = AllConversationsRules.PAGE)
        val statuses = server.gateway.sessionStatuses()
        if (run != generation) return
        model.update { cur ->
            val old = cur.sources[server.profile] ?: SourcePages()
            val older = if (old.preview) emptyList() else old.sessions
            val running =
                overlayRunning(
                    server.profile,
                    (statuses as? Outcome.Ok)
                        ?.value
                        ?.filterValues(AllConversationsRules::isRunning)
                        ?.keys ?: old.running,
                )
            val source =
                when (page) {
                    is Outcome.Ok ->
                        old.copy(
                            sessions = merge(server.profile, older, page.value.items),
                            cursor = page.value.nextCursor,
                            loaded = true,
                            failure = null,
                            running = running,
                            pinned = pins.ids(server),
                            preview = false,
                            contextUsage =
                                merge(server.profile, old.sessions, page.value.items)
                                    .mapNotNull { row ->
                                        context?.fraction(server.profile, row.id.raw)?.let {
                                            row.id to it
                                        }
                                    }
                                    .toMap(),
                        )
                    is Outcome.Failed ->
                        old.copy(loaded = true, failure = page.failure, running = running)
                }
            cur.copy(sources = cur.sources + (server.profile to source))
        }
        if (page is Outcome.Ok) fetchPinned(server, run)
        if (page is Outcome.Ok && savedScope != null && run == generation) {
            // Dart saves sortedSessions(): visible roots only, pinned first, then newest, so the
            // capped restart preview never shows subagent, archived or AI Team rows.
            val source = model.value.sources[server.profile]
            val pinned = source?.pinned.orEmpty()
            previews.save(
                server,
                savedScope,
                source
                    ?.sessions
                    .orEmpty()
                    .filter {
                        it.parentId == null &&
                            it.time?.archivedMs == null &&
                            !TeamFolders.isTeamFolder(it.directory)
                    }
                    .sortedWith(
                        compareByDescending<io.github.eslamasabry.oc.core.model.Session> {
                                it.id in pinned
                            }
                            .thenByDescending(AllConversationsRules::updatedAt)
                    ),
            )
        }
    }

    /** Reads each off-page pin once; a failed read keeps both the pin and its visible retry. */
    private suspend fun fetchPinned(server: ConversationServer, run: Int) {
        val pinned = pins.ids(server)
        for (id in pinned) {
            val key = server.profile to id
            if (
                model.value.sources[server.profile]?.sessions?.any { it.id == id } == true ||
                    !attemptedPins.add(key)
            )
                continue
            val read = server.gateway.session(id)
            if (run != generation) {
                attemptedPins.remove(key)
                return
            }
            when (read) {
                is Outcome.Ok -> {
                    failedPins.remove(key)
                    model.update { cur ->
                        val source = cur.sources[server.profile] ?: return@update cur
                        cur.copy(
                            sources =
                                cur.sources +
                                    (server.profile to
                                        source.copy(
                                            sessions =
                                                merge(
                                                    server.profile,
                                                    source.sessions,
                                                    listOf(read.value),
                                                )
                                        ))
                        )
                    }
                }
                is Outcome.Failed -> {
                    failedPins.add(key)
                    model.update { cur ->
                        val source = cur.sources[server.profile] ?: return@update cur
                        cur.copy(
                            sources =
                                cur.sources +
                                    (server.profile to source.copy(pinFailure = read.failure))
                        )
                    }
                }
            }
        }
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
                val page =
                    server.gateway.sessionPage(cursor = cursor, limit = AllConversationsRules.PAGE)
                if (run != generation) return@launch
                if (
                    page is Outcome.Failed &&
                        (page.failure is Failure.NotFound ||
                            (page.failure as? Failure.Rejected)?.status == 410)
                ) {
                    reload()
                    return@launch
                }
                model.update { cur ->
                    val old = cur.sources[server.profile] ?: SourcePages(loaded = true)
                    val next =
                        when (page) {
                            is Outcome.Ok ->
                                old.copy(
                                    sessions =
                                        merge(server.profile, old.sessions, page.value.items),
                                    cursor = page.value.nextCursor,
                                    moreFailure = null,
                                )
                            // The cursor stays, so Try again asks for the same page (visible Try
                            // again, B12).
                            is Outcome.Failed -> old.copy(moreFailure = page.failure)
                        }
                    val read = if (page is Outcome.Ok) 1 else 0
                    cur.copy(
                        sources = cur.sources + (server.profile to next),
                        loadedPages = cur.loadedPages + read,
                    )
                }
            }
            if (run == generation) model.update { it.copy(loadingMore = false) }
        }
    }

    override fun retry() {
        attemptedPins.removeAll(failedPins)
        failedPins.clear()
        model.update { cur ->
            cur.copy(sources = cur.sources.mapValues { (_, src) -> src.copy(pinFailure = null) })
        }
        reload()
    }

    override fun back() {
        commitDelete()
        nav.back()
    }

    override fun search(query: String): Unit = model.update { it.copy(query = query) }

    override fun toggleRunning(): Unit = model.update { it.copy(running = !it.running) }

    override fun toggleArchived(): Unit = model.update {
        it.copy(archived = !it.archived, selected = emptySet())
    }

    override fun chooseProject(directory: String): Unit = model.update {
        it.copy(project = if (it.project == directory) null else directory)
    }

    override fun clearFilters(): Unit = model.update {
        it.copy(query = "", running = false, project = null)
    }

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
        val target = ConversationRef(profile, session.id)
        // H4: an agent conversation the helper cannot resume asks before anything opens.
        val item = feed.snapshot.value.items.firstOrNull { it.ref == target }
        AgentResumeRules.ask(item, item?.let { ChatsState.row(it).agentName })?.let { ask ->
            model.update { it.copy(startNew = ask) }
            return
        }
        connections.prefetch(target)
        nav.openChat(target)
    }

    override fun confirmStartNew() {
        val ask = model.value.startNew ?: return
        model.update { it.copy(startNew = null) }
        nav.open(AgentResumeRules.route(ask))
    }

    override fun dismissStartNew() {
        model.update { it.copy(startNew = null) }
    }

    override fun continueHere(key: String) {
        val row = AllConversationsRules.find(model.value, key) ?: return
        if (row.first !in model.value.stealCapable) return
        model.update { it.copy(moving = MovingHere(key)) }
    }

    override fun cancelContinueHere() {
        if (model.value.moving?.working == true) return
        model.update { it.copy(moving = null) }
    }

    override fun confirmContinueHere() {
        val moving = model.value.moving ?: return
        if (moving.working) return
        val (profile, session) = AllConversationsRules.find(model.value, moving.key) ?: return
        val operations = serverOf(moving.key)?.operations
        if (operations == null) {
            model.update { it.copy(moving = moving.copy(failed = true)) }
            return
        }
        model.update { it.copy(moving = moving.copy(working = true, failed = false)) }
        viewModelScope.launch {
            when (val result = operations.stealSessionIntoWorkspace(session.id)) {
                is Outcome.Ok -> {
                    model.update { it.copy(moving = null) }
                    nav.openChat(ConversationRef(profile, result.value))
                }
                is Outcome.Failed ->
                    model.update { it.copy(moving = moving.copy(working = false, failed = true)) }
            }
        }
    }

    override fun rowAct(key: String, act: RowAct) {
        val m = model.value
        if (m.selecting) return
        val (profile, session) = AllConversationsRules.find(m, key) ?: return
        commitDelete()
        nav.openRowAct(ConversationRef(profile, session.id), act)
    }

    override fun startSelecting(key: String?): Unit = model.update {
        it.copy(selecting = true, selected = if (key != null) setOf(key) else emptySet())
    }

    override fun stopSelecting(): Unit = model.update {
        it.copy(selecting = false, selected = emptySet())
    }

    override fun rename(key: String) {
        val m = model.value
        val (_, session) = AllConversationsRules.find(m, key) ?: return
        val initial = SessionTitles.text(m.titles[key] ?: session.title)
        model.update { it.copy(renaming = RenameUi(key, initial)) }
    }

    override fun renameSelected() {
        val only =
            AllConversationsRules.build(model.value).rows.filter { it.selected }.singleOrNull()
                ?: return
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
                is Outcome.Ok ->
                    model.update { it.copy(titles = it.titles + (asked.key to wanted)) }
                is Outcome.Failed ->
                    model.update {
                        it.copy(problem = AllProblem(AllProblemKind.Rename, done.failure))
                    }
            }
        }
    }

    override fun togglePin(key: String) {
        val server = serverOf(key) ?: return
        val session = AllConversationsRules.find(model.value, key)?.second ?: return
        val wanted = session.id !in pins.ids(server)
        viewModelScope.launch {
            when (val result = pins.set(server, session.id, wanted)) {
                is Outcome.Ok -> {
                    model.update { cur ->
                        val old = cur.sources[server.profile] ?: return@update cur
                        cur.copy(
                            sources =
                                cur.sources +
                                    (server.profile to old.copy(pinned = pins.ids(server)))
                        )
                    }
                    // Launcher shortcuts follow the pins (a pinned conversation, one tap from the icon).
                    val cur = model.value
                    val pinnedNow = pins.ids(server)
                    pins.published(
                        server,
                        cur.sources[server.profile]
                            ?.sessions
                            .orEmpty()
                            .filter { it.id in pinnedNow }
                            .associate { s ->
                                s.id to
                                    (cur.titles["${server.profile.raw}/${s.id.raw}"] ?: s.title).orEmpty()
                            },
                    )
                }
                is Outcome.Failed ->
                    model.update {
                        it.copy(problem = AllProblem(AllProblemKind.Pin, result.failure))
                    }
            }
        }
    }

    override fun toggleSelectedPin() {
        val row =
            AllConversationsRules.build(model.value).rows.singleOrNull { it.selected } ?: return
        togglePin(row.key)
    }

    override fun delete(key: String): Unit = hide(listOf(key))

    override fun deleteSelected() {
        val keys =
            AllConversationsRules.build(model.value).rows.filter { it.selected }.map { it.key }
        if (keys.isNotEmpty()) hide(keys)
    }

    /**
     * One undo offer at a time: a new delete sends the earlier one first (B-KIT undo: a new offer
     * replaces).
     */
    private fun hide(keys: List<String>) {
        commitDelete()
        // Each row keeps its own session, so a reload inside the window cannot lose what the DELETE
        // needs.
        val doomed = keys.mapNotNull { k ->
            AllConversationsRules.find(model.value, k)?.let { (_, s) -> PendingDelete(k, s) }
        }
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
        // The row's server as it is now (a reconnect may have replaced its gateway); a server that
        // is gone has no
        // rows left to hide, so nothing stays hidden without its DELETE.
        val targets = doomed.mapNotNull { p ->
            serverOf(p.key)?.let { Triple(p.key, it, p.session.id) }
        }
        // The rows stay hidden while the deletes are sent; a refused one comes back with its
        // reason.
        model.update {
            it.copy(
                pending = emptyList(),
                hiddenSent = it.hiddenSent + targets.map { t -> t.first },
            )
        }
        // The app scope, so leaving the page inside the window still sends what the person deleted.
        appScope.launch {
            targets.forEach { (k, server, id) ->
                when (val done = server.gateway.deleteSession(id)) {
                    is Outcome.Ok -> {
                        removeSession(server.profile, id)
                        model.update { cur ->
                            val profile = server.profile
                            val src = cur.sources[profile]
                            cur.copy(
                                hiddenSent = cur.hiddenSent - k,
                                sources =
                                    if (src == null) cur.sources
                                    else
                                        cur.sources +
                                            (profile to
                                                src.copy(
                                                    sessions =
                                                        src.sessions.filterNot { it.id == id }
                                                )),
                            )
                        }
                    }
                    is Outcome.Failed ->
                        model.update {
                            it.copy(
                                hiddenSent = it.hiddenSent - k,
                                problem = AllProblem(AllProblemKind.Delete, done.failure),
                            )
                        }
                }
            }
        }
    }

    override fun archive(key: String): Unit = archiveAll(listOf(key))

    override fun archiveSelected() {
        val keys =
            AllConversationsRules.build(model.value)
                .rows
                .filter { it.selected && it.canArchive }
                .map { it.key }
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
                        model.update {
                            it.copy(problem = AllProblem(AllProblemKind.Archive, done.failure))
                        }
                }
            }
        }
    }

    override fun dismissProblem(): Unit = model.update { it.copy(problem = null) }

    override fun openServers() {
        nav.open(ServersRoute.Home)
    }

    private var importer: io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportEngine? = null

    override fun openImport() {
        if (model.value.importing != null) return
        val capable = model.value.servers.filter { it.profile in model.value.importCapable }
        when (capable.size) {
            0 -> Unit
            1 -> startImport(capable.single().profile)
            else -> model.update { it.copy(importing = ImportModel(choices = capable)) }
        }
    }

    override fun importPick(profile: ProfileId) {
        if (profile in model.value.importCapable) startImport(profile)
    }

    private fun startImport(profile: ProfileId) {
        val server = servers.firstOrNull { it.profile == profile } ?: return
        val work = dispatchers ?: return
        val engine =
            io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportEngine(
                server.gateway,
                { servers.firstOrNull { it.profile == profile }?.gateway === server.gateway },
                importFiles.firstOrNull(),
                work,
                io.github.eslamasabry.oc.core.model.ServerLocation(
                    server.gateway.location.value.directory,
                    server.gateway.location.value.workspace,
                ),
            )
        importer?.close()
        importer = engine
        model.update { it.copy(importing = ImportModel(profile = profile, session = engine.state.value)) }
        viewModelScope.launch {
            engine.state.takeWhile { !it.closed }.collect { value ->
                if (importer === engine)
                    model.update { m -> m.copy(importing = m.importing?.copy(session = value)) }
            }
        }
    }

    override fun importChoose() {
        importer?.let { engine -> viewModelScope.launch { engine.choose() } }
    }

    override fun importReview(text: String) {
        importer?.let { engine -> viewModelScope.launch { engine.review(text) } }
    }

    override fun importDestination(directory: String, workspace: String) {
        importer?.destination(directory, workspace)
    }

    override fun importSend() {
        importer?.let { engine -> viewModelScope.launch { engine.send() } }
    }

    override fun importOpen() {
        val engine = importer ?: return
        val profile = model.value.importing?.profile ?: return
        val session = engine.state.value.imported ?: return
        if (!engine.mayOpen()) return
        closeImport()
        nav.openChat(ConversationRef(profile, session))
    }

    override fun closeImport() {
        importer?.close()
        importer = null
        model.update { it.copy(importing = null) }
    }

    private fun serverOf(key: String): ConversationServer? {
        val profile: ProfileId = AllConversationsRules.profileOf(key)
        return servers.firstOrNull { it.profile == profile }
    }

    override fun onCleared() {
        importer?.close()
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
