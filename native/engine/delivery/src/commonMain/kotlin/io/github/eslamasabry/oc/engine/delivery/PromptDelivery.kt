package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.engine.delivery.port.ConversationFacts
import io.github.eslamasabry.oc.engine.delivery.port.DeliveryConnection
import io.github.eslamasabry.oc.engine.delivery.port.SendLink
import io.github.eslamasabry.oc.engine.delivery.port.StopMemory
import io.github.eslamasabry.oc.engine.delivery.port.nothingSent
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.QueueErrors
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRefusal
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceipt
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptController
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptState
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchProof
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@JvmInline public value class LocalSendId(public val raw: String)

@JvmInline public value class SendOutcomeId(public val raw: String)

/**
 * One send as the composer hands it over (09 §4.2). [typedText] is what the person typed, before
 * references were folded.
 */
public data class SendRequest(
    val ref: ConversationRef,
    val text: String,
    val mentions: ImmutableList<PromptAgentMention> = persistentListOf(),
    val attachments: ImmutableList<PromptAttachment> = persistentListOf(),
    val typedText: String = text,
    /** The conversation's selection at send time (saved with an offline or held entry). */
    val model: ModelSelection? = null,
    val agent: String? = null,
) {
    override fun toString(): String =
        "SendRequest(ref=${ref.sessionId.raw}, textLen=${text.length}, attachments=${attachments.size})"
}

public enum class SendPhase {
    /**
     * The bubble is shown; the transport, the model choice or storage is still being waited on.
     * Stop calls it off.
     */
    Waiting,

    /** The request is on the wire. Stop stops now and again once the server took it. */
    OnWire,

    /**
     * The server admitted it; the bubble stays until the transcript shows the server's copy (§4.6).
     */
    Accepted,
}

/** An optimistic bubble (09 §4.6). `toString` prints no text. */
public data class PendingSend(
    val localId: LocalSendId,
    val ref: ConversationRef,
    val route: SendRoute,
    val text: String,
    val attachments: ImmutableList<PromptAttachment>,
    val createdAtMs: Long,
    val phase: SendPhase,
    /**
     * The wire id when the gateway posted a client id; matches the transcript's user message
     * exactly.
     */
    val dispatchedMessageId: MessageId? = null,
    /** The durable marker's queue entry id (`online-<µs>`), when one was written. */
    val reviewId: String? = null,
    /** Chips and Continue never become draft text when Stop calls them off. */
    val restoreDraftOnCancel: Boolean = true,
) {
    val requestComplete: Boolean
        get() = phase == SendPhase.Accepted

    override fun toString(): String =
        "PendingSend(${localId.raw}, $route, $phase, textLen=${text.length})"
}

/**
 * Durable outcomes the composer applies (09 §4.2, engine order: delivery never calls the composer).
 */
public sealed interface SendOutcome {
    public val id: SendOutcomeId
    public val ref: ConversationRef

    /** Admitted. `:engine:composer` records history and learns the phrase from it. */
    public data class Accepted(
        override val id: SendOutcomeId,
        override val ref: ConversationRef,
        val localId: LocalSendId,
        val typedText: String,
        val phraseCandidate: Boolean,
        val route: SendRoute,
    ) : SendOutcome

    /** Nothing left the device: the words go back into the draft, before what was typed since. */
    public data class PutBack(
        override val id: SendOutcomeId,
        override val ref: ConversationRef,
        val text: String,
        val attachments: ImmutableList<PromptAttachment>,
        val reason: Failure,
        val localId: LocalSendId? = null,
    ) : SendOutcome

    /**
     * Bytes may have left without a confirmed outcome: "We couldn't confirm whether this was sent"
     * with Check again ([reviewId] when a marker exists). Never put back into the draft, never
     * resent on its own.
     */
    public data class Unconfirmed(
        override val id: SendOutcomeId,
        override val ref: ConversationRef,
        val reviewId: String?,
        val reason: Failure?,
        val localId: LocalSendId? = null,
    ) : SendOutcome

