package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.delivery.port.AutomationPolicy
import io.github.eslamasabry.oc.engine.delivery.port.DeliveryConnection
import io.github.eslamasabry.oc.engine.delivery.port.SendLink
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.time.Instant

internal val P1 = ProfileId("1735000000000001")
internal val P2 = ProfileId("1735000000000002")
internal val S1 = SessionId("ses_1")
internal val S2 = SessionId("ses_2")
internal const val NOW_MS = 1_767_225_600_000L

/** A preferences file that can refuse (or throw on) chosen commits, and remembers what a restart would load. */
internal class Disk(initial: Map<String, RawPref> = emptyMap()) : InMemoryPrefsBackend(initial) {
    /** Return true to refuse this commit (the platform answered false). */
    var refuse: (Map<String, RawPref?>) -> Boolean = { false }
    var commits = 0

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        commits++
        if (refuse(changes)) return false
        return super.commit(changes)
    }

    fun refuseQueueWrites(times: Int = Int.MAX_VALUE) {
        var left = times
        refuse = { changes -> changes.keys.any { it.endsWith("oc.offlineQueue") } && left-- > 0 }
    }

    fun allowAll() {
        refuse = { false }
    }

    fun queueBlob(): String? = (persisted()[FlutterPrefsCodec.physicalKey(OfflineQueueStore.KEY)] as? RawPref.Str)?.value

    companion object {
        fun withQueue(raw: String) = Disk(mapOf(FlutterPrefsCodec.physicalKey(OfflineQueueStore.KEY) to RawPref.Str(raw)))
    }
}

@OptIn(io.github.eslamasabry.oc.core.data.migration.MigrationOnly::class)
internal fun kv(disk: Disk, scope: TestScope) =
    PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(scope.testScheduler))

/** A scriptable server link. [delivered] is what the server holds: the count that must never exceed one per prompt. */
internal class FakeLink(
    override var scope: DispatchScope = DispatchScope(P1, "http://127.0.0.1:4097", null, null),
    override var capabilities: ServerCapabilities = ServerCapabilities(),
    override var connected: Boolean = true,
    override var correlated: Boolean = false,
) : SendLink {
    data class Sent(val session: SessionId, val prompt: OutgoingPrompt)

    val delivered = mutableListOf<Sent>()
    val attempts = mutableListOf<Sent>()
    val stops = mutableListOf<SessionId>()
    val lookups = mutableListOf<String>()
    val selections = mutableListOf<Pair<ModelSelection?, String?>>()
    var transport: suspend () -> Outcome<Unit> = { Unit.ok() }
    var revert: suspend (SessionId) -> Outcome<Boolean> = { false.ok() }

    /** What the server and the network do with one prompt. Default: delivered and confirmed. */
    var answer: suspend (Sent) -> Answer = { Answer.Confirmed }
    var midTurn: MidTurnPrompt? = null
    override var hasMidTurnFacet: Boolean = false
    private var nextId = 0

    sealed interface Answer {
        /** Delivered, receipt came back. */
        data object Confirmed : Answer

        /** Delivered, the response was lost on the way back. */
        data object LostResponse : Answer

        /** Delivered, then the connection broke with this failure (the client cannot tell). */
        data class DeliveredThenFailed(val failure: Failure) : Answer

        /** Refused by the server before admission (nothing held), with this failure. */
        data class Refused(val failure: Failure) : Answer
    }

    override suspend fun awaitActionTransport(): Outcome<Unit> = transport()

    override suspend fun revertPending(session: SessionId): Outcome<Boolean> = revert(session)

    var onSelection: suspend () -> Unit = {}

    override suspend fun applySelection(session: SessionId, model: ModelSelection?, agent: String?): Outcome<Unit> {
        selections += model to agent
        onSelection()
        return Unit.ok()
    }

    override fun newClientMessageId(): MessageId? = if (correlated) MessageId("msg_client_${nextId++}") else null

    override suspend fun prompt(session: SessionId, prompt: OutgoingPrompt, beforeSend: () -> Boolean): Outcome<PromptAdmission> {
        if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val sent = Sent(session, prompt)
        attempts += sent
        return when (val a = answer(sent)) {
            Answer.Confirmed -> {
                delivered += sent
                PromptAdmission.Confirmed(prompt.clientMessageId?.raw).ok()
            }
            Answer.LostResponse -> {
                delivered += sent
                PromptAdmission.Uncertain.ok()
            }
            is Answer.DeliveredThenFailed -> {
                delivered += sent
                a.failure.failed()
            }
            is Answer.Refused -> a.failure.failed()
        }
    }

    override suspend fun lookupAdmission(session: SessionId, messageId: MessageId): Outcome<Boolean> {
        lookups += messageId.raw
        return delivered.any { it.session == session && it.prompt.clientMessageId == messageId }.ok()
    }

    var stopAnswer: suspend () -> Outcome<StopResult> = { StopResult.Stopped.ok() }
    var stopLog: MutableList<String>? = null

    override suspend fun stop(session: SessionId): Outcome<StopResult> {
        stops += session
        stopLog?.add("link.stop")
        return stopAnswer()
    }

    override fun midTurnPrompt(session: SessionId): MidTurnPrompt? = midTurn

    val commands = mutableListOf<String>()

    override suspend fun slashCommand(session: SessionId, command: String, args: String): Outcome<Unit> {
        commands += "/$command $args"
        return Unit.ok()
    }

    override suspend fun shell(session: SessionId, command: String): Outcome<Unit> {
        commands += "!$command"
        return Unit.ok()
    }

    fun deliveredTexts(): List<String> = delivered.map { it.prompt.text }
}

