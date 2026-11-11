package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SubAgent
import io.github.eslamasabry.oc.core.model.SubAgentOpen
import io.github.eslamasabry.oc.core.model.SubAgentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * The one mapping from child sessions to [SubAgent]s, shared by every gateway that has child conversations, so the
 * rules (forks out, state from the run statuses, read-only from `subagentReplies`) live in one place.
 */
public object SubAgents {
    /** No list is longer than this (OpenCode 2's own page for children is 200, as Flutter asked for). */
    public const val MAX_CHILDREN: Int = 200

    /**
     * [children] of [parent] as sub-agents: only direct children, never a fork, never [parent] itself, each once,
     * at most [MAX_CHILDREN]. [statuses] null means no status was read (state [SubAgentState.Unknown] unless
     * [ended] knows how the last run ended).
     */
    public fun of(
        parent: SessionId,
        children: List<Session>,
        statuses: Map<SessionId, SessionStatus>?,
        readOnly: Boolean,
        ended: Map<SessionId, SubAgentState> = emptyMap(),
    ): ImmutableList<SubAgent> =
        children
            .asSequence()
            .filter { it.parentId == parent && isSubAgent(it) }
            .distinctBy { it.id }
            .take(MAX_CHILDREN)
            .map { child ->
                SubAgent(
                    id = child.id,
                    parent = parent,
                    name = nameOf(child),
                    title = child.title,
                    state = stateOf(child, statuses, ended[child.id]),
                    open = SubAgentOpen.Conversation(child.id, readOnly),
                    createdMs = child.time?.createdMs,
                )
            }
            .toList()
            .toImmutableList()

    /**
     * The one rule for "this session is a sub-agent of [Session.parentId]": it has a parent, is not a fork and is not
     * its own parent. Lists ([of]) and live events (the edge dock's child source) both ask this.
     */
    public fun isSubAgent(session: Session): Boolean =
        session.parentId != null && session.forkOf == null && session.parentId != session.id

    /** The agent or task name when the server says it, else the title. */
    public fun nameOf(child: Session): String? =
        child.agent?.trim()?.takeIf { it.isNotEmpty() } ?: child.title?.trim()?.takeIf { it.isNotEmpty() }

    /** A running status wins; then how the last run ended; a known idle child is done. */
    public fun stateOf(
        child: Session,
        statuses: Map<SessionId, SessionStatus>?,
        ended: SubAgentState? = null,
    ): SubAgentState {
        val status = statuses?.get(child.id)
        return when {
            status == SessionStatus.Busy || status == SessionStatus.Retry -> SubAgentState.Working
            ended != null -> ended
            child.lastRunFailed -> SubAgentState.Failed
            statuses == null -> SubAgentState.Unknown
            else -> SubAgentState.Done
        }
    }

    /**
     * [children] of [parent] with the run states [live] knows right now (one status read; a failed status read
     * leaves the states unknown rather than failing the list) and read-only when the server takes no replies in a
     * sub-agent (`subagentReplies` false).
     */
    public suspend fun withStates(
        live: ServerGateway,
        parent: SessionId,
        children: List<Session>,
        ended: Map<SessionId, SubAgentState> = emptyMap(),
    ): ImmutableList<SubAgent> {
        val statuses = (live.sessionStatuses() as? Outcome.Ok)?.value
        return of(parent, children, statuses, readOnly = !live.capabilities.value.subagentReplies, ended)
    }
}

/** One read of [parent]'s sub-agents. */
public data class SubAgentsRead(val parent: SessionId, val subAgents: ImmutableList<SubAgent>)

/**
 * The backfill a live surface (the edge dock) needs beside the event stream: children that existed before the app
 * connected are never announced by an event, so they are read. Reads happen on attach, after every Connected (the
 * running conversations plus the ones the caller [watched]) and when a conversation starts a run (that one only).
 * No polling, no timer: every read answers an event. Bounded: at most [MAX_PARENTS] parents per batch, each list at
 * most [SubAgents.MAX_CHILDREN]. A failed read emits nothing (the surface keeps what it had). Off while the server
 * does not have `subagentSessions`; the capability is checked at every batch, never the server's kind.
 */
public object SubAgentBackfill {
    public const val MAX_PARENTS: Int = 16
    private const val MAX_KNOWN_CHILDREN: Int = 512

    private sealed interface Trigger {
        data object Connect : Trigger

        data class Started(val session: SessionId) : Trigger
    }

    public fun reads(
        live: ServerGateway,
        ops: SessionOperationsGateway,
        watched: () -> Collection<SessionId> = { emptyList() },
    ): Flow<SubAgentsRead> = channelFlow {
        val triggers = Channel<Trigger>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        launch {
            live.events.all.collect { envelope ->
                when (val event = envelope.event) {
                    GatewayEvent.Connected -> triggers.send(Trigger.Connect)
                    is GatewayEvent.RunStarted -> triggers.send(Trigger.Started(event.sessionId))
                    else -> Unit
                }
            }
        }
        // The stream may already be up when this starts: one read on attach.
        triggers.send(Trigger.Connect)
        // Children seen in earlier reads: a running child is not asked for its own children on connect.
        val knownChildren = LinkedHashSet<SessionId>()
        while (true) {
            val batch = mutableListOf(triggers.receive())
            while (true) batch += triggers.tryReceive().getOrNull() ?: break
            if (!live.capabilities.value.subagentSessions) continue
            for (parent in parentsOf(batch, live, watched, knownChildren)) {
                val read = ops.subAgents(parent) as? Outcome.Ok ?: continue
                remember(knownChildren, read.value)
                send(SubAgentsRead(parent, read.value))
            }
        }
    }

    /** The parents one batch reads: on connect the running ones and the watched ones, then each that started. */
    private suspend fun parentsOf(
        batch: List<Trigger>,
        live: ServerGateway,
        watched: () -> Collection<SessionId>,
        knownChildren: Set<SessionId>,
    ): List<SessionId> {
        val parents = LinkedHashSet<SessionId>()
        if (Trigger.Connect in batch) {
            val statuses = (live.sessionStatuses() as? Outcome.Ok)?.value.orEmpty()
            parents += statuses.filterValues { it == SessionStatus.Busy || it == SessionStatus.Retry }.keys
            parents -= knownChildren
            parents += watched()
        }
        parents += batch.filterIsInstance<Trigger.Started>().map { it.session }.filterNot { it in knownChildren }
        return parents.take(MAX_PARENTS)
    }

    private fun remember(knownChildren: LinkedHashSet<SessionId>, read: List<SubAgent>) {
        for (child in read) {
            knownChildren.remove(child.id)
            knownChildren += child.id
        }
        while (knownChildren.size > MAX_KNOWN_CHILDREN) knownChildren.remove(knownChildren.first())
    }
}
