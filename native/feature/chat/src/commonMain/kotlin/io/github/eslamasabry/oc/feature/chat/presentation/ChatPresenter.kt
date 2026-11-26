package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptRules
import io.github.eslamasabry.oc.engine.transcript.ledger.factTicks
import io.github.eslamasabry.oc.engine.transcript.ledger.latest
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.starprompt.ChatStarPrompt
import io.github.eslamasabry.oc.feature.chat.starprompt.StarPromptEvent
import io.github.eslamasabry.oc.feature.chat.starprompt.StarPromptTurn
import io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.StoppedPrompt
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatUndone
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.feature.chat.world.ReplacedModel
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationPage
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the presenter runs a command's suspending half (the ViewModel's scope; tests start it in
 * place).
 */
public fun interface ChatLauncher {
    public fun launch(block: suspend () -> Unit)
}

/** The screen-local facts no engine owns (open menu, find query, arrival, …). */
internal data class ChatLocal(
    val findQuery: String? = null,
    val findKey: FindHit? = null,
    val findCursor: Int = -1,
    val timelineQuery: String? = null,
    val reasoningExpanded: Boolean = false,
    val searchingAll: Boolean = false,
    val loadingOlder: Boolean = false,
    val olderFailure: Failure? = null,
    val menu: MessageMenuUi? = null,
    val confirm: ChatConfirm? = null,
    val clip: ClipUi? = null,
    val copied: CopiedUi? = null,
    val readAloud: ReadAloudUi? = null,
    val arrival: PendingArrival? = null,
    val actionFailure: Failure? = null,
    val startingAgent: Boolean = false,
    val agentFailed: Boolean = false,
    val atLiveEdge: Boolean = true,
    val stopping: Boolean = false,
    val stopAfterStep: Boolean = false,
    /** C5: the words of the note that went, so Undo can find it in the inbox. */
    val stopNote: String? = null,
    val undoingStopNote: Boolean = false,
    /**
     * The words of a "Stop after this step" note sent in this conversation, kept after the note is gone: the note is
     * a control message (Flutter keeps it in the inbox, never as a prompt bubble), so a prompt row with exactly these
     * words is not drawn, while it waits, after Undo, and once it was read.
     */
    val hiddenStopNote: String? = null,
    /** C6: steered messages Stop-then-resend took back; the server keeps them in its record, the chat shows them once. */
    val withdrawn: PersistentSet<String> = persistentSetOf(),
    val leaving: Boolean = false,
    val select: SelectTextUi? = null,
    val insert: ClipUi? = null,
    val linkRefused: Boolean = false,
    val picture: PictureUi? = null,
    val confirmedPaths: PersistentSet<String> = persistentSetOf(),
    val receiptChecks: PersistentMap<Long, ReceiptCheckUi> = persistentMapOf(),
    val receiptResult: ReceiptCheckResult? = null,
    /** S30 story 3: what the server can background, read once the conversation is shown. */
    val backgroundSupport: io.github.eslamasabry.oc.core.model.BackgroundWorkSupport =
        io.github.eslamasabry.oc.core.model.BackgroundWorkSupport.Unavailable,
    /** Parts already sent to the background (Flutter `_backgroundRequestedParts`); never offered twice. */
    val backgroundRequested: PersistentSet<String> = persistentSetOf(),
    val backgrounding: Boolean = false,
    /** B-169: the open switch sheet. */
    val switching: SwitchUi? = null,
    /** B-169: the switch waiting on "already ran {N} steps" (turn id and the chosen model). */
    val switchAsk: Pair<String, io.github.eslamasabry.oc.feature.chat.world.ModelKey>? = null,
    /** B-169 §5: switches sent per stopped prompt ([LimitSwitchRules.promptKey]). */
    val switches: PersistentMap<String, Int> = persistentMapOf(),
    val attachAgain: AttachAgainUi? = null,
    /** The one-time first-reply ask riding the finished reply's footer. */
    val replyAsk: ReplyAskUi? = null,
    /** Stale-choice replacement notice the person already dismissed. */
    val dismissedModelReplaced: ReplacedModel? = null,
)

internal data class PendingArrival(
    val id: Long,
    val focusComposer: Boolean,
    val landOnFailure: Boolean,
    val rowKey: String? = null,
)

/** One snapshot of everything the screen reads from the world. */
internal data class ChatInputs(
    val transcript: TranscriptState,
    val load: ChatLoad,
    val title: String?,
    val capabilities: ServerCapabilities,
    val link: ChatLink,
    val pending: ImmutableList<PendingSend>,
    /** The Back guard's only need of the draft: typing must not present a new screen state per key. */
    val draftUnsaved: Boolean,
    val retry: LinkRetryFacts = LinkRetryFacts.None,
)

/**
 * The chat's state holder and intents (plan 09 §6.2), free of Android and of the ViewModel class so
 * it is tested with plain `kotlin.test`: [current] presents the world's present values; [states] is
 * the production flow. The combine is O(changed side tables): rows pass through by reference; find
 * runs on [states]' work context.
 */