    /** Offline: waiting in the queue ("Send when back online"). */
    public data class Queued(
        override val id: SendOutcomeId,
        override val ref: ConversationRef,
        val entryId: String,
    ) : SendOutcome
}

/** The local half of the live line: a send accepted for dispatch (09 §4.1 rule 1). */
public data class LocalTurn(val sinceMs: Long, val sending: Boolean, val acceptedAtMs: Long? = null)

/** Whether a Stop tap asked the server (Asked) or fell inside the guard after a send and asked nothing (Ignored). */
public enum class StopAnswer {
    Asked,
    Ignored,
}

public enum class StopMode {
    Now,
    AfterThisStep,
}

public enum class ReceiptLook {
    Confirmed,
    StillUnknown,
}

/**
 * What a typed line is (09 §4.2 step 1): matched on what the person typed, before references are
 * folded in.
 */
public sealed interface TypedKind {
    public data object Prompt : TypedKind

    public data class Command(val name: String, val args: String) : TypedKind

    public data class Shell(val command: String) : TypedKind

    public companion object {
        private val COMMAND = Regex("^/(\\S+)(?:\\s+([\\s\\S]*))?$")
        private val SHELL = Regex("^!([^\\s!][\\s\\S]*)$")

        public fun of(typed: String): TypedKind {
            COMMAND.find(typed)?.let {
                return Command(it.groupValues[1], it.groupValues[2])
            }
            SHELL.find(typed)?.let {
                return Shell(it.groupValues[1])
            }
            return Prompt
        }
    }
}

/**
 * The send pipeline (port of `_performSend`; 09 §4.2–§4.4; rows B-STATE-039…043, 119, B-PROTO-025,
 * 028 (preflight order), 043). A prompt is never sent twice: an ordinary send on a queue-capable
 * server writes the durable marker (an `online-<µs>` queue entry) before the composer clears;
 * whatever happens after the request left — a lost response, a crash, a reconnect — leaves that
 * marker for the person's review, never for an automatic resend.
 *
 * [scope] is the connection scope: [send] returns once the bubble exists and the marker is on disk,
 * and the rest runs there, so leaving the chat never cancels a send.
 */
