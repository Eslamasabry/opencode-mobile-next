package io.github.eslamasabry.oc.engine.attention

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.EventLocation
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Identity includes location: different folders may use the same session and request identifiers.
 */
public data class AttentionTarget(
    val directory: String,
    val workspace: String?,
    val session: SessionId,
    val kind: AttentionKind,
    val request: String,
)

public enum class AttentionKind {
    Permission,
    Question,
    Form,
    FailedRun,
}

/** Only observed identity and timing, never server text or provider error payloads. */
public data class AttentionObservation(
    val target: AttentionTarget,
    val observedAtMs: Long,
    val isFresh: Boolean = true,
)

public data class ProjectActivity(
    val directory: String,
    val running: Set<SessionId>,
    val waiting: Set<SessionId>,
    /**
     * False while any retained observation or running session in this project needs a fresh event.
     */
    val isFresh: Boolean = true,
)

public data class ElsewhereSnapshot(
    val rows: List<AttentionObservation> = emptyList(),
    val running: Map<String, Set<SessionId>> = emptyMap(),
    /** Freshness belongs to each running identity, not to the stream as a whole. */
    val staleRunning: Map<String, Set<SessionId>> = emptyMap(),
) {
    public fun observations(except: String? = null): List<AttentionObservation> = rows.filter {
        it.target.directory != except
    }

    public fun activity(except: String? = null): List<ProjectActivity> =
        (running.keys +
                rows
                    .filter { it.target.kind != AttentionKind.FailedRun }
                    .map { it.target.directory })
            .filter { it != except }
            .distinct()
            .map { directory ->
                ProjectActivity(
                    directory,
                    running[directory].orEmpty(),
                    rows
                        .filter {
                            it.target.directory == directory &&
                                it.target.kind != AttentionKind.FailedRun
                        }
                        .map { it.target.session }
                        .toSet(),
                    isFresh =
                        running[directory].orEmpty().none {
                            it in staleRunning[directory].orEmpty()
                        } && rows.filter { it.target.directory == directory }.all { it.isFresh },
                )
            }
            .filter { it.running.isNotEmpty() || it.waiting.isNotEmpty() }
            .sortedWith(
                compareByDescending<ProjectActivity> { it.waiting.size }
                    .thenByDescending { it.running.size }
                    .thenBy { it.directory }
            )

    public fun waitingCount(except: String? = null): Int =
        rows
            .filter { it.target.directory != except && it.target.kind != AttentionKind.FailedRun }
            .map { it.target.directory to it.target.session }
            .toSet()
            .size
}

/**
 * One instance per connected profile; fed from the existing all-server stream, never another
 * socket.
 */
public class ElsewhereAttention(dispatchers: AppDispatchers) {
    private val lane = dispatchers.serial("elsewhere-attention")
    private val observations = LinkedHashMap<AttentionTarget, AttentionObservation>()
    private val running = LinkedHashMap<String, MutableSet<SessionId>>()
    private val staleRunning = LinkedHashMap<String, MutableSet<SessionId>>()
    private val mutable = MutableStateFlow(ElsewhereSnapshot())
    public val state: StateFlow<ElsewhereSnapshot> = mutable.asStateFlow()

    public suspend fun observe(envelope: GatewayEnvelope): Unit =
        withContext(lane) {
            if (envelope.event == GatewayEvent.Connected) {
                stale()
                publish()
                return@withContext
            }
            val location = envelope.location ?: return@withContext
            val directory = location.directory?.takeIf { it.isNotBlank() } ?: return@withContext
            if (teamDirectory(directory)) return@withContext
            when (val event = envelope.event) {
                is GatewayEvent.PermissionAsked ->
                    add(
                        location,
                        event.request.sessionId,
                        AttentionKind.Permission,
                        event.request.id.raw,
                        envelope.receivedAtMs,
                    )
                is GatewayEvent.PermissionSettled ->
                    remove(location, event.sessionId, AttentionKind.Permission, event.requestId.raw)
                is GatewayEvent.QuestionAsked ->
                    add(
                        location,
                        event.question.sessionId,
                        AttentionKind.Question,
                        event.question.id.raw,
                        envelope.receivedAtMs,
                    )
                is GatewayEvent.QuestionSettled ->
                    remove(location, event.sessionId, AttentionKind.Question, event.requestId.raw)
                is GatewayEvent.FormCreated ->
                    add(
                        location,
                        event.form.sessionId,
                        AttentionKind.Form,
                        event.form.id.raw,
                        envelope.receivedAtMs,
                    )
                is GatewayEvent.FormSettled ->
                    remove(location, event.sessionId, AttentionKind.Form, event.formId.raw)
                is GatewayEvent.RunStarted -> start(directory, event.sessionId)
                is GatewayEvent.RunRetrying -> start(directory, event.sessionId)
                is GatewayEvent.RunEnded -> {
                    settle(directory, event.sessionId)
                    if (event.outcome is RunOutcome.Failed)
                        add(
                            location,
                            event.sessionId,
                            AttentionKind.FailedRun,
                            event.sessionId.raw,
                            envelope.receivedAtMs,
                        )
                }
                is GatewayEvent.MessageUpserted ->
                    if (event.message.role == Role.User)
                        clearFailure(directory, SessionId(event.message.sessionId))
                is GatewayEvent.SessionDeleted -> {
                    settle(directory, event.sessionId)
                    clearFailure(directory, event.sessionId)
                }
                else -> Unit
            }
            publish()
        }

