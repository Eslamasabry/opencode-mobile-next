package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.engine.analytics.NoReporting
import io.github.eslamasabry.oc.engine.analytics.Reporting
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.delivery.live.InboxProjection
import io.github.eslamasabry.oc.engine.delivery.port.AutomationPolicy
import io.github.eslamasabry.oc.engine.delivery.port.ConversationFacts
import io.github.eslamasabry.oc.engine.delivery.port.DeliveryConnection
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
import io.github.eslamasabry.oc.engine.delivery.port.ObservedPromptReceipts
import io.github.eslamasabry.oc.engine.delivery.port.SendLink
import io.github.eslamasabry.oc.engine.delivery.port.StopMemory
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The storage owner supplies the adopted, gated store. Absence refuses admission; never substitute
 * volatile prefs.
 */
public class DeliveryStorage(
    public val store: KeyValueStore,
    public val lanes: WriterLanes,
    public val profiles: ProfileDirectory,
) {
    /**
     * One process owner survives connection replacement and keeps closed profile admission closed.
     */
    public val journals: PendingCommandJournals = PendingCommandJournals(store, lanes, profiles)
}

@ContributesTo(AppScope::class)
public interface DeliveryStorageBindings {
    @Multibinds(allowEmpty = true) public val deliveryStores: Set<DeliveryStorage>
    @Multibinds(allowEmpty = true) public val interactiveSendChecks: Set<InteractiveSendCheck>
}

/**
 * Platform admission before interactive work, including explicit queue retries. No server mutation.
 */
public fun interface InteractiveSendCheck {
    public suspend fun check(profile: ServerProfile): Outcome<Unit>
}

/**
 * B-146: the connection's delivery for app-level owners that are not a chat screen. The binding is created on first
 * read, and only a created delivery sends that server's queued prompts when its stream connects, so whoever opens a
 * connection to send what waits reads it ([ConnectionDelivery.sendWaiting]).
 */
@ContributesTo(ConnectionScope::class)
public interface DeliveryAccessors {
    public val connectionDelivery: ConnectionDelivery
}