public class PromptDelivery(
    private val connection: DeliveryConnection,
    private val queue: OfflineQueue,
    private val journals: PendingCommandJournals,
    private val facts: ConversationFacts,
    private val stopMemory: StopMemory,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val mutablePending = MutableStateFlow<ImmutableList<PendingSend>>(persistentListOf())
    public val pending: StateFlow<ImmutableList<PendingSend>> = mutablePending

    private val mutableOutcomes = MutableStateFlow<ImmutableList<SendOutcome>>(persistentListOf())

    /** Bounded (≤ 32), survives the screen; an entry leaves when [acknowledge]d (09 §4.2). */
    public val outcomes: StateFlow<ImmutableList<SendOutcome>> = mutableOutcomes

    private val mutableLocalTurns =
        MutableStateFlow<ImmutableMap<ConversationRef, LocalTurn>>(persistentMapOf())
    public val localTurns: StateFlow<ImmutableMap<ConversationRef, LocalTurn>> = mutableLocalTurns

    /**
     * Guards the Waiting → OnWire step against Stop (a send is either called off or on the wire,
     * never both).
     */
    private val wire = Mutex()
    private val jobs = HashMap<LocalSendId, Job>()
    /**
     * A review outlives its optimistic bubble and the composer outcome acknowledgement
     * (B-STATE-043/044).
     */
    private val reviewIds = LinkedHashMap<LocalSendId, String>()
    private val stopAfterDispatch = HashSet<LocalSendId>()
    private val lastSendAtMs = HashMap<ConversationRef, Long>()
    private val selectionLocks = HashMap<ConversationRef, Mutex>()

    /** Runs on the queue lane only (every dispatch does), so the map needs no lock of its own. */
    private fun selectionLock(ref: ConversationRef): Mutex = selectionLocks.getOrPut(ref) { Mutex() }
    private var counter = 0L

    /**
     * Codex composer #3 (B-STATE-118/119): a picker change takes the same per-conversation lock as a send's
     * select-then-prompt step, so a choice made while a send is between its selection check and its prompt can't
     * land in between (OC2's prompt carries no model: the session's model at admission is what runs).
     */
    public suspend fun <T> withSelection(ref: ConversationRef, block: suspend () -> T): T {
        val lock = withContext(queue.lane) { selectionLock(ref) }
        return lock.withLock { block() }
    }

    public fun acknowledge(id: SendOutcomeId) {
        mutableOutcomes.value = mutableOutcomes.value.filter { it.id != id }.toImmutableList()
    }

    /** What Send does right now in [ref] (the composer's words come from this, 09 §4.3). */
    public fun routeFor(ref: ConversationRef): SendRoute {
        val link =
            connection.link.value?.takeIf { it.connected && it.scope.profileId == ref.profileId }
        return SteerPolicy.route(
            online = link != null,
            running = running(ref),
            capabilities = link?.capabilities ?: return SendRoute.Offline,
            hasMidTurnFacet = link.hasMidTurnFacet,
            midTurnPrompt = link.midTurnPrompt(ref.sessionId),
        )
    }

    public suspend fun send(request: SendRequest): Outcome<LocalSendId> =
        withContext(queue.lane) { perform(request, putBack = true) }

    /**
     * Chips, Continue, Send again: the same pipeline, but a failure never leaves its words in the
     * box.
     */
    public suspend fun sendInsteadOfDraft(request: SendRequest): Outcome<LocalSendId> =
        withContext(queue.lane) { perform(request, putBack = false) }

    /** Texts of dispatched, not-confirmed sends: the draft never restores one (09 §10.8). */
    public fun unconfirmedTexts(ref: ConversationRef): Set<String> = queue.unconfirmedTexts(ref)

    // ------------------------------------------------------------------------------------------------ the pipeline

    private suspend fun perform(request: SendRequest, putBack: Boolean): Outcome<LocalSendId> {
        val ref = request.ref
        // 1. Route by what the person typed.
        // Files never ride the command path: with attachments, `/…` and `!…` go out as a prompt
        // (Flutter chat_send.dart:293), so no file is dropped.
        val kind =
            if (request.attachments.isNotEmpty()) TypedKind.Prompt
            else TypedKind.of(request.typedText.trim())
        // 2. A staged undo is resolved first.
        if (facts.undoUnresolved(ref)) return Failure.Refused(RefusalReason.UndoUnresolved).failed()
        // 4. Offline: wait for the transport (a send after a resume waits instead of queueing),
        // then the queue.
        var link = currentLink(ref)
        if (link == null || !link.connected) {
            link =
                withTimeoutOrNull(OFFLINE_WAIT) {
                    connection.link.first {
                        it != null && it.connected && it.scope.profileId == ref.profileId
                    }
                }
                    ?: return offline(
                        request,
                        kind,
                        connection.link.value?.takeIf { it.scope.profileId == ref.profileId },
                    )
        }
        val origin = link.scope
        if (kind !is TypedKind.Prompt) return sendCommand(link, origin, request, kind)
        val route =
            SteerPolicy.route(
                online = true,
                running = running(ref),
                capabilities = link.capabilities,
                hasMidTurnFacet = link.hasMidTurnFacet,
                midTurnPrompt = link.midTurnPrompt(ref.sessionId),
            )
        if (route == SendRoute.Blocked)
            return Failure.Unavailable(FeatureId("midTurnPrompt")).failed()
        // 3. "Stop and send" agents: stop first.
        if (route == SendRoute.RestartTurn) {
            val ready = link.awaitActionTransport()
            if (ready is Outcome.Failed) return ready
            if (!sameTarget(link, origin)) return Failure.Refused(RefusalReason.StaleScope).failed()
            stopMemory.noteStopFromThisApp(ref)
            when (val stopped = link.stop(ref.sessionId)) {
                is Outcome.Failed -> return stopped
                is Outcome.Ok ->
                    if (stopped.value == StopResult.Uncertain) {
                        return Failure.Refused(RefusalReason.DispatchUncertain).failed()
                    }
            }
            if (!sameTarget(link, origin)) return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        val now = clock.now().toEpochMilliseconds()
        val localId = LocalSendId("local-$now-${nextCounter()}")
        // 5. The durable dispatch marker, before the composer clears (not for steers).
        var reviewId: String? = null
        if (SteerPolicy.holdsMarker(route, link.capabilities)) {
            val id = "online-${now * 1000 + nextCounter() % 1000}"
            val held = queue.holdOnline(entryFor(id, request, now, dispatchedAt = now))
            if (!held)
                return Failure.StorageRefused()
                    .failed() // the prompt stays unsent and the words stay
            reviewId = id
            forgetResolvedReviews()
            reviewIds[localId] = id
        }
        val bubble =
            PendingSend(
                localId,
                ref,
                route,
                request.text,
                request.attachments,
                now,
                SendPhase.Waiting,
                reviewId = reviewId,
                restoreDraftOnCancel = putBack,
            )
        lastSendAtMs[ref] = now
        if (SteerPolicy.bubbleFirst(route)) {
            // 6. Bubble first; the composer clears its field when this returns.
            showBubble(bubble)
            jobs[localId] =
                scope.launch(queue.lane) { dispatch(link, origin, bubble, request, putBack) }
        } else {
            // Steers stay transport-first: they need the exact message id.
            val ready = link.awaitActionTransport()
            if (ready is Outcome.Failed || !sameTarget(link, origin)) {
                return (ready as? Outcome.Failed)?.failure?.failed()
                    ?: Failure.Refused(RefusalReason.StaleScope).failed()
            }
            showBubble(bubble)
            jobs[localId] =
                scope.launch(queue.lane) { dispatch(link, origin, bubble, request, putBack) }
        }
        return localId.ok()
    }

    private suspend fun dispatch(
        link: SendLink,
        origin: DispatchScope,
        bubble: PendingSend,
        request: SendRequest,
        putBack: Boolean,
    ) {
        val ref = bubble.ref
        // 7. Transport, then the epoch: a changed connection dispatches to neither server, and the
        // session's model is never mutated before both hold.
        val ready = link.awaitActionTransport()
        if (ready is Outcome.Failed) return failed(bubble, request, putBack, ready.failure, left = false)
        if (!sameTarget(link, origin))
            return failed(bubble, request, putBack, Failure.Refused(RefusalReason.StaleScope), left = false)
        // The model is session state on servers with session selection: a send's selection and its
        // prompt's admission form one step, so another send's choice can never land in between.
        // Only sends carrying a choice take the lock, and it spans one request, never a turn: plain
        // sends and steers never wait (no invisible queue).
        if (request.model == null && request.agent == null) admit(link, origin, bubble, request, putBack)
        else selectionLock(ref).withLock { admit(link, origin, bubble, request, putBack) }
    }

    private suspend fun admit(
        link: SendLink,
        origin: DispatchScope,
        bubble: PendingSend,
        request: SendRequest,
        putBack: Boolean,
    ) {
        val ref = bubble.ref
        // 7b. The conversation's intentional model/agent choice reaches the server before the prompt is
        // admitted to the wire — a choice is never silently dropped. A failed or stale selection sends
        // nothing and the words stay (never an unknown-prompt receipt); Stop may have called the send off
        // while it waited, and Stop itself already put the words back, so a vanished bubble just ends.
        if (mutablePending.value.none { it.localId == bubble.localId }) return
        if (request.model != null || request.agent != null) {
            if (!sameTarget(link, origin))
                return failed(bubble, request, putBack, Failure.Refused(RefusalReason.StaleScope), left = false)
            when (val applied = link.applySelection(ref.sessionId, request.model, request.agent)) {
                is Outcome.Failed -> return failed(bubble, request, putBack, applied.failure, left = false)
                is Outcome.Ok -> Unit
            }
            if (mutablePending.value.none { it.localId == bubble.localId }) return
            if (!sameTarget(link, origin)) {
                return failed(
                    bubble,
                    request,
                    putBack,
                    Failure.Refused(RefusalReason.StaleScope),
                    left = false,
                )
            }
        }
        val clientId =
            if (link.correlated && link.capabilities.clientPromptMessageID)
                link.newClientMessageId()
            else null
        val proceed = wire.withLock {
            val current =
                mutablePending.value.firstOrNull { it.localId == bubble.localId }
                    ?: return@withLock null
            val stale =
                !sameTarget(link, origin) ||
                    (bubble.reviewId != null && !queue.canDispatchOnline(bubble.reviewId))
            if (stale) return@withLock false
            update(current.copy(phase = SendPhase.OnWire, dispatchedMessageId = clientId))
            true
        }
        when (proceed) {
            null -> return // called off by Stop: Stop already put the words back
            false ->
                return failed(
                    bubble,
                    request,
                    putBack,
                    Failure.Refused(RefusalReason.StaleScope),
                    left = false,
                )
            true -> Unit
        }
        // 8. Dispatch.
        val prompt =
            OutgoingPrompt(
                text = request.text,
                model = request.model,
                agent = request.agent,
                attachments = request.attachments,
                mentions = request.mentions,
                delivery = bubble.route.wireDelivery,
                clientMessageId = clientId,
            )
        fun fence(): Boolean =
            sameTarget(link, origin) &&
                (bubble.reviewId == null || queue.canDispatchOnline(bubble.reviewId))
        var failure: Failure? = null
        val accepted: Boolean
        val journal = (journals.forProfile(ref.profileId) as? Outcome.Ok)?.value
        if (
            bubble.reviewId != null &&
                clientId != null &&
                link.capabilities.commandReceipts &&
                journal != null
        ) {
            val receipt =
                CommandReceipt(
                    commandId = CommandReceipt.queueCommandId(bubble.reviewId),
                    receiptId = clientId.raw,
                    sessionId = ref.sessionId.raw,
                    tabId = ref.sessionId.raw,
                    scope = origin.receiptScope,
                    createdAt = bubble.createdAtMs,
                    state = CommandReceiptState.Sent,
                )
            val sent =
                CommandReceiptController(journal).send(receipt) { rid ->
                    val r =
                        link.prompt(ref.sessionId, prompt.copy(clientMessageId = MessageId(rid))) {
                            fence() && journal.isOpen
                        }
                    if (confirms(r, rid)) DispatchProof.Confirmed
                    else DispatchProof.Uncertain.also { failure = (r as? Outcome.Failed)?.failure }
                }
            accepted = sent is Outcome.Ok && sent.value.state == CommandReceiptState.Confirmed
            if (sent is Outcome.Failed)
                failure =
                    Failure.Refused(RefusalReason.DispatchUncertain) // claim refused: nothing sent
        } else {
            val r = link.prompt(ref.sessionId, prompt) { fence() }
            accepted = confirms(r, clientId?.raw)
            if (!accepted) failure = (r as? Outcome.Failed)?.failure
        }
        if (accepted) {
            // 9. Accepted.
            bubble.reviewId?.let {
                queue.finishOnline(it, dispatched = true, accepted = true, error = null)
            }
            forgetResolvedReviews()
            stopMemory.clearStoppedByPerson(ref)
            mutablePending.value
                .firstOrNull { it.localId == bubble.localId }
                ?.let { update(it.copy(phase = SendPhase.Accepted)) }
            setLocalTurn(
                ref,
                LocalTurn(
                    bubble.createdAtMs,
                    sending = false,
                    acceptedAtMs = clock.now().toEpochMilliseconds(),
                ),
            )
            emit {
                SendOutcome.Accepted(
                    it,
                    ref,
                    bubble.localId,
                    request.typedText,
                    phraseCandidate = putBack,
                    route = bubble.route,
                )
            }
            jobs -= bubble.localId
            if (stopAfterDispatch.remove(bubble.localId) && sameTarget(link, origin))
                stopNow(ref) // Stop pressed while on the wire: stop again now
        } else {
            // 10. Failed. A refusal the port promises sent nothing; anything else may have been
            // delivered.
            val left = failure?.nothingSent != true
            failed(bubble, request, putBack, failure, left)
        }
    }

    private suspend fun failed(
        bubble: PendingSend,
        request: SendRequest,
        putBack: Boolean,
        failure: Failure?,
        left: Boolean,
    ) {
        val ref = bubble.ref
        removeBubble(bubble.localId)
        clearLocalTurn(ref)
        jobs -= bubble.localId
        stopAfterDispatch -= bubble.localId
        if (left) {
            // Keep the marker; never auto-resend; Check again looks the receipt up.
            bubble.reviewId?.let {
                queue.finishOnline(
                    it,
                    dispatched = true,
                    accepted = false,
                    error = failure?.let(QueueErrors::of) ?: QueueErrors.UNCONFIRMED,
                )
            }
            emit { SendOutcome.Unconfirmed(it, ref, bubble.reviewId, failure, bubble.localId) }
        } else {
            bubble.reviewId?.let {
                queue.finishOnline(it, dispatched = false, accepted = false, error = null)
            }
            forgetResolvedReviews()
            val reason = failure ?: Failure.Unknown()
            if (putBack)
                emit {
                    SendOutcome.PutBack(
                        it,
                        ref,
                        request.text,
                        request.attachments,
                        reason,
                        bubble.localId,
                    )
                }
        }
    }

    private suspend fun offline(
        request: SendRequest,
        kind: TypedKind,
        lastLink: SendLink?,
    ): Outcome<LocalSendId> {
        val caps = lastLink?.capabilities
        if (kind !is TypedKind.Prompt || caps == null || !caps.offlinePromptQueue) {
            return Failure.Network(NetworkWhy.Offline)
                .failed() // "draft saved": the composer keeps the words
        }
        val now = clock.now().toEpochMilliseconds()
        val id = "${now * 1000 + nextCounter() % 1000}"
        return when (
            val queued = queue.queuePrompt(entryFor(id, request, now, dispatchedAt = null))
        ) {
            is Outcome.Failed -> queued
            is Outcome.Ok ->
                when (queued.value) {
                    null -> {
                        emit { SendOutcome.Queued(it, request.ref, id) }
                        LocalSendId(id).ok()
                    }
                    QueueRefusal.Closed -> Failure.Refused(RefusalReason.ProfileClosing).failed()
                    QueueRefusal.TooLarge,
                    QueueRefusal.NoRoom,
                    QueueRefusal.DuplicateId ->
                        Failure.Refused(RefusalReason.OfflineQueueFull).failed()
                }
        }
    }

    private suspend fun sendCommand(
        link: SendLink,
        origin: DispatchScope,
        request: SendRequest,
        kind: TypedKind,
    ): Outcome<LocalSendId> {
        val ref = request.ref
        val ready = link.awaitActionTransport()
        if (ready is Outcome.Failed) return ready
        if (!sameTarget(link, origin)) return Failure.Refused(RefusalReason.StaleScope).failed()
        val result =
            when (kind) {
                is TypedKind.Command -> link.slashCommand(ref.sessionId, kind.name, kind.args)
                is TypedKind.Shell -> link.shell(ref.sessionId, kind.command)
                TypedKind.Prompt -> error("unreachable")
            }
        return when (result) {
            is Outcome.Ok -> {
                val now = clock.now().toEpochMilliseconds()
                setLocalTurn(ref, LocalTurn(now, sending = false, acceptedAtMs = now))
                LocalSendId("cmd-$now-${nextCounter()}").ok()
            }
            is Outcome.Failed -> result
        }
    }

    // ------------------------------------------------------------------------------------------------ stop (09 §4.4)

    /**
     * Stop for the whole running turn. Ignored for 800 ms after a send (a double tap on Send never
     * stops). A send still waiting is called off and its words come back; one on the wire is
     * stopped now and again once the server took it. Records `stoppedByPerson` so a stop by the
     * person is never "Failed" (B-AGENT-243).
     */
    public suspend fun stop(ref: ConversationRef, mode: StopMode = StopMode.Now): Outcome<Unit> =
        withContext(queue.lane) { stopOnLane(ref, mode).map {} }

    /**
     * [stop], saying whether the tap was heard: [StopAnswer.Ignored] inside the 800 ms after a send asked the server
     * nothing, so the caller keeps Stop usable (Flutter's ignored-tap guard, Codex chat-bugs 2).
     */
    public suspend fun stopTurn(ref: ConversationRef, mode: StopMode = StopMode.Now): Outcome<StopAnswer> =
        withContext(queue.lane) { stopOnLane(ref, mode) }

    private suspend fun stopOnLane(
        ref: ConversationRef,
        mode: StopMode = StopMode.Now,
    ): Outcome<StopAnswer> {
        if (mode == StopMode.AfterThisStep)
            return Failure.Unavailable(FeatureId("stopAfterStep")).failed()
        val now = clock.now().toEpochMilliseconds()
        lastSendAtMs[ref]?.let {
            if (now - it < STOP_IGNORE_AFTER_SEND.inWholeMilliseconds) return StopAnswer.Ignored.ok()
        }
        val calledOff = wire.withLock {
            val waiting =
                mutablePending.value.filter { it.ref == ref && it.phase == SendPhase.Waiting }
            waiting.forEach { removeBubble(it.localId) }
            stopAfterDispatch +=
                mutablePending.value
                    .filter { it.ref == ref && it.phase == SendPhase.OnWire }
                    .map { it.localId }
            waiting
        }
        for (bubble in calledOff) {
            jobs.remove(bubble.localId)?.cancel()
            bubble.reviewId?.let {
                queue.finishOnline(it, dispatched = false, accepted = false, error = null)
            }
            forgetResolvedReviews()
            if (bubble.restoreDraftOnCancel)
                emit {
                    SendOutcome.PutBack(
                        it,
                        ref,
                        bubble.text,
                        bubble.attachments,
                        Failure.Refused(RefusalReason.StaleScope),
                        localId = bubble.localId,
                    )
                }
        }
        return stopNow(ref).map { StopAnswer.Asked }
    }

    private suspend fun stopNow(ref: ConversationRef): Outcome<Unit> {
        clearLocalTurn(ref)
        val link = currentLink(ref) ?: return Failure.Network(NetworkWhy.Offline).failed()
        val origin = link.scope
        withTimeoutOrNull(STOP_TRANSPORT_WAIT) { link.awaitActionTransport() }
        if (!sameTarget(link, origin)) return Failure.Refused(RefusalReason.StaleScope).failed()
        stopMemory.noteStopFromThisApp(
            ref
        ) // before the call: the server's interrupt must not read as another device
        stopMemory.markStoppedByPerson(ref)
        return when (val r = link.stop(ref.sessionId)) {
            is Outcome.Ok -> Unit.ok()
            is Outcome.Failed -> r
        }
    }

    // ------------------------------------------------------------------------------------------------ review

    /** Looks the receipt up; never sends (B-STATE-008). */
    public suspend fun checkAgain(id: LocalSendId): Outcome<ReceiptLook> =
        withContext(queue.lane) { checkAgainOnLane(id) }

    private suspend fun checkAgainOnLane(id: LocalSendId): Outcome<ReceiptLook> {
        val confirmed = queue.checkReceipt(reviewIdOf(id))
        forgetResolvedReviews()
        return (if (confirmed) ReceiptLook.Confirmed else ReceiptLook.StillUnknown).ok()
    }

    /**
     * Sends again only on the person's explicit confirmation; a receipt-bearing command only looks
     * (B-STATE-008, 044).
     */
    public suspend fun resend(id: LocalSendId, confirmedByPerson: Boolean): Outcome<Boolean> =
        withContext(queue.lane) { resendOnLane(id, confirmedByPerson) }

    private suspend fun resendOnLane(
        id: LocalSendId,
        confirmedByPerson: Boolean,
    ): Outcome<Boolean> {
        if (!confirmedByPerson) return Failure.Refused(RefusalReason.DispatchUncertain).failed()
        val result = queue.resend(reviewIdOf(id))
        forgetResolvedReviews()
        return result
    }

    private fun reviewIdOf(id: LocalSendId): String =
        reviewIds[id] ?: mutablePending.value.firstOrNull { it.localId == id }?.reviewId ?: id.raw

    private fun forgetResolvedReviews() {
        val retained = queue.entries().mapTo(HashSet()) { it.id }
        reviewIds.keys.retainAll(reviewIds.filterValues { it in retained }.keys)
    }

    // ------------------------------------------------------------------------------------------------ state

    private fun running(ref: ConversationRef): Boolean =
        facts.running(ref) || mutableLocalTurns.value[ref] != null

    private fun sameTarget(link: SendLink, origin: DispatchScope): Boolean =
        connection.link.value?.epoch === link.epoch &&
            link.scope == origin &&
            connection.activeProfile.value == origin.profileId

    private fun currentLink(ref: ConversationRef): SendLink? =
        connection.link.value?.takeIf { it.scope.profileId == ref.profileId }

    private fun entryFor(
        id: String,
        request: SendRequest,
        now: Long,
        dispatchedAt: Long?,
    ): QueuedPrompt =
        QueuedPrompt(
            id = id,
            profileId = request.ref.profileId,
            sessionId = request.ref.sessionId,
            text = request.text,
            attachments = request.attachments,
            mentions = request.mentions,
            modelProviderId = request.model?.model?.providerId,
            modelId = request.model?.model?.modelId,
            agent = request.agent,
            variant = request.model?.variant,
            createdAt = now,
            dispatchedAt = dispatchedAt,
        )

    private fun showBubble(bubble: PendingSend) {
        mutablePending.value = (mutablePending.value + bubble).toImmutableList()
        setLocalTurn(bubble.ref, LocalTurn(bubble.createdAtMs, sending = true))
    }

    private fun update(bubble: PendingSend) {
        mutablePending.value =
            mutablePending.value
                .map { if (it.localId == bubble.localId) bubble else it }
                .toImmutableList()
    }

    private fun removeBubble(id: LocalSendId) {
        mutablePending.value = mutablePending.value.filter { it.localId != id }.toImmutableList()
    }

    /** The transcript showed the server's copy (§4.6 reconcile): the bubble leaves. */
    public fun reconciled(id: LocalSendId) {
        removeBubble(id)
    }

    /**
     * The server went idle (or a finished step ended the turn): the local guess clears (09 §4.1
     * rules 2, 6).
     */
    public fun turnEnded(ref: ConversationRef) {
        if (mutablePending.value.none { it.ref == ref && it.phase != SendPhase.Accepted })
            clearLocalTurn(ref)
    }

    private fun setLocalTurn(ref: ConversationRef, turn: LocalTurn) {
        mutableLocalTurns.value = (mutableLocalTurns.value + (ref to turn)).toImmutableMap()
    }

    private fun clearLocalTurn(ref: ConversationRef) {
        mutableLocalTurns.value = (mutableLocalTurns.value - ref).toImmutableMap()
    }

    private fun emit(make: (SendOutcomeId) -> SendOutcome) {
        val outcome = make(SendOutcomeId("outcome-${nextCounter()}"))
        mutableOutcomes.value =
            (mutableOutcomes.value + outcome).takeLast(MAX_OUTCOMES).toImmutableList()
    }

    private fun nextCounter(): Long = ++counter

    private fun confirms(r: Outcome<PromptAdmission>, clientId: String?): Boolean {
        val admission = (r as? Outcome.Ok)?.value as? PromptAdmission.Confirmed ?: return false
        // A receipt naming another id is not ours: unconfirmed, never a reason to send again.
        return clientId == null || admission.receiptId == null || admission.receiptId == clientId
    }

    public companion object {
        public const val MAX_OUTCOMES: Int = 32
        public val OFFLINE_WAIT: kotlin.time.Duration = 10.seconds
        public val STOP_TRANSPORT_WAIT: kotlin.time.Duration = 5.seconds

        /** `MotionTokens.STOP_IGNORE_AFTER_SEND` (fix 49d067f2d). */
        public val STOP_IGNORE_AFTER_SEND: kotlin.time.Duration = 800.milliseconds
    }
}
