package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
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
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.coroutines.CoroutineContext

/** Where the presenter runs a command's suspending half (the ViewModel's scope; tests start it in place). */
public fun interface ChatLauncher {
    public fun launch(block: suspend () -> Unit)
}

/** The screen-local facts no engine owns (open menu, find query, arrival, …). */
internal data class ChatLocal(
    val findQuery: String? = null,
    val findKey: FindHit? = null,
    val findCursor: Int = -1,
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
)

internal data class PendingArrival(val id: Long, val focusComposer: Boolean, val landOnFailure: Boolean)

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
 * The chat's state holder and intents (plan 09 §6.2), free of Android and of the ViewModel class so it is tested with
 * plain `kotlin.test`: [current] presents the world's present values; [states] is the production flow. The combine is
 * O(changed side tables): rows pass through by reference; find runs on [states]' work context.
 */
public class ChatPresenter(
    private val world: ChatWorld,
    private val nav: AppNavigator,
    private val launcher: ChatLauncher,
    focusComposer: Boolean = false,
    landOnFailure: Boolean = false,
    private val outside: ChatOutside? = null,
) : ChatActions {
    private var nextId = 1L
    private val local =
        MutableStateFlow(
            ChatLocal(
                arrival = if (focusComposer || landOnFailure) PendingArrival(nextId++, focusComposer, landOnFailure) else null,
            ),
        )
    private var revision = 0L
    private var lastHits: ImmutableList<FindHit> = persistentListOf()
    private var readAllowed = false
    private var readBusy = false

    public val ref: ConversationRef get() = world.ref

    /** The present state from the world's current values (find computed in place). */
    public fun current(): ChatUi {
        val inputs = inputsNow()
        val l = local.value
        val hits = l.findQuery?.let { TranscriptFind.search(inputs.transcript, it) } ?: persistentListOf()
        return present(inputs, l, hits)
    }

    /** The production state: [work] is where find searches (never the main thread). */
    public fun states(scope: CoroutineScope, work: CoroutineContext, started: SharingStarted): StateFlow<ChatUi> {
        val hits =
            combine(world.transcript, local.map { it.findQuery }.distinctUntilChanged()) { t, q -> t to q }
                .conflate()
                .map { (t, q) -> if (q == null) persistentListOf() else TranscriptFind.search(t, q) }
                .flowOn(work)
        val inputs =
            combine(world.transcript, world.load, world.title, world.capabilities, world.link) { t, l, ti, c, k ->
                Five(t, l, ti, c, k)
            }.combine(world.pending) { f, p -> f to p }
                .combine(world.draft) { (f, p), d -> ChatInputs(f.t, f.l, f.ti, f.c, f.k, p, d) }
        // Read acknowledgement follows the world, not the screen's composition (plan 09 §6.7).
        combine(world.load, world.transcript, local) { _, _, _ -> syncRead() }.launchIn(scope)
        return combine(inputs, local, hits, world.hasOlder) { i, l, h, _ -> present(i, l, h) }
            .stateIn(scope, started, ChatUi.loading(world.ref))
    }

    private data class Five(val t: TranscriptState, val l: ChatLoad, val ti: String?, val c: ServerCapabilities, val k: ChatLink)

    private fun inputsNow(): ChatInputs =
        ChatInputs(
            world.transcript.value, world.load.value, world.title.value, world.capabilities.value, world.link.value,
            world.pending.value, world.draft.value,
        )

    internal fun present(i: ChatInputs, l: ChatLocal, hits: ImmutableList<FindHit>): ChatUi {
        lastHits = hits
        val t = i.transcript
        val cursor = TranscriptFind.keep(hits, l.findKey, l.findCursor)
        val hit = hits.getOrNull(cursor)
        val find = l.findQuery?.let { q ->
            FindUi(q, hits.size, cursor, hit?.rowKey, hit?.let { t.indexOf(it.rowKey) } ?: -1, partial = world.hasOlder.value)
        }
        val arrival = l.arrival?.let { a ->
            val land = if (a.landOnFailure) Landing.newestFailedKey(t.rows)?.let(t::indexOf)?.takeIf { it >= 0 } else null
            ChatArrivalUi(a.id, a.focusComposer, land)
        }
        return ChatUi(
            revision = ++revision,
            ref = world.ref,
            title = i.title,
            status = StatusPick.pick(i.link, i.pending, t.run, t.rows, l.actionFailure, l.linkRefused),
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
            guardBack = BackOrder.handles(l.menu != null || l.select != null || l.picture != null, l.confirm != null, l.findQuery != null, i.draft.unsaved),
            select = l.select,
            insert = l.insert,
            picture = l.picture,
            readAloud = l.readAloud,
        )
    }

    /**
     * Read acknowledgement (plan 09 §6.7): on each move into "loaded and at the live edge", and when a reply ends while
     * it holds. Never while loading, failed or away from the newest end.
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
        when (BackOrder.next(l.menu != null || l.select != null || l.picture != null, l.confirm != null, l.findQuery != null, world.draft.value.unsaved)) {
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
                    local.update { it.copy(leaving = false, confirm = if (saved is Outcome.Ok) it.confirm else ChatConfirm.LeaveUnsaved) }
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
        local.update { it.copy(stopping = true, actionFailure = null) }
        launcher.launch {
            val result = world.stop()
            local.update { it.copy(stopping = false, actionFailure = (result as? Outcome.Failed)?.failure) }
        }
    }

    override fun retrySend(localId: Long) {
        launcher.launch { world.retrySend(localId) }
    }

    override fun dismissStatus(id: String) {
        if (id == StatusPick.ACTION) local.update { it.copy(actionFailure = null) }
        if (id == StatusPick.LINK) local.update { it.copy(linkRefused = false) }
    }

    private fun capsOf(c: ServerCapabilities): ChatCapabilitiesUi =
        ChatCapabilitiesUi(fork = c.sessionFork, deleteMessage = c.messageDelete, undo = c.sessionRevert, changes = true)

    // ---- Find (plan 09 §6.6) ----

    override fun find(query: String?) {
        local.update { it.copy(findQuery = query, findKey = null, findCursor = if (query == null) -1 else 0) }
    }

    override fun findNext(): Unit = stepFind(+1)

    override fun findPrevious(): Unit = stepFind(-1)

    private fun stepFind(by: Int) {
        val l = local.value
        if (l.findQuery == null) return
        val hits = lastHits
        if (hits.isEmpty()) return
        val next = TranscriptFind.step(hits.size, TranscriptFind.keep(hits, l.findKey, l.findCursor), by)
        local.update { it.copy(findKey = hits[next], findCursor = next) }
    }

    /** Slice 2: the timeline sheet is S4's; until it lands the pill opens find, the in-chat way to a message. */
    override fun loadOlder() { launcher.launch { world.loadOlder() } }

    override fun openTimeline() {
        if (local.value.findQuery == null) find("")
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
        val actions = MessageActions.of(kind, capsOf(world.capabilities.value), t.run.busy, messageId, hasText, feel = outside != null)
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
                if (outside == null) local.update { it.copy(clip = ClipUi(nextId++, text)) } else copyText(text)
            }
            MessageAction.Share -> {
                val text = textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                if (outside?.share(text) == false) local.update { it.copy(actionFailure = Failure.Unexpected()) }
            }
            MessageAction.SelectText -> {
                val text = textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                local.update { it.copy(select = SelectTextUi(nextId++, text)) }
            }
            MessageAction.EditAndResend -> {
                val text = textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                local.update { it.copy(insert = ClipUi(nextId++, text)) }
            }
            MessageAction.ReadAloud -> {
                val text = textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                local.update { it.copy(readAloud = ReadAloudUi(nextId++, target.rowKey, text)) }
            }
            MessageAction.SendAgain -> {
                val text = textOf(world.transcript.value, target)?.takeIf { it.isNotBlank() } ?: return
                launcher.launch { world.send(text) } // a failed send shows on its bubble (status line), not twice
            }
            MessageAction.Fork -> {
                val id = target.messageId ?: return
                launcher.launch {
                    when (val forked = world.fork(MessageId(id))) {
                        is Outcome.Ok -> nav.openChat(ConversationRef(world.ref.profileId, forked.value))
                        is Outcome.Failed -> local.update { it.copy(actionFailure = forked.failure) }
                    }
                }
            }
            MessageAction.Changes -> nav.open(ReviewRoute.Changes(world.ref, target.messageId ?: return))
            MessageAction.UndoFromHere -> nav.open(ReviewRoute.UndoFromHere(world.ref, target.messageId ?: return))
            MessageAction.Delete -> if (target.messageId != null) local.update { it.copy(confirm = ChatConfirm.DeleteMessage(target)) }
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
            is LinkCheck.Ask -> local.update { it.copy(confirm = ChatConfirm.OpenLink(url, check.host)) }
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
        if (outside?.copy(text) == false) local.update { it.copy(actionFailure = Failure.Unexpected()) }
    }

    override fun closeSelection() {
        local.update { it.copy(select = null) }
    }

    override fun openPicture(path: String, name: String) {
        val id = nextId++
        local.update { it.copy(picture = PictureUi(id, name, bytes = null, failed = false)) }
        launcher.launch {
            val read = world.readPicture(path)
            local.update {
                if (it.picture?.id != id) it
                else it.copy(picture = PictureUi(id, name, (read as? Outcome.Ok)?.value, failed = read !is Outcome.Ok))
            }
        }
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

    /** The newest assistant message of the footer's turn: the first older row of that turn that names one. */
    private fun replyMessageId(t: TranscriptState, footerIndex: Int): String? {
        val footer = t.rows[footerIndex] as? FooterRow ?: return null
        for (i in footerIndex + 1 until t.rows.size) {
            val row = t.rows[i]
            if (row.turnId != footer.turnId || row is PromptRow) return null
            RowIds.messageIdOf(row)?.let { return it }
        }
        return null
    }
}
