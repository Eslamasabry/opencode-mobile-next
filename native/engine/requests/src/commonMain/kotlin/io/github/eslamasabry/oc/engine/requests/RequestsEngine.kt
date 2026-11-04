package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PermissionReply
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Requests that wait on the person, for one live connection (plan 09 §5.1; slice 2: permissions and
 * questions).
 *
 * - [pending] is every waiting request of the connection, oldest first; [forSession] is the one
 *   card a chat shows.
 * - [answer] starts the [HELD_ANSWER] hold and returns at once; [undo] works inside the hold only;
 *   a second answer is refused ([RefusalReason.AlreadyAnswered]); [flushHeld] (leaving the chat)
 *   and [close] send held answers now.
 * - Exactly one reply per request (B-CHAT-074): every phase change is one atomic claim on
 *   [pending], and every caller of [answerNow] (a notification action) shares the one in-flight
 *   write (B-AGENT-031).
 * - Asks are never replayed: every Connected refetches, and [RequestsTimers.REREAD] re-reads while
 *   the person can act. A refetch never resurrects a request that settled while it was in flight
 *   (B-PROTO-048).
 *
 * Structure changes (add, remove, the settled ledger) run on the engine's serial lane; phase
 * changes are atomic updates from any thread. The connection graph owns [scope] and cancels it on
 * disconnect, after [close].
 */
