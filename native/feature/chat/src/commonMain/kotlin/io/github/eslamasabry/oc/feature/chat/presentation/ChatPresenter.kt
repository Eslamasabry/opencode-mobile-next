package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
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
    val readAloud: ReadAloudUi? = null,
    val arrival: PendingArrival? = null,
    val actionFailure: Failure? = null,
    val atLiveEdge: Boolean = true,
    val stopping: Boolean = false,
    val leaving: Boolean = false,
    val select: SelectTextUi? = null,
    val insert: ClipUi? = null,
    val linkRefused: Boolean = false,
    val picture: PictureUi? = null,
    val confirmedPaths: PersistentSet<String> = persistentSetOf(),
    val receiptChecks: PersistentMap<Long, ReceiptCheckUi> = persistentMapOf(),
    val receiptResult: ReceiptCheckResult? = null,
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
        // Read acknowledgement follows the world, not the screen's composition (plan 09 §6.7).
        combine(world.load, world.transcript, local) { _, _, _ ->
                syncRead()
                settleStop()
            }
            .launchIn(scope)
        return combine(inputs, local, hits, world.hasOlder, feed) { i, l, h, _, _ ->
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
        )

    internal fun present(i: ChatInputs, l: ChatLocal, hits: ImmutableList<FindHit>): ChatUi {
        lastHits = hits
        val t = i.transcript
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
            l.arrival?.let { a ->
                val land =
                    (a.rowKey ?: if (a.landOnFailure) Landing.newestFailedKey(t.rows) else null)
                        ?.let(t::indexOf)
                        ?.takeIf { it >= 0 }
                ChatArrivalUi(a.id, a.focusComposer, land)
            }
        return ChatUi(
            revision = ++revision,
            ref = world.ref,
            title = i.title,
            status =
                StatusPick.pick(i.link, i.pending, t.run, t.rows, l.actionFailure, l.linkRefused)
                    ?: l.receiptResult?.let {
                        ChatStatusUi(
                            RECEIPT_STATUS,
                            ChatStatusKind.ReceiptChecked,
                            receiptResult = it,
                        )
                    },
            phase =
                when (val load = i.load) {
                    ChatLoad.Loading -> ChatPhase.Loading
                    ChatLoad.Live -> ChatPhase.Live
                    is ChatLoad.Failed -> ChatPhase.Failed(load.failure)
                },
            rows = t.rows,
            newestRowKey = t.newestKey,
            live = if (t.run.busy) LiveTurnUi(l.stopping) else null,
            pending = i.pending.asReversed().toImmutableList(),
            find = find,
            menu = l.menu,
            confirm = l.confirm,
            clip = l.clip,
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
        )
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
        if (mark) world.markRead()
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
        local.update { it.copy(confirm = null) }
    }

    // ---- Load, stop, sends ----

    override fun retryLoad() {
        launcher.launch { world.reload() }
    }

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
    }

    override fun continueReply(words: String) {
        if (words.isBlank()) return
        launcher.launch { world.sendAgain(words) }
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
                // Busy: another check of this bubble is already running and will announce its answer.
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
                        receiptResult = result,
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
        )

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
                if (outside == null) local.update { it.copy(clip = ClipUi(nextId++, text)) }
                else copyText(text)
            }
            MessageAction.Share -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                if (outside?.share(text) == false)
                    local.update { it.copy(actionFailure = Failure.Unexpected()) }
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
            MessageAction.ReadAloud -> {
                val text =
                    textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                local.update { it.copy(readAloud = ReadAloudUi(nextId++, target.rowKey, text)) }
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
        local.update { it.copy(linkRefused = false) }
        when (val check = out.checkLink(url)) {
            LinkCheck.Opened -> Unit
            is LinkCheck.Ask ->
                local.update { it.copy(confirm = ChatConfirm.OpenLink(url, check.host)) }
            LinkCheck.Refused -> local.update { it.copy(linkRefused = true) }
        }
    }

    override fun confirmOpenLink() {
        val link = local.value.confirm as? ChatConfirm.OpenLink ?: return
        local.update { it.copy(confirm = null) }
        if (outside?.openLink(link.url) != true) local.update { it.copy(linkRefused = true) }
    }

    override fun copyText(text: String) {
        if (text.isEmpty()) return
        if (outside?.copy(text) == false)
            local.update { it.copy(actionFailure = Failure.Unexpected()) }
    }

    override fun closeSelection() {
        local.update { it.copy(select = null) }
    }

    override suspend fun readPicture(path: String): ByteArray? =
        (world.readPicture(path) as? Outcome.Ok)?.value

    override fun openPicture(path: String, name: String) {
        val id = nextId++
        local.update { it.copy(picture = PictureUi(id, name, bytes = null, failed = false)) }
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
        val footer = t.rows[footerIndex] as? FooterRow ?: return null
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