internal class FakeConnection(link: SendLink?, profile: ProfileId? = P1) : DeliveryConnection {
    override val link = MutableStateFlow(link)
    override val activeProfile = MutableStateFlow(profile)
}

internal class Policy(var allows: Boolean = true) : AutomationPolicy {
    override fun allowsQueuedSends(profile: ProfileId): Boolean = allows
}

/** One "process": the stores and engines over [disk]. A restart is a new [World] over the same [disk] and server. */
internal class World(
    val disk: Disk,
    val test: TestScope,
    val link: FakeLink = FakeLink(),
    val clock: TestClock = TestClock(Instant.fromEpochMilliseconds(NOW_MS)),
    val policy: Policy = Policy(),
    profiles: Set<ProfileId> = setOf(P1, P2),
    engineScope: CoroutineScope = test.backgroundScope,
) {
    val knownProfiles = profiles.toMutableSet()
    val kv = kv(disk, test)
    val lanes = DefaultWriterLanes()
    val store = OfflineQueueStore(kv, lanes.app)
    val journals = PendingCommandJournals(kv, lanes, ProfileDirectory { it in knownProfiles })
    val connection = FakeConnection(link)
    val queue = OfflineQueue(store, journals, connection, policy, clock, engineScope, TestDispatchers(StandardTestDispatcher(test.testScheduler)))

    fun restart(): World = World(disk, test, link, clock, policy, knownProfiles, test.backgroundScope)
}

internal fun prompt(
    id: String,
    text: String = "prompt $id",
    profile: ProfileId = P1,
    session: SessionId = S1,
    createdAt: Long = NOW_MS - 1_000,
    dispatchedAt: Long? = null,
    attachmentUrls: List<String> = emptyList(),
): QueuedPrompt =
    QueuedPrompt(
        id = id,
        profileId = profile,
        sessionId = session,
        text = text,
        attachments = attachmentUrls.mapIndexed { i, url ->
            io.github.eslamasabry.oc.core.model.PromptAttachment("text/plain", "f$i.txt", url)
        },
        createdAt = createdAt,
        dispatchedAt = dispatchedAt,
    )

internal class Facts : io.github.eslamasabry.oc.engine.delivery.port.ConversationFacts {
    val running = mutableSetOf<io.github.eslamasabry.oc.core.common.ids.ConversationRef>()
    val undo = mutableSetOf<io.github.eslamasabry.oc.core.common.ids.ConversationRef>()

    override fun running(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef): Boolean = ref in running

    override fun undoUnresolved(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef): Boolean = ref in undo
}

/** Records the stop memory calls in order, interleaved with the link's stops by the caller. */
internal class Stops(private val log: MutableList<String> = mutableListOf()) : io.github.eslamasabry.oc.engine.delivery.port.StopMemory {
    val events: List<String> get() = log

    override fun noteStopFromThisApp(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef) {
        log += "note"
    }

    override fun markStoppedByPerson(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef) {
        log += "mark"
    }

    override fun clearStoppedByPerson(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef) {
        log += "clear"
    }
}

internal val REF = io.github.eslamasabry.oc.core.common.ids.ConversationRef(P1, S1)

internal fun World.delivery(
    facts: Facts = Facts(),
    stops: Stops = Stops(),
    scope: CoroutineScope = test.backgroundScope,
) = PromptDelivery(connection, queue, journals, facts, stops, clock, scope)

internal fun req(text: String, typed: String = text) = SendRequest(REF, text, typedText = typed)