public class RequestsEngine(
    private val port: RequestsPort,
    private val scope: CoroutineScope,
    dispatchers: AppDispatchers,
    private val clock: Clock,
    pacer: Pacer,
    private val holdWindow: Duration = HELD_ANSWER,
) {
    private val lane = dispatchers.serial("requests")
    private val log = AppLog.tagged("requests")
    private val state = MutableStateFlow(NoRequests)
    private val tokens = AtomicLong(0)

    // Lane-confined bookkeeping.
    private var seq = 0L

    private data class PendingRead(
        val epoch: Long,
        val result: CompletableDeferred<Outcome<Unit>> = CompletableDeferred(),
    )

    private var readEpoch = 0L
    private var pendingRead: PendingRead? = null
    private val addedAt = HashMap<RequestId, Long>()
    private val settled = LinkedHashSet<RequestId>()

    /**
     * First display on this phone, by request id (survives a refetch that drops and re-adds the
     * request).
     */
    private val firstShown = MutableStateFlow<Map<RequestId, Long>>(emptyMap())

    /** Every waiting request of this connection, oldest first. */
    public val pending: StateFlow<ImmutableList<PendingRequest>> = state.asStateFlow()

    init {
        scope.launch(lane) {
            // Pending-list snapshots cover the current project; other-project attention has its own
            // owner.
            port.events.project.collect { envelope ->
                when (val event = envelope.event) {
                    GatewayEvent.Connected -> {
                        readEpoch++
                        scope.launch(lane) { refresh() }
                    }
                    is GatewayEvent.PermissionAsked ->
                        upsert(PendingRequest.Permission(event.request))
                    is GatewayEvent.PermissionSettled -> remove(event.requestId)
                    else -> Unit
                }
            }
        }
        scope.launch(lane) { pacer.ticks(RequestsTimers.REREAD).collect { refresh() } }
    }

    /**
     * The card one chat shows and how many more wait (plan 09 §5.1 "one card at a time, oldest
     * first").
     */
    public fun forSession(session: SessionId): Flow<SessionRequests> =
        state.map { SessionRequests.of(it, session) }.distinctUntilChanged()

    /**
     * The card for [id] is on screen: its age counts from the first time (reopening does not
     * restart it).
     */
    public fun markShown(id: RequestId) {
        val now = clock.now().toEpochMilliseconds()
        var at = now
        firstShown.update { ledger ->
            at = ledger[id] ?: now
            if (id in ledger) ledger else (ledger + (id to now)).trimmed()
        }
        state.update { list ->
            list.mapEntry(id) { if (it.firstShownAtMs == null) it.withShown(at) else it }
        }
    }

    /**
     * The person answered on the card: held for [HELD_ANSWER], then sent once. Refused when the
     * request is not waiting (a second answer, or one already settled), when the answer does not
     * fit the request, or when Always allow is asked where the server keeps no grants or the
     * request offers none. [expectedRequest] is the immutable request the person or automatic
     * policy actually reviewed, not only a reused wire id.
     */
    public fun answer(
        id: RequestId,
        answer: RequestAnswer,
        expectedRequest: PendingRequest? = null,
    ): Outcome<Unit> {
        val until = clock.now().toEpochMilliseconds() + holdWindow.inWholeMilliseconds
        val token = tokens.incrementAndFetch()
        var refusal: Failure? = null
        state.update { list ->
            val entry = list.firstOrNull { it.id == id }
            refusal =
                when {
                    expectedRequest != null &&
                        (entry == null ||
                            expectedRequest.id != id ||
                            !entry.sameIncarnation(expectedRequest)) ->
                        Failure.Refused(RefusalReason.StaleScope)
                    entry == null || entry.phase != AnswerPhase.Waiting ->
                        Failure.Refused(RefusalReason.AlreadyAnswered)
                    else -> admissible(entry, answer)
                }
            if (refusal != null) list
            else list.mapEntry(id) { it.withPhase(AnswerPhase.Held(answer, until, token)) }
        }
        refusal?.let {
            return it.failed()
        }
        scope.launch(lane) {
            delay(holdWindow)
            val held = phaseOf(id) as? AnswerPhase.Held
            if (held?.token == token) send(id, held)
        }
        return Unit.ok()
    }

    /** True only while this captured request still has the same immutable contents. */
    public fun isCurrent(request: PendingRequest): Boolean =
        state.value.firstOrNull { it.id == request.id }?.sameIncarnation(request) == true

    /** Takes a held answer back; true when there was one. Nothing is sent. */
    public fun undo(id: RequestId, expectedRequest: PendingRequest? = null): Boolean {
        var undone = false
        state.update { list ->
            undone = false
            list.mapEntry(id) { entry ->
                if (
                    entry.phase is AnswerPhase.Held &&
                        (expectedRequest == null || entry.sameIncarnation(expectedRequest))
                )
                    entry.withPhase(AnswerPhase.Waiting).also { undone = true }
                else entry
            }
        }
        return undone
    }

    /**
     * An answer that must leave now (a notification action): the first decision wins. A held answer
     * is sent now instead of [answer]; a write already out is awaited, never repeated; a request
     * already gone is Ok (B-PROTO-074).
     */
    public suspend fun answerNow(
        id: RequestId,
        answer: RequestAnswer,
        expectedRequest: PendingRequest? = null,
    ): Outcome<Unit> {
        while (true) {
            val entry = state.value.firstOrNull { it.id == id } ?: return Unit.ok()
            if (
                expectedRequest != null &&
                    (expectedRequest.id != id || !entry.sameIncarnation(expectedRequest))
            )
                return Failure.Refused(RefusalReason.StaleScope).failed()
            when (val phase = entry.phase) {
                is AnswerPhase.Sending -> return phase.result.await()
                is AnswerPhase.Sent -> return Unit.ok()
                is AnswerPhase.Held ->
                    send(id, phase, expectedRequest)?.let {
                        return it
                    }
                AnswerPhase.Waiting,
                is AnswerPhase.NotAccepted -> {
                    admissible(entry, answer)?.let {
                        return it.failed()
                    }
                    claimAndWrite(id, phase, answer, expectedRequest ?: entry)?.let {
                        return it
                    }
                }
            }
        }
    }

    /** Try again after "Not accepted": the same answer, sent once more. */
    public suspend fun retry(
        id: RequestId,
        expectedRequest: PendingRequest? = null,
    ): Outcome<Unit> {
        val phase =
            phaseOf(id) as? AnswerPhase.NotAccepted
                ?: return Failure.Refused(RefusalReason.AlreadyAnswered).failed()
        return claimAndWrite(id, phase, phase.answer, expectedRequest)
            ?: Failure.Refused(RefusalReason.AlreadyAnswered).failed()
    }

    /** Leaving the chat: its held answers go now (plan 09 §5.1). */
    public suspend fun flushHeld(session: SessionId) {
        flush { it.sessionId == session }
    }

    /**
     * The connection is going away: held answers are sent, not lost (Flutter
     * `DelayedAnswers.dispose`).
     */
    public suspend fun close() {
        flush { true }
    }

    /**
     * Re-reads what waits on the server and reconciles: new asks join at the end, asks no longer
     * listed leave (a held answer to one is dropped: it was answered elsewhere), an in-flight write
     * keeps its request. A failed read keeps what is known for that kind and is returned.
     */
    public suspend fun refresh(): Outcome<Unit> =
        withContext(lane) {
            val epoch = readEpoch
            pendingRead
                ?.takeIf { it.epoch == epoch }
                ?.let {
                    return@withContext it.result.await()
                }
            val mine = PendingRead(epoch)
            pendingRead = mine
            try {
                readPending(epoch).also { mine.result.complete(it) }
            } finally {
                if (!mine.result.isCompleted)
                    mine.result.complete(Failure.Refused(RefusalReason.StaleScope).failed())
                if (pendingRead === mine) pendingRead = null
            }
        }

    private suspend fun readPending(epoch: Long): Outcome<Unit> {
        val caps = port.capabilities.value
        val start = seq
        val permissions = if (caps.permissionRequests) port.pendingPermissions() else null
        val questions = if (caps.legacyQuestionRequests) port.pendingQuestions() else null
        if (readEpoch != epoch) return Failure.Refused(RefusalReason.StaleScope).failed()
        if (permissions is Outcome.Ok) {
            reconcile(permissions.value.map { PendingRequest.Permission(it) }, start) {
                it is PendingRequest.Permission
            }
        }
        if (questions is Outcome.Ok) {
            reconcile(questions.value.map { PendingRequest.Question(it) }, start) {
                it is PendingRequest.Question
            }
        }
        return (permissions as? Outcome.Failed) ?: (questions as? Outcome.Failed) ?: Unit.ok()
    }

    // ---- writes

    private suspend fun flush(which: (PendingRequest) -> Boolean) {
        val held = state.value.filter { which(it) && it.phase is AnswerPhase.Held }
        coroutineScope {
            held.forEach { entry ->
                launch { (entry.phase as? AnswerPhase.Held)?.let { send(entry.id, it) } }
            }
        }
    }

    /** Sends a held answer if [held] is still its phase; null when someone else moved it first. */
    private suspend fun send(
        id: RequestId,
        held: AnswerPhase.Held,
        expectedRequest: PendingRequest? = null,
    ): Outcome<Unit>? = claimAndWrite(id, held, held.answer, expectedRequest)

    /**
     * Claims and captures the entry together; the connection owns the write, callers only await its
     * result.
     */
    private suspend fun claimAndWrite(
        id: RequestId,
        expected: AnswerPhase,
        answer: RequestAnswer,
        expectedRequest: PendingRequest? = null,
    ): Outcome<Unit>? {
        val reviewed =
            expectedRequest ?: state.value.firstOrNull { it.id == id } ?: return Unit.ok()
        val sending =
            AnswerPhase.Sending(answer, clock.now().toEpochMilliseconds(), CompletableDeferred())
        var claimed: PendingRequest? = null
        var refusal: Failure? = null
        state.update { list ->
            claimed = null
            refusal = null
            list.mapEntry(id) { entry ->
                if (reviewed.id != id || !entry.sameIncarnation(reviewed)) {
                    refusal = Failure.Refused(RefusalReason.StaleScope)
                    entry
                } else if (entry.phase !== expected) entry
                else {
                    val invalid = admissible(entry, answer)
                    refusal = invalid
                    if (invalid != null) entry.withPhase(AnswerPhase.NotAccepted(answer, invalid))
                    else entry.withPhase(sending).also { claimed = it }
                }
            }
        }
        refusal?.let {
            return it.failed()
        }
        val entry = claimed ?: return null
        val write =
            scope.launch(lane) {
                // Settlement can remove a claimed entry before this job starts or while the
                // transport wakes.
                val written =
                    if (phaseOf(id) !== sending) Unit.ok()
                    else {
                        val transport = port.awaitActionTransport()
                        when {
                            phaseOf(id) !== sending -> Unit.ok()
                            transport is Outcome.Failed -> transport
                            else -> reply(entry, answer)
                        }
                    }
                sending.result.complete(settleWrite(entry, sending, written))
            }
        // Also runs if the connection was cancelled before the job could start. Never replay an
        // uncertain write.
        write.invokeOnCompletion { cause ->
            if (cause != null) {
                val failure = Failure.Refused(RefusalReason.DispatchUncertain)
                state.update { list ->
                    list.mapEntry(id) {
                        if (it.phase === sending)
                            it.withPhase(AnswerPhase.NotAccepted(answer, failure))
                        else it
                    }
                }
                sending.result.complete(if (phaseOf(id) == null) Unit.ok() else failure.failed())
            }
        }
        return sending.result.await()
    }

    private suspend fun reply(entry: PendingRequest, answer: RequestAnswer): Outcome<Unit> =
        when (answer) {
            is RequestAnswer.Permission ->
                port.replyPermission(entry.sessionId, entry.id, answer.reply, answer.note)
            is RequestAnswer.Question ->
                port.answerQuestion(entry.sessionId, entry.id, answer.answers)
            RequestAnswer.Decline ->
                when (entry) {
                    is PendingRequest.Permission ->
                        port.replyPermission(
                            entry.sessionId,
                            entry.id,
                            PermissionReply.Reject,
                            null,
                        )
                    is PendingRequest.Question -> port.rejectQuestion(entry.sessionId, entry.id)
                }
        }

    private suspend fun settleWrite(
        entry: PendingRequest,
        sending: AnswerPhase.Sending,
        written: Outcome<Unit>,
    ): Outcome<Unit> {
        val id = entry.id
        // A replacement may reuse the wire id; the old decision owns only its captured phase.
        if (phaseOf(id) !== sending) return Unit.ok()
        if (written is Outcome.Ok) {
            state.update { list ->
                list.mapEntry(id) {
                    if (it.phase === sending) it.withPhase(AnswerPhase.Sent(sending.answer, now()))
                    else it
                }
            }
            return written
        }
        val failure = (written as Outcome.Failed).failure
        // The server timed the ask out (404 naming this request): the card leaves with no error
        // (B-CHAT-076).
        // Anything else is checked against the server's own list: an ask resolved meanwhile shows
        // no error (B-PROTO-048).
        val resolved =
            timedOut(failure, entry) || stillListed(entry) == false || phaseOf(id) == null
        if (phaseOf(id) !== sending) return Unit.ok()
        if (resolved) {
            withContext(lane) { if (phaseOf(id) === sending) remove(id) }
            return Unit.ok()
        }
        log.w { "a reply was not accepted (${failure::class.simpleName})" }
        state.update { list ->
            list.mapEntry(id) {
                if (it.phase === sending)
                    it.withPhase(AnswerPhase.NotAccepted(sending.answer, failure))
                else it
            }
        }
        return written
    }

    private fun timedOut(failure: Failure, entry: PendingRequest): Boolean {
        val requestTag =
            when (entry) {
                is PendingRequest.Permission -> "PermissionNotFoundError"
                is PendingRequest.Question -> "QuestionNotFoundError"
            }
        return failure is Failure.NotFound &&
            failure.tag?.raw == requestTag &&
            (failure.requestId == null || failure.requestId == entry.id)
    }

    /** Null when the list could not be read. */
    private suspend fun stillListed(entry: PendingRequest): Boolean? =
        when (entry) {
            is PendingRequest.Permission ->
                (port.pendingPermissions() as? Outcome.Ok)?.value?.any { it.id == entry.id }
            is PendingRequest.Question ->
                (port.pendingQuestions() as? Outcome.Ok)?.value?.any { it.id == entry.id }
        }

    private fun admissible(entry: PendingRequest, answer: RequestAnswer): Failure? =
        when (entry) {
            is PendingRequest.Permission ->
                when {
                    answer is RequestAnswer.Question -> Failure.Unexpected()
                    answer is RequestAnswer.Permission &&
                        answer.reply == PermissionReply.Always &&
                        !(port.capabilities.value.persistentPermissionGrants &&
                            entry.request.canAlwaysAllow) ->
                        Failure.Unavailable(FeatureId("persistentPermissionGrants"))
                    else -> null
                }
            is PendingRequest.Question ->
                when {
                    answer is RequestAnswer.Permission -> Failure.Unexpected()
                    answer is RequestAnswer.Question && !validQuestionAnswer(entry, answer) ->
                        Failure.Rejected(status = null, tag = WireTag("QUESTION_INVALID_ANSWER"))
                    else -> null
                }
        }

    /**
     * Validate wire-ready answers too: notifications and other callers do not pass through the
     * sheet.
     */
    private fun validQuestionAnswer(
        entry: PendingRequest.Question,
        answer: RequestAnswer.Question,
    ): Boolean {
        val prompts = entry.question.prompts
        if (answer.answers.size != prompts.size) return false
        return prompts.indices.all { index ->
            val prompt = prompts[index]
            val values = answer.answers[index]
            val offered = prompt.choices.map { it.label }
            (prompt.optional || values.isNotEmpty()) &&
                (prompt.multiple || values.size <= 1) &&
                values.none { it.isBlank() } &&
                values.distinct().size == values.size &&
                (prompt.custom || values.all { it in offered })
        }
    }

    // ---- structure (lane only)

    private fun upsert(request: PendingRequest) {
        settled.remove(request.id)
        val previous = state.value.firstOrNull { it.id == request.id }
        val changed = previous != null && !previous.sameContents(request)
        state.update { list ->
            if (list.any { it.id == request.id }) {
                list.mapEntry(request.id) { it.withData(request) }
            } else {
                (list + request.withShown(firstShown.value[request.id])).toImmutableList()
            }
        }
        if (request.id !in addedAt || changed) addedAt[request.id] = ++seq
    }

    private fun remove(id: RequestId) {
        settled.add(id)
        while (settled.size > SETTLED_KEPT) settled.remove(settled.first())
        addedAt.remove(id)
        state.update { list -> list.filterNot { it.id == id }.toImmutableList() }
    }

    private fun reconcile(
        listed: List<PendingRequest>,
        start: Long,
        ofKind: (PendingRequest) -> Boolean,
    ) {
        val ids = listed.mapTo(HashSet()) { it.id }
        state.value.filter(ofKind).forEach { entry ->
            val keep =
                entry.id in ids ||
                    entry.phase is AnswerPhase.Sending ||
                    (addedAt[entry.id] ?: 0L) > start
            if (!keep) remove(entry.id)
        }
        listed.forEach { request ->
            if (request.id !in settled && (addedAt[request.id] ?: 0L) <= start) upsert(request)
        }
    }

    private fun phaseOf(id: RequestId): AnswerPhase? =
        state.value.firstOrNull { it.id == id }?.phase

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun Map<RequestId, Long>.trimmed(): Map<RequestId, Long> =
        if (size <= SHOWN_KEPT) this
        else entries.drop(size - SHOWN_KEPT).associate { it.key to it.value }

    private companion object {
        /** Settled ids remembered so a stale refetch cannot bring them back. */
        const val SETTLED_KEPT = 256

        /** Dart keeps 64 first-seen times per connection. */
        const val SHOWN_KEPT = 64
    }
}

