package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.ledger.factTicks
import io.github.eslamasabry.oc.engine.transcript.ledger.latest
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.StoppedPrompt
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatUndone
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    val atLiveEdge: Boolean = true,
    val stopping: Boolean = false,
    val stopAfterStep: Boolean = false,
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
    val draft: DraftFacts,
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
) : ChatActions {
    private var nextId = 1L
    private val local =
        MutableStateFlow(
            ChatLocal(
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
        return present(inputs, l, hits)
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
                    if (q == null) persistentListOf() else TranscriptFind.search(t, q)
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
                .combine(world.draft) { (f, p), d -> ChatInputs(f.t, f.l, f.ti, f.c, f.k, p, d) }
                .combine(retryFacts()) { i, r -> i.copy(retry = r) }
        // Read acknowledgement follows the world, not the screen's composition (plan 09 §6.7).
        combine(world.load, world.transcript, local) { _, _, _ ->
                syncRead()
                settleStop()
            }
            .launchIn(scope)
        // S30 story 3: what this server can background, read each time the chat goes live (Flutter reads it on
        // attach), so a reconnect to another server version is read again.
        world.load
            .map { it == ChatLoad.Live }
            .distinctUntilChanged()
            .onEach { live -> if (live) loadBackgroundSupport() }
            .launchIn(scope)
        // S28 story 3: the undone line follows the session as well.
        val extra = feed.combine(world.undone) { f, u -> f to u }
        return combine(inputs, local, hits, world.hasOlder, extra) { i, l, h, _, _ ->
                present(i, l, h)
            }
            .stateIn(scope, started, ChatUi.loading(world.ref))
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
            world.draft.value,
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
        )

    /** S24-CHAT: ticks once a second only while a retry is scheduled (Flutter's `_Ticker`). */
    private fun retryFacts(): Flow<LinkRetryFacts> {
        val c = clock ?: return world.linkRetryAtMs.map { retryNow(it) }
        return world.linkRetryAtMs.latest { at ->
            factTicks(c, flowOf(at != null)).map { now ->
                LinkRetryFacts(at, now, world.serverOnThisPhone)
            }
        }
    }

    internal fun present(i: ChatInputs, l: ChatLocal, hits: ImmutableList<FindHit>): ChatUi {
        lastHits = hits
        // N-47: when the server goes away after the conversation was read, keep the last transcript and title on
        // screen with one offline line (Try again retries the load) instead of replacing them with an error.
        if (i.load == ChatLoad.Live && i.transcript.rows.isNotEmpty()) lastLive = i.transcript
        i.title?.let { lastTitle = it }
        val kept = if (i.load is ChatLoad.Failed) lastLive else null
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
                    excerpt = hit?.let { findExcerpt(t, it) },
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
        return ChatUi(
            revision = ++revision,
            ref = world.ref,
            title = i.title ?: lastTitle,
            status =
                (if (kept != null) i.retry.line(KEPT_OFFLINE_STATUS, ChatStatusKind.Offline) else null) ?: StatusPick.pick(i.link, i.pending, t.run, t.rows, l.actionFailure, l.linkRefused, i.retry)
                    ?: l.receiptResult?.let {
                        ChatStatusUi(
                            RECEIPT_STATUS,
                            ChatStatusKind.ReceiptChecked,
                            receiptResult = it,
                        )
                    }
                    ?: undoneLine(world.undone.value),
            phase =
                when (val load = i.load) {
                    ChatLoad.Loading -> ChatPhase.Loading
                    ChatLoad.Live -> ChatPhase.Live
                    is ChatLoad.Failed ->
                        if (kept != null) ChatPhase.Live else ChatPhase.Failed(load.failure)
                },
            rows = t.rows,
            newestRowKey = t.newestKey,
            canPutBack = world.undone.value != ChatUndone.None,
            canBackground = canBackground(t.run.busy, l),
            live =
                if (t.run.busy)
                    LiveTurnUi(
                        l.stopping,
                        stoppingAfterStep = l.stopAfterStep,
                        canStopAfterStep =
                            !l.stopping && !l.stopAfterStep && i.capabilities.promptSteering,
                    )
                else null,
            pending = i.pending.asReversed().toImmutableList(),
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
                    i.draft.unsaved,
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
            receiptChecks =
                l.receiptChecks
                    .filterKeys { id -> i.pending.any { it.localId == id } }
                    .toPersistentMap(),
            limitSwitch = limitOffers(t, i.capabilities, l.switches),
            switchOnline = i.link == ChatLink.Live,
            switching = l.switching,
            attachAgain = l.attachAgain,
        )
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
        for (row in t.rows) {
            if (row !is FooterRow || !row.failed) continue
            val words = t.rows.firstOrNull { it is PromptRow && it.turnId == row.turnId } as? PromptRow
            val count = words?.let { switches[LimitSwitchRules.promptKey(it.text)] } ?: 0
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
        launcher.launch { world.reload() }
    }

    override fun tryLinkNow() {
        world.tryLinkNow()
        if (world.load.value is ChatLoad.Failed) retryLoad()
    }

    override fun restartPhoneServer() {
        if (!world.serverOnThisPhone) return
        local.update { it.copy(confirm = ChatConfirm.RestartPhoneServer) }
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
        val text = newestPrompt()?.text?.takeIf { it.isNotBlank() } ?: return
        launcher.launch { world.sendAgain(text) }
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

    override fun stop() {
        if (local.value.stopping) return
        stopInFlight = true
        local.update { it.copy(stopping = true, actionFailure = null) }
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
        }
    }

    /** The turn ended after an accepted Stop: the stopping state settles (B10). */
    internal fun settleStop() {
        if (!stopInFlight && local.value.stopping && !world.transcript.value.run.busy)
            local.update { it.copy(stopping = false) }
        // The turn ended: the stop note did its work (or the turn finished first).
        if (local.value.stopAfterStep && !world.transcript.value.run.busy)
            local.update { it.copy(stopAfterStep = false) }
    }

    override fun stopAfterStep(note: String) {
        val l = local.value
        if (note.isBlank() || l.stopping || l.stopAfterStep) return
        if (!world.transcript.value.run.busy || !world.capabilities.value.promptSteering) return
        local.update { it.copy(stopAfterStep = true) }
        launcher.launch {
            val sent = world.sendAgain(note)
            if (sent is Outcome.Failed)
                local.update { it.copy(stopAfterStep = false, actionFailure = sent.failure) }
        }
    }

    override fun useModelAndResend(turnId: String, providerId: String, modelId: String) {
        val t = world.transcript.value
        val words = t.rows.firstOrNull { it is PromptRow && it.turnId == turnId } as? PromptRow
        val text = words?.text?.takeIf { it.isNotBlank() } ?: return
        val choice =
            world.models.value.choices.firstOrNull {
                it.providerId == providerId && it.modelId == modelId
            } ?: return
        launcher.launch {
            when (val picked = world.selectModel(choice)) {
                // A refused switch keeps the old model and sends nothing.
                is Outcome.Failed -> local.update { it.copy(actionFailure = picked.failure) }
                is Outcome.Ok -> world.sendAgain(text)
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

    override fun dismissStatus(id: String) {
        if (id == RECEIPT_STATUS) local.update { it.copy(receiptResult = null) }
        if (id == StatusPick.ACTION) local.update { it.copy(actionFailure = null) }
        if (id == StatusPick.LINK) local.update { it.copy(linkRefused = false) }
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
            if (query.isNotBlank()) {
                find(query)
                val hits = TranscriptFind.search(t, query)
                val cursor = hits.indexOfFirst { it.rowKey == entry.rowKey }
                if (cursor >= 0)
                    local.update { it.copy(findKey = hits[cursor], findCursor = cursor) }
            }
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
        out.fileLinkPath(url)?.let {
            openPath(it, null)
            return
        }
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
     * output — is masked by the app's redactor before it reaches the clipboard, and the copy says whether it hid anything.
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

    override suspend fun readPicture(path: String): ByteArray? =
        (world.readPicture(path) as? Outcome.Ok)?.value

    override fun openPicture(path: String, name: String) {
        val id = nextId++
        val (gallery, index) = ConversationPictures.around(world.transcript.value.rows, path, name)
        local.update {
            it.copy(picture = PictureUi(id, name, bytes = null, failed = false, gallery, index))
        }
        launcher.launch {
            val read = world.readPicture(path)
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
            if (path in local.value.confirmedPaths) continue
            val seen = pathChecks[path]
            // A miss is checked again only after the conversation moved on and is idle (Flutter:
            // agents name a file
            // moments before they create it), never once per streamed word.
            if (
                seen != null &&
                    (seen == CHECKING || seen == transcript.revision || transcript.run.busy)
            )
                continue
            if (pathChecks.size >= MAX_PATH_CHECKS) pathChecks.remove(pathChecks.keys.first())
            pathChecks[path] = CHECKING
            launcher.launch {
                val found = (world.fileExists(path) as? Outcome.Ok)?.value == true
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
        else nav.open(FilesRoute.File(world.ref, clean, line))
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

private const val CHECKING = -1L
private const val MAX_PATH_CHECKS = 256

/** Flutter `_drawablePictureName`: the files the picture viewer draws. */
private val PICTURE_NAME = Regex("\\.(png|jpe?g|gif|webp|bmp)$", RegexOption.IGNORE_CASE)

private const val RECEIPT_STATUS: String = "chat-receipt-check"
private const val STAGED_UNDO_STATUS: String = "staged-revert"
private const val UNDONE_STATUS: String = "undone"
private const val KEPT_OFFLINE_STATUS: String = "chat-kept-offline"