    /** Snapshot readers can observe a hydrated question through the same typed target. */
    public suspend fun observeQuestion(
        id: RequestId,
        session: SessionId,
        location: EventLocation,
        receivedAtMs: Long,
    ): Unit =
        withContext(lane) {
            add(location, session, AttentionKind.Question, id.raw, receivedAtMs)
            publish()
        }

    public suspend fun settleQuestion(
        id: RequestId,
        session: SessionId,
        location: EventLocation,
    ): Unit =
        withContext(lane) {
            remove(location, session, AttentionKind.Question, id.raw)
            publish()
        }

    public suspend fun markStale(): Unit =
        withContext(lane) {
            stale()
            publish()
        }

    public suspend fun clear(): Unit =
        withContext(lane) {
            observations.clear()
            running.clear()
            staleRunning.clear()
            publish()
        }

    private fun stale() {
        observations.entries.forEach { it.setValue(it.value.copy(isFresh = false)) }
        for ((directory, sessions) in running) {
            staleRunning.getOrPut(directory) { LinkedHashSet() }.addAll(sessions)
        }
    }

    private fun add(
        location: EventLocation,
        session: SessionId,
        kind: AttentionKind,
        id: String,
        at: Long,
    ) {
        val directory = location.directory?.takeIf { it.isNotBlank() } ?: return
        if (session.raw.isBlank() || id.isBlank() || teamDirectory(directory)) return
        val target = AttentionTarget(directory, location.workspace, session, kind, id)
        observations[target] = AttentionObservation(target, at)
    }

    private fun remove(
        location: EventLocation,
        session: SessionId,
        kind: AttentionKind,
        request: String,
    ) {
        observations.remove(
            AttentionTarget(
                location.directory ?: return,
                location.workspace,
                session,
                kind,
                request,
            )
        )
    }

    private fun start(directory: String, session: SessionId) {
        if (session.raw.isBlank()) return
        clearFailure(directory, session)
        running.getOrPut(directory) { LinkedHashSet() }.add(session)
        refreshRunning(directory, session)
    }

    private fun refreshRunning(directory: String, session: SessionId) {
        staleRunning[directory]?.remove(session)
        if (staleRunning[directory]?.isEmpty() == true) staleRunning.remove(directory)
    }

    private fun settle(directory: String, session: SessionId) {
        refreshRunning(directory, session)
        running[directory]?.remove(session)
        if (running[directory]?.isEmpty() == true) running.remove(directory)
        observations.keys.removeAll {
            it.directory == directory && it.session == session && it.kind != AttentionKind.FailedRun
        }
    }

    private fun clearFailure(directory: String, session: SessionId) {
        observations.keys.removeAll {
            it.directory == directory && it.session == session && it.kind == AttentionKind.FailedRun
        }
    }

    private fun teamDirectory(directory: String): Boolean {
        val parts = ArrayList<String>()
        for (part in directory.replace('\\', '/').split('/')) {
            when (part) {
                "",
                "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.take(2) == listOf("root", "aiteam") ||
            ".gc" in parts ||
            parts.zipWithNext().any { it.first == ".oc" && it.second == "aiteam" }
    }

    private fun publish() {
        mutable.value =
            ElsewhereSnapshot(
                observations.values.toList(),
                running.mapValues { it.value.toSet() },
                staleRunning.mapValues { it.value.toSet() },
            )
    }
}