/** Connection-owned pipeline, shared by every entry and kept after an entry is destroyed. */
@SingleIn(ConnectionScope::class)
@Inject
public class ConnectionDelivery(
    private val gateway: ServerGateway,
    profile: ServerProfile,
    private val scope: ConnectionCoroutineScope,
    clock: Clock,
    stores: Set<DeliveryStorage>,
    dispatchers: AppDispatchers,
    tracers: Set<PromptTracing> = emptySet(),
    private val interactiveSendChecks: Set<InteractiveSendCheck> = emptySet(),
    private val reporting: Reporting = NoReporting,
    recoveryVisibility: Set<RunRecoveryVisibility> = emptySet(),
) {
    private val tracing: PromptTracing? = tracers.firstOrNull()?.forConnection(profile.id.raw)
    private val reconcileLane = dispatchers.serial("delivery-reconcile")
    private val runRecovery = (gateway as? io.github.eslamasabry.oc.core.domain.RunRecoveryGateway)?.let { recovery ->
        recoveryVisibility.singleOrNull()?.let { visibility ->
            RunReconciler(recovery, gateway.events, visibility.state(), { clock.now().toEpochMilliseconds() }, scope, reconcileLane,
                checked = { id, busy -> tracing?.runChecked(id.raw, busy) })
        }
    }
    private val running = MutableStateFlow<Set<ConversationRef>>(emptySet())
    private val stoppedMemory = stores.singleOrNull()?.let { StoppedTurnMemory(it.store, it.lanes.app, profile.id, scope) }
    private val stopped = stoppedMemory?.refs ?: MutableStateFlow<Set<ConversationRef>>(emptySet())
    public val stoppedByPerson: kotlinx.coroutines.flow.StateFlow<Set<ConversationRef>> = stopped

    /**
     * A Stop this app asked and the server has not shown the end of yet (its busy statuses are the same
     * run, not a newer one).
     */
    private val requestedStop = MutableStateFlow<Set<ConversationRef>>(emptySet())

    /** The person's Stop of the whole server cut these runs: each is "stopped", like a Stop pressed on its turn. */
    public suspend fun markCutByStop(refs: Collection<ConversationRef>) {
        for (ref in refs) stoppedMemory?.add(ref) ?: stopped.update { it + ref }
        stoppedMemory?.settled()
    }

    private suspend fun admission(profile: ServerProfile): Outcome<Unit> {
        for (check in interactiveSendChecks) {
            val result = check.check(profile)
            if (result is Outcome.Failed) return result
        }
        return Outcome.Ok(Unit)
    }

    private val observedReceipts = ObservedPromptReceipts()
    private val link =
        GatewaySendLink(profile.id, profile.origin.toString(), gateway, tracing, observedReceipts) {
            admission(profile)
        }
    private val connection =
        object : DeliveryConnection {
            override val link = MutableStateFlow<SendLink?>(this@ConnectionDelivery.link)
            override val activeProfile = MutableStateFlow<ProfileId?>(profile.id)
        }

    private val storage = stores.singleOrNull()
    private val journals = storage?.journals
    public val queue: OfflineQueue? = storage?.let {
        OfflineQueue(
            OfflineQueueStore(it.store, it.lanes.app),
            journals!!,
            connection,
            AutomationPolicy { target ->
                target == profile.id && queueAutomationAllowed(it.store, profile.id)
            },
            clock,
            scope,
            dispatchers,
        )
    }
    public val engine: PromptDelivery? = queue?.let {
        PromptDelivery(
            connection,
            it,
            journals!!,
            object : ConversationFacts {
                override fun running(ref: ConversationRef): Boolean = ref in running.value

                override fun undoUnresolved(ref: ConversationRef): Boolean = false
            },
            object : StopMemory {
                override fun noteStopFromThisApp(ref: ConversationRef) {
                    tracing?.stopped(ref.sessionId.raw)
                    requestedStop.update { it + ref }
                }

                override fun markStoppedByPerson(ref: ConversationRef) {
                    stoppedMemory?.add(ref) ?: stopped.update { it + ref }
                }

                override fun stoppedByPerson(ref: ConversationRef): Boolean = ref in stopped.value

                override fun clearStoppedByPerson(ref: ConversationRef) {
                    stoppedMemory?.remove(ref) ?: stopped.update { it - ref }
                    requestedStop.update { it - ref }
                }

                override fun stopAnswered(ref: ConversationRef, endedSomething: Boolean) {
                    if (!endedSomething) requestedStop.update { it - ref }
                }
            },
            clock,
            scope,
            reporting,
        )
    }

    private fun feed(trace: PromptTracing, event: GatewayEvent) {
        when (event) {
            is GatewayEvent.MessageUpserted ->
                trace.message(
                    event.message.sessionId,
                    event.message.id,
                    event.message.role == Role.Assistant,
                )
            is GatewayEvent.PartUpserted -> {
                trace.part(event.part.sessionId, event.part.messageId, event.part.rawType)
                if (event.part.type == PartType.Tool) trace.tool(event.part.sessionId, event.part.id)
                if (event.part.type == PartType.Text && event.part.isRenderable)
                    trace.text(event.part.sessionId, event.part.messageId, streamed = false)
            }
            is GatewayEvent.PartDelta -> {
                trace.delta(event.sessionId.raw, event.field)
                if (event.field == DeltaField.Text && event.delta.isNotBlank())
                    trace.text(event.sessionId.raw, event.messageId.raw, streamed = true)
            }
            is GatewayEvent.RunStarted -> trace.busy(event.sessionId.raw)
            is GatewayEvent.RunRetrying -> trace.retrying(event.sessionId.raw)
            is GatewayEvent.RunEnded -> {
                val history = event.reconciledHistory
                if (history != null) trace.recovered(event.sessionId.raw)
                history?.drop(history.indexOfLast { it.info.role == Role.User } + 1)?.forEach { message ->
                    trace.message(message.info.sessionId, message.info.id, message.info.role == Role.Assistant)
                    if (message.info.role == Role.Assistant) message.parts.forEach { part ->
                        if (part.type == PartType.Text && part.isRenderable) trace.text(part.sessionId, part.messageId, streamed = false)
                    }
                }
                when (val outcome = event.outcome) {
                    is RunOutcome.Failed -> trace.error(event.sessionId.raw, outcome.kind?.name, outcome.tag?.raw, outcome.details?.text)
                    is RunOutcome.Interrupted ->
                        if (outcome.reason == InterruptReason.User) trace.error(event.sessionId.raw, "Aborted", null, null)
                        else trace.idle(event.sessionId.raw)
                    else -> trace.idle(event.sessionId.raw)
                }
            }
            else -> Unit
        }
    }

    /**
     * Server-admitted sends, separate from the device's offline queue. The connection owns their
     * live projection.
     */
    public val inbox: InboxProjection = InboxProjection(gateway)

    init {
        tracing?.let { trace ->
            scope.launch { gateway.events.project.collect { feed(trace, it.event) } }
            // The stream going away and coming back, for a turn that was running through it (analytics only).
            scope.launch {
                var connected = gateway.events.status.value == StreamStatus.Connected
                gateway.events.status.collect { status ->
                    val now = status == StreamStatus.Connected
                    if (now != connected) {
                        connected = now
                        trace.stream(profile.id.raw, profile.managedOnPhone, now, streamDropReason(status))
                    }
                }
            }
        }
        val lane = reconcileLane
        // A message the server already shows for this send scope proves the prompt arrived, so a
        // retried send with the same id is not posted twice.
        scope.launch {
            gateway.events.all.collect { envelope ->
                val event = envelope.event
                val current = connection.link.value
                val location = envelope.location
                if (
                    event is GatewayEvent.MessageUpserted &&
                        current != null &&
                        (location?.directory == null ||
                            location.directory == current.scope.directory) &&
                        (location?.workspace == null ||
                            location.workspace == current.scope.workspace)
                )
                    observedReceipts.observe(current.scope, event)
            }
        }
        // The live truth with no chat screen open: the streamed busy word itself clears the stored
        // stop (Home and the Edge light never reach running()), and the run's end retires the Stop
        // wind-down guard, so only the wind-down of this app's own Stop is excused.
        scope.launch {
            gateway.events.all.collect { envelope ->
                when (val event = envelope.event) {
                    is GatewayEvent.RunStarted -> serverBusy(ConversationRef(profile.id, event.sessionId))
                    is GatewayEvent.RunRetrying -> serverBusy(ConversationRef(profile.id, event.sessionId))
                    is GatewayEvent.RunEnded ->
                        requestedStop.update { it - ConversationRef(profile.id, event.sessionId) }
                    else -> Unit
                }
            }
        }
        scope.launch(lane) {
            gateway.events.project.collect { envelope ->
                val event = envelope.event
                if (event is GatewayEvent.InboxChanged && gateway.capabilities.value.inbox)
                    inbox.onEvent(event)
            }
        }
        scope.launch(lane) {
            var first = true
            var connects = gateway.events.connects.value
            var status = gateway.events.status.value
            combine(gateway.events.connects, gateway.events.status) { n, s -> n to s }
                .collect { (n, s) ->
                    val changed = first || n != connects || s != status
                    first = false
                    connects = n
                    status = s
                    if (changed) {
                        connection.link.value =
                            GatewaySendLink(
                                profile.id,
                                profile.origin.toString(),
                                gateway,
                                tracing,
                                observedReceipts,
                            ) {
                                admission(profile)
                            }
                        if (s == StreamStatus.Connected) {
                            // A reconnect reconciles admitted sends and receipts; marked/uncertain
                            // sends are never replayed.
                            if (gateway.capabilities.value.inbox)
                                scope.launch(lane) { inbox.refreshKnown() }
                            scope.launch(lane) { queue?.flush() }
                        }
                    }
                }
        }
    }

    /**
     * B-146: sends this server's queued prompts now when its stream is connected; otherwise the next connect does.
     * Safe to call often: a flush already running takes it as one more pass, and an entry whose dispatch marker is
     * set is never sent again, so a prompt goes out once.
     */
    public fun sendWaiting() {
        val queue = queue ?: return
        if (gateway.events.status.value != StreamStatus.Connected) return
        scope.launch(reconcileLane) { queue.flush() }
    }

    /**
     * The live truth wins: a busy status the server reports now is a run the person's stored stop
     * did not cut, unless this app's own Stop is still winding that same run down (requestedStop).
     */
    private fun serverBusy(ref: ConversationRef) {
        if (ref !in requestedStop.value) stoppedMemory?.remove(ref) ?: stopped.update { it - ref }
    }

    public fun running(ref: ConversationRef, busy: Boolean) {
        runRecovery?.running(ref.sessionId, busy)
        running.update { if (busy) it + ref else it - ref }
        if (busy) {
            serverBusy(ref)
        } else {
            engine?.turnEnded(ref)
        }
    }

    /**
     * A picker change under the conversation's send-selection lock (Codex composer #3); no engine:
     * runs as is.
     */
    public suspend fun <T> withSelection(ref: ConversationRef, block: suspend () -> T): T =
        engine?.withSelection(ref, block) ?: block()

    /**
     * Only the durable pipeline may accept; the caller keeps its draft on any refusal.
     * [attachments] and [model] ride the same request: the files are snapshotted immutably and the
     * choice is applied before the prompt is admitted to the wire.
     */
    public suspend fun send(
        ref: ConversationRef,
        text: String,
        attachments: ImmutableList<PromptAttachment> = persistentListOf(),
        model: ModelSelection? = null,
        /**
         * Sub-agent `@name` mentions in [text] (Codex composer #6; OC2 sends them as the prompt's
         * agents).
         */
        mentions: ImmutableList<PromptAgentMention> = persistentListOf(),
        /** Ephemeral voice turns never learn phrases or restore themselves into a later draft. */
        insteadOfDraft: Boolean = false,
    ): Outcome<LocalSendId> {
        val engine = engine ?: return Outcome.Failed(Failure.StorageRefused())
        val currentLink = connection.link.value ?: link
        // Offline admission is a local durable write. Flush checks staged undo again before any
        // dispatch.
        if (currentLink.connected) {
            when (val undo = currentLink.revertPending(ref.sessionId)) {
                is Outcome.Failed -> return undo
                is Outcome.Ok ->
                    if (undo.value)
                        return Outcome.Failed(Failure.Refused(RefusalReason.UndoUnresolved))
            }
        }
        val request =
            SendRequest(ref, text, attachments = attachments, model = model, mentions = mentions)
        return if (insteadOfDraft) engine.sendInsteadOfDraft(request) else engine.send(request)
    }

    public companion object {
        /** Saved session ids whose newest turn the person stopped on [profile] (readable while offline). */
        public fun readStoppedByPerson(store: KeyValueStore, profile: ProfileId): Set<SessionId> =
            StoppedTurnMemory.readIds(store, profile)
    }
}

/**
 * The existing Flutter consent record. A missing record uses its default; an unreadable record
 * grants nothing.
 */
private fun queueAutomationAllowed(store: KeyValueStore, profile: ProfileId): Boolean {
    val value = store.get("oc.automation.${profile.raw}") ?: return true
    val text = (value as? PrefValue.Str)?.value ?: return false
    val root =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        } ?: return false
    if ((root["version"] as? JsonPrimitive)?.intOrNull != 1) return false
    if (
        (root["supervision"] as? JsonPrimitive)?.contentOrNull !in
            setOf("high", "balanced", "autonomous")
    )
        return false
    val behaviors = root["behaviors"] as? JsonObject ?: return false
    return (behaviors["reconcileQueuedSends"] as? JsonPrimitive)?.booleanOrNull == true
}
