package io.github.eslamasabry.oc.engine.review

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.StagedRevertGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** One conversation's live and operations gateways, from its own connection graph (ADR §9.1). */
public data class ReviewGateways(val live: ServerGateway, val operations: ServerOperationsGateway)

/** How this server undoes from a prompt, read from its capabilities, never from the backend kind. */
public enum class UndoShape {
    /** Stage, review, then keep or put back (OpenCode 2's stage/clear/commit). */
    Staged,

    /** The server undoes at once; the confirm is the only review. */
    OneStep,

    /** This server cannot undo from a prompt. */
    Unavailable,
}

/**
 * The conversation's undo as the app knows it. [revision] moves on every change of the server's staged boundary seen
 * here (an event, a fresh read, this app's own stage/clear/commit): a review taken at one revision is stale at the next.
 */
public data class ReviewState(
    val staged: SessionRevert? = null,
    val revision: Long = 0,
    val loaded: Boolean = false,
    /** One of this conversation's undo actions is running here. */
    val working: Boolean = false,
    /** The conversation is running a turn: undo waits for it (B-CHAT-106). */
    val running: Boolean = false,
    /** The conversation's title as the server names it (the questions name the conversation); null until read. */
    val title: String? = null,
)

/** A point-in-time review of the staged undo: actions on it are refused once it is not current. */
public data class StagedReview(val revert: SessionRevert, val revision: Long)

/** History, oldest first; [complete] is false when the page budget ran out before the start. */
public data class HistoryRead(val messages: ImmutableList<MessageWithParts>, val complete: Boolean)

/** The files of a turn (or the whole conversation) and the prompt "Undo all" goes back to. */
public data class ChangeSet(
    val files: ImmutableList<ChangedFile>,
    /** The prompt an "Undo all" of these changes stages from; null when it is not known. */
    val undoFrom: String?,
    /** False when older history could not be read: the list may be missing files. */
    val complete: Boolean,
)

