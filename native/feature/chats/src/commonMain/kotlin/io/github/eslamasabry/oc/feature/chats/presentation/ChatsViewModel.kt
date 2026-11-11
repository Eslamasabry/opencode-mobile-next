package io.github.eslamasabry.oc.feature.chats.presentation

import kotlinx.collections.immutable.toImmutableMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.FeedDay
import io.github.eslamasabry.oc.engine.agents.feed.OPENCODE_AGENT_ID
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.MonitorInboxRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.CapabilityServerDoor
import io.github.eslamasabry.oc.ui.routes.CapabilityServerSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Conversations home (07 §5 B01, B15). Plain screen ViewModel through Metro (plan 03 §13.7).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ChatsViewModel(
    private val feed: ConversationsFeed,
    private val briefs: ReturnBriefFeed,
    private val settings: SettingsDoor,
    private val day: FeedDay,
    private val clock: Clock,
    private val nav: AppNavigator,
    private val updates: UpdateDoor = NoUpdateDoor(),
    private val home: ChatsHomeExtras = NoChatsHomeExtras(),
    private val permissionAnswers: Set<HomePermissionAnswers> = emptySet(),
    private val connections: ConversationServers = NoConversationServers(),
    private val appScope: AppCoroutineScope? = null,
    private val pins: ConversationPins = NoConversationPins(),
    private val agentNotices: Set<HomeAgentNoticeSource> = emptySet(),
    dispatchers: AppDispatchers? = null,
) : ViewModel(), ChatsActions {
    /** Where the home's list is built: off the main thread in the app; where the caller stands in a test. */
    private val work: kotlin.coroutines.CoroutineContext =
        dispatchers?.default ?: kotlin.coroutines.EmptyCoroutineContext

    override fun openInbox(): Unit = nav.open(MonitorInboxRoute())

    private val filters = MutableStateFlow(ChatsFilters())
    private val sheet = MutableStateFlow<ChatsHomeSheet?>(null)
    private val watchedSelection = MutableStateFlow<String?>(null)
    private val startNewAsk = MutableStateFlow<StartNewUi?>(null)
    private val answers =
        MutableStateFlow<Pair<Set<String>, io.github.eslamasabry.oc.core.common.Failure?>>(
            emptySet<String>() to null
        )

    /** Archive in flight: [hidden] rows are out of the list; [pending] is the one waiting out its undo window. */
    private data class Archiving(
        val hidden: Set<String> = emptySet(),
        val pending: PendingArchive? = null,
        val offer: Int = 0,
        val failure: io.github.eslamasabry.oc.core.common.Failure? = null,
        val failureAct: RowActFailure = RowActFailure.Archive,
    )

    /** N-30: [delete] sends a DELETE instead of an archive when the undo window ends. */
    private data class PendingArchive(
        val key: String,
        val ref: ConversationRef,
        val title: RowTitle,
        val delete: Boolean = false,
    )

    /** N-30: +1 whenever the stored pins change (here or on another page), so the menu's Pin/Unpin follows. */
    private val pinTick = MutableStateFlow(0)

    private val archiving = MutableStateFlow(Archiving())

    /** Rename in flight: the open dialog, and the last refusal. */
    private data class Renaming(
        val dialog: RenameUi? = null,
        val ref: ConversationRef? = null,
        val failure: io.github.eslamasabry.oc.core.common.Failure? = null,
    )

    private val renaming = MutableStateFlow(Renaming())
    private val newRefused = MutableStateFlow(false)

    /** The switcher's switch in flight, and why the last one did not happen. */
    private data class Switching(
        val to: ProfileId? = null,
        val failure: io.github.eslamasabry.oc.core.common.Failure? = null,
    )

    private val switching = MutableStateFlow(Switching())

    /** The sheet-level failure of "Connections in this list": the person's choice could not be saved. */
    private val sourceFailure = MutableStateFlow<io.github.eslamasabry.oc.core.common.Failure?>(null)

    /** A pull to refresh in flight (one at a time). */
    private val refreshing = MutableStateFlow(false)

    /** G6: the phone agents' notices, or none where the app binds no source. */
    private val agentNoticeFlow: StateFlow<HomeAgentNotices> =
        agentNotices.singleOrNull()?.notices ?: MutableStateFlow(HomeAgentNotices())

    /**
     * H1: a permission answer waits [ANSWER_HOLD_MS] with Undo before it is sent (F: lib/state/delayed_answers.dart),
     * by request id. The conversation to answer and the reply are kept beside the words the row shows. The switcher's
     * Allow once uses the same hold, so a request answered in either place is held once and shows Undo in both.
     */
    private val heldAnswers = MutableStateFlow<Map<String, HomeHeldReply>>(emptyMap())
    private val heldRows = HashMap<String, Pair<ConversationRef, HomePermissionReply>>()
    private val holdJobs = HashMap<String, kotlinx.coroutines.Job>()

    /**
     * H1: question answers from a row: held for the undo window ([QuestionAnswersState.held], the words the person
     * chose), then sent ([QuestionAnswersState.sending]); one the server did not take stays ([refused]) with its answer
     * kept so Try again resends exactly it.
     */
    private val questionState = MutableStateFlow(QuestionAnswersState())
    private val questionRows = HashMap<String, HeldQuestion>()
    private val questionJobs = HashMap<String, kotlinx.coroutines.Job>()

    /** The row whose Always allow waits on its confirm. */
    private val confirmingAlways = MutableStateFlow<ConversationRowUi?>(null)

    /** P14: the row whose Details sheet is open over the home. */
    private val rowDetails = MutableStateFlow<RowDetailsUi?>(null)

    /** A1: the last-message lines of the rows the home shows, read lazily and kept while the screen lives. */
    private val previews =
        HomePreviews(viewModelScope, { connections.servers.value }, { clock.now().toEpochMilliseconds() })

    /**
     * The feed the home draws. While the opening server has not answered, the rows saved last time stand in as the
     * same feed rows, so the layout is the connected one and live rows replace them in place (same identity).
     */
    private val shownFeed =
        combine(feed.snapshot, home.snapshot) { snapshot, extra -> HomeCachedRows.merge(snapshot, extra.opening) }

    /** A row tapped while its server was still answering: it opens the moment the server does. */
    private var pendingOpen: ConversationRef? = null

    public val state: StateFlow<ChatsUi> =
        combine(
                combine(shownFeed, briefs.brief, filters, updates.ready, archiving) {
                    snapshot,
                    brief,
                    chosen,
                    update,
                    archived ->
                    val shown =
                        if (archived.hidden.isEmpty()) snapshot
                        else
                            snapshot.copy(
                                items =
                                    snapshot.items
                                        .filter { it.identity !in archived.hidden }
                                        .toImmutableList()
                            )
                    build(shown, brief, chosen)
                        .copy(
                            updateReady = update,
                            archivable = archivable(shown),
                            archiveUndo =
                                archived.pending?.let { ArchiveUndoUi(archived.offer, it.title, it.delete) },
                            archiveFailure = archived.failure,
                            archiveFailureAct = archived.failureAct,
                        )
                }
                    // Filtering and sorting the whole feed is work for a worker, not the frame's thread.
                    .flowOn(work),
                home.snapshot,
                sheet,
                watchedSelection,
                answers,
            ) { base, extra, open, selected, answering ->
                base.copy(
                    home = extra,
                    answering = answering.first.toImmutableSet(),
                    answerFailure = answering.second,
                    sheet = open,
                    confirmWatched = extra.monitoring.requests.firstOrNull { it.key == selected },
                )
            }
            .combine(startNewAsk) { base, ask -> base.copy(startNew = ask) }
            .combine(renaming) { base, rename ->
                base.copy(
                    renamable = editable(feed.snapshot.value),
                    renaming = rename.dialog,
                    renameFailure = rename.failure,
                )
            }
            .combine(pinTick) { base, _ ->
                val snapshot = feed.snapshot.value
                base.copy(
                    deletable = deletable(snapshot),
                    pinnable = if (pins.supported) editable(snapshot) else kotlinx.collections.immutable.persistentSetOf(),
                    pinned = pinnedKeys(snapshot),
                    alwaysAllowable = alwaysAllowable(snapshot),
                )
            }
            .combine(confirmingAlways) { base, row -> base.copy(confirmAlways = row) }
            .combine(rowDetails) { base, details -> base.copy(rowDetails = details) }
            .combine(heldAnswers) { base, held -> base.copy(held = held.toImmutableMap()) }
            .combine(questionState) { base, q ->
                base.copy(
                    heldQuestions = q.held.toImmutableMap(),
                    sendingQuestions = q.sending.toImmutableSet(),
                    refusedQuestions = q.refused.toImmutableSet(),
                )
            }
            .combine(agentNoticeFlow) { base, notices -> base.copy(agentNotices = notices) }
            .combine(newRefused) { base, refused -> base.copy(newBlocked = refused && base.home.opening != null) }
            // B-161 / B-162: each row's branch, and the Usage row's tightest limit.
            .combine(home.branches) { base, known -> HomeRows.withBranches(base, known) }
            .combine(home.usageLimits) { base, limits -> base.copy(usage = HomeRows.tightest(limits)) }
            .combine(previews.texts) { base, texts -> HomePreviewRules.withPreviews(base, texts) }
            // G4: the project sheet's kind glyphs and "Git" pills.
            .combine(home.projectFacts) { base, facts -> base.copy(projectFacts = facts.toImmutableMap()) }
            // The pill's switcher: every saved server, what it is doing now, and the switch in flight.
            .combine(refreshing) { base, now -> base.copy(refreshing = now) }
            .combine(sourceFailure) { base, failure -> base.copy(home = base.home.copy(failure = failure)) }
            .combine(switching) { base, now ->
                base.copy(
                    switcher =
                        ServerSwitcherUi(
                            ServerSwitcherRules.rows(base.home, feed.snapshot.value.items),
                            now.to,
                            now.failure,
                        )
                )
            }
            .combine(heldAnswers) { base, held ->
                base.copy(switcher = base.switcher.copy(held = held.toImmutableMap()))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                build(feed.snapshot.value, briefs.brief.value, filters.value)
                    .copy(updateReady = updates.ready.value, home = home.snapshot.value),
            )

    /** N-30: OpenCode conversations on a live connection: they can be renamed and pinned. */
    private fun editable(snapshot: ChatFeedSnapshot): kotlinx.collections.immutable.ImmutableSet<String> {
        val live = connections.servers.value.mapTo(hashSetOf()) { it.profile }
        return snapshot.items
            .filter { !it.isSubagent && it.agentId == OPENCODE_AGENT_ID && it.profileId in live }
            .mapTo(hashSetOf()) { it.identity }
            .toImmutableSet()
    }

    /** N-30: OpenCode conversations whose server deletes them. */
    private fun deletable(snapshot: ChatFeedSnapshot): kotlinx.collections.immutable.ImmutableSet<String> {
        val capable =
            connections.servers.value
                .filter { it.gateway.capabilities.value.sessionDelete }
                .mapTo(hashSetOf()) { it.profile }
        return snapshot.items
            .filter { !it.isSubagent && it.agentId == OPENCODE_AGENT_ID && it.profileId in capable }
            .mapTo(hashSetOf()) { it.identity }
            .toImmutableSet()
    }

    /**
     * F: lib/ui/screens/chats/chats_host.dart:262-270: Always allow is offered only where the server keeps the grant
     * (`capabilities.persistentPermissionGrants`), and only with an answer port to send it through.
     */
    private fun alwaysAllowable(snapshot: ChatFeedSnapshot): kotlinx.collections.immutable.ImmutableSet<String> {
        if (permissionAnswers.size != 1) return kotlinx.collections.immutable.persistentSetOf()
        val capable =
            connections.servers.value
                .filter { it.gateway.capabilities.value.persistentPermissionGrants }
                .mapTo(hashSetOf()) { it.profile }
        return snapshot.items
            .filter { it.permission != null && it.profileId in capable }
            .mapTo(hashSetOf()) { it.identity }
            .toImmutableSet()
    }

    private fun pinnedKeys(snapshot: ChatFeedSnapshot): kotlinx.collections.immutable.ImmutableSet<String> {
        if (!pins.supported) return kotlinx.collections.immutable.persistentSetOf()
        val byProfile = connections.servers.value.associateBy { it.profile }
        return snapshot.items
            .filter { item -> byProfile[item.profileId]?.let { item.ref.sessionId in pins.ids(it) } == true }
            .mapTo(hashSetOf()) { it.identity }
            .toImmutableSet()
    }

    init {
        viewModelScope.launch { pins.changes.collect { pinTick.update { it + 1 } } }
        viewModelScope.launch {
            home.snapshot.collect { extra ->
                extra.opening?.rows?.let { previews.seed(it) }
                val ref = pendingOpen
                if (ref != null && extra.opening == null) {
                    pendingOpen = null
                    openConversation(ref)
                }
            }
        }
        viewModelScope.launch { previews.texts.collect { if (it.isNotEmpty()) home.rememberPreviews(it) } }
        // B-161: the branch port reads the folders the list shows, and only those. A1: the last message of the rows on
        // show, and only those; the list never waits for it. One build of the list serves both, only when an input
        // really changed, off the main thread.
        viewModelScope.launch {
            var lastPlaces: Set<BranchPlace>? = null
            var lastWanted: List<ChatFeedItem>? = null
            combine(feed.snapshot, briefs.brief, filters) {
                    snapshot,
                    brief,
                    chosen ->
                    val ui = build(snapshot, brief, chosen)
                    val shown = (ui.rightNow + ui.recent + ui.brief?.rows.orEmpty()).mapTo(hashSetOf()) { it.key }
                    HomeRows.places(ui) to snapshot.items.filter { it.identity in shown }
                }
                .flowOn(work)
                .collect { (places, wanted) ->
                    if (places != lastPlaces) {
                        lastPlaces = places
                        home.wantBranches(places)
                    }
                    if (wanted != lastWanted) {
                        lastWanted = wanted
                        previews.want(wanted)
                    }
                }
        }
    }

    /** Rows the person can archive: any agent's conversation on a connection whose server offers it (E04-S05). */
    private fun archivable(snapshot: ChatFeedSnapshot): kotlinx.collections.immutable.ImmutableSet<String> {
        val capable =
            connections.servers.value
                .filter { it.operations != null && it.gateway.capabilities.value.sessionArchive }
                .mapTo(hashSetOf()) { it.profile }
        return ChatsState.archivable(snapshot.items, capable)
    }

    private fun build(snapshot: ChatFeedSnapshot, brief: ReturnBrief, chosen: ChatsFilters) =
        ChatsState.build(
            feed = snapshot,
            brief = brief,
            filters = chosen,
            startOfTodayMs = day.startOfToday(clock.now()).toEpochMilliseconds(),
            settingsAvailable = settings.available,
            nowMs = clock.now().toEpochMilliseconds(),
        )

    override fun answerPermission(row: ConversationRowUi, allow: Boolean) {
        hold(row, if (allow) HomePermissionReply.Once else HomePermissionReply.Reject)
    }

    private fun hold(row: ConversationRowUi, reply: HomePermissionReply) {
        hold(row.ref, row.permission?.requestId ?: return, reply)
    }

    /** Holds the answer for the undo window, then sends it once. A second tap on the same request does nothing. */
    private fun hold(ref: ConversationRef, id: String, reply: HomePermissionReply) {
        if (permissionAnswers.singleOrNull() == null) return
        if (id in heldRows || id in answers.value.first) return
        heldRows[id] = ref to reply
        heldAnswers.update {
            it + (id to
                when (reply) {
                    HomePermissionReply.Once -> HomeHeldReply.Allowed
                    HomePermissionReply.Reject -> HomeHeldReply.Rejected
                    HomePermissionReply.Always -> HomeHeldReply.AlwaysAllowed
                })
        }
        holdJobs[id] =
            viewModelScope.launch {
                kotlinx.coroutines.delay(ANSWER_HOLD_MS)
                release(id)
            }
    }

    /** The undo window ended: the answer leaves now. */
    private fun release(id: String) {
        val (ref, reply) = heldRows.remove(id) ?: return
        holdJobs.remove(id)
        sendAnswer(ref, id, reply)
        heldAnswers.update { it - id }
    }

    override fun undoAnswer(row: ConversationRowUi) {
        val ask = row.question?.requestId
        if (ask != null && ask in questionRows) {
            questionRows.remove(ask)
            questionJobs.remove(ask)?.cancel()
            questionState.update { it.copy(held = it.held - ask) }
            return
        }
        undoHeld(row.permission?.requestId ?: return)
    }

    private fun undoHeld(id: String) {
        if (heldRows.remove(id) == null) return
        holdJobs.remove(id)?.cancel()
        heldAnswers.update { it - id }
    }

    override fun answerQuestion(row: ConversationRowUi, answers: List<List<String>>, shown: String) {
        val ask = row.question ?: return
        if (permissionAnswers.singleOrNull() == null || answers.isEmpty()) return
        val id = ask.requestId
        val q = questionState.value
        if (id in questionRows || id in q.sending) return
        holdQuestion(id, HeldQuestion(row.ref, answers.map { it.toList() }, shown))
    }

    private fun holdQuestion(id: String, held: HeldQuestion) {
        questionRows[id] = held
        questionState.update { it.copy(held = it.held + (id to held.shown), refused = it.refused - id) }
        questionJobs[id] =
            viewModelScope.launch {
                kotlinx.coroutines.delay(ANSWER_HOLD_MS)
                releaseQuestion(id)
            }
    }

    private fun releaseQuestion(id: String) {
        val held = questionRows.remove(id) ?: return
        questionJobs.remove(id)
        val port = permissionAnswers.singleOrNull() ?: return
        questionState.update { it.copy(held = it.held - id, sending = it.sending + id) }
        viewModelScope.launch {
            val result = port.answerQuestion(held.ref, id, held.answers)
            questionState.update {
                it.copy(
                    sending = it.sending - id,
                    refused =
                        if (result is io.github.eslamasabry.oc.core.common.Outcome.Failed) it.refused + id else it.refused,
                )
            }
            if (result is io.github.eslamasabry.oc.core.common.Outcome.Failed) failedQuestions[id] = held
            else failedQuestions.remove(id)
            feed.refresh()
        }
    }

    /** Answers the server did not take, kept for Try again. */
    private val failedQuestions = HashMap<String, HeldQuestion>()

    override fun retryQuestion(row: ConversationRowUi) {
        val id = row.question?.requestId ?: return
        val held = failedQuestions.remove(id) ?: return
        questionState.update { it.copy(refused = it.refused - id) }
        // Try again is a decision just made: it goes out at once, without a second undo window.
        questionRows[id] = held
        releaseQuestion(id)
    }

    override fun openRequest(row: ConversationRowUi) {
        openConversation(row.ref)
    }

    // H3, F: chats_home_screen.dart:511-577 `_incompleteLine` retry: every connection that did not answer is asked
    // again, then the list is read once more.
    override fun retryList() {
        val failing = state.value.home.sources.filter { it.shown && it.unreachable }.map { it.profile }
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                failing.forEach { home.retrySource(it) }
                feed.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    override fun openAgents() {
        nav.open(io.github.eslamasabry.oc.ui.routes.AgentsRoute.Home)
    }

    override fun openAgent(agentId: String) {
        nav.open(io.github.eslamasabry.oc.ui.routes.AgentsRoute.Agent(agentId))
    }

    override fun askAlwaysAllow(row: ConversationRowUi) {
        if (row.permission == null || row.key !in state.value.alwaysAllowable) return
        confirmingAlways.value = row
    }

    override fun confirmAlwaysAllow() {
        val row = confirmingAlways.value ?: return
        confirmingAlways.value = null
        // The request it was asked for: a newer one on the same row is not what the person confirmed.
        val now = (state.value.rightNow + state.value.recent + state.value.brief?.rows.orEmpty()).firstOrNull { it.key == row.key }
        if (now?.permission?.requestId != row.permission?.requestId) return
        hold(row, HomePermissionReply.Always)
    }

    override fun cancelAlwaysAllow() {
        confirmingAlways.value = null
    }

    private fun sendAnswer(ref: ConversationRef, requestId: String, reply: HomePermissionReply) {
        val port = permissionAnswers.singleOrNull() ?: return
        if (requestId in answers.value.first) return
        answers.value = (answers.value.first + requestId) to null
        viewModelScope.launch {
            val result = port.answer(ref, requestId, reply)
            answers.value =
                (answers.value.first - requestId) to
                    (result as? io.github.eslamasabry.oc.core.common.Outcome.Failed)?.failure
            feed.refresh()
        }
    }

    // F: lib/ui/screens/chats/chats_home_screen.dart:536: the list's pull reads every source again; not while the
    // server is still opening (the saved titles there are refreshed by the server answering).
    override fun refreshList() {
        if (refreshing.value || state.value.home.opening != null) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                // A source that fails says so in its own line, as after any read.
                feed.refresh()
                // B8: the rows' last words are read again too, so a refreshed list never keeps an old line.
                previews.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    override fun openConversation(ref: ConversationRef) {
        if (state.value.home.opening != null) {
            pendingOpen = ref
            return
        }
        // A row whose conversation cannot be reopened yet (its workspace is still connecting) stays shut.
        val item = feed.snapshot.value.items.firstOrNull { it.ref == ref && it.canReopen } ?: return
        // H4: an agent conversation the helper cannot resume asks before anything opens (F: chats_home_screen.dart:186).
        AgentResumeRules.ask(item, ChatsState.row(item).agentName)?.let {
            sheet.value = null
            startNewAsk.value = it
            return
        }
        sheet.value = null
        feed.prefetch(ref)
        nav.openChat(ref)
    }

    override fun confirmStartNew() {
        val ask = startNewAsk.value ?: return
        startNewAsk.value = null
        nav.open(AgentResumeRules.route(ask))
    }

    override fun dismissStartNew() {
        startNewAsk.value = null
    }

    override fun rowAct(row: ConversationRowUi, act: RowAct) {
        // P14 (F: session_menu.dart:42-48, Details does not need the conversation): the row's facts in a sheet over the
        // home; Back closes it and the person is still on the list.
        if (act == RowAct.Details) {
            sheet.value = null
            rowDetails.value = RowDetailsUi.of(row, state.value.home.sources)
            return
        }
        if (!row.canOpen || state.value.home.opening != null) return
        sheet.value = null
        nav.openRowAct(row.ref, act)
    }

    override fun closeRowDetails() {
        rowDetails.value = null
    }

    override fun archive(row: ConversationRowUi) {
        if (row.key !in state.value.archivable) return
        // One offer at a time: a new archive sends the earlier one first.
        commitArchive()
        val title = row.title
        archiving.update {
            it.copy(
                hidden = it.hidden + row.key,
                pending = PendingArchive(row.key, row.ref, title),
                offer = it.offer + 1,
                failure = null,
            )
        }
    }

    override fun delete(row: ConversationRowUi) {
        if (row.key !in state.value.deletable) return
        commitArchive()
        archiving.update {
            it.copy(
                hidden = it.hidden + row.key,
                pending = PendingArchive(row.key, row.ref, row.title, delete = true),
                offer = it.offer + 1,
                failure = null,
            )
        }
    }

    override fun togglePin(row: ConversationRowUi) {
        if (row.key !in state.value.pinnable) return
        val server = connections.servers.value.firstOrNull { it.profile == row.ref.profileId } ?: return
        val wanted = row.ref.sessionId !in pins.ids(server)
        viewModelScope.launch {
            when (val done = pins.set(server, row.ref.sessionId, wanted)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> {
                    pinTick.update { it + 1 }
                    // Launcher shortcuts follow the pins, as on All conversations.
                    val ids = pins.ids(server)
                    val titles =
                        feed.snapshot.value.items
                            .filter { it.profileId == server.profile && it.ref.sessionId in ids }
                            .associate { it.ref.sessionId to SessionTitles.text(it.title) }
                    pins.published(server, titles)
                }
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    archiving.update { it.copy(failure = done.failure, failureAct = RowActFailure.Pin) }
            }
        }
    }

    override fun undoArchive() {
        archiving.update { cur ->
            val back = cur.pending ?: return@update cur
            cur.copy(hidden = cur.hidden - back.key, pending = null)
        }
    }

    override fun commitArchive() {
        val job = archiving.value.pending ?: return
        archiving.update { it.copy(pending = null) }
        // The app scope, so leaving the page inside the window still sends what the person archived.
        (appScope ?: viewModelScope).launch {
            val ops =
                connections.servers.value.firstOrNull { it.profile == job.ref.profileId }?.operations
            val server = connections.servers.value.firstOrNull { it.profile == job.ref.profileId }
            val done: io.github.eslamasabry.oc.core.common.Outcome<Unit> =
                if (job.delete)
                    server?.gateway?.deleteSession(job.ref.sessionId)
                        ?: io.github.eslamasabry.oc.core.domain.unavailable("sessionDelete")
                else
                    ops?.archiveSession(job.ref.sessionId)
                        ?: io.github.eslamasabry.oc.core.domain.unavailable("sessionArchive")
            when (done) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> {
                    connections.left(job.ref)
                    feed.refresh()
                }
                // A refusal (or a server that went away) brings the row back with its reason.
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    archiving.update {
                        it.copy(
                            hidden = it.hidden - job.key,
                            failure = done.failure,
                            failureAct = if (job.delete) RowActFailure.Delete else RowActFailure.Archive,
                        )
                    }
            }
        }
    }

    override fun dismissArchiveFailure() {
        archiving.update { it.copy(failure = null) }
    }

    override fun rename(row: ConversationRowUi) {
        if (row.key !in state.value.renamable) return
        val initial = (row.title as? RowTitle.Text)?.text.orEmpty()
        renaming.value = Renaming(RenameUi(row.key, initial), row.ref, null)
    }

    override fun finishRename(title: String?) {
        val asked = renaming.value
        val dialog = asked.dialog ?: return
        val ref = asked.ref
        renaming.value = Renaming()
        val wanted = title?.trim().orEmpty()
        if (ref == null || wanted.isEmpty() || wanted == dialog.initial) return
        val server = connections.servers.value.firstOrNull { it.profile == ref.profileId } ?: return
        (appScope ?: viewModelScope).launch {
            when (val done = server.gateway.renameSession(ref.sessionId, wanted)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> feed.refresh()
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    renaming.value = Renaming(failure = done.failure)
            }
        }
    }

    override fun dismissRenameFailure() {
        renaming.update { it.copy(failure = null) }
    }

    override fun onCleared() {
        commitArchive()
        // Leaving the page inside an undo window still sends what the person answered (the app scope outlives it).
        val port = permissionAnswers.singleOrNull()
        val scope = appScope
        if (port != null && scope != null)
            heldRows.toList().forEach { (id, held) ->
                val (ref, reply) = held
                scope.launch { port.answer(ref, id, reply) }
            }
        heldRows.clear()
        if (port != null && scope != null)
            questionRows.toList().forEach { (id, held) -> scope.launch { port.answerQuestion(held.ref, id, held.answers) } }
        questionRows.clear()
        super.onCleared()
    }

    override fun newConversation() {
        // A server that is not answering cannot start a conversation; the home says so instead of doing nothing.
        if (state.value.home.opening == null) nav.open(ChatsRoute.New(profile = null)) else newRefused.value = true
    }

    override fun dismissNewBlocked() {
        newRefused.value = false
    }

    override fun openSettings() {
        if (settings.available) settings.open()
    }

    override fun openUpdate(): Unit = updates.open()

    override fun search(query: String): Unit = filters.update { it.copy(query = query) }

    override fun toggleRunning(): Unit = filters.update {
        it.copy(running = !it.running, needsYou = false)
    }

    override fun toggleNeedsYou(): Unit = filters.update {
        it.copy(needsYou = !it.needsYou, running = false)
    }

    override fun chooseProject(directory: String) {
        val fromSheet = sheet.value == ChatsHomeSheet.Projects
        filters.update {
            it.copy(
                projectDirectory =
                    if (fromSheet || it.projectDirectory != directory) directory else null,
                otherFolders = false,
            )
        }
        sheet.value = null
    }

    override fun clearFilters(): Unit = filters.update { ChatsFilters() }

    override fun dismissBrief(shown: ReturnBrief) {
        viewModelScope.launch { briefs.dismiss(shown) }
    }

    override fun showBriefOverflow(shown: BriefUi) {
        filters.value = ChatsFilters(identities = shown.overflowKeys)
    }

    /** P5: All conversations opens with the home's narrowing (its project, else Running), as the row's count says. */
    override fun showAll() {
        val chosen = filters.value
        val filter = chosen.projectDirectory?.takeIf { it.startsWith("/") } ?: ChatsAllViewModel.RUNNING.takeIf { chosen.running }
        nav.open(ChatsRoute.All(filter = filter))
    }

    override fun openUsage(): Unit = nav.open(UsageRoute.Hub())

    override fun openSearch(): Unit = nav.open(SearchRoute.Conversations())

    override fun openAttention() {
        sheet.value = null
        nav.open(ChatsRoute.Attention)
    }

    override fun openProjects() {
        sheet.value = null
        nav.open(ProjectsRoute.Picker())
    }

    override fun showProjectChoices() {
        sheet.value = ChatsHomeSheet.Projects
    }

    override fun showSources() {
        sourceFailure.value = null
        sheet.value = ChatsHomeSheet.Sources
    }

    override fun showInbox() {
        sheet.value = ChatsHomeSheet.Inbox
    }

    override fun closeHomeSheet() {
        sourceFailure.value = null
        sheet.value = null
    }

    override fun allProjects() {
        filters.update { it.copy(projectDirectory = null, otherFolders = false) }
        sheet.value = null
    }

    override fun otherFolders() {
        filters.update { it.copy(projectDirectory = null, otherFolders = true) }
        sheet.value = null
    }

    override fun showSource(profile: ProfileId, shown: Boolean) {
        viewModelScope.launch {
            // Only a choice that could not be saved is the sheet's failure; a server that does not answer is its row's.
            sourceFailure.value = (home.showSource(profile, shown) as? io.github.eslamasabry.oc.core.common.Outcome.Failed)?.failure?.let(SourceChoiceRules::sheetFailure)
        }
    }

    override fun retrySource(profile: ProfileId) {
        viewModelScope.launch {
            home.retrySource(profile) // its answer shows on the row (unreachable), not as a failure of the sheet
            sourceFailure.value = null
        }
    }

    override fun retryOpening() {
        viewModelScope.launch { home.retryOpening() }
    }

    override fun editOpeningServer() {
        val profile = state.value.home.opening?.profile ?: return
        sheet.value = null
        nav.open(ServersRoute.Edit(profile))
    }

    // The switcher's Allow once is the home's own held answer (one undo window, shared with the rows).
    override fun allowSwitcherRequest(ref: ConversationRef, requestId: String) {
        hold(ref, requestId, HomePermissionReply.Once)
    }

    override fun undoSwitcherRequest(requestId: String) {
        undoHeld(requestId)
    }

    override fun openSwitcherRequest(ref: ConversationRef) {
        // The conversation is on another server: open it from the list the sheet sits on.
        openConversation(ref)
    }

    override fun openTailscale() {
        sheet.value = null
        nav.open(CapabilityServerSetupRoute(CapabilityServerDoor.Tailscale))
    }

    override fun openServers() {
        sheet.value = null
        nav.open(ServersRoute.Home)
    }

    // F: lib/ui/screens/home_screen.dart:434-470: the bar's server pill opens the switcher sheet, not the Servers page.
    override fun openServerSwitcher() {
        switching.update { it.copy(failure = null) }
        sheet.value = ChatsHomeSheet.Servers
    }

    override fun switchServer(profile: ProfileId) {
        val rows = state.value.switcher.servers
        val row = rows.firstOrNull { it.profile == profile } ?: return
        // Tapping the server already in use has nowhere to go but back to it (Flutter: reconnecting drops live state).
        if (row.current) {
            sheet.value = null
            return
        }
        // A password or token the app can no longer use is typed in the server's form, not tried again here.
        if (row.secret != SwitcherSecret.None) {
            sheet.value = null
            nav.open(ServersRoute.Edit(profile))
            return
        }
        if (switching.value.to != null) return
        switching.value = Switching(to = profile)
        viewModelScope.launch {
            when (val done = home.switchTo(profile)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> {
                    switching.value = Switching()
                    if (sheet.value == ChatsHomeSheet.Servers) sheet.value = null
                }
                // The sheet stays open and says why; the rows are as they were.
                is io.github.eslamasabry.oc.core.common.Outcome.Failed -> switching.value = Switching(failure = done.failure)
            }
        }
    }

    override fun startOpeningPhoneServer() {
        val opening = home.snapshot.value.opening ?: return
        val profile = opening.profile ?: return
        if (!opening.onThisPhone) return
        newRefused.value = false
        viewModelScope.launch { home.startPhoneServer(profile) }
    }

    override fun restartOpeningPhoneServer() {
        val opening = home.snapshot.value.opening ?: return
        val profile = opening.profile ?: return
        if (!opening.onThisPhone) return
        // The card follows the phone server's own state (starting, then the list or a new failure).
        viewModelScope.launch { home.restartPhoneServer(profile) }
    }

    override fun disconnectServer(profile: ProfileId) {
        if (switching.value.to != null) return
        switching.value = Switching(to = profile)
        viewModelScope.launch {
            when (val done = home.disconnect(profile)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> {
                    switching.value = Switching()
                    if (sheet.value == ChatsHomeSheet.Servers) sheet.value = null
                }
                // The sheet stays open and says why; the rows are as they were.
                is io.github.eslamasabry.oc.core.common.Outcome.Failed -> switching.value = Switching(failure = done.failure)
            }
        }
    }

    override fun addServer() {
        sheet.value = null
        nav.open(ServersRoute.Edit())
    }

    override fun serverSettings(profile: ProfileId) {
        sheet.value = null
        nav.open(ServersRoute.Edit(profile))
    }

    override fun connectComputer() {
        sheet.value = null
        nav.open(ServersRoute.Edit())
    }

    override fun openDemo() {
        sheet.value = null
        nav.open(io.github.eslamasabry.oc.ui.routes.DemoRoute)
    }

    /**
     * B-182: "Open a project…" leads to the real flow (where the project is, its server's projects, its folders), as
     * Flutter's chip opens ProjectFolderActions.openFolder; the old path-only box stays inside it as Type a path.
     */
    override fun openFilterProject() {
        sheet.value = null
        nav.open(ProjectsRoute.Open)
    }

    override fun finishFilterProject(directory: String?) {
        if (directory != null) {
            val path = directory.trim().trimEnd('/').ifEmpty { "/" }
            if (
                path.startsWith('/') &&
                    path.split('/').none { it == ".." || it == "." } &&
                    path.none { it.code <= 0x1f || it.code == 0x7f }
            ) {
                filters.update { it.copy(projectDirectory = path, otherFolders = false) }
            }
        }
        sheet.value = null
    }

    override fun showMonitoring() {
        sheet.value = ChatsHomeSheet.Monitoring
    }

    override fun setWatched(profile: ProfileId, enabled: Boolean) {
        viewModelScope.launch { home.setWatched(profile, enabled) }
    }

    override fun refreshWatched() {
        viewModelScope.launch { home.refreshWatched() }
    }

    override fun openWatched(key: String) {
        sheet.value = null
        watchedSelection.value = key
    }

    override fun dismissWatched() {
        watchedSelection.value = null
    }

    override fun confirmWatched() {
        val key = watchedSelection.value ?: return
        watchedSelection.value = null
        viewModelScope.launch {
            when (val result = home.openWatched(key)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> {
                    sheet.value = null
                    nav.openChat(result.value)
                }
                // The refusal is shown in the Inbox, which was closed for the confirmation.
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    sheet.value = ChatsHomeSheet.Inbox
            }
        }
    }
}

public fun interface HomePermissionAnswers {
    public suspend fun answer(
        ref: ConversationRef,
        requestId: String,
        allow: Boolean,
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit>

    /**
     * H1: the answer to a waiting question, one list of chosen words (or typed text) per prompt. A port with no
     * requests engine refuses, so nothing is reported as sent.
     */
    public suspend fun answerQuestion(
        ref: ConversationRef,
        requestId: String,
        answers: List<List<String>>,
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit> =
        io.github.eslamasabry.oc.core.common.Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("questionRequests")
            )
        )

    /**
     * The row's answer, Always allow included (F: lib/ui/screens/chats/chats_host.dart:258-270). A port that cannot
     * keep a grant refuses Always, so nothing is allowed for longer than the person chose.
     */
    public suspend fun answer(
        ref: ConversationRef,
        requestId: String,
        reply: HomePermissionReply,
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit> =
        when (reply) {
            HomePermissionReply.Once -> answer(ref, requestId, true)
            HomePermissionReply.Reject -> answer(ref, requestId, false)
            HomePermissionReply.Always ->
                io.github.eslamasabry.oc.core.common.Outcome.Failed(
                    io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                        io.github.eslamasabry.oc.core.common.FeatureId("persistentPermissionGrants")
                    )
                )
        }
}

/** H1: a question answer being held or retried; the conversation, the answers and the words the row repeats. */
private class HeldQuestion(val ref: ConversationRef, val answers: List<List<String>>, val shown: String)

private data class QuestionAnswersState(
    val held: Map<String, String> = emptyMap(),
    val sending: Set<String> = emptySet(),
    val refused: Set<String> = emptySet(),
)

/** What a list row answers a waiting permission. */
public enum class HomePermissionReply { Once, Always, Reject }

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface HomePermissionBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val homePermissionAnswers: Set<HomePermissionAnswers>
}

/**
 * F: lib/state/delayed_answers.dart: how long a permission answer waits with Undo before it is sent. The one undo
 * window for the home: a row's answers and the switcher's Allow once share it; the port answers at once (no second
 * hold in the engine).
 */
internal const val ANSWER_HOLD_MS: Long = 3_000L
