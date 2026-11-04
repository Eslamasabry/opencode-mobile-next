package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** The phone project engine's bounded snapshot (only what attention needs). */
public data class MonitorTeamWorkspace(val projects: List<MonitorTeamProject>)

public data class MonitorTeamProject(
    val id: String,
    val tasks: List<MonitorTeamTask> = emptyList(),
    val requests: List<MonitorTeamRequest> = emptyList(),
)

public data class MonitorTeamTask(val id: String, val title: String? = null, val status: String)

public data class MonitorTeamRequest(
    val id: String,
    val taskId: String = "",
    val title: String? = null,
    val answered: Boolean = false,
)

/**
 * The authenticated phone project engine client for one profile; it owns its probe and its read.
 */
public interface MonitorPhoneEngine {
    /** The profile id the engine says it serves. */
    public suspend fun health(): Outcome<String>

    /** True when the engine supports projects. */
    public val projects: Boolean

    public suspend fun workspace(): Outcome<MonitorTeamWorkspace>

    public fun close()
}

/**
 * Bounded attention enrichment inside the monitor's polling lane (port of `MonitorAttentionReader`;
 * B-AGENT-138/139). Owns no watcher, timer or persistent state, never performs an act, and keeps
 * one shared [timeout] deadline for all of one read. A late engine client still belongs to the
 * reader and is closed in [cleanupScope] when it arrives.
 */
