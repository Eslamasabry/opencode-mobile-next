package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.feature.chats.presentation.HeldAnswers
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionReply
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Edge light's list: state ([ui]) and acts, no drawing. One per panel life ([scope] ends with it); everything that
 * must outlive the panel lives in the shared [held] (answers) and [stops] (Stop) stores, which belong to the app scope,
 * so closing the list never cancels a hold. Confined to one dispatcher.
 *
 * Opening is [AppNavigator.switchTo]; "Always allow" is not offered here.
 */
public class LampListController(
    private val scope: CoroutineScope,
    list: StateFlow<DockListModel>,
    private val nav: AppNavigator,
    private val held: HeldAnswers,
    private val stops: HeldStops,
    private val port: LampPort,
    private val clock: Clock,
    place: StateFlow<LampPlace>,
    private val hideUndoMs: Long = HIDE_UNDO_MS,
) {
    private val folds = LampFolds()
    private val editing = MutableStateFlow(false)
    private val notice = MutableStateFlow<LampNotice?>(null)
    private val foldTick = MutableStateFlow(0)
    private var noticeJob: Job? = null
    private var latest: DockListModel = list.value

    public val ui: StateFlow<LampUi> =
        combine(
                combine(list, held.held, stops.phases) { model, answers, phases -> Triple(model, answers, phases) },
                combine(editing, place, notice, foldTick) { e, p, n, _ -> Triple(e, p, n) },
            ) { (model, answers, phases), (isEditing, where, told) ->
                latest = model
                folds.observe(model)
                LampListPresenter.present(
                    model,
                    LampInputs(
                        nowMs = clock.now().toEpochMilliseconds(),
                        held = answers,
                        stops = phases,
                        folds = folds.folds.toMap(),
                        overlapSeen = folds.overlapSeen.toSet(),
                        editing = isEditing,
                        place = where,
                        notice = told,
                    ),
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, LampListPresenter.present(list.value, LampInputs(clock.now().toEpochMilliseconds(), place = place.value)))

    init {
        scope.launch { stops.failed.collect { ref -> tell(LampNotice.StopFailed(ref, titleOf(ref))) } }
    }

    private fun titleOf(ref: ConversationRef): String? =
        latest.rows.firstOrNull { it.ref == ref }?.name
            ?: latest.rows.firstNotNullOfOrNull { r -> r.subAgents.firstOrNull { it.ref == ref }?.name }

    // Opening.

    /** A row or a sub-agent: you are there, and Back returns exactly. */
    public fun open(ref: ConversationRef) {
        nav.switchTo(ref)
    }

    // Requests.

    /** Allow once: held 3 seconds with Undo (the home's own hold), then sent once. */
    public fun allowOnce(owner: ConversationRef, requestId: String) {
        held.hold(owner, requestId, HomePermissionReply.Once)
    }

    public fun deny(owner: ConversationRef, requestId: String) {
        held.hold(owner, requestId, HomePermissionReply.Reject)
    }

    public fun undoAnswer(requestId: String) {
        held.undo(requestId)
    }

    /** "Answer…" of a question or a form: the conversation opens at that request. */
    public fun answer(owner: ConversationRef, requestId: String) {
        nav.switchTo(owner, requestId)
    }

    // Stop.

    public fun stop(ref: ConversationRef) {
        stops.hold(ref)
    }

    public fun undoStop(ref: ConversationRef) {
        stops.undo(ref)
    }

    // Folding.

    public fun setFolded(ref: ConversationRef, folded: Boolean) {
        folds.toggle(ref, folded)
        foldTick.update { it + 1 }
    }

    // Edit mode.

    public fun edit() {
        editing.value = true
    }

    public fun done() {
        editing.value = false
    }

    public fun pin(ref: ConversationRef): Unit = port.pin(ref)

    public fun unpin(ref: ConversationRef): Unit = port.unpin(ref)

    /** The drag: [from] takes the place of pinned [to]. */
    public fun reorder(from: ConversationRef, to: ConversationRef) {
        if (from != to) port.reorder(from, to)
    }

    /** TalkBack's "Move up": over the pinned row above it, in the order shown. */
    public fun moveUp(ref: ConversationRef) {
        val pinned = pinnedOrder()
        val i = pinned.indexOf(ref)
        if (i > 0) port.reorder(ref, pinned[i - 1])
    }

    public fun moveDown(ref: ConversationRef) {
        val pinned = pinnedOrder()
        val i = pinned.indexOf(ref)
        if (i in 0 until pinned.lastIndex) port.reorder(ref, pinned[i + 1])
    }

    private fun pinnedOrder(): List<ConversationRef> = latest.rows.filter { it.pinned && !it.isOpen }.map { it.ref }

    /** The home's rename, with the new words typed in the dialog. */
    public fun rename(ref: ConversationRef, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val title = titleOf(ref)
        port.rename(ref, clean) { tell(LampNotice.RenameFailed(ref, title)) }
    }

    /** Hides it from this list; "Hidden from this list · ‹title› · Undo" for 8 seconds. */
    public fun hide(ref: ConversationRef) {
        val title = titleOf(ref)
        port.hide(ref)
        tell(LampNotice.Hidden(ref, title), hideUndoMs)
    }

    /** Undo of [hide], and "Show again" of a hidden row. */
    public fun restore(ref: ConversationRef) {
        port.restore(ref)
        if ((notice.value as? LampNotice.Hidden)?.ref == ref) clearNotice()
    }

    /** "Hidden · N · Show again": brings every hidden conversation back. */
    public fun restoreAll() {
        latest.hidden.forEach { port.restore(it.ref) }
    }

    // Footer and the light itself.

    public fun newConversation(): Unit = port.newConversation()

    public fun allConversations(): Unit = port.allConversations()

    /** Move…: one of six spots. */
    public fun move(place: LampPlace): Unit = port.place(place)

    /** Turn off: the Settings choice becomes Hidden. */
    public fun turnOff() {
        port.turnOff()
        tell(LampNotice.TurnedOff)
    }

    public fun dismissNotice(): Unit = clearNotice()

    private fun tell(told: LampNotice, forMs: Long? = null) {
        noticeJob?.cancel()
        notice.value = told
        noticeJob = forMs?.let { ms -> scope.launch { delay(ms); notice.value = null } }
    }

    private fun clearNotice() {
        noticeJob?.cancel()
        noticeJob = null
        notice.value = null
    }

    public companion object {
        /** "Hidden from this list" keeps its Undo this long. */
        public const val HIDE_UNDO_MS: Long = 8_000L
    }
}
