package io.github.eslamasabry.oc.engine.agents.models

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A server-owned chat's model, variant and agent (Flutter `SessionSelection`). A known-null [model] means the chat
 * inherits the server default; an unknown one ([modelKnown] false) means its record has not been read yet (B-PROTO-102).
 */
public data class SessionSelection(
    public val model: ModelRef? = null,
    public val variant: String = "",
    public val agent: String? = null,
    public val modelKnown: Boolean = true,
    public val agentKnown: Boolean = true,
) {
    /** The agent to show and send with; `""` when inherited or unknown. */
    public val agentOrEmpty: String get() = agent ?: ""

    /** Replaces model and variant; the agent half stays as it was, known or not. */
    public fun withModel(model: ModelRef?, variant: String): SessionSelection =
        SessionSelection(model, variant, agent, modelKnown = true, agentKnown = agentKnown)

    /** Replaces the agent; the model half stays as it was, known or not. */
    public fun withAgent(agent: String?): SessionSelection =
        SessionSelection(model, variant, agent, modelKnown = modelKnown, agentKnown = true)

    public companion object {
        /** Not read yet: neither half is known. */
        public val Unknown: SessionSelection = SessionSelection(modelKnown = false, agentKnown = false)

        /**
         * The selection of an OpenCode 2 session record (`mapApi2Session`): `model {providerID, id, variant}` and
         * `agent`. The server's `"default"` variant is the app's `""`. A record without a model inherits (known null).
         */
        public fun fromWireSession(session: JsonObject): SessionSelection {
            val ref = WireModelRef.fromJson(session["model"])
            return SessionSelection(
                model = ref?.toModelRef(),
                variant = when (val v = ref?.variant) {
                    null, "", "default" -> ""
                    else -> v
                },
                agent = session.string("agent"),
            )
        }
    }
}

/**
 * A live selection event (`session.model.selected` / `session.agent.selected`) after the OpenCode 2 adapter: a model
 * event without a valid model and an agent event with an empty agent are dropped, so malformed data never clears a
 * selection.
 */
public sealed interface SelectionEvent {
    public val sessionId: String

    public data class ModelSelected(override val sessionId: String, public val model: ModelRef, public val variant: String) :
        SelectionEvent

    public data class AgentSelected(override val sessionId: String, public val agent: String) : SelectionEvent

    public companion object {
        /** Null for every other event type and for malformed selection events. */
        public fun parse(type: String, data: JsonElement?): SelectionEvent? {
            val obj = data as? JsonObject ?: return null
            val sessionId = obj.string("sessionID") ?: return null
            if (sessionId.isEmpty()) return null
            return when (type) {
                "session.model.selected" -> WireModelRef.fromJson(obj["model"])?.let {
                    ModelSelected(sessionId, it.toModelRef().normalized, it.variant ?: "")
                }
                "session.agent.selected" -> obj.string("agent")?.takeIf { it.isNotEmpty() }?.let {
                    AgentSelected(sessionId, it)
                }
                else -> null
            }
        }
    }
}

/**
 * The per-session selection projection of server-owned chats. Events patch only their own session and never touch
 * the model shortcuts (a remote choice is not the person's recent choice). [Applied.needsRefresh] asks for a one-off
 * read when an event leaves a half unknown (an unhydrated session).
 */
public data class SessionSelections(
    public val bySession: ImmutableMap<String, SessionSelection> = persistentMapOf(),
    public val deleted: ImmutableSet<String> = persistentSetOf(),
) {
    public data class Applied(public val selections: SessionSelections, public val needsRefresh: Boolean)

    /** [SessionSelection.Unknown] for a session not read yet. */
    public fun selectionFor(sessionId: String): SessionSelection = bySession[sessionId] ?: SessionSelection.Unknown

    public fun modelFor(sessionId: String): ModelRef? = selectionFor(sessionId).model

    public fun variantFor(sessionId: String): String = selectionFor(sessionId).variant

    public fun agentFor(sessionId: String): String = selectionFor(sessionId).agentOrEmpty

    /** A full session read replaces what events had patched. */
    public fun hydrate(sessionId: String, selection: SessionSelection): SessionSelections =
        if (sessionId in deleted) this else copy(bySession = (bySession + (sessionId to selection)).toImmutableMap())

    public fun delete(sessionId: String): SessionSelections =
        SessionSelections((bySession - sessionId).toImmutableMap(), (deleted + sessionId).toImmutableSet())

    public fun apply(event: SelectionEvent): Applied {
        if (event.sessionId in deleted) return Applied(this, needsRefresh = false)
        val old = selectionFor(event.sessionId)
        val next = when (event) {
            is SelectionEvent.ModelSelected -> old.withModel(event.model, event.variant)
            is SelectionEvent.AgentSelected -> old.withAgent(event.agent)
        }
        return Applied(
            copy(bySession = (bySession + (event.sessionId to next)).toImmutableMap()),
            needsRefresh = !next.modelKnown || !next.agentKnown,
        )
    }
}
