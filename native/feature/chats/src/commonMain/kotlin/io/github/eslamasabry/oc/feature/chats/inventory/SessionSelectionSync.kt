package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.SessionSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Port of the server-owned (OpenCode 2) half of lib/state/connection/models.dart: selectionForSession,
// displayModelForSession, _mutateSessionSelection, selectModelForSession and selectAgentForSession.

/** The connected server's selection writes. */
public interface SessionSelectionWriter {
    public suspend fun setSessionModel(id: SessionId, model: ModelRef, variant: String): Outcome<Unit>

    public suspend fun setSessionAgent(id: SessionId, agent: String): Outcome<Unit>
}

/** What a conversation will use next is not known yet (its record is not here). */
public val UnknownSelection: SessionSelection =
    SessionSelection(model = null, variant = "", agent = null, modelKnown = false, agentKnown = false)

/**
 * Per-conversation model and agent, owned by the server. Reads come from the [inventory]'s records only, so offline
 * the confirmed selection still shows and a missing record is UNKNOWN, never borrowed from a default. For display
 * only, the last confirmed model of each conversation is remembered under `oc.sessionDisplayModels.<profileId>`
 * (newest 200), so a cold start names it before the gateway exists; a confirmed server default forgets it.
 *
 * Writes need a connection ([writer] null otherwise), run one at a time per conversation, skip a choice the server
 * already has, and adopt the server's state by re-reading the record. A failed write keeps the confirmed selection,
 * records the failure in [errors] and can be retried.
 */
public class SessionSelectionSync(
    private val inventory: SessionInventory,
    private val storage: PreviewStorage,
    private val scope: CoroutineScope,
    private val profile: () -> ProfileId?,
    private val writer: () -> SessionSelectionWriter?,
    private val canWrite: () -> Boolean = { true },
) {
    private var memoryOwner: ProfileId? = null
    private var memory: LinkedHashMap<String, ModelRef>? = null
    private val memoryWrites = TurnLane()
    private val lanes = HashMap<SessionId, TurnLane>()
    private val mutableErrors = LinkedHashMap<SessionId, Failure>()

    public val errors: Map<SessionId, Failure> get() = mutableErrors

    public fun selectionFor(id: SessionId): SessionSelection = inventory.sessionsById[id]?.selection ?: UnknownSelection

    public fun modelFor(id: SessionId): ModelRef? = selectionFor(id).model

    public fun agentFor(id: SessionId): String = selectionFor(id).agent ?: ""

    public fun variantFor(id: SessionId): String = selectionFor(id).variant

    /** Display only: sending always reads [selectionFor]. */
    public fun displayModelFor(id: SessionId): ModelRef? {
        val selection = selectionFor(id)
        if (selection.modelKnown) {
            rememberDisplayModel(id, selection.model)
            return selection.model
        }
        return memory()[id.raw]
    }

    /** True while a write for [id] runs or waits. */
    public fun saving(id: SessionId): Boolean = lanes[id]?.busy == true

    public suspend fun selectModel(id: SessionId, model: ModelRef, variant: String = ""): Outcome<Unit> =
        mutate(id) { w ->
            val current = selectionFor(id)
            if (current.modelKnown && current.model == model && current.variant == variant) null
            else w.setSessionModel(id, model, variant)
        }

    public suspend fun selectAgent(id: SessionId, agent: String): Outcome<Unit> {
        if (agent.isEmpty()) return Outcome.Ok(Unit)
        return mutate(id) { w ->
            val current = selectionFor(id)
            if (current.agentKnown && current.agent == agent) null else w.setSessionAgent(id, agent)
        }
    }

    /** [write] answers null when nothing needs writing. */
    private suspend fun mutate(id: SessionId, write: suspend (SessionSelectionWriter) -> Outcome<Unit>?): Outcome<Unit> {
        val w = writer() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val source = inventory.currentSource
        return lanes.getOrPut(id) { TurnLane() }.run {
            if (writer() !== w || inventory.currentSource !== source) {
                return@run Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            }
            mutableErrors.remove(id)
            when (val result = write(w)) {
                null -> Outcome.Ok(Unit)
                is Outcome.Ok -> {
                    inventory.invalidate(id)
                    inventory.ensureSession(id)
                    inventory.detailsErrors[id]?.let { Outcome.Failed(it) } ?: result
                }
                is Outcome.Failed -> {
                    // A timeout may have applied the change: reconcile before a retry.
                    inventory.invalidate(id)
                    inventory.ensureSession(id)
                    if (inventory.currentSource === source) mutableErrors[id] = result.failure
                    result
                }
            }
        }
    }

    private fun memory(): LinkedHashMap<String, ModelRef> {
        val owner = profile()
        memory?.let { if (memoryOwner == owner) return it }
        memoryOwner = owner
        val read = LinkedHashMap<String, ModelRef>()
        if (owner != null) {
            val decoded = storage.read(keyFor(owner))?.let(DartJson::decodeOrNull) as? Map<*, *>
            decoded?.entries?.take(MAX_MODELS)?.forEach { (k, v) ->
                val m = v as? Map<*, *>
                val provider = m?.get("providerID") as? String
                val model = m?.get("modelID") as? String
                if (k is String && provider != null && model != null) read[k] = ModelRef(provider, model)
            }
        }
        memory = read
        return read
    }

    private fun rememberDisplayModel(id: SessionId, model: ModelRef?) {
        if (!canWrite()) return
        val owner = profile() ?: return
        val models = memory()
        if (models[id.raw] == model) return
        models.remove(id.raw)
        if (model != null) models[id.raw] = model
        while (models.size > MAX_MODELS) models.remove(models.keys.first())
        val encoded = DartJson.encode(
            models.entries.associate { (k, v) -> k to linkedMapOf("providerID" to v.providerId, "modelID" to v.modelId) },
        )
        scope.launch { memoryWrites.run { if (canWrite()) storage.write(keyFor(owner), encoded) } }
    }

    public companion object {
        public const val MAX_MODELS: Int = 200

        public fun keyFor(profile: ProfileId): String = "oc.sessionDisplayModels.${profile.raw}"
    }
}
