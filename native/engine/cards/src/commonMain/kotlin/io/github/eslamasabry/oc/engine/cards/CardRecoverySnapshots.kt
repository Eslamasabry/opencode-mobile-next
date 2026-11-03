package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The cheap recovery facts for one conversation: its newest prompt's stamp and whether it is idle now. */
public data class CardRecoverySnapshot(val promptStamp: String?, val idle: Boolean)

/**
 * Card-recovery snapshots of agent conversations (Flutter `PaseoGateway.genUiRecoverySnapshot`; B-AGENT-166), read
 * from the agents the gateway already listed and the stream it already follows: answering never does network I/O.
 *
 * The prompt stamp is `lastUserMessageAt`, else (never prompted) `createdAt`; `updatedAt` is ignored so a title edit
 * does not reread history. Running or initializing agents and any active turn are not idle; closed and failed agents
 * without a turn are. A live `turn_started` overrides an idle listing until `turn_completed`. A draft conversation and
 * the daemon agent it became share one entry.
 */
public class CardRecoverySnapshots {
    private class Agent(val promptStamp: String?, val listedIdle: Boolean)

    private val agents = HashMap<String, Agent>()
    private val liveTurns = HashSet<String>()
    private val aliases = HashMap<String, String>()

    /** One `fetch_agents_response` entry's `agent` object. */
    public fun listed(agent: JsonObject) {
        val id = agent.text("id") ?: return
        val status = agent.text("status")
        val activeTurn = agent["activeTurn"].let { it != null && it != JsonNull }
        val idle = !activeTurn && status != "running" && status != "initializing"
        agents[id] = Agent(stamp(agent.text("lastUserMessageAt")) ?: stamp(agent.text("createdAt")), idle)
    }

    /** One `agent_stream` event for [agentId]. */
    public fun streamed(agentId: String, eventType: String) {
        val id = aliases[agentId] ?: agentId
        when (eventType) {
            "turn_started" -> liveTurns += id
            "turn_completed", "turn_failed", "turn_canceled", "turn_cancelled" -> liveTurns -= id
        }
    }

    /** A draft conversation whose first prompt created [daemonId]; its prompt is now in flight. */
    public fun alias(draftId: String, daemonId: String, turnPending: Boolean = true) {
        aliases[draftId] = daemonId
        if (turnPending) liveTurns += daemonId
    }

    public fun daemonId(sessionId: String): String = aliases[sessionId] ?: sessionId

    /** Null when the conversation was never listed; no I/O either way. */
    public fun snapshot(sessionId: String): CardRecoverySnapshot? {
        val id = daemonId(sessionId)
        val agent = agents[id] ?: return null
        return CardRecoverySnapshot(agent.promptStamp, agent.listedIdle && id !in liveTurns)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun stamp(value: String?): String? = value?.takeIf { it.isNotBlank() }
}
