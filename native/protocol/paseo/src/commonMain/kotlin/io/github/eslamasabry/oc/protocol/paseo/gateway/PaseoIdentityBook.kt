package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Who a conversation of one Paseo server is, kept for every gateway the app opens to that server, not only the one
 * that started it.
 *
 * A new conversation is local until its first message (a draft, under an id the app made), and once that message
 * created the daemon's agent the app keeps calling it by its own id while the daemon knows only its agent id. Both
 * facts used to live in one gateway's memory: a gateway closed (a reconnect, the helper's connection replaced) or moved
 * to another folder forgot them, and the conversation page, still holding the app's id, asked the daemon for an agent
 * it never had. The daemon answered `agent: null` and the person read "Couldn't open this conversation" with Details
 * `NotFound`, for a conversation that existed (or was about to). One book per server (the factory keys it by profile
 * and address) lets the next gateway answer for the same ids.
 *
 * Bounded ([MAX_DRAFTS], [MAX_ALIASES], oldest dropped first); safe to share across the gateways' lanes.
 */
public class PaseoIdentityBook {
    /** A conversation opened here whose first message has not created its agent yet. */
    internal data class Draft(
        val session: Session,
        val provider: String?,
        val model: ModelRef?,
        val variant: String?,
    )

    private data class State(
        val drafts: Map<String, Draft> = emptyMap(),
        /** App id → the daemon's agent id. */
        val real: Map<String, String> = emptyMap(),
        /** The daemon's agent id → app id. */
        val app: Map<String, String> = emptyMap(),
    )

    private val state = MutableStateFlow(State())

    internal fun draft(appId: String): Draft? = state.value.drafts[appId]

    internal fun real(appId: String): String? = state.value.real[appId]

    internal fun app(realId: String): String? = state.value.app[realId]

    /** Records (or updates) the draft [appId]. */
    internal fun keepDraft(appId: String, draft: Draft) {
        state.update { s ->
            val drafts = LinkedHashMap(s.drafts).apply {
                remove(appId)
                put(appId, draft)
                while (size > MAX_DRAFTS) remove(keys.first())
            }
            s.copy(drafts = drafts)
        }
    }

    /** The draft [appId]'s first message created the daemon's agent [realId]: from now on the two name one conversation. */
    internal fun created(appId: String, realId: String) {
        state.update { s ->
            val real = LinkedHashMap(s.real).apply {
                remove(appId)
                put(appId, realId)
                while (size > MAX_ALIASES) remove(keys.first())
            }
            s.copy(drafts = s.drafts - appId, real = real, app = real.entries.associate { (a, r) -> r to a })
        }
    }

    /** The conversation [appId] is gone (deleted, archived, a start that failed and was discarded). */
    internal fun forget(appId: String) {
        state.update { s ->
            if (appId !in s.drafts && appId !in s.real) s
            else {
                val real = s.real - appId
                s.copy(drafts = s.drafts - appId, real = real, app = real.entries.associate { (a, r) -> r to a })
            }
        }
    }

    public companion object {
        public const val MAX_DRAFTS: Int = 64
        public const val MAX_ALIASES: Int = 512
    }
}