/**
 * The change review of one conversation (unit P12-REVIEW; Flutter `state/connection/revert.dart`,
 * `staged_revert_message_count.dart`, `run_result.dart`). Engines own the truth: [state] out, suspend commands
 * returning [Outcome] in. Every mutation is exclusive, re-reads the server first, and dispatches only to the
 * connection it was checked against (B-CHAT-105, B-CHAT-106).
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ConversationReview(
    public val session: SessionId,
    private val gateways: StateFlow<ReviewGateways?>,
    scope: CoroutineScope,
    private val pageSize: Int = 100,
    private val maxPages: Int = 20,
) {
    private val mutable = MutableStateFlow(ReviewState())
    public val state: StateFlow<ReviewState> = mutable.asStateFlow()
    private val lane = Mutex()

    init {
        scope.launch {
            gateways.flatMapLatest { it?.live?.events?.all ?: emptyFlow() }.collect { envelope ->
                when (val event = envelope.event) {
                    is GatewayEvent.RevertChanged ->
                        if (event.sessionId == session) {
                            // A remote stage, clear or commit: whatever was reviewed is stale (B-CHAT-105), read again.
                            mutable.update { it.copy(revision = it.revision + 1) }
                            refresh()
                        }
                    is GatewayEvent.SessionUpserted -> if (event.session.id == session) adopt(event.session.stagedRevert)
                    is GatewayEvent.RunStarted -> if (event.sessionId == session) mutable.update { it.copy(running = true) }
                    is GatewayEvent.RunEnded -> if (event.sessionId == session) mutable.update { it.copy(running = false) }
                    GatewayEvent.Connected -> refresh() // the stream is volatile: reconcile by refetch
                    else -> Unit
                }
            }
        }
    }

    /** What this server offers (B-CHAT-120: at once on servers that undo directly, staged where staged undo exists). */
    public fun shape(): UndoShape {
        val g = gateways.value ?: return UndoShape.Unavailable
        val caps = g.live.capabilities.value
        return when {
            !caps.sessionRevert -> UndoShape.Unavailable
            g.live is StagedRevertGateway -> UndoShape.Staged
            g.operations is StagedRevertGateway -> UndoShape.Staged
            else -> UndoShape.OneStep
        }
    }

    /** The server shows a diff of the uncommitted working tree. */
    public fun workingTreeAvailable(): Boolean = gateways.value?.live?.capabilities?.value?.sessionDiff == true

    /** The current review, or null when nothing is staged. */
    public fun review(): StagedReview? = mutable.value.let { s -> s.staged?.let { StagedReview(it, s.revision) } }

    public fun isCurrent(review: StagedReview): Boolean = mutable.value.let { it.revision == review.revision && it.staged == review.revert }

    /** Reads the conversation's staged boundary (and whether it runs) again. */
    public suspend fun refresh(): Outcome<Unit> {
        val g = gateways.value ?: return notConnected()
        return when (val read = g.live.session(session)) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                mutable.update { it.copy(title = read.value.title) }
                adopt(read.value.stagedRevert)
                Unit.ok()
            }
        }
    }

    private fun adopt(staged: SessionRevert?) {
        mutable.update { s -> if (s.loaded && s.staged == staged) s else s.copy(staged = staged, revision = s.revision + 1, loaded = true) }
    }

    /**
     * The history, oldest first. Pages come newest page first, each in chronological order (the gateway contract),
     * up to [maxPages] pages.
     */
    public suspend fun history(): Outcome<HistoryRead> {
        val g = gateways.value ?: return notConnected()
        val pages = ArrayList<List<MessageWithParts>>()
        var cursor: String? = null
        repeat(maxPages) {
            when (val page = g.live.messagePage(session, cursor, pageSize)) {
                is Outcome.Failed -> return page
                is Outcome.Ok -> {
                    pages += page.value.items
                    cursor = page.value.nextCursor ?: return HistoryRead(pages.asReversed().flatten().toImmutableList(), complete = true).ok()
                }
            }
        }
        return HistoryRead(pages.asReversed().flatten().toImmutableList(), complete = false).ok()
    }

    /** The files the turn of [messageId] changed; with null, the whole conversation's (tool evidence, B-CHAT-068). */
    public suspend fun changes(messageId: String?): Outcome<ChangeSet> =
        when (val read = history()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val messages = read.value.messages
                if (messageId == null) {
                    ChangeSet(
                        TurnChanges.editedFiles(messages.filter { it.info.role == Role.Assistant }),
                        TurnChanges.firstPrompt(messages).takeIf { read.value.complete },
                        read.value.complete,
                    ).ok()
                } else {
                    val steps = TurnChanges.turnSteps(messages, messageId)
                    ChangeSet(TurnChanges.editedFiles(steps), TurnChanges.turnPrompt(messages, messageId), read.value.complete).ok()
                }
            }
        }

    /** The working-tree diff as the server computes it, where it can (`sessionDiff`). */
    public suspend fun workingTree(): Outcome<ImmutableList<FileDiff>> {
        val g = gateways.value ?: return notConnected()
        if (!g.live.capabilities.value.sessionDiff) return Failure.Unavailable(FeatureId("sessionDiff")).failed()
        return g.operations.listVcsDiffs(VcsDiffMode.WorkingTree)
    }

    /** The words of the prompt [messageId] (file names for an attachment-only one); null when it is not a prompt. */
    public suspend fun prompt(messageId: String): Outcome<String?> {
        val g = gateways.value ?: return notConnected()
        val staged = (g.live as? StagedRevertGateway) ?: (g.operations as? StagedRevertGateway)
        if (staged != null) return staged.sessionRevertPrompt(session, MessageId(messageId))
        return when (val read = history()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> read.value.messages.firstOrNull { it.info.id == messageId }?.takeIf(TurnChanges::isPrompt)?.let(TurnChanges::promptText).ok()
        }
    }

    /** The paths completed edit tools reported after the prompt [promptId], first seen, once (B-CHAT-120). */
    public suspend fun editedAfter(promptId: String): Outcome<ImmutableList<String>> =
        when (val read = history()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> TurnChanges.editedPaths(TurnChanges.stepsAfter(read.value.messages, promptId)).ok()
        }

    /**
     * How many messages follow the prompt [promptId], across all kinds and pages, the prompt itself excluded
     * (B-CHAT-103). Null when the prompt was not reached: unknown is never shown as zero or partial.
     */
    public suspend fun messagesAfter(promptId: String): Outcome<Int?> {
        val g = gateways.value ?: return notConnected()
        var cursor: String? = null
        var newer = 0
        repeat(maxPages) {
            when (val page = g.live.messagePage(session, cursor, pageSize)) {
                is Outcome.Failed -> return page
                is Outcome.Ok -> {
                    // Pages come newest first, each in chronological order: count what follows within the page.
                    val ids = page.value.items.map { it.info.id }
                    val at = ids.indexOf(promptId)
                    if (at >= 0) return (newer + countNewer(page.value.items, at)).ok()
                    newer += ids.size
                    cursor = page.value.nextCursor ?: return Outcome.Ok(null)
                }
            }
        }
        return Outcome.Ok(null)
    }

    /** Messages of one chronological page newer than the one at [at]: by creation time when every row has one, else by position. */
    private fun countNewer(items: List<MessageWithParts>, at: Int): Int {
        val pivot = items[at].info.time?.createdMs
        return if (pivot != null && items.all { it.info.time?.createdMs != null }) {
            items.withIndex().count { (i, m) -> i != at && (m.info.time!!.createdMs!! > pivot || (m.info.time!!.createdMs == pivot && i < at)) }
        } else {
            items.size - 1 - at // chronological: everything after it is newer
        }
    }

    /** Stages an undo from the prompt [promptId] (with files back when [applyFiles]); the preview comes back at once. */
    public suspend fun stage(promptId: String, applyFiles: Boolean): Outcome<StagedReview> =
        exclusive { g ->
            val staged = (g.live as? StagedRevertGateway) ?: (g.operations as? StagedRevertGateway)
                ?: return@exclusive Failure.Unavailable(FeatureId("sessionRevert")).failed()
            when (val result = staged.stageSessionRevert(session, MessageId(promptId), applyFiles)) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    mutable.update { it.copy(staged = result.value, revision = it.revision + 1, loaded = true) }
                    StagedReview(result.value, mutable.value.revision).ok()
                }
            }
        }

    /** The one-step undo of servers without a staged one: removes the prompt and what follows, files back. */
    public suspend fun undoNow(promptId: String): Outcome<Unit> =
        exclusive { g ->
            if (shape() != UndoShape.OneStep) return@exclusive Failure.Unavailable(FeatureId("sessionRevert")).failed()
            val result = g.operations.revertSession(session, MessageId(promptId))
            if (result is Outcome.Ok) refreshQuietly(g)
            result
        }

    /** "Put everything back": clears the staged undo [review] (messages and files back). */
    public suspend fun putBack(review: StagedReview): Outcome<Unit> = resolve(review, commit = false)

    /** "Delete the hidden messages": makes the staged undo [review] permanent. */
    public suspend fun keep(review: StagedReview): Outcome<Unit> = resolve(review, commit = true)

    private suspend fun resolve(review: StagedReview, commit: Boolean): Outcome<Unit> =
        exclusive(beforeDispatch = { g ->
            // Fresh server boundaries block the act even when the change event was missed (B-CHAT-105).
            when (val read = g.live.session(session)) {
                is Outcome.Failed -> read
                is Outcome.Ok -> {
                    adopt(read.value.stagedRevert)
                    if (!isCurrent(review)) Failure.Refused(RefusalReason.StaleScope).failed() else Unit.ok()
                }
            }
        }) { g ->
            if (!isCurrent(review)) return@exclusive Failure.Refused(RefusalReason.StaleScope).failed()
            val staged = (g.live as? StagedRevertGateway) ?: (g.operations as? StagedRevertGateway)
                ?: return@exclusive Failure.Unavailable(FeatureId("sessionRevert")).failed()
            val result = if (commit) staged.commitSessionRevert(session) else staged.clearSessionRevert(session)
            when {
                result is Outcome.Ok -> {
                    mutable.update { it.copy(staged = null, revision = it.revision + 1) }
                    result
                }
                ambiguous((result as Outcome.Failed).failure) -> {
                    // The answer was lost: read the server before anyone tries again (B-CHAT-106).
                    when (val read = g.live.session(session)) {
                        is Outcome.Ok ->
                            if (read.value.stagedRevert == null) {
                                mutable.update { it.copy(staged = null, revision = it.revision + 1) }
                                Unit.ok()
                            } else {
                                adopt(read.value.stagedRevert)
                                result
                            }
                        is Outcome.Failed -> result
                    }
                }
                else -> result
            }
        }

    /**
     * One mutation at a time (concurrent ones are refused, never queued), checked against the server first, then
     * dispatched only if the conversation still uses the connection it was checked on (B-CHAT-106).
     */
    private suspend fun <T> exclusive(
        beforeDispatch: suspend (ReviewGateways) -> Outcome<Unit> = { Unit.ok() },
        act: suspend (ReviewGateways) -> Outcome<T>,
    ): Outcome<T> {
        if (!lane.tryLock()) return Failure.Busy(BusyKind.SessionBusy).failed()
        mutable.update { it.copy(working = true) }
        try {
            val g = gateways.value ?: return notConnected()
            when (val statuses = g.live.sessionStatuses()) {
                is Outcome.Failed -> return statuses
                is Outcome.Ok -> {
                    val running = statuses.value[session].let { it != null && it != SessionStatus.Idle }
                    mutable.update { it.copy(running = running) }
                    if (running) return Failure.Busy(BusyKind.SessionBusy).failed()
                }
            }
            val checked = beforeDispatch(g)
            if (checked is Outcome.Failed) return checked
            if (gateways.value !== g) return Failure.Refused(RefusalReason.StaleScope).failed()
            return act(g)
        } finally {
            mutable.update { it.copy(working = false) }
            lane.unlock()
        }
    }

    private suspend fun refreshQuietly(g: ReviewGateways) {
        (g.live.session(session) as? Outcome.Ok)?.let { adopt(it.value.stagedRevert) }
    }

    private fun ambiguous(failure: Failure): Boolean =
        failure is Failure.UnreadableReply || failure is Failure.TimedOut || failure is Failure.Network ||
            (failure as? Failure.Refused)?.reason == RefusalReason.DispatchUncertain

    private fun notConnected(): Outcome<Nothing> = Failure.Refused(RefusalReason.ProfileClosing).failed()

    public companion object {
        /** Nothing read yet: an empty change set. */
        public val Empty: ChangeSet = ChangeSet(persistentListOf(), null, complete = true)
    }
}