private inline fun ImmutableList<PendingRequest>.mapEntry(
    id: RequestId,
    transform: (PendingRequest) -> PendingRequest,
): ImmutableList<PendingRequest> =
    if (none { it.id == id }) this
    else map { if (it.id == id) transform(it) else it }.toImmutableList()

internal fun PendingRequest.withPhase(phase: AnswerPhase): PendingRequest =
    when (this) {
        is PendingRequest.Permission -> copy(phase = phase)
        is PendingRequest.Question -> copy(phase = phase)
    }

private fun PendingRequest.withShown(at: Long?): PendingRequest =
    when (this) {
        is PendingRequest.Permission -> copy(firstShownAtMs = at)
        is PendingRequest.Question -> copy(firstShownAtMs = at)
    }

/** An equivalent snapshot preserves the decision; replacement content requires a fresh decision. */
private fun PendingRequest.withData(fresh: PendingRequest): PendingRequest =
    if (sameContents(fresh)) this else fresh.withShown(firstShownAtMs)

private fun PendingRequest.sameContents(other: PendingRequest): Boolean =
    when {
        this is PendingRequest.Permission && other is PendingRequest.Permission ->
            request == other.request
        this is PendingRequest.Question && other is PendingRequest.Question ->
            question == other.question
        else -> false
    }

/**
 * Equal hydration snapshots preserve the raw object; settlement then re-addition creates a new
 * incarnation.
 */
private fun PendingRequest.sameIncarnation(other: PendingRequest): Boolean =
    when {
        this is PendingRequest.Permission && other is PendingRequest.Permission ->
            request === other.request
        this is PendingRequest.Question && other is PendingRequest.Question ->
            question === other.question
        else -> false
    }