public class ChatPresenter(
    private val world: ChatWorld,
    private val nav: AppNavigator,
    private val launcher: ChatLauncher,
    focusComposer: Boolean = false,
    landOnFailure: Boolean = false,
    private val outside: ChatOutside? = null,
    private val clock: Clock? = null,
    private val star: ChatStarPrompt? = null,
) : ChatActions {
    private var nextId = 1L
    private val local =
        MutableStateFlow(
            ChatLocal(
                withdrawn = world.withdrawnPrompts().toPersistentSet(),
                arrival =
                    if (focusComposer || landOnFailure)
                        PendingArrival(nextId++, focusComposer, landOnFailure)
                    else null
            )
        )
    private var revision = 0L
    private var lastHits: ImmutableList<FindHit> = persistentListOf()
    private var lastLive: TranscriptState? = null
    private var lastTitle: String? = null
    private var readAllowed = false
    private var readBusy = false
    private var stopInFlight = false
    /** B8: the turn whose prompt Send again is sending, or sent; one tap sends it once. */
    private var resentTurn: String? = null
    private var historySearchGeneration = 0L
    private val feed = (world as? TurnFeedSource)?.turnFeed ?: MutableStateFlow(TurnFeed.Empty)

    public val ref: ConversationRef
        get() = world.ref

    /** The present state from the world's current values (find computed in place). */
    public fun current(): ChatUi {
        settleStop()
        val inputs = inputsNow()
        val l = local.value
        val hits =
            l.findQuery?.let { TranscriptFind.search(inputs.transcript, it) } ?: persistentListOf()
        return present(inputs, l, FoundHits(l.findQuery, hits))
    }

    /** The production state: [work] is where find searches (never the main thread). */
    public fun states(
        scope: CoroutineScope,
        work: CoroutineContext,
        started: SharingStarted,
    ): StateFlow<ChatUi> {
        val hits =
            combine(world.transcript, local.map { it.findQuery }.distinctUntilChanged()) { t, q ->
                    t to q
                }
                .conflate()
                .map { (t, q) ->
                    // Tagged with its query: the query changes at once, its hits arrive later (issue 147).
                    FoundHits(q, if (q == null) persistentListOf() else TranscriptFind.search(t, q))
                }
                .flowOn(work)
        val inputs =
            combine(world.transcript, world.load, world.title, world.capabilities, world.link) {
                    t,
                    l,
                    ti,
                    c,
                    k ->
                    Five(t, l, ti, c, k)
                }
                .combine(world.pending) { f, p -> f to p }
                .combine(world.draft.map { it.unsaved }.distinctUntilChanged()) { (f, p), d ->
                    ChatInputs(f.t, f.l, f.ti, f.c, f.k, p, d)
                }
                .combine(retryFacts()) { i, r -> i.copy(retry = r) }
                // Open while starting: what the page waits for, said on the connection line.
                .combine(world.serverWait) { i, w -> i.copy(retry = i.retry.copy(serverWait = w)) }
                .combine(setUpChanges()) { i, _ -> i }
                // B-458: the phone agents' sign-in as it is now; the stopped turns follow it.
                .combine(signInChanges()) { i, s ->
                    signInsNow = s
                    i
                }
        // Read acknowledgement follows the world, not the screen's composition (plan 09 §6.7).
        combine(world.load, world.transcript, local) { _, _, _ ->
                syncRead()
                settleStop()
            }
            .launchIn(scope)
        // A requests model that arrives later (a reconnect) takes the chat's Stop state too.
        world.requests.onEach { syncRunEnding() }.launchIn(scope)
        watchTurns(scope)
        // S30 story 3: what this server can background, read each time the chat goes live (Flutter reads it on
        // attach), so a reconnect to another server version is read again.
        world.load
            .map { it == ChatLoad.Live }
            .distinctUntilChanged()
            .onEach { live -> if (live) loadBackgroundSupport() }
            .launchIn(scope)
        // S28 story 3: the undone line follows the session as well.
        val extra =
            feed
                .combine(world.undone) { f, u -> f to u }
                .combine(world.parent) { x, _ -> x }
                .combine(world.models.map { it.replaced }.distinctUntilChanged()) { x, _ -> x }
        // The page's first frame shows what the world already holds (kept rows, a message on its way, the title),
        // never a loading state it would leave a frame later (open-transitions: the combine's first value arrives
        // after the first frame is drawn), and again on every return.
        val upstream = combine(inputs, local, hits, world.hasOlder, extra) { i, l, h, _, _ -> present(i, l, h) }
        val out = MutableStateFlow(current())
        // Every START (the first open, every return) relaunches the collect by hand: the ordered latest chain
        // drops each restart after an inner was cancelled. The job type stays spelled out (K1's list has no Job).
        var inner: kotlinx.coroutines.Job? = null
        started.command(out.subscriptionCount).distinctUntilChanged().onEach { c ->
            inner?.cancel()
            inner = null
            if (c == SharingCommand.START) {
                inner = scope.launch { upstream.onStart { emit(current()) }.collect { out.value = it } }
            }
        }.launchIn(scope)
        return out.asStateFlow()
    }

    /**
     * The one-time star ask (app-wide, see [ChatStarPrompt]): a turn that starts here is a prompt sent; a turn that ends
     * is a reply, good or not. A run already going when the chat opens, or a steer into a running turn, is neither.
     */
    private fun watchTurns(scope: CoroutineScope) {
        val port = star ?: return
        var wasBusy: Boolean? = null
        var heldTurnId: String? = null
        val hold: (String?) -> Unit = { heldTurnId = it }
        world.requests
            .latest { req -> req?.state?.map { it.card != null } ?: flowOf(false) }
            .onEach { hasCard ->
                val turnId = heldTurnId
                if (!hasCard && turnId != null && isAskLiveAtRest()) {
                    heldTurnId = null
                    askAfterReply(port, turnId, hold)
                }
            }
            .launchIn(scope)
        world.transcript
            .onEach { t ->
                val busy = t.run.busy
                val before = wasBusy
                wasBusy = busy
                if (before == false && busy && world.link.value == ChatLink.Live) {
                    // Never while a turn runs: a new turn takes the ask down, and counting it asks nothing.
                    heldTurnId = null
                    local.update { it.copy(replyAsk = null) }
                    launcher.launch { port.promptStarted() }
                } else if (before == true && !busy) {
                    heldTurnId = null
                    local.update { it.copy(replyAsk = null) }
                    val turnId = StarPromptTurn.succeededTurnId(t.rows)
                    if (turnId != null) askAfterReply(port, turnId, hold)
                    else launcher.launch { port.replyEnded(false) }
                }
            }
            .launchIn(scope)
    }

    private fun isAskLiveAtRest(): Boolean =
        !world.transcript.value.run.busy && world.link.value == ChatLink.Live

    private fun askAfterReply(port: ChatStarPrompt, turnId: String, hold: (String?) -> Unit) {
        launcher.launch {
            if (!isAskLiveAtRest()) return@launch
            if (world.requests.value?.state?.value?.card != null) {
                hold(turnId)
            } else {
                val asked = port.replyEnded(true)
                val clear = isAskLiveAtRest() && world.requests.value?.state?.value?.card == null
                if (asked && clear) {
                    local.update { it.copy(replyAsk = ReplyAskUi(turnId = turnId)) }
                    port.report(StarPromptEvent.Shown)
                }
            }
        }
    }

    override fun replyAskYes() {
        val cur = local.value.replyAsk ?: return
        if (cur.stage != ReplyAskStage.Ask) return
        local.update { it.copy(replyAsk = cur.copy(stage = ReplyAskStage.Thanks, menu = true)) }
        launcher.launch { star?.answered(io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskAnswer.Yes) }
    }

    override fun replyAskNo() {
        val cur = local.value.replyAsk ?: return
        if (cur.stage != ReplyAskStage.Ask) return
        local.update { it.copy(replyAsk = cur.copy(stage = ReplyAskStage.Sorry, menu = true)) }
        launcher.launch { star?.answered(io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskAnswer.No) }
    }

    override fun replyAskSkip() {
        val cur = local.value.replyAsk ?: return
        if (cur.stage != ReplyAskStage.Ask) return
        local.update { it.copy(replyAsk = null) }
        launcher.launch { star?.answered(io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskAnswer.Skip) }
        star?.report(StarPromptEvent.Dismissed)
    }

    override fun replyAskUse(use: io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskUse) {
        val cur = local.value.replyAsk ?: return
        if (!cur.menu || cur.stage == ReplyAskStage.Ask) return
        local.update { it.copy(replyAsk = cur.copy(menu = false)) }
        launcher.launch { star?.usedFor(use) }
    }

    override fun replyAskMenuClosed() {
        val cur = local.value.replyAsk ?: return
        if (!cur.menu) return
        local.update { it.copy(replyAsk = cur.copy(menu = false)) }
    }

    override fun replyAskStar() {
        val cur = local.value.replyAsk ?: return
        if (cur.stage != ReplyAskStage.Thanks) return
        local.update { it.copy(replyAsk = null) }
        star?.report(StarPromptEvent.Tapped)
        // Opened on the app's own link path; a refusal costs nothing, the ask is already spent.
        outside?.openOwnLink(StarPromptRules.URL)
    }

    override fun replyAskReport() {
        val cur = local.value.replyAsk ?: return
        if (cur.stage != ReplyAskStage.Sorry) return
        local.update { it.copy(replyAsk = null) }
        reportProblem()
    }

    private data class Five(
        val t: TranscriptState,
        val l: ChatLoad,
        val ti: String?,
        val c: ServerCapabilities,
        val k: ChatLink,
    )

    private fun inputsNow(): ChatInputs =
        ChatInputs(
            world.transcript.value,
            world.load.value,
            world.title.value,
            world.capabilities.value,
            world.link.value,
            world.pending.value,
            world.draft.value.unsaved,
            retryNow(world.linkRetryAtMs.value),
        )

    /**
     * S28 story 3 (Flutter `_stagedRevertStatus`, `_undoneStatus`): the quietest line, below every real one.
     */
    private fun undoneLine(undone: ChatUndone): ChatStatusUi? =
        when (undone) {
            ChatUndone.None -> null
            ChatUndone.Staged -> ChatStatusUi(STAGED_UNDO_STATUS, ChatStatusKind.StagedUndo)
            ChatUndone.Applied -> ChatStatusUi(UNDONE_STATUS, ChatStatusKind.Undone)
        }

    /** S28 story 3 (Flutter `_restore`, Put back and `/redo`): a staged undo opens its review page instead. */
    override fun putBack() {
        when (world.undone.value) {
            ChatUndone.None -> Unit
            ChatUndone.Staged -> nav.open(ReviewRoute.Staged(world.ref))
            ChatUndone.Applied ->
                launcher.launch {
                    val r = world.putBack()
                    if (r is Outcome.Failed) local.update { it.copy(actionFailure = r.failure) }
                }
        }
    }

    /** The retry schedule at the clock's present (no countdown without a clock). */
    private fun retryNow(at: Long?): LinkRetryFacts =
        LinkRetryFacts(
            retryAtMs = at.takeIf { clock != null },
            nowMs = clock?.now()?.toEpochMilliseconds() ?: 0,
            onThisPhone = world.serverOnThisPhone,
            stopped = world.linkStopped.value,
            serverWait = world.serverWait.value,
        )

    /** S24-CHAT: ticks once a second only while a retry is scheduled (Flutter's `_Ticker`). */
    private fun retryFacts(): Flow<LinkRetryFacts> {
        val c = clock ?: return world.linkRetryAtMs.map { retryNow(it) }
        return world.linkRetryAtMs.latest { at ->
            factTicks(c, flowOf(at != null)).map { now ->
                LinkRetryFacts(at, now, world.serverOnThisPhone, world.linkStopped.value)
            }
        }
    }

    /** A connection line on the phone agents' server carries the helper's facts (Restart agent, starting, failed). */
    /**
     * A staged undo hides turns until it is reviewed, so its line (Review) stands above every failure: a refused send,
     * a failed run or a refused link never takes the way back off the screen (the failed bubble sits under it). Only a
     * lost connection, which the person must know first, comes before it.
     */
    private fun stagedFirst(line: ChatStatusUi?): ChatStatusUi? {
        val staged = undoneLine(world.undone.value)?.takeIf { it.kind == ChatStatusKind.StagedUndo }
        val connection = line?.kind == ChatStatusKind.Offline || line?.kind == ChatStatusKind.Connecting
        return if (staged != null && !connection) staged else line
    }

    private fun modelReplacedLine(l: ChatLocal): ChatStatusUi? =
        world.models.value.replaced
            ?.takeIf { it != l.dismissedModelReplaced }
            ?.let { replaced ->
                ChatStatusUi(
                    id = MODEL_REPLACED_STATUS,
                    kind = ChatStatusKind.ModelReplaced,
                    oldModel = replaced.oldLabel,
                    newModel = replaced.newLabel,
                )
            }

    private fun agentLine(line: ChatStatusUi?, l: ChatLocal): ChatStatusUi? =
        if (line == null || !world.serverIsAgentHelper ||
            (line.kind != ChatStatusKind.Offline && line.kind != ChatStatusKind.Connecting)
        ) line
        else line.copy(canRestartAgent = true, startingAgent = l.startingAgent, agentFailed = l.agentFailed)

    /**
     * The page's state for these inputs. A new [ChatUi.revision] only when something in it changed (c2-performance,
     * 2026-11-15): every input that ticks (the list leaving or reaching the live edge, a retry fact, a server wait)
     * re-ran this, and a revision bumped every time made each run a new state, so the whole conversation page was
     * composed again (about 170 scopes) for nothing on screen. An equal result keeps the last state, and StateFlow
     * then hands the screen nothing.
     */
    internal fun present(i: ChatInputs, l: ChatLocal, found: FoundHits): ChatUi {
        val built = build(i, l, found)
        val last = lastUi
        if (last != null && last == built.copy(revision = last.revision)) return last
        return built.copy(revision = ++revision).also { lastUi = it }
    }

    private var lastUi: ChatUi? = null

    private fun build(i: ChatInputs, l: ChatLocal, found: FoundHits): ChatUi {
        // Hits made for another query (the previous one, while the new one is searched) count for nothing: no mark, no
        // jump, no step, no count until the new query's own hits arrive (issue 147).
        val searching = l.findQuery != null && found.query != l.findQuery
        val hits = if (found.query == l.findQuery) found.hits else persistentListOf()
        lastHits = hits
        // N-47: when the server goes away after the conversation was read, keep the last transcript and title on
        // screen with one offline line (Try again retries the load) instead of replacing them with an error.
        if (i.load == ChatLoad.Live && i.transcript.rows.isNotEmpty()) lastLive = i.transcript
        i.title?.let { lastTitle = it }
        // Cache-first: rows kept from a past visit count as last-known too (the world holds them while it cannot load).
        val kept = if (i.load is ChatLoad.Failed) lastLive ?: i.transcript.takeIf { it.rows.isNotEmpty() } else null
        val t = kept ?: i.transcript
        val cursor = TranscriptFind.keep(hits, l.findKey, l.findCursor)
        val hit = hits.getOrNull(cursor)
        val find =
            l.findQuery?.let { q ->
                FindUi(
                    q,
                    hits.size,
                    cursor,
                    hit?.rowKey,
                    hit?.let { t.indexOf(it.rowKey) } ?: -1,
                    partial = world.hasOlder.value,
                    searchingAll = l.searchingAll,
                    loadingOlder = l.loadingOlder,
                    olderFailure = l.olderFailure,
                    currentOrdinal = TranscriptFind.ordinalOf(hits, cursor),
                    currentSource = hit?.sourceId,
                    searching = searching,
                )
            }
        val arrival =
            l.arrival
                ?.takeIf { !it.landOnFailure || i.load == ChatLoad.Live }
                ?.let { a ->
                    val land =
                        (a.rowKey ?: if (a.landOnFailure) Landing.newestFailedKey(t.rows) else null)
                            ?.let(t::indexOf)
                            ?.takeIf { it >= 0 }
                    ChatArrivalUi(a.id, a.focusComposer, land)
                }
        val pending = withSignIn(withSetUp(i.pending, firstMessage = t.rows.isEmpty()))
        return ChatUi(
            revision = revision,
            ref = world.ref,
            title = i.title ?: lastTitle,
            status =
              agentLine(
                stagedFirst(
                // Issue 147: an old phone agent conversation that continues in a new one is not offline; its message
                // box says what happens instead.
                (if (kept != null && world.readOnly.value?.restart == null) i.retry.line(KEPT_OFFLINE_STATUS, ChatStatusKind.Offline) else null) ?: StatusPick.pick(i.link, pending, t.run, t.rows, l.actionFailure, l.linkRefused, i.retry)
                    ?: l.receiptResult?.let {
                        ChatStatusUi(
                            RECEIPT_STATUS,
                            ChatStatusKind.ReceiptChecked,
                            receiptResult = it,
                        )
                    }
                    ?: undoneLine(world.undone.value)
                    ?: world.parent.value?.let { ChatStatusUi(SUBAGENT_STATUS, ChatStatusKind.Subagent) }
                    ?: modelReplacedLine(l),
                ),
                l,
              ),
            phase =
                when (val load = i.load) {
                    // Cache-first: known rows draw the real transcript while the live read is on its way. So do the
                    // person's own messages on their way (open-transitions: a conversation just started from New
                    // conversation opened on a skeleton of made-up turns instead of the message just typed): the
                    // skeleton is only for a conversation of which nothing is known.
                    ChatLoad.Loading -> if (t.rows.isNotEmpty() || pending.isNotEmpty()) ChatPhase.Live else ChatPhase.Loading
                    ChatLoad.Live -> ChatPhase.Live
                    is ChatLoad.Failed ->
                        if (
                            kept != null ||
                                (load.failure !is Failure.NotFound &&
                                    pending.any { it.state == PendingState.WaitingForConnection })
                        )
                            ChatPhase.Live
                        else ChatPhase.Failed(load.failure, loadAgent())
                },
            rows =
                if (l.withdrawn.isEmpty() && l.hiddenStopNote == null) t.rows
                else
                    t.rows
                        .filterNot {
                            it.key in l.withdrawn ||
                                (l.hiddenStopNote != null && it is PromptRow && it.text.trim() == l.hiddenStopNote)
                        }
                        .toImmutableList(),
            newestRowKey = t.newestKey,
            canPutBack = world.undone.value != ChatUndone.None,
            canBackground = canBackground(t.run.busy, l),
            live =
                if (t.run.busy)
                    LiveTurnUi(
                        l.stopping,
                        stoppingAfterStep = l.stopAfterStep,
                        joinsTurn =
                            i.capabilities.promptSteering ||
                                world.midTurn() == io.github.eslamasabry.oc.core.model.MidTurnPrompt.JoinsTurn,
                        agentName = world.models.value.agent,
                        canUndoStopAfterStep =
                            l.stopAfterStep && l.stopNote != null && !l.undoingStopNote &&
                                i.capabilities.inbox,
                        canStopAfterStep =
                            !l.stopping && !l.stopAfterStep && i.capabilities.promptSteering,
                    )
                else null,
            pending = pendingShown(drawnOnce(pending, t, l)),
            find = find,
            menu = l.menu,
            confirm = l.confirm,
            clip = l.clip,
            copied = l.copied,
            arrival = arrival,
            capabilities = capsOf(i.capabilities),
            guardBack =
                BackOrder.handles(
                    l.menu != null || l.select != null || l.picture != null,
                    l.confirm != null,
                    l.findQuery != null,
                    i.draftUnsaved,
                ),
            select = l.select,
            insert = l.insert,
            picture = l.picture,
            readAloud = l.readAloud,
            confirmedPaths = l.confirmedPaths,
            reasoningExpanded = l.reasoningExpanded,
            timeline =
                l.timelineQuery?.let { query ->
                    TimelineUi(
                        query,
                        timelineEntries(feed.value, t, query, i.capabilities.sessionFork),
                        world.hasOlder.value,
                        l.loadingOlder,
                        l.olderFailure,
                    )
                },
            older = if (world.hasOlder.value) OlderUi(l.loadingOlder, l.olderFailure) else null,
            receiptChecks = receiptChecksShown(l.receiptChecks, i.pending),
            limitSwitch = limitOffers(t, i.capabilities, l.switches),
            switchOnline = i.link == ChatLink.Live,
            switching = l.switching,
            attachAgain = l.attachAgain,
            replyAsk =
                l.replyAsk?.takeIf {
                    !t.run.busy && i.link == ChatLink.Live && world.requests.value?.state?.value?.card == null
                },
            setUpReplies = setUpReplies(t.rows),
            signInTurns = signInTurns(t.rows, waiting = i.pending.isNotEmpty()),
        )
    }

    /** B-458: the sign-in of this chat's phone agents right now, by provider id ([ChatOutside.agentSignIns]). */
    private var signInsNow: Map<String, Boolean> = emptyMap()
    private var lastSignInTurns: kotlinx.collections.immutable.ImmutableMap<String, SignInTurn> = persistentMapOf()

    private fun signInChanges(): Flow<Map<String, Boolean>> {
        val port = outside
        val owner = signInOwner()
        return if (port == null || owner == null) flowOf(emptyMap())
        else port.agentSignIns(owner).onStart { emit(emptyMap()) }
    }

    /**
     * B-458 (owner, build 10086: the old turn still offered "Sign in again" after the sign-in worked): each stopped
     * turn whose agent's sign-in ran out, newest first. While the agent reads signed in now, every such turn is past
     * ("needed you to sign in again", no button), and the newest one also offers to send its message again when
     * nothing was sent after it ([waiting]: a message on its way counts). While it needs a sign-in (or is not known
     * yet), only the newest such turn of that agent offers Sign in again; older ones are past. Decided from the agent's
     * state every time the transcript is read, so it holds after the app is opened again.
     */
    private fun signInTurns(
        rows: ImmutableList<TranscriptRow>,
        waiting: Boolean,
    ): kotlinx.collections.immutable.ImmutableMap<String, SignInTurn> {
        val turns: Map<String, SignInTurn> = if (signInOwner() == null) emptyMap() else signInTurnMap(rows, waiting)
        if (turns != lastSignInTurns) lastSignInTurns = turns.toPersistentMap()
        return lastSignInTurns
    }

    /** [signInTurns]' reading of the transcript, newest first. */
    private fun signInTurnMap(rows: ImmutableList<TranscriptRow>, waiting: Boolean): Map<String, SignInTurn> {
        val runtime = world.runtimeId()
        val newestPrompt = rows.firstOrNull { it is PromptRow }?.turnId
        val offered = HashSet<String>()
        val turns = LinkedHashMap<String, SignInTurn>()
        for (failed in failedTurns(rows)) {
            val footer = failed.footer
            if (footer.reason?.let(AgentErrorText::signInExpired) != true) continue
            val provider = runtime ?: footer.providerId ?: continue
            turns[footer.turnId] = signInTurnOf(provider, last = footer.turnId == newestPrompt && !waiting, offered)
        }
        return turns
    }

    /**
     * One stopped sign-in turn of [provider]: [last] = nothing was sent after it; [offered] = agents already offered.
     */
    private fun signInTurnOf(provider: String, last: Boolean, offered: MutableSet<String>): SignInTurn =
        when {
            signInsNow[provider] == true -> if (last) SignInTurn.PastSendAgain else SignInTurn.Past
            offered.add(provider) -> SignInTurn.Offer
            else -> SignInTurn.Past
        }

    private var lastPending: ImmutableList<PendingSend> = persistentListOf()

    /** Set up at first use: whether the agent was set up when each counted failure came (by bubble). */
    private val setUpAtFailure = HashMap<Long, Boolean>()

    /**
     * OMP-OPEN: who the load-failure page names. A conversation of this phone's agents names its agent (its runtime,
     * as the gateway knows it) and offers that agent's set-up where this phone has a set-up terminal for it; a server's
     * own conversation names nobody here (null), as before.
     */
    private fun loadAgent(): ChatLoadAgent? {
        if (!world.serverIsAgentHelper) return null
        val agent = world.runtimeId()?.let(AgentSetUpRules::catalogAgent)
        val canSetUp = agent != null && outside?.phoneAgentOwner(world.ref) != null
        return ChatLoadAgent(agent?.name, agent?.id?.raw?.takeIf { canSetUp })
    }

    /** The set-up list of this conversation's phone owner changed: the bubbles are read again. */
    private fun setUpChanges(): Flow<Unit> {
        val port = outside ?: return flowOf(Unit)
        val owner = port.phoneAgentOwner(world.ref) ?: return flowOf(Unit)
        return port.agentsSetUpChanges(owner).onStart { emit(Unit) }
    }

    /** Set up at first use: whether the agent was set up when each failed reply came (by turn). */
    private val replySetUpAtFailure = HashMap<String, Boolean>()
    private var lastSetUpReplies: kotlinx.collections.immutable.ImmutableMap<String, AgentSetUpNeed> = persistentMapOf()

    /**
     * Set up at first use, the reply seam ([AgentSetUpRules.replyNeed]): a failed reply of a phone agent that was never
     * set up (omp's first answer) says so. Only phone agents' conversations are read; the same map while equal.
     */
    private fun setUpReplies(rows: ImmutableList<TranscriptRow>): kotlinx.collections.immutable.ImmutableMap<String, AgentSetUpNeed> {
        val port = outside
        val owner = port?.phoneAgentOwner(world.ref)
        if (port == null || owner == null) {
            replySetUpAtFailure.clear()
            return persistentMapOf<String, AgentSetUpNeed>().also { lastSetUpReplies = it }
        }
        val failed = failedTurns(rows).map { it.footer }.filter { !it.reason.isNullOrBlank() }
        replySetUpAtFailure.keys.retainAll { turn -> failed.any { it.turnId == turn } }
        // Newest first: the oldest footer is the conversation's first answer, unless older history is not loaded.
        val firstTurn = if (world.hasOlder.value) null else rows.lastOrNull { it is FooterRow }?.turnId
        val setUp = port.agentsSetUp(owner)
        val runtime = world.runtimeId()
        noteAnswered(port, owner, rows, runtime, setUp)
        noteSignInLapsed(port, owner, rows, runtime)
        val needs = HashMap<String, AgentSetUpNeed>()
        for (footer in failed) {
            val agentRuntime = runtime ?: footer.providerId ?: continue
            val done = AgentSetUpRules.agentId(agentRuntime)?.let { it in setUp } ?: continue
            val before = replySetUpAtFailure.getOrPut(footer.turnId) { done }
            AgentSetUpRules.replyNeed(
                    agentRuntime,
                    footer.reason,
                    firstAnswer = footer.turnId == firstTurn,
                    setUpDone = before,
                    setUpSinceFailure = done && !before,
                )
                ?.let { needs[footer.turnId] = it }
        }
        if (needs == lastSetUpReplies) return lastSetUpReplies
        return needs.toPersistentMap().also { lastSetUpReplies = it }
    }

    /** The agents this chat already told the registry about ([noteAnswered]). */
    private val answeredNoted = HashSet<String>()

    /**
     * Seen set up: a reply of a set-up-at-first-use agent that came back well (its turn ended, not failed, not stopped)
     * is the agent working. Said once per agent, and only while it is not yet known set up ([setUp]).
     */
    private fun noteAnswered(
        port: ChatOutside,
        owner: ProfileId,
        rows: ImmutableList<TranscriptRow>,
        runtime: String?,
        setUp: Set<String>,
    ) {
        // The conversation's own runtime decides when known: any other agent's conversation reads nothing more.
        val known = runtime?.let { AgentSetUpRules.firstUseAgent(it) ?: return }
        if (known != null && (known in setUp || known in answeredNoted)) return
        val answered = rows.firstOrNull { it is FooterRow && !it.failed && !it.stopped } as FooterRow? ?: return
        val agent = known ?: AgentSetUpRules.firstUseAgent(answered.providerId ?: return) ?: return
        if (agent in setUp || !answeredNoted.add(agent)) return
        port.agentAnswered(owner, agent)
    }

    /** The turns this chat already reported as a lapsed sign-in ([noteSignInLapsed]). */
    private val lapseNoted = HashSet<String>()

    /**
     * Issue 147: the newest reply of a phone agent ended on its sign-in (Claude Code's "OAuth session expired and could
     * not be refreshed"): the phone's agents then read Sign in needed everywhere until a sign-in completes. Said once per
     * turn, with the reply's own time, so an old failure read after a newer sign-in changes nothing.
     */
    private fun noteSignInLapsed(
        port: ChatOutside,
        owner: ProfileId,
        rows: ImmutableList<TranscriptRow>,
        runtime: String?,
    ) {
        val newest = rows.firstOrNull { it is FooterRow } as FooterRow? ?: return
        // signin-truth: a reply that was the plan's usage limit is said as a limit, never as a sign-in.
        val limit = ChatSignInLapse.limit(newest)
        if (!ChatSignInLapse.lapsed(newest) && limit == null) return
        val provider = runtime ?: newest.providerId ?: return
        if (!lapseNoted.add(newest.turnId)) return
        val at = newest.createdMs ?: return
        if (limit != null) port.agentLimitReached(owner, provider, at, limit)
        else port.agentSignInLapsed(owner, provider, at)
    }

    /**
     * A failed send whose agent's sign-in ran out says so on its bubble ([AgentSetUpRules.signInNeed]), whichever kind of
     * conversation it is in: the reason is the agent's, not the connection's. Set up at first use wins when it applies.
     */
    private fun withSignIn(pending: ImmutableList<PendingSend>): ImmutableList<PendingSend> {
        if (pending.none(AgentSetUpRules::counts)) return pending
        val runtime = world.runtimeId() ?: return pending
        return pending
            .map { p ->
                if (p.setUp != null) return@map p
                val need = AgentSetUpRules.signInNeed(p, runtime)
                if (need == p.signIn) p else p.copy(signIn = need)
            }
            .toImmutableList()
    }

    /** Set up at first use ([AgentSetUpRules]): a failed send whose agent was never set up says so on its bubble. */
    private fun withSetUp(pending: ImmutableList<PendingSend>, firstMessage: Boolean): ImmutableList<PendingSend> {
        setUpAtFailure.keys.retainAll { id -> pending.any { it.localId == id && AgentSetUpRules.counts(it) } }
        if (pending.none(AgentSetUpRules::counts)) return pending
        val port = outside ?: return pending
        val owner = port.phoneAgentOwner(world.ref) ?: return pending
        val runtime = world.runtimeId() ?: return pending
        val done = AgentSetUpRules.agentId(runtime)?.let { it in port.agentsSetUp(owner) } ?: return pending
        return pending
            .map { p ->
                if (!AgentSetUpRules.counts(p)) return@map p
                val before = setUpAtFailure.getOrPut(p.localId) { done }
                val need =
                    AgentSetUpRules.need(p, runtime, firstMessage, before, setUpSinceFailure = done && !before)
                if (need == p.setUp) p else p.copy(setUp = need)
            }
            .toImmutableList()
    }
    private var lastReceiptChecks: PersistentMap<Long, ReceiptCheckUi> = persistentMapOf()

    /**
     * F2 (build 10113: the first message drawn twice for a frame): a bubble whose server row is shown is drawn as that
     * row only. A failed bubble keeps its own place (its Try again), and a row this chat hides never takes a bubble.
     */
    private fun drawnOnce(
        pending: ImmutableList<PendingSend>,
        t: TranscriptState,
        l: ChatLocal,
    ): ImmutableList<PendingSend> {
        fun shown(p: PendingSend): Boolean {
            val key =
                p.serverRowKey?.takeIf {
                    p.state != PendingState.Failed &&
                        p.state != PendingState.WaitingForConnection &&
                        it !in l.withdrawn
                } ?: return false
            val row = t.indexOf(key).takeIf { it >= 0 }?.let(t.rows::getOrNull) ?: return false
            return !(l.hiddenStopNote != null && row is PromptRow && row.text.trim() == l.hiddenStopNote)
        }
        return if (pending.none(::shown)) pending else pending.filterNot(::shown).toImmutableList()
    }

    /** Newest first; the same instance while the bubbles are equal, so readers of it skip. */
    private fun pendingShown(pending: ImmutableList<PendingSend>): ImmutableList<PendingSend> {
        if (pending.isEmpty() && lastPending.isEmpty()) return lastPending
        if (pending.size == lastPending.size && pending.asReversed() == lastPending) return lastPending
        return pending.asReversed().toImmutableList().also { lastPending = it }
    }

    /** The receipt checks of the bubbles still shown; the same instance while equal. */
    private fun receiptChecksShown(
        checks: PersistentMap<Long, ReceiptCheckUi>,
        pending: ImmutableList<PendingSend>,
    ): PersistentMap<Long, ReceiptCheckUi> {
        if (checks.isEmpty() && lastReceiptChecks.isEmpty()) return lastReceiptChecks
        val shown = checks.filterKeys { id -> pending.any { it.localId == id } }
        if (shown == lastReceiptChecks) return lastReceiptChecks
        return shown.toPersistentMap().also { lastReceiptChecks = it }
    }

    /** Issue 147 meter: rows the per-state walks over every row look at (tests read it). */
    internal var rowVisits: Long = 0
        private set

    /** A failed turn's footer and the prompt it answered (null: the turn has none). */
    private class FailedTurn(val footer: FooterRow, val prompt: PromptRow?)

    private var failedSource: List<TranscriptRow>? = null
    private var failedSettled: List<FailedTurn> = emptyList()

    /**
     * The failed turns, newest first. Issue 147: this runs for every state the chat presents, so on every publish while
     * a reply streams. What the settled turns hold is kept while the engine hands over the same settled rows
     * ([NewestFirst.settled]); only the newest turn's rows are walked again. A footer finds its prompt among its own
     * turn's rows, which sit next to it (it used to walk every row once per failed footer).
     */
    private fun failedTurns(rows: ImmutableList<TranscriptRow>): List<FailedTurn> {
        @Suppress("UNCHECKED_CAST")
        val settled = (rows as? NewestFirst<*>)?.settled as kotlinx.collections.immutable.PersistentList<TranscriptRow>?
        val live = failedIn(rows, if (settled == null) rows.size else rows.size - settled.size)
        if (settled == null) return live
        if (settled !== failedSource) {
            failedSettled = failedIn(NewestFirst(settled), settled.size)
            failedSource = settled
        }
        return if (live.isEmpty()) failedSettled else live + failedSettled
    }

    /** The failed footers among the newest [count] rows of [rows] (newest first), each with its turn's prompt. */
    private fun failedIn(rows: List<TranscriptRow>, count: Int): List<FailedTurn> {
        var out: ArrayList<FailedTurn>? = null
        for (i in 0 until count) {
            rowVisits++
            val footer = rows[i] as? FooterRow ?: continue
            if (!footer.failed) continue
            var prompt: PromptRow? = null
            var j = i + 1
            while (j < rows.size) {
                rowVisits++
                val row = rows[j]
                if (row.turnId != footer.turnId) break
                if (row is PromptRow) {
                    prompt = row
                    break
                }
                j++
            }
            (out ?: ArrayList<FailedTurn>().also { out = it }) += FailedTurn(footer, prompt)
        }
        return out ?: emptyList()
    }

    /** B-169: the limit offer of each failed turn. */
    private fun limitOffers(
        t: TranscriptState,
        caps: ServerCapabilities,
        switches: PersistentMap<String, Int>,
    ): PersistentMap<String, LimitSwitchUi> {
        if (!caps.promptModelChoice) return persistentMapOf()
        val now = clock?.now()?.toEpochMilliseconds()
        var out = persistentMapOf<String, LimitSwitchUi>()
        for (failed in failedTurns(t.rows)) {
            val row = failed.footer
            val count = failed.prompt?.let { switches[LimitSwitchRules.promptKey(it.text)] } ?: 0
            val offer = LimitSwitchRules.offer(row.reason, now, caps.promptModelChoice, count)
            if (offer != LimitSwitchOffer.None)
                out = out.put(row.turnId, LimitSwitchUi(offer, stoppedModelName(row.turnId)))
        }
        return out
    }

    /**
     * Read acknowledgement (plan 09 §6.7): on each move into "loaded and at the live edge", and
     * when a reply ends while it holds. Never while loading, failed or away from the newest end.
     */
    internal fun syncRead() {
        val allowed = ReadAck.allowed(world.load.value, local.value.atLiveEdge)
        val busy = world.transcript.value.run.busy
        val mark = allowed && (!readAllowed || (readBusy && !busy))
        readAllowed = allowed
        readBusy = busy
        if (mark)
            world.markReadIfVisible {
                ReadAck.allowed(world.load.value, local.value.atLiveEdge) &&
                    !world.transcript.value.run.busy
            }
    }

    // ---- Back (07 §3.3–§3.4) ----

    override fun leaveRequested() {
        val l = local.value
        if (l.timelineQuery != null) {
            closeTimeline()
            return
        }
        when (
            BackOrder.next(
                l.menu != null || l.select != null || l.picture != null,
                l.confirm != null,
                l.findQuery != null,
                world.draft.value.unsaved,
            )
        ) {
            BackStep.CloseMenu ->
                when {
                    l.menu != null -> closeMessageMenu()
                    l.select != null -> closeSelection()
                    else -> closePicture()
                }
            BackStep.CloseConfirm -> dismissConfirm()
            BackStep.CloseFind -> find(null)
            BackStep.Leave -> nav.back()
            BackStep.SaveDraftThenLeave -> {
                if (l.leaving) return
                local.update { it.copy(leaving = true) }
                launcher.launch {
                    val saved = world.saveDraft()
                    local.update {
                        it.copy(
                            leaving = false,
                            confirm =
                                if (saved is Outcome.Ok) it.confirm else ChatConfirm.LeaveUnsaved,
                        )
                    }
                    if (saved is Outcome.Ok) nav.back()
                }
            }
        }
    }

    override fun confirmLeave() {
        if (local.value.confirm != ChatConfirm.LeaveUnsaved) return
        local.update { it.copy(confirm = null) }
        nav.back()
    }

    override fun dismissConfirm() {
        local.update { it.copy(confirm = null, switchAsk = null) }
    }

    // ---- Load, stop, sends ----

    override fun retryLoad() {
        startHelperThen { reloadNow() }
    }

    private fun reloadNow() {
        launcher.launch { world.reload() }
    }

    override fun tryLinkNow() {
        startHelperThen(::reconnectNow)
    }

    private fun reconnectNow() {
        world.tryLinkNow()
        if (world.load.value is ChatLoad.Failed) reloadNow()
    }

    override fun restartAgent() {
        if (!world.serverIsAgentHelper) return
        startHelperThen(::reconnectNow)
    }

    /**
     * Reconnecting to the phone agents' server while its link is down first makes sure the helper runs (a dead
     * helper otherwise fails against a closed port). One start per tap, none automatic; [next] runs only after it.
     */
    private fun startHelperThen(next: () -> Unit) {
        if (!world.serverIsAgentHelper || world.link.value == ChatLink.Live) return next()
        if (local.value.startingAgent) return
        local.update { it.copy(startingAgent = true, agentFailed = false) }
        launcher.launch {
            val started = world.startAgentHelper()
            local.update { it.copy(startingAgent = false, agentFailed = started is Outcome.Failed) }
            if (started is Outcome.Ok) next()
        }
    }

    override fun restartPhoneServer() {
        if (!world.serverOnThisPhone) return
        local.update { it.copy(confirm = ChatConfirm.RestartPhoneServer) }
    }

    override fun startPhoneServer() {
        if (!world.serverOnThisPhone) return
        launcher.launch {
            val result = world.startPhoneServer()
            if (result is Outcome.Failed) local.update { it.copy(actionFailure = result.failure) }
        }
    }

    override fun confirmRestartPhoneServer() {
        if (local.value.confirm !is ChatConfirm.RestartPhoneServer) return
        local.update { it.copy(confirm = null) }
        launcher.launch {
            val result = world.restartPhoneServer()
            if (result is Outcome.Failed) local.update { it.copy(actionFailure = result.failure) }
        }
    }

    override fun reportProblem() {
        nav.open(io.github.eslamasabry.oc.ui.routes.SettingsRoute.ReportProblem)
    }

    override fun retryLastPrompt() {
        val row = newestPrompt() ?: return
        val text = row.text.takeIf { it.isNotBlank() } ?: return
        // B8 (Flutter `_retryLast` returns while `_sending`): a second tap before the copy lands sends nothing.
        if (resentTurn == row.turnId) return
        resentTurn = row.turnId
        // The prompt as the transcript holds it, with its files (Flutter `_retryLast` carries them), on the
        // conversation's current agent and model as Flutter sends it; a file that can no longer be read leaves the
        // words alone, as before.
        val prompt =
            world.stoppedPrompt(row.turnId)
                ?.takeIf { it.text.isNotBlank() && it.unreadable.isEmpty() }
                ?.copy(agent = null)
        launcher.launch {
            val sent = if (prompt != null) world.sendAgainWith(prompt) else world.sendAgain(text)
            if (sent is Outcome.Failed) {
                resentTurn = null
                local.update { it.copy(actionFailure = sent.failure) }
            }
        }
    }

    override fun undoLastPrompt() {
        val row = newestPrompt() ?: return
        val id = RowIds.messageIdOf(row) ?: return
        nav.open(ReviewRoute.UndoFromHere(world.ref, id))
    }

    override fun openNewConversation() {
        nav.open(io.github.eslamasabry.oc.ui.routes.ChatsRoute.New(profile = world.ref.profileId))
    }

    override fun openConversations() {
        nav.land()
    }

    private fun newestPrompt(): PromptRow? =
        world.transcript.value.rows.firstOrNull { it is PromptRow } as? PromptRow

    /**
     * RC1: the chat's request cards follow its own Stop at once. From the tap a waiting card reads "No longer needed ·
     * The run is ending, so nothing was sent." and takes no answer; the server's confirmation only removes it, and a
     * refused Stop brings it back.
     */
    private fun syncRunEnding() {
        world.requests.value?.runEnding(local.value.stopping)
    }

    override fun stop() {
        if (local.value.stopping) return
        stopInFlight = true
        local.update { it.copy(stopping = true, actionFailure = null) }
        syncRunEnding()
        launcher.launch {
            val result = world.stopTurn()
            stopInFlight = false
            // B10: an accepted Stop keeps "Stopping…" until the server ends the turn; a refused one
            // says why.
            // Codex chat-bugs 2: a tap the guard after a send ignored asked nothing, so Stop stays
            // usable.
            val failure = (result as? Outcome.Failed)?.failure
            val asked = (result as? Outcome.Ok)?.value == true
            local.update {
                it.copy(
                    stopping = asked && world.transcript.value.run.busy,
                    actionFailure = failure,
                )
            }
            syncRunEnding()
        }
    }

    /** The turn ended after an accepted Stop: the stopping state settles (B10). */
    internal fun settleStop() {
        if (!stopInFlight && local.value.stopping && !world.transcript.value.run.busy) {
            local.update { it.copy(stopping = false) }
            syncRunEnding()
        }
        // The turn ended: the stop note did its work (or the turn finished first).
        if (local.value.stopAfterStep && !world.transcript.value.run.busy)
            local.update { it.copy(stopAfterStep = false, stopNote = null) }
    }

    override fun stopAfterStep(note: String) {
        val l = local.value
        if (note.isBlank() || l.stopping || l.stopAfterStep) return
        if (!world.transcript.value.run.busy || !world.capabilities.value.promptSteering) return
        local.update { it.copy(stopAfterStep = true, stopNote = note, hiddenStopNote = note.trim()) }
        launcher.launch {
            val sent = world.sendAgain(note)
            if (sent is Outcome.Failed)
                local.update { it.copy(stopAfterStep = false, stopNote = null, actionFailure = sent.failure) }
        }
    }

    /**
     * C6 (Flutter `_stopForSteer`): Stop on the "{agent} reads this when this step finishes" note. Stops the step the
     * message waits on, then sends the same words again so the agent reads them at once; the stopped copy is left out
     * of the chat so the words show once. Nothing happens when the step already ended (the agent has the message).
     */
    override fun stopForSteer(promptKey: String, words: String) {
        if (local.value.stopping || words.isBlank() || !world.transcript.value.run.busy) return
        stopInFlight = true
        local.update { it.copy(stopping = true, actionFailure = null) }
        syncRunEnding()
        launcher.launch {
            val result = world.stopTurn()
            stopInFlight = false
            val failure = (result as? Outcome.Failed)?.failure
            val asked = (result as? Outcome.Ok)?.value == true
            local.update { it.copy(stopping = asked && world.transcript.value.run.busy, actionFailure = failure) }
            syncRunEnding()
            // Too late: the stop was not asked, or the agent started on the message in the last moment.
            if (!asked || !SteerWaitRules.unreadAfterStop(world.transcript.value.rows, promptKey)) return@launch
            val sent = world.sendAgain(words.trim())
            if (sent is Outcome.Failed) local.update { it.copy(actionFailure = sent.failure) }
            else {
                local.update { it.copy(withdrawn = it.withdrawn.add(promptKey)) }
                world.rememberWithdrawn(promptKey)
            }
        }
    }

    override fun undoStopAfterStep() {
        val l = local.value
        val note = l.stopNote ?: return
        if (!l.stopAfterStep || l.undoingStopNote) return
        local.update { it.copy(undoingStopNote = true) }
        launcher.launch {
            // True: the agent never read it, the turn carries on. False: too late (or no inbox), the stop stays.
            val withdrawn = world.withdrawNote(note)
            local.update {
                if (withdrawn) it.copy(stopAfterStep = false, stopNote = null, undoingStopNote = false)
                else it.copy(stopNote = null, undoingStopNote = false)
            }
        }
    }

    private val resendingTurns = HashSet<String>()

    override fun useModelAndResend(turnId: String, providerId: String, modelId: String) {
        val text =
            if (turnId in resendingTurns) null
            else
                (world.transcript.value.rows.firstOrNull { it is PromptRow && it.turnId == turnId } as? PromptRow)
                    ?.text
                    ?.takeIf { it.isNotBlank() }
        val choice =
            if (text == null) null
            else
                world.models.value.choices.firstOrNull {
                    it.providerId == providerId && it.modelId == modelId
                }
        if (text == null || choice == null || !resendingTurns.add(turnId)) return
        launcher.launch {
            try {
                when (val picked = world.selectModel(choice)) {
                    // A refused switch keeps the old model and sends nothing.
                    is Outcome.Failed -> local.update { it.copy(actionFailure = picked.failure) }
                    is Outcome.Ok -> world.sendAgain(text)
                }
            } finally {
                resendingTurns.remove(turnId)
            }
        }
    }

    // ---- B-169: switch model when a limit stops a reply ----

    private val usedUp = UsedUpModels()

    /** The catalog's name of the model [turnId] stopped on, else one made from its id. */
    private fun stoppedModelName(turnId: String): String? {
        val key = world.stoppedPrompt(turnId)?.model ?: return null
        return world.models.value.choices
            .firstOrNull { it.providerId == key.providerId && it.modelId == key.modelId }
            ?.label
            ?: ReplyModelNames.fromId(key.modelId)
    }

    private fun nowMs(): Long? = clock?.now()?.toEpochMilliseconds()

    override fun switchModel(turnId: String, resetsAtMs: Long?) {
        val prompt = world.stoppedPrompt(turnId)?.takeIf { canSwitch(it) } ?: return
        prompt.model?.let { usedUp.mark(it, resetsAtMs) }
        val recent = world.models.value.recent.map(io.github.eslamasabry.oc.feature.chat.world.ModelKey::of)
        local.update {
            it.copy(
                switching = SwitchUi(turnId, prompt.model, usedUp.all(nowMs()), prompt.contextTokens, recent),
                attachAgain = null,
            )
        }
    }

    /** Online, on a server whose prompts carry their model, and not yet switched as often as it may be (§5, §6). */
    private fun canSwitch(prompt: StoppedPrompt): Boolean =
        world.link.value == ChatLink.Live &&
            world.capabilities.value.promptModelChoice &&
            (local.value.switches[LimitSwitchRules.promptKey(prompt.text)] ?: 0) < LimitSwitchRules.MAX_SWITCHES

    override fun dismissSwitch() {
        local.update { it.copy(switching = null) }
    }

    override fun switchTo(providerId: String, modelId: String) {
        val open = local.value.switching ?: return
        val choice = catalogModel(providerId, modelId)
        val prompt = world.stoppedPrompt(open.turnId)
        if (choice == null || prompt == null || !switchable(open, choice, prompt)) return
        if (prompt.toolSteps > 0) {
            local.update {
                it.copy(
                    switching = null,
                    switchAsk = open.turnId to io.github.eslamasabry.oc.feature.chat.world.ModelKey.of(choice),
                    confirm = ChatConfirm.SendAgainAfterSteps(prompt.toolSteps, choice.label),
                )
            }
        } else {
            local.update { it.copy(switching = null) }
            sendSwitched(prompt, choice)
        }
    }

    /** Not the stopped or a used-up model, offered by the catalog, and (the context guard) long enough. */
    private fun switchable(
        open: SwitchUi,
        choice: io.github.eslamasabry.oc.feature.chat.world.ComposerModel,
        prompt: StoppedPrompt,
    ): Boolean {
        val key = io.github.eslamasabry.oc.feature.chat.world.ModelKey.of(choice)
        return choice.enabled &&
            key != open.failed &&
            key !in open.usedUp &&
            !SwitchModelRules.tooLong(choice, prompt.contextTokens)
    }

    override fun confirmSwitch() {
        val ask = local.value.switchAsk?.takeIf { local.value.confirm is ChatConfirm.SendAgainAfterSteps } ?: return
        local.update { it.copy(confirm = null, switchAsk = null) }
        val choice = catalogModel(ask.second.providerId, ask.second.modelId)
        val prompt = world.stoppedPrompt(ask.first)
        if (choice != null && prompt != null) sendSwitched(prompt, choice)
    }

    private fun catalogModel(providerId: String, modelId: String) =
        world.models.value.choices.firstOrNull { it.providerId == providerId && it.modelId == modelId }

    /** Selects [choice] as the model chip does, then sends [prompt] again with it (design §4.1–4.3). */
    private fun sendSwitched(prompt: StoppedPrompt, choice: io.github.eslamasabry.oc.feature.chat.world.ComposerModel) {
        launcher.launch {
            when (val picked = world.selectModel(choice)) {
                // A refused switch keeps the old model and sends nothing.
                is Outcome.Failed -> local.update { it.copy(actionFailure = picked.failure) }
                is Outcome.Ok -> {
                    val missing = prompt.unreadable.firstOrNull()
                    if (missing != null) {
                        // A file that can no longer be read: the person sends it by hand, with the file again.
                        world.replaceAttachments(prompt.attachments - prompt.unreadable.toSet())
                        local.update {
                            it.copy(
                                insert = ClipUi(nextId++, prompt.text),
                                attachAgain = AttachAgainUi(prompt.turnId, missing.filename.ifBlank { missing.mime }),
                            )
                        }
                        return@launch
                    }
                    val promptKey = LimitSwitchRules.promptKey(prompt.text)
                    local.update { it.copy(switches = it.switches.put(promptKey, (it.switches[promptKey] ?: 0) + 1)) }
                    val sent = world.sendAgainWith(prompt)
                    if (sent is Outcome.Failed) local.update { it.copy(actionFailure = sent.failure) }
                }
            }
        }
    }

    override fun continueReply(words: String) {
        if (words.isBlank()) return
        launcher.launch { world.sendAgain(words) }
    }

    /** A contributed chat action failed before navigation or dispatch. */
    public fun actionFailed(failure: Failure) {
        local.update { it.copy(actionFailure = failure) }
    }

    override fun checkReceipt(localId: Long) {
        val target =
            world.pending.value.firstOrNull {
                it.localId == localId && it.state == PendingState.Unconfirmed && it.canCheckReceipt
            } ?: return
        if (local.value.receiptChecks[localId]?.checking == true) return
        local.update {
            val previous = it.receiptChecks[localId] ?: ReceiptCheckUi()
            it.copy(
                receiptChecks =
                    it.receiptChecks.putting(
                        localId,
                        previous.copy(checking = true, result = null),
                    ),
                receiptResult = null,
            )
        }
        launcher.launch {
            try {
                val looked = world.checkReceipt(localId)
                val stillHere =
                    world.pending.value.any {
                        it.localId == localId &&
                            it.text == target.text &&
                            it.state == PendingState.Unconfirmed
                    }
                val failure = (looked as? Outcome.Failed)?.failure
                // Busy: another check of this bubble is already running and will announce its
                // answer.
                val stale =
                    (failure is Failure.Refused && failure.reason == RefusalReason.StaleScope) ||
                        failure is Failure.Busy
                // The world may remove a confirmed bubble. Any other vanished/replaced target has
                // nothing to announce.
                val confirmed = looked is Outcome.Ok && looked.value
                if (stale || (!confirmed && !stillHere)) {
                    local.update { it.copy(receiptChecks = it.receiptChecks.removing(localId)) }
                    return@launch
                }
                val result =
                    when {
                        confirmed -> ReceiptCheckResult.Confirmed
                        looked is Outcome.Failed &&
                            (looked.failure is Failure.StorageRefused ||
                                looked.failure is Failure.SecretUnavailable) ->
                            ReceiptCheckResult.StorageProblem
                        else -> ReceiptCheckResult.StillUnknown
                    }
                val checkedAt =
                    if (result == ReceiptCheckResult.StillUnknown)
                        clock?.now()?.toEpochMilliseconds()
                    else local.value.receiptChecks[localId]?.checkedAtMs
                local.update {
                    it.copy(
                        receiptChecks =
                            it.receiptChecks.putting(
                                localId,
                                ReceiptCheckUi(checkedAtMs = checkedAt, result = result),
                            ),
                        // A remaining bubble already presents this check's result and button.
                        // Only confirmation, which removes the bubble, needs a status notice.
                        receiptResult = result.takeIf { confirmed },
                    )
                }
            } finally {
                local.update {
                    val check = it.receiptChecks[localId]
                    if (check?.checking == true)
                        it.copy(
                            receiptChecks =
                                it.receiptChecks.putting(localId, check.copy(checking = false))
                        )
                    else it
                }
            }
        }
    }

    override fun retrySend(localId: Long) {
        launcher.launch { world.retrySend(localId) }
    }

    override fun cancelWaitingSend(localId: Long) {
        launcher.launch { world.cancelWaitingSend(localId) }
    }

    /** Set up at first use: the same set-up terminal the Agents page opens, for this conversation's phone owner. */
    override fun setUpAgent(agentId: String) {
        val owner = outside?.phoneAgentOwner(world.ref) ?: return
        nav.open(io.github.eslamasabry.oc.ui.routes.AgentsRoute.SignInTerminal(owner, agentId))
    }

    /**
     * B-AGENT-189: whose sign-in terminal this conversation's agents use. A phone agents conversation lives on the
     * `<owner>-agents` helper profile, which that terminal refuses: it is the phone owner's, the same owner Set up uses.
     * A conversation on the phone's own server is its own owner's; anything else has none.
     */
    private fun signInOwner(): io.github.eslamasabry.oc.core.common.ids.ProfileId? {
        val port = outside ?: return null
        return port.phoneAgentOwner(world.ref) ?: world.ref.profileId.takeIf { port.isPhoneServer(world.ref) }
    }

    override fun phoneAgentSignIn(): Boolean = signInOwner() != null

    override fun openAgentSignIn(providerId: String) {
        val owner = signInOwner() ?: return
        nav.open(io.github.eslamasabry.oc.ui.routes.AgentsRoute.SignInTerminal(owner, providerId))
    }

    override fun dismissStatus(id: String) {
        if (id == RECEIPT_STATUS) local.update { it.copy(receiptResult = null) }
        if (id == StatusPick.ACTION) local.update { it.copy(actionFailure = null) }
        if (id == StatusPick.LINK) local.update { it.copy(linkRefused = false) }
        if (id == MODEL_REPLACED_STATUS) {
            local.update { it.copy(dismissedModelReplaced = world.models.value.replaced) }
        }
    }

    private fun capsOf(c: ServerCapabilities): ChatCapabilitiesUi =
        ChatCapabilitiesUi(
            fork = c.sessionFork,
            deleteMessage = c.messageDelete,
            undo = c.sessionRevert,
            changes = true,
            compact = c.sessionCompact,
            shell = c.conversationShellOn,
            files = c.fileBrowsing,
            slashCommands = c.slashCommands,
            terminal = c.terminal,
            serverCatalog = c.serverCatalog,
            agents = c.agentSelection,
            approvals = c.permissionRequests,
            health = c.languageServiceStatus || c.formatterStatus,
            subagents = c.subagentSessions,
        )

    override fun openPlace(place: ChatPlace) {
        val ref = world.ref
        val profile = ref.profileId
        val directory = world.directory
        val route: io.github.eslamasabry.oc.ui.routes.AppRoute =
            when (place) {
                ChatPlace.Workspaces ->
                    directory?.let {
                        io.github.eslamasabry.oc.ui.routes.ProjectsRoute.Project(
                            profile,
                            it,
                            initialWorkspace = true,
                        )
                    } ?: io.github.eslamasabry.oc.ui.routes.ProjectsRoute.Picker(profile)
                ChatPlace.Health ->
                    io.github.eslamasabry.oc.ui.routes.SearchRoute.ProjectHealth(
                        profile,
                        directory ?: return,
                    )
                ChatPlace.Terminal -> io.github.eslamasabry.oc.ui.routes.TerminalRoute.Conversation(ref)
                ChatPlace.McpServers -> integration(ServerIntegrationPage.Mcp)
                ChatPlace.Providers -> integration(ServerIntegrationPage.Providers)
                ChatPlace.Skills -> integration(ServerIntegrationPage.Skills)
                ChatPlace.References -> integration(ServerIntegrationPage.References)
                ChatPlace.ServerStatus -> io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute(profile)
                ChatPlace.Diagnostics -> io.github.eslamasabry.oc.ui.routes.SettingsRoute.ReportProblem
                ChatPlace.Appearance ->
                    io.github.eslamasabry.oc.ui.routes.SettingsRoute.Target("settings-appearance")
            }
        nav.open(route)
    }

    private fun integration(page: ServerIntegrationPage) =
        io.github.eslamasabry.oc.ui.routes.ServerIntegrationRoute(world.ref.profileId, page)

    // ---- Find (plan 09 §6.6) ----

    override fun find(query: String?) {
        if (query == null) cancelSearchAll()
        local.update {
            it.copy(findQuery = query, findKey = null, findCursor = if (query == null) -1 else 0)
        }
    }

    override fun findNext(): Unit = stepFind(+1)

    override fun findPrevious(): Unit = stepFind(-1)

    private fun stepFind(by: Int) {
        val l = local.value
        if (l.findQuery == null) return
        val hits = lastHits
        if (hits.isEmpty()) return
        val next =
            TranscriptFind.step(hits.size, TranscriptFind.keep(hits, l.findKey, l.findCursor), by)
        local.update { it.copy(findKey = hits[next], findCursor = next) }
    }

    override fun loadOlder() {
        if (local.value.loadingOlder) return
        local.update { it.copy(loadingOlder = true, olderFailure = null) }
        launcher.launch {
            try {
                val result = world.loadOlder()
                if (result is Outcome.Failed)
                    local.update { it.copy(olderFailure = result.failure) }
            } finally {
                local.update { it.copy(loadingOlder = false) }
            }
        }
    }

    override fun searchAllHistory() {
        if (local.value.findQuery == null || local.value.loadingOlder || local.value.searchingAll)
            return
        val generation = ++historySearchGeneration
        local.update { it.copy(searchingAll = true, olderFailure = null) }
        launcher.launch {
            try {
                val visited = HashSet<String?>()
                while (
                    generation == historySearchGeneration &&
                        local.value.findQuery != null &&
                        world.hasOlder.value
                ) {
                    val page = world.olderPageKey
                    if (!visited.add(page)) {
                        local.update { it.copy(olderFailure = Failure.Unexpected()) }
                        break
                    }
                    local.update { it.copy(loadingOlder = true) }
                    when (val result = world.loadOlder()) {
                        is Outcome.Failed -> {
                            local.update { it.copy(olderFailure = result.failure) }
                            break
                        }
                        is Outcome.Ok -> Unit
                    }
                }
            } finally {
                local.update { it.copy(loadingOlder = false, searchingAll = false) }
            }
        }
    }

    override fun cancelSearchAll() {
        historySearchGeneration++
        local.update { it.copy(searchingAll = false) }
    }

    override fun toggleReasoning() {
        local.update { it.copy(reasoningExpanded = !it.reasoningExpanded) }
    }

    override fun compactAgain() {
        if (world.transcript.value.run.busy || !world.capabilities.value.sessionCompact) return
        launcher.launch {
            val result = world.compact()
            if (result is Outcome.Failed) local.update { it.copy(actionFailure = result.failure) }
        }
    }

    /** S30 story 3: reads what the server can move to the background; a failure leaves Background unoffered. */
    public suspend fun loadBackgroundSupport() {
        val support = world.backgroundWorkSupport()
        if (support is Outcome.Ok) local.update { it.copy(backgroundSupport = support.value) }
    }

    /** Flutter `_canBackgroundWork`: a reply runs and has work the server can background, not yet sent there. */
    private fun canBackground(busy: Boolean, l: ChatLocal): Boolean =
        busy &&
            !l.backgrounding &&
            world.readOnly.value == null &&
            BackgroundWork.parts(feed.value.ledger.messages, l.backgroundSupport).any {
                BackgroundWork.key(it) !in l.backgroundRequested
            }

    override fun backgroundWork() {
        val l = local.value
        if (!canBackground(world.transcript.value.run.busy, l)) return
        val keys = BackgroundWork.parts(feed.value.ledger.messages, l.backgroundSupport).map(BackgroundWork::key)
        local.update { it.copy(backgrounding = true) }
        launcher.launch {
            val result = world.backgroundWork()
            local.update {
                when {
                    result is Outcome.Failed -> it.copy(backgrounding = false, actionFailure = result.failure)
                    (result as Outcome.Ok).value == io.github.eslamasabry.oc.core.model.BackgroundWorkResult.Unchanged ->
                        it.copy(backgrounding = false)
                    else -> it.copy(backgrounding = false, backgroundRequested = it.backgroundRequested.addAll(keys))
                }
            }
            // Acknowledgement is not a job record: read the conversation again (Flutter reconciles the same way).
            if (result is Outcome.Ok) world.reload()
        }
    }

    /**
     * The running-work sheet's "go to what started it": the transcript lands on the message whose step started the
     * work (as a timeline pick does). A message the transcript does not hold (not loaded) changes nothing.
     */
    override fun showWorkStart(messageId: String) {
        val rowKey = messageRowKey(feed.value, world.transcript.value, messageId) ?: return
        local.update { it.copy(arrival = PendingArrival(nextId++, false, false, rowKey = rowKey)) }
    }

    override fun compactRequested() {
        if (world.transcript.value.run.busy || !world.capabilities.value.sessionCompact) return
        local.update { it.copy(confirm = ChatConfirm.Compact) }
    }

    override fun confirmCompact() {
        if (local.value.confirm != ChatConfirm.Compact) return
        local.update { it.copy(confirm = null) }
        compactAgain()
    }

    override fun openTimeline() {
        local.update { it.copy(timelineQuery = "", olderFailure = null) }
    }

    override fun closeTimeline() {
        local.update { it.copy(timelineQuery = null) }
    }

    override fun timelineQuery(query: String) {
        if (local.value.timelineQuery != null) local.update { it.copy(timelineQuery = query) }
    }

    override fun timelineSelect(messageId: String, fork: Boolean) {
        val query = local.value.timelineQuery ?: return
        val t = world.transcript.value
        val entry =
            timelineEntries(feed.value, t, query, world.capabilities.value.sessionFork)
                .firstOrNull { it.messageId == messageId } ?: return
        closeTimeline()
        if (fork) {
            if (entry.forkable)
                messageAction(
                    MessageTarget(
                        t.rows.firstOrNull { it.key == entry.rowKey }?.turnId ?: return,
                        entry.rowKey,
                        TargetKind.Prompt,
                        messageId,
                    ),
                    MessageAction.Fork,
                )
        } else {
            // Audit 1 (2026-11-14): a tap goes to that message and nothing else opens. It opened Find with the query,
            // whose first hit (inside a code block of another message) took the view instead of the message picked.
            local.update {
                it.copy(
                    arrival =
                        PendingArrival(
                            nextId++,
                            false,
                            false,
                            rowKey = entry.rowKey,
                        )
                )
            }
        }
    }

    // ---- Message actions (plan 09 §6.6) ----

    override fun openMessageMenu(rowKey: String) {
        val t = world.transcript.value
        val index = t.indexOf(rowKey).takeIf { it >= 0 } ?: return
        val row = t.rows[index]
        val kind = row.targetKind() ?: return
        val messageId =
            when (kind) {
                TargetKind.Prompt -> RowIds.messageIdOf(row)
                TargetKind.Reply -> replyMessageId(t, index)
            }
        val target = MessageTarget(row.turnId, rowKey, kind, messageId)
        val hasText = !textOf(t, target).isNullOrBlank()
        val actions =
            MessageActions.of(
                kind,
                capsOf(world.capabilities.value),
                t.run.busy,
                messageId,
                hasText,
                feel = outside != null,
            )
        if (actions.isEmpty()) return
        local.update { it.copy(menu = MessageMenuUi(target, actions), actionFailure = null) }
    }

    override fun closeMessageMenu() {
        local.update { it.copy(menu = null) }
    }

    override fun messageAction(target: MessageTarget, action: MessageAction) {
        local.update { it.copy(menu = null, actionFailure = null) }
        when (action) {
            MessageAction.Copy -> {
                val text = textOf(world.transcript.value, target) ?: return
                // E15-S06: the person's own prompt goes through the redactor too, like every other copy.
                if (outside == null) local.update { it.copy(clip = ClipUi(nextId++, text)) }
                else copyOut(text)
            }
            MessageAction.Share -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                val out = outside ?: return
                // E15-S06: one mask for every role; a key the person pasted never leaves in a share either.
                if (!out.share(out.mask(text))) local.update { it.copy(actionFailure = Failure.Unexpected()) }
            }
            MessageAction.SelectText -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                local.update { it.copy(select = SelectTextUi(nextId++, text)) }
            }
            MessageAction.EditAndResend -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                local.update { it.copy(insert = ClipUi(nextId++, text)) }
            }
            MessageAction.ReadAloud,
            MessageAction.ReadAloudOtherVoice -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                val choose = action == MessageAction.ReadAloudOtherVoice
                local.update { it.copy(readAloud = ReadAloudUi(nextId++, target.rowKey, text, choose)) }
            }
            MessageAction.SendAgain -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                // Only its words: the draft's files stay with the draft. A failed send shows on its
                // bubble, not twice.
                launcher.launch { world.sendAgain(text) }
            }
            MessageAction.Fork -> {
                val id = target.messageId ?: return
                launcher.launch {
                    when (val forked = world.fork(MessageId(id))) {
                        is Outcome.Ok ->
                            nav.openChat(ConversationRef(world.ref.profileId, forked.value))
                        is Outcome.Failed ->
                            local.update { it.copy(actionFailure = forked.failure) }
                    }
                }
            }
            MessageAction.Changes ->
                nav.open(ReviewRoute.Changes(world.ref, target.messageId ?: return))
            MessageAction.UndoFromHere ->
                nav.open(ReviewRoute.UndoFromHere(world.ref, target.messageId ?: return))
            MessageAction.Delete ->
                if (target.messageId != null)
                    local.update { it.copy(confirm = ChatConfirm.DeleteMessage(target)) }
        }
    }

    override fun openChanges() {
        nav.open(ReviewRoute.Changes(world.ref))
    }

    override fun confirmDelete() {
        val target = (local.value.confirm as? ChatConfirm.DeleteMessage)?.target ?: return
        val id = target.messageId ?: return
        local.update { it.copy(confirm = null) }
        launcher.launch {
            val result = world.deleteMessage(MessageId(id))
            if (result is Outcome.Failed) local.update { it.copy(actionFailure = result.failure) }
        }
    }

    override fun confirmUndo() {
        val target = (local.value.confirm as? ChatConfirm.UndoFrom)?.target ?: return
        val id = target.messageId ?: return
        local.update { it.copy(confirm = null) }
        launcher.launch {
            val result = world.revertFrom(MessageId(id))
            if (result is Outcome.Failed) local.update { it.copy(actionFailure = result.failure) }
        }
    }

    // ---- P12-CHAT-FEEL: links, copy, selection, edit and resend ----

    override fun openLink(url: String) {
        val out = outside ?: return
        val filePath = out.fileLinkPath(url) ?: LinkTargets.file(url)
        if (filePath != null) openLinkedFile(filePath) else openWebLink(out, url)
    }

    /**
     * A reply's link to a file: a `file:` address or a target with no scheme (`notes.txt`, `src/a.kt`, `/root/p/a.kt`)
     * opens as a project file by the same rules as a path chip, and never reaches the link policy or the browser. A name
     * with no slash is a file only if the project root lists it; any other stays the refused link it was.
     */
    private fun openLinkedFile(path: String) {
        if (path.contains('/')) {
            openPath(path, null)
        } else {
            local.update { it.copy(linkRefused = false) }
            launcher.launch {
                if ((world.fileExists(path) as? Outcome.Ok)?.value == true) openPath(path, null)
                else local.update { it.copy(linkRefused = true) }
            }
        }
    }

    private fun openWebLink(out: ChatOutside, url: String) {
        local.update { it.copy(linkRefused = false) }
        when (val check = out.checkLink(url)) {
            LinkCheck.Opened -> Unit
            is LinkCheck.Ask ->
                local.update {
                    it.copy(confirm = ChatConfirm.OpenLink(url, check.host, check.insecure))
                }
            LinkCheck.Refused -> local.update { it.copy(linkRefused = true) }
        }
    }

    override fun confirmOpenLink() {
        val link = local.value.confirm as? ChatConfirm.OpenLink ?: return
        local.update { it.copy(confirm = null) }
        if (outside?.openLink(link.url) != true) local.update { it.copy(linkRefused = true) }
    }

    /** A code block's, a tool output's or a reply's Copy: always through the redactor (E15-S05). */
    override fun copyText(text: String) {
        copyOut(text)
    }

    /**
     * The chat's one copy (E15-S06): every text — the person's own prompt as much as a reply, a code block or a tool's
     * output — is masked by the app's redactor before it reaches the clipboard, and the copy says whether it hid
     * anything.
     */
    private fun copyOut(text: String) {
        if (text.isEmpty()) return
        val out = outside ?: return
        val safe = out.mask(text)
        if (out.copy(safe)) local.update { it.copy(copied = CopiedUi(nextId++, masked = safe != text)) }
        else local.update { it.copy(actionFailure = Failure.Unexpected()) }
    }

    override fun closeSelection() {
        local.update { it.copy(select = null) }
    }

    override val pictureScope: String get() = world.ref.profileId.raw

    override suspend fun readPicture(path: String): ByteArray? = readInProject(path)

    /** A picture's bytes by its place under the project; one outside the project is not read. */
    private suspend fun readInProject(path: String): ByteArray? {
        val inProject = projectRelativePath(path, world.directory)
        if (inProject.startsWith("/")) return null
        return (world.readPicture(inProject) as? Outcome.Ok)?.value
    }

    override fun openPicture(path: String, name: String) {
        val id = nextId++
        val (gallery, index) = ConversationPictures.around(world.transcript.value.rows, path, name)
        local.update {
            it.copy(picture = PictureUi(id, name, bytes = null, failed = false, gallery, index))
        }
        launcher.launch {
            val read = readInProject(path)?.let { Outcome.Ok(it) }
            local.update {
                if (it.picture?.id != id) it
                else
                    it.copy(
                        picture =
                            PictureUi(
                                id,
                                name,
                                (read as? Outcome.Ok)?.value,
                                failed = read !is Outcome.Ok,
                                gallery,
                                index,
                            )
                    )
            }
        }
    }

    /** path → the transcript revision its last miss was seen at; [CHECKING] while a check runs. */
    private val pathChecks = LinkedHashMap<String, Long>()

    override fun checkPaths(paths: List<String>) {
        if (!world.capabilities.value.fileBrowsing) return
        val transcript = world.transcript.value
        for (path in paths) {
            val seen = pathChecks[path]
            // A miss is checked again only after the conversation moved on and is idle (Flutter:
            // agents name a file
            // moments before they create it), never once per streamed word.
            // An absolute path under the project is asked for by its place there; one outside it is never
            // asked for (the server reads only the project), so it stays plain text.
            val inProject = projectRelativePath(path, world.directory)
            val skip =
                inProject.startsWith("/") ||
                    path in local.value.confirmedPaths ||
                    (seen != null && (seen == CHECKING || seen == transcript.revision || transcript.run.busy))
            if (skip) continue
            if (pathChecks.size >= MAX_PATH_CHECKS) pathChecks.remove(pathChecks.keys.first())
            pathChecks[path] = CHECKING
            launcher.launch {
                val found = (world.fileExists(inProject) as? Outcome.Ok)?.value == true
                if (found) {
                    pathChecks.remove(path)
                    local.update { it.copy(confirmedPaths = it.confirmedPaths.add(path)) }
                } else {
                    pathChecks[path] = world.transcript.value.revision
                }
            }
        }
    }

    override fun openPath(path: String, line: Int?) {
        val clean = path.trim()
        if (clean.isEmpty() || !world.capabilities.value.fileBrowsing) return
        launcher.launch {
            when (outside?.phoneFileKind(world.ref, clean) ?: PhoneFileKind.Preview) {
                PhoneFileKind.Preview -> previewPath(clean, line)
                PhoneFileKind.Open ->
                    local.update {
                        it.copy(confirm = ChatConfirm.PhoneFile(clean, install = false))
                    }
                PhoneFileKind.Install ->
                    local.update { it.copy(confirm = ChatConfirm.PhoneFile(clean, install = true)) }
            }
        }
    }

    override fun confirmPhoneFile() {
        val path =
            when (val asked = local.value.confirm) {
                is ChatConfirm.PhoneFile -> asked.path
                is ChatConfirm.PhoneFileAccess -> asked.path
                else -> return
            }
        local.update { it.copy(confirm = null) }
        val out = outside ?: return
        launcher.launch {
            when (val handed = out.openPhoneFile(world.ref, path)) {
                is Outcome.Failed -> local.update { it.copy(actionFailure = handed.failure) }
                is Outcome.Ok ->
                    when (handed.value) {
                        PhoneFileOpen.Preview -> previewPath(path, null)
                        PhoneFileOpen.Opened -> Unit
                        PhoneFileOpen.AccessRequested ->
                            local.update { it.copy(confirm = ChatConfirm.PhoneFileAccess(path)) }
                    }
            }
        }
    }

    private fun previewPath(clean: String, line: Int?) {
        if (!world.capabilities.value.fileBrowsing) return
        val name = clean.substringAfterLast('/')
        if (PICTURE_NAME.containsMatchIn(name)) openPicture(clean, name)
        else nav.open(FilesRoute.File(world.ref, projectRelativePath(clean, world.directory), line))
    }

    override fun closePicture() {
        local.update { it.copy(picture = null) }
    }

    override fun insertDone(id: Long) {
        local.update { if (it.insert?.id == id) it.copy(insert = null) else it }
    }

    override fun clipDone(id: Long) {
        local.update { if (it.clip?.id == id) it.copy(clip = null) else it }
    }

    override fun readAloudDone(id: Long) {
        local.update { if (it.readAloud?.id == id) it.copy(readAloud = null) else it }
    }

    // ---- Arrivals and reading ----

    override fun arrivalShown(id: Long) {
        local.update { if (it.arrival?.id == id) it.copy(arrival = null) else it }
    }

    override fun liveEdgeChanged(atLiveEdge: Boolean) {
        if (local.value.atLiveEdge == atLiveEdge) return
        local.update { it.copy(atLiveEdge = atLiveEdge) }
        syncRead()
    }

    private fun textOf(t: TranscriptState, target: MessageTarget): String? =
        when (target.kind) {
            TargetKind.Prompt -> (t.rows.getOrNull(t.indexOf(target.rowKey)) as? PromptRow)?.text
            TargetKind.Reply -> ReplyText.of(t.rows, target.turnId)
        }

    /**
     * The newest assistant message of the footer's turn: the first older row of that turn that
     * names one.
     */
    private fun replyMessageId(t: TranscriptState, footerIndex: Int): String? {
        val source = t.rows[footerIndex]
        if (source is io.github.eslamasabry.oc.engine.transcript.state.BlockRow)
            return RowIds.messageIdOf(source)
        val footer = source as? FooterRow ?: return null
        for (i in footerIndex + 1 until t.rows.size) {
            val row = t.rows[i]
            if (row.turnId != footer.turnId || row is PromptRow) return null
            RowIds.messageIdOf(row)?.let {
                return it
            }
        }
        return null
    }
}

/**
 * The path Files opens: relative to the conversation's own project folder. A tool reports a file by its absolute path
 * ("/work/app/notes.txt"); Files takes paths below the project, so a path under [directory] loses that prefix. A path
 * that is already relative, or that lies outside the project, is left as it is.
 */
internal fun projectRelativePath(path: String, directory: String?): String {
    val root = directory?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return path
    if (!path.startsWith("/")) return path
    return when {
        path == root -> ""
        path.startsWith("$root/") -> path.removePrefix("$root/")
        else -> path
    }
}

private const val CHECKING = -1L
private const val MAX_PATH_CHECKS = 256

/** Flutter `_drawablePictureName`: the files the picture viewer draws. */
private val PICTURE_NAME = Regex("\\.(png|jpe?g|gif|webp|bmp)$", RegexOption.IGNORE_CASE)

private const val RECEIPT_STATUS: String = "chat-receipt-check"
private const val STAGED_UNDO_STATUS: String = "staged-revert"
private const val UNDONE_STATUS: String = "undone"
private const val SUBAGENT_STATUS: String = "subagent"
private const val KEPT_OFFLINE_STATUS: String = "chat-kept-offline"
private const val MODEL_REPLACED_STATUS: String = "model-replaced"
