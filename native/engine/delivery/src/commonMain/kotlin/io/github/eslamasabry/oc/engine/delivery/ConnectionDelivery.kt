package io.github.eslamasabry.oc.engine.delivery

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
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.delivery.live.InboxProjection
import io.github.eslamasabry.oc.engine.delivery.port.AutomationPolicy
import io.github.eslamasabry.oc.engine.delivery.port.ConversationFacts
import io.github.eslamasabry.oc.engine.delivery.port.DeliveryConnection
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
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

/** Connection-owned pipeline, shared by every entry and kept after an entry is destroyed. */
@SingleIn(ConnectionScope::class)
@Inject
public class ConnectionDelivery(
    gateway: ServerGateway,
    profile: ServerProfile,
    private val scope: ConnectionCoroutineScope,
    clock: Clock,
    stores: Set<DeliveryStorage>,
    dispatchers: AppDispatchers,
    tracers: Set<PromptTracing> = emptySet(),
    private val interactiveSendChecks: Set<InteractiveSendCheck> = emptySet(),
) {
    private val tracing: PromptTracing? = tracers.firstOrNull()
    private val running = MutableStateFlow<Set<ConversationRef>>(emptySet())
    private val stopped = MutableStateFlow<Set<ConversationRef>>(emptySet())
    public val stoppedByPerson: kotlinx.coroutines.flow.StateFlow<Set<ConversationRef>> = stopped
    private val requestedStop = MutableStateFlow<Set<ConversationRef>>(emptySet())

    private suspend fun admission(profile: ServerProfile): Outcome<Unit> {
        for (check in interactiveSendChecks) {
            val result = check.check(profile)
            if (result is Outcome.Failed) return result
        }
        return Outcome.Ok(Unit)
    }

    private val link =
        GatewaySendLink(profile.id, profile.origin.toString(), gateway, tracing) { admission(profile) }
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
                    requestedStop.update { it + ref }
                }

                override fun markStoppedByPerson(ref: ConversationRef) {
                    stopped.update { it + ref }
                }

                override fun clearStoppedByPerson(ref: ConversationRef) {
                    stopped.update { it - ref }
                    requestedStop.update { it - ref }
                }
            },
            clock,
            scope,
        )
    }

    private fun feed(trace: PromptTracing, event: GatewayEvent) {
        when (event) {
            is GatewayEvent.MessageUpserted ->
                trace.message(event.message.sessionId, event.message.id, event.message.role == Role.Assistant)
            is GatewayEvent.PartUpserted -> trace.part(event.part.sessionId, event.part.messageId, event.part.rawType)
            is GatewayEvent.PartDelta -> trace.delta(event.sessionId.raw)
            is GatewayEvent.RunStarted -> trace.busy(event.sessionId.raw)
            is GatewayEvent.RunEnded ->
                if (event.outcome is RunOutcome.Failed) trace.error(event.sessionId.raw) else trace.idle(event.sessionId.raw)
            else -> Unit
        }
    }

    /**
     * Server-admitted sends, separate from the device's offline queue. The connection owns their
     * live projection.
     */
    public val inbox: InboxProjection = InboxProjection(gateway)

    init {
        tracing?.let { trace -> scope.launch { gateway.events.project.collect { feed(trace, it.event) } } }
        val lane = dispatchers.serial("delivery-reconcile")
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
                            GatewaySendLink(profile.id, profile.origin.toString(), gateway, tracing) {
                                admission(profile)
                            }
                        if (s == StreamStatus.Connected) {
                            // A reconnect reconciles admitted sends and receipts; marked/uncertain
                            // sends are never replayed.
                            if (gateway.capabilities.value.inbox) inbox.refreshKnown()
                            queue?.flush()
                        }
                    }
                }
        }
    }

    public fun running(ref: ConversationRef, busy: Boolean) {
        running.update { if (busy) it + ref else it - ref }
        if (!busy) engine?.turnEnded(ref)
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
        when (val undo = (connection.link.value ?: link).revertPending(ref.sessionId)) {
            is Outcome.Failed -> return undo
            is Outcome.Ok ->
                if (undo.value) return Outcome.Failed(Failure.Refused(RefusalReason.UndoUnresolved))
        }
        val request =
            SendRequest(ref, text, attachments = attachments, model = model, mentions = mentions)
        return if (insteadOfDraft) engine.sendInsteadOfDraft(request) else engine.send(request)
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
