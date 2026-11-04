package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/*
 * Work Claude Code left running in the background, as one Paseo gateway saw it (port of BackgroundTaskBook in
 * lib/domain/background_tasks.dart and lib/paseo/gateway/background_tasks.dart; B-AGENT-034, 035, 151).
 *
 * Paseo forwards the start (a finished shell step the mapper marks `backgroundTask`) and the end (a synthetic
 * `task_notification` step naming the starting call), but not the agent SDK's task list, so a task the process took
 * down with it has no end: an agent snapshot with no process behind it (no `activeTurn`, closed, starting) stands in
 * for it. Work is believed for at most six hours. The same rules live in :engine:transcript's turnfacts
 * (BackgroundTaskBook), which :protocol:paseo cannot depend on; this is the gateway-side copy the chat feed reads.
 */

/** The longest background work is believed without its notice (6 hours). */
internal const val PASEO_BACKGROUND_MAX_AGE_MS: Long = 6L * 60 * 60 * 1000

/** Whether [message] ends a turn on the agent's own failure (true), says words (false) or says nothing (null). */
internal fun paseoTurnFailedAfter(message: MessageWithParts): Boolean? {
    if (message.info.role == Role.User) return false
    if (message.info.errorText != null) return true
    return if (message.parts.any { it.type == PartType.Text && it.text.isNotBlank() }) false else null
}

/**
 * [shared] (AGENT-2): the server-wide book the app keeps across this server's reconnects. Every
 * observation is mirrored into it, and its answers count too.
 */
internal class PaseoBackgroundTaskBook(
    private val shared: io.github.eslamasabry.oc.protocol.paseo.work.PaseoBackgroundTasks? = null
) {
    private class Started(val callId: String, val taskId: String?, val sinceMs: Long)

    private class Agent {
        val started = LinkedHashMap<String, Started>()
        val endedCalls = LinkedHashSet<String>()
        val endedTasks = LinkedHashSet<String>()
        var gapMs: Long? = null
        var failed = false

        fun observe(message: MessageWithParts) {
            failed = paseoTurnFailedAfter(message) ?: failed
            for (part in message.parts) {
                val start = started(message, part)
                if (start != null) {
                    started[start.callId] = start
                    continue
                }
                if (part.type != PartType.Tool || (part.toolName ?: "").trim().lowercase() != "task_notification") continue
                val input = part.tool?.input ?: continue
                input.str("toolUseId")?.trim()?.takeIf { it.isNotEmpty() }?.let(endedCalls::add)
                input.str("taskId")?.trim()?.takeIf { it.isNotEmpty() }?.let(endedTasks::add)
            }
            while (started.size > MAX_TASKS) started.remove(started.keys.first())
            while (endedCalls.size > MAX_TASKS * 4) endedCalls.remove(endedCalls.first())
            while (endedTasks.size > MAX_TASKS * 4) endedTasks.remove(endedTasks.first())
        }

        private fun started(message: MessageWithParts, part: Part): Started? {
            val tool = part.tool
            if (part.type != PartType.Tool || tool == null || tool.status != ToolStatus.Completed) return null
            val marker = tool.metadata?.get("backgroundTask").objectOrNull() ?: return null
            val created = message.info.time?.createdMs ?: return null
            val callId = part.callId ?: part.id
            if (callId.isEmpty()) return null
            return Started(callId, marker["id"].stringOrNull()?.takeIf { it.isNotEmpty() }, created)
        }

        fun running(nowMs: Long): Boolean =
            started.values.any { task ->
                task.callId !in endedCalls &&
                    !(task.taskId != null && task.taskId in endedTasks) &&
                    nowMs - task.sinceMs < PASEO_BACKGROUND_MAX_AGE_MS &&
                    gapMs.let { gap -> gap == null || task.sinceMs > gap }
            }

        fun signature(): String = "${started.keys}|$endedCalls|$endedTasks|$failed"
    }

    private val agents = LinkedHashMap<String, Agent>()
    private val revisionState = MutableStateFlow(0)

    /** Counts the changes to what [running] or [turnFailed] answer (local only). */
    val revision: StateFlow<Int> = revisionState.asStateFlow()

    private fun agent(key: String): Agent {
        val agent = agents.remove(key) ?: Agent()
        agents[key] = agent
        while (agents.size > MAX_AGENTS) agents.remove(agents.keys.first())
        return agent
    }

    /** Everything read of [key]'s conversation: a full read is the truth. */
    fun replace(key: String, messages: Iterable<MessageWithParts>) {
        val fresh = Agent()
        val agent = agent(key)
        fresh.gapMs = agent.gapMs
        val before = agent.signature()
        messages.forEach(fresh::observe)
        agents[key] = fresh
        shared?.replace(key, messages.toList())
        if (fresh.signature() != before) revisionState.value++
    }

    /** One more streamed message of [key]'s conversation. */
    fun observe(key: String, message: MessageWithParts) {
        val agent = agent(key)
        val before = agent.signature()
        agent.observe(message)
        shared?.observe(key, message)
        if (agent.signature() != before) revisionState.value++
    }

    /** [key]'s agent was seen with no process behind it at [atMs]: work started before then has ended. */
    fun runtimeGap(key: String, atMs: Long) {
        val agent = agent(key)
        val had = agent.running(atMs)
        val gap = agent.gapMs
        if (gap == null || atMs > gap) agent.gapMs = atMs
        shared?.runtimeGap(key, atMs)
        if (had && !agent.running(atMs)) revisionState.value++
    }

    fun gapAt(key: String): Long? = agents[key]?.gapMs

    fun turnFailed(key: String): Boolean =
        (agents[key]?.failed ?: false) || shared?.turnFailed(key) == true

    fun running(key: String, nowMs: Long): Boolean =
        (agents[key]?.running(nowMs) ?: false) || shared?.running(key, nowMs) == true

    /** Changes of this book and of the shared one (the feed's refresh signal). */
    val changes: kotlinx.coroutines.flow.Flow<Unit>
        get() =
            kotlinx.coroutines.flow.merge(
                revision.drop(1).map {},
                shared?.changes ?: kotlinx.coroutines.flow.emptyFlow(),
            )

    fun forget(key: String) {
        if (agents.remove(key) != null) revisionState.value++
    }

    fun clear() {
        agents.clear()
    }

    private companion object {
        const val MAX_AGENTS = 256
        const val MAX_TASKS = 64
    }
}

// ---- the gateway's reads (Dart _PaseoBackgroundTasksApi). Call on the gateway's lane.

/** The last time this gateway saw [session]'s agent with no process behind it; null when never seen down. */
public fun PaseoGateway.agentRuntimeGapAt(session: SessionId): Long? = backgroundTasks.gapAt(real(session.raw))

/** Whether [session] has background work still running, from what this gateway read of it. */
public fun PaseoGateway.backgroundTasksRunning(session: SessionId): Boolean = backgroundTasks.running(real(session.raw), nowMs())

/** Whether [session]'s last turn, as read, ended on the agent's own failure (until the next prompt). */
public fun PaseoGateway.lastTurnFailed(session: SessionId): Boolean = backgroundTasks.turnFailed(real(session.raw))

/** An agent snapshot with no process behind it (not loaded, closed, starting) ends whatever ran before now. */
internal fun PaseoGateway.noteAgentProcess(id: String, agent: JsonObject) {
    val alive = "activeTurn" in agent && agent.str("status") in setOf("running", "idle", "error")
    if (!alive) backgroundTasks.runtimeGap(real(id), nowMs())
}