public class MonitorAttentionReader(
    private val clock: Clock,
    private val cleanupScope: CoroutineScope,
    private val redactor: Redactor = Redactor(),
    /** Builds the authenticated engine client; only called when the profile has a token. */
    private val phoneEngine: (suspend (MonitorProfile) -> MonitorPhoneEngine)? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    public val timeout: Duration = 8.seconds,
    /**
     * Production factories resolve secrets inside their bounded read, never in a synchronous
     * profile list.
     */
    private val resolvePhoneCredentials: Boolean = false,
) : MonitorAttentionReading {
    private val cursors = LinkedHashMap<String, Pair<String, Int>>()
    private var disposed = false

    public fun forget(profileId: String) {
        cursors.remove(profileId)
    }

    public fun dispose() {
        disposed = true
        cursors.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun read(
        profile: MonitorProfile,
        gateway: MonitorGateway,
        sessions: List<MonitorSession>,
        statuses: Map<String, String>,
        current: () -> Boolean,
    ): MonitorAttentionDetails {
        val started = timeSource.markNow()
        fun remaining(): Duration = timeout - started.elapsedNow()
        fun allowed(): Boolean = !disposed && current() && started.elapsedNow() < timeout
        suspend fun <T> call(action: suspend () -> Outcome<T>): T? {
            if (!allowed()) return null
            val value =
                attempt({ Failure.Unexpected() }) { withTimeoutOrNull(remaining()) { action() } }
                    .getOrNull()
                    ?.getOrNull()
            if (!allowed()) return null
            return value
        }

        val items = mutableListOf<MonitorAttentionItem>()
        val checked = mutableSetOf<String>()
        var historiesComplete = true
        var teamComplete = !profile.hasPhoneEngine
        val location =
            JsonArray(listOf(gateway.directory, gateway.workspace).map { JsonPrimitive(it) })
                .toString()
        val old = cursors[profile.id]
        val start = if (old?.first == location) old.second else 0
        val count = minOf(SESSION_LIMIT, sessions.size)
        if (allowed() && sessions.isNotEmpty()) {
            if (cursors.size >= MAX_CURSORS && profile.id !in cursors)
                cursors.remove(cursors.keys.first())
            cursors[profile.id] = location to (start + count) % sessions.size
        }
        var index = 0
        while (index < count && allowed()) {
            val session = sessions[(start + index) % sessions.size]
            index++
            // A newer active turn makes an earlier terminal failure obsolete.
            val status = statuses[session.id]
            if (status != null && status != "idle") {
                checked += session.id
                continue
            }
            // Failed or truncated history is unknown: the monitor keeps older facts.
            val page = call { gateway.messagePage(session.id, MESSAGE_LIMIT) } ?: continue
            if (page.hasMore || page.items.size >= MESSAGE_LIMIT) historiesComplete = false
            val messages = page.items.takeLast(MESSAGE_LIMIT)
            val hasUser = messages.any { it.role == "user" }
            val emptyComplete = messages.isEmpty() && !page.hasMore
            if (!hasUser && !emptyComplete) continue
            checked += session.id
            if (!lastTurnFailed(messages)) continue
            items +=
                MonitorAttentionItem(
                    id = "session-failure:${session.id}",
                    kind = MonitorAttentionKind.FailedRun,
                    phase = MonitorWorkPhase.Failed,
                    observedAt = clock.now(),
                    sessionId = session.id,
                    title = title(session.title),
                    directory = session.directory ?: gateway.directory,
                    workspace = session.workspace ?: gateway.workspace,
                )
        }

        val build = phoneEngine
        if (
            profile.hasPhoneEngine &&
                build != null &&
                (profile.teamEngineToken.isNotEmpty() || resolvePhoneCredentials) &&
                allowed()
        ) {
            // A late client result still belongs to this reader and must close.
            val pending = cleanupScope.async(start = CoroutineStart.UNDISPATCHED) { build(profile) }
            val engine =
                withTimeoutOrNull(remaining().coerceAtLeast(Duration.ZERO)) { pending.await() }
            if (engine == null) {
                pending.invokeOnCompletion { cause ->
                    if (cause == null) pending.getCompleted().close()
                }
                teamComplete = false
            } else {
                try {
                    val served = call { engine.health() }
                    // A wrong identity is unknown, never another profile's attention.
                    if (served != profile.id || !engine.projects) {
                        teamComplete = false
                    } else {
                        val snapshot = call { engine.workspace() }
                        if (snapshot == null) {
                            teamComplete = false
                        } else {
                            val records =
                                snapshot.projects.size +
                                    snapshot.projects.sumOf { it.tasks.size + it.requests.size }
                            items +=
                                projectObservations(snapshot, gateway.directory, gateway.workspace)
                            teamComplete = records <= TEAM_RECORD_LIMIT && allowed()
                        }
                    }
                } finally {
                    attempt({ Failure.Unexpected() }) { engine.close() }
                        .let { if (it is Outcome.Failed) teamComplete = false }
                }
            }
        } else if (profile.hasPhoneEngine) {
            // Missing credentials: no call is made and coverage is unknown.
            teamComplete = false
        }
        return MonitorAttentionDetails(
            items = items,
            complete =
                allowed() &&
                    checked.size == sessions.size &&
                    sessions.size < 100 &&
                    historiesComplete &&
                    teamComplete,
            checkedSessionIds = checked,
            teamComplete = teamComplete,
        )
    }

    /**
     * Bounded projection of the authenticated phone engine's projects: open requests and
     * failed/waiting tasks.
     */
    internal fun projectObservations(
        snapshot: MonitorTeamWorkspace,
        directory: String?,
        workspace: String?,
    ): List<MonitorAttentionItem> {
        val result = mutableListOf<MonitorAttentionItem>()
        var remaining = TEAM_RECORD_LIMIT
        val at = clock.now()
        for (project in snapshot.projects) {
            if (remaining-- <= 0) break
            val requested = mutableSetOf<String>()
            for (request in project.requests) {
                if (remaining-- <= 0) return result
                if (request.answered) continue
                requested += request.taskId
                result +=
                    MonitorAttentionItem(
                        id = "team-project-request:${project.id}:${request.id}",
                        kind = MonitorAttentionKind.TeamGate,
                        phase = MonitorWorkPhase.NeedsYou,
                        observedAt = at,
                        requestId = request.id,
                        taskId = request.taskId.ifEmpty { null },
                        title = title(request.title),
                        directory = directory,
                        workspace = workspace,
                    )
            }
            for (task in project.tasks) {
                if (remaining-- <= 0) return result
                if (task.id in requested || task.status !in WAITING_TASK_STATES) continue
                val failed = task.status == "failed"
                result +=
                    MonitorAttentionItem(
                        id = "team-project-task:${project.id}:${task.id}",
                        kind =
                            if (failed) MonitorAttentionKind.FailedRun
                            else MonitorAttentionKind.TeamGate,
                        phase = if (failed) MonitorWorkPhase.Failed else MonitorWorkPhase.NeedsYou,
                        observedAt = at,
                        taskId = task.id,
                        title = title(task.title),
                        directory = directory,
                        workspace = workspace,
                    )
            }
        }
        return result
    }

    private fun title(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val redacted = redactor.redact(value)
        return if (redacted.length <= 160) redacted else redacted.take(160)
    }

    public companion object {
        public const val SESSION_LIMIT: Int = 4
        public const val MESSAGE_LIMIT: Int = 50
        public const val TEAM_RECORD_LIMIT: Int = 256
        private const val MAX_CURSORS = 64

        /**
         * "interrupted" that cannot resume itself is presented as review, not a failure or request.
         */
        private val WAITING_TASK_STATES = setOf("failed", "needsYou", "waitingForYou")

        /**
         * The latest turn (from its user message) failed or was cut off. No user message is no
         * turn.
         */
        internal fun lastTurnFailed(messages: List<MonitorMessage>): Boolean {
            val lastUser = messages.indexOfLast { it.role == "user" }
            if (lastUser < 0) return false
            val reply = messages.drop(lastUser + 1).filter { it.role == "assistant" }
            if (reply.isEmpty()) return false
            return reply.any { it.failed } || reply.last().finish == "length"
        }
    }
}
