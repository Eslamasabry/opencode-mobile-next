package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.mappers.PASEO_CONTINUE_MAX
import io.github.eslamasabry.oc.protocol.paseo.mappers.PASEO_CONTINUE_PROMPT
import io.github.eslamasabry.oc.protocol.paseo.mappers.PASEO_TURN_OUTLIVING_PROVIDERS
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoIsContinuePrompt
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoLeftRunningCommands
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/*
 * Issue 147: an agent whose turn ends with a command of its own still running (Antigravity, see PaseoAgentContinue.kt
 * in mappers) is continued by the app: once per command it left running, at most [PASEO_CONTINUE_MAX] times after one
 * request of the person, never while a permission or question waits, never after a stop or a failed turn. The
 * continuation is a goal prompt, so the agent waits for the command inside the turn and carries on: the conversation
 * reads as working, with Stop, until the work is really done.
 */

/** Per agent: the command calls already continued and how many continuations followed the person's last request. */
internal class PaseoContinueBook {
    private class Agent {
        val continued = LinkedHashSet<String>()
        var sinceRequest = 0
    }

    private val agents = LinkedHashMap<String, Agent>()

    private fun agent(id: String): Agent {
        val agent = agents.remove(id) ?: Agent()
        agents[id] = agent
        while (agents.size > MAX_AGENTS) agents.remove(agents.keys.first())
        return agent
    }

    /** The person sent a request of their own: continuations may follow it again. */
    fun requestSeen(id: String) {
        agents[id]?.sinceRequest = 0
    }

    /** Whether to continue [id] for [calls] now (some not continued yet, under the bound); counts it when so. */
    fun take(id: String, calls: List<String>): Boolean {
        val agent = agent(id)
        val fresh = calls.filter { it !in agent.continued }
        if (fresh.isEmpty() || agent.sinceRequest >= PASEO_CONTINUE_MAX) return false
        agent.continued += fresh
        while (agent.continued.size > MAX_CALLS) agent.continued.remove(agent.continued.first())
        agent.sinceRequest++
        return true
    }

    private companion object {
        const val MAX_AGENTS = 256
        const val MAX_CALLS = 256
    }
}

/** A user message on [id]'s stream: the person's own request (not the app's continuation) resets the bound. */
internal fun PaseoGateway.noteUserMessage(id: String, item: JsonObject) {
    if (!paseoIsContinuePrompt(paseoText(item["text"]))) continueBook.requestSeen(id)
}

/** [id]'s turn completed: continue it when it left a command of its own running (see the file comment). */
internal fun PaseoGateway.continueLeftRunning(id: String, state: PaseoLive) {
    val provider = providerIdForSession(SessionId(id)) ?: return
    if (provider !in PASEO_TURN_OUTLIVING_PROVIDERS) return
    if (requests.hasPermission(id) || requests.hasQuestion(id)) return
    val left = paseoLeftRunningCommands(state.tools.values)
    if (left.isEmpty() || !continueBook.take(id, left)) return
    scope.launch { prompt(SessionId(id), OutgoingPrompt(PASEO_CONTINUE_PROMPT)) }
}
