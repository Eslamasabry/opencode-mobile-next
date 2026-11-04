package io.github.eslamasabry.oc.engine.attention.feed

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How a server's attention source last read (Flutter `ProfileMonitorStatus`). */
public enum class AttentionSourceStatus { Disabled, Waiting, Checking, Current, Unavailable, WifiRequired, Paused }

/** Coverage of one server, independent of its last rows: unavailable/partial/stale never means "nothing pending". */
public enum class AttentionCheckState { Disabled, Waiting, Checking, Current, Partial, Unavailable, WifiRequired, Paused, Stale }

public enum class AttentionRequestKind { Permission, Question, Form, CheckIn }

/** One pending request a source read (Flutter `MonitoredRequest`). */
public data class AttentionRequest(
    val id: String,
    val sessionId: String,
    val kind: AttentionRequestKind,
    val title: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
)

/** One server's attention evidence: the profile monitor's or the selected transport's (`ProfileAttentionSnapshot`). */
public data class AttentionServerSnapshot(
    val profileId: String,
    val status: AttentionSourceStatus,
    val checkedAt: Instant? = null,
    val nextCheckAt: Instant? = null,
    val directory: String? = null,
    val workspace: String? = null,
    val requests: List<AttentionRequest> = emptyList(),
    /** Failed-run and team-gate observations; partial reads keep their timestamps and read as stale. */
    val attention: List<AttentionObservation> = emptyList(),
    val attentionComplete: Boolean = false,
    val complete: Boolean = false,
) {
    val isCurrent: Boolean get() = status == AttentionSourceStatus.Current && complete
}

/** Supply only the current saved profiles: a monitor cache may outlive removal and is never an authority for them. */
public data class AttentionServer(val profileId: String, val name: String, val snapshot: AttentionServerSnapshot)

public data class AttentionServerCheck(
    val profileId: String,
    val serverName: String,
    val state: AttentionCheckState,
    val isFresh: Boolean,
    val coverageComplete: Boolean,
    val checkedAt: Instant? = null,
    val nextCheckAt: Instant? = null,
) {
    val isCurrent: Boolean get() = state == AttentionCheckState.Current
}

/**
 * In-memory projection of the profile monitor and the selected transport (port of `AttentionFeed`, B-STATE-010). Owns
 * no polling, transport, persistence, notification or automatic action. Each saved server appears once; each
 * observation once per server, the newest wins. Titles and server names are redacted here.
 */
public class AttentionFeed private constructor(
    public val items: ImmutableList<AttentionFeedItem>,
    public val servers: ImmutableList<AttentionServerCheck>,
) {
    /** Only this permits an all-clear: no known item is a lower bound if any server could not be checked completely. */
    public val isComplete: Boolean get() = servers.all { it.isCurrent } && items.all { it.isFresh }
    public val knownAttentionCount: Int get() = items.size
    public val freshAttentionCount: Int get() = items.count { it.isFresh }
    public val attentionCount: Int? get() = if (isComplete) items.size else null

    public companion object {
        /** Covers the monitor's five-minute background cadence. */
        public val DEFAULT_MAX_AGE: Duration = 6.minutes

        public val Empty: AttentionFeed = AttentionFeed(persistentListOf(), persistentListOf())

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        public fun fromServers(
            savedServers: Iterable<AttentionServer>,
            now: Instant,
            redactor: Redactor,
            maxAge: Duration = DEFAULT_MAX_AGE,
        ): AttentionFeed {
            val items = mutableListOf<AttentionFeedItem>()
            val checks = mutableListOf<AttentionServerCheck>()
            val seen = mutableSetOf<String>()
            for (server in savedServers) {
                if (!seen.add(server.profileId)) continue
                val snapshot = server.snapshot
                val name = redactor.redact(server.name)
                if (snapshot.profileId != server.profileId) {
                    checks += AttentionServerCheck(server.profileId, name, AttentionCheckState.Unavailable, isFresh = false, coverageComplete = false)
                    continue
                }
                val current = snapshot.isCurrent && recent(snapshot.checkedAt, now, maxAge)
                val coverage = snapshot.complete && snapshot.attentionComplete
                checks +=
                    AttentionServerCheck(
                        profileId = server.profileId,
                        serverName = name,
                        state = checkState(snapshot, current, coverage),
                        isFresh = current,
                        coverageComplete = coverage,
                        checkedAt = snapshot.checkedAt,
                        nextCheckAt = snapshot.nextCheckAt,
                    )
                val observations = LinkedHashMap<String, AttentionObservation>()
                fun add(o: AttentionObservation) {
                    val previous = observations[o.identity]
                    if (previous == null || o.observedAt > previous.observedAt ||
                        (o.observedAt == previous.observedAt && (!previous.isFresh || o.isFresh))
                    ) {
                        observations[o.identity] = o
                    }
                }
                // Pending requests are complete independently of the bounded reads; a busy check-in is not a request.
                val checkedAt = snapshot.checkedAt
                if (checkedAt != null) {
                    for (request in snapshot.requests) {
                        val kind =
                            when (request.kind) {
                                AttentionRequestKind.Permission -> AttentionKind.Permission
                                AttentionRequestKind.Question -> AttentionKind.Question
                                AttentionRequestKind.Form -> AttentionKind.Form
                                AttentionRequestKind.CheckIn -> null
                            } ?: continue
                        add(
                            AttentionObservation(
                                id = request.id,
                                kind = kind,
                                phase = AttentionPhase.NeedsYou,
                                observedAt = checkedAt,
                                sessionId = request.sessionId,
                                requestId = request.id,
                                title = request.title,
                                directory = request.directory ?: snapshot.directory,
                                workspace = request.workspace ?: snapshot.workspace,
                            ),
                        )
                    }
                }
                for (o in snapshot.attention) {
                    add(o.copy(directory = o.directory ?: snapshot.directory, workspace = o.workspace ?: snapshot.workspace))
                }
                for (o in observations.values) {
                    items +=
                        AttentionFeedItem(
                            identity = DartJson.encode(listOf(server.profileId, o.identity)),
                            profileId = server.profileId,
                            serverName = name,
                            kind = o.kind,
                            title = o.title?.let { redactor.redact(it) },
                            target =
                                AttentionTarget(
                                    profileId = server.profileId,
                                    kind = o.kind,
                                    sessionId = o.sessionId,
                                    requestId = o.requestId ?: if (o.kind == AttentionKind.FailedRun) null else o.id,
                                    taskId = o.taskId,
                                    runId = o.runId,
                                    directory = o.directory,
                                    workspace = o.workspace,
                                ),
                            phase = o.phase,
                            isFresh = current && o.isFresh && recent(o.observedAt, now, maxAge),
                            observedAt = o.observedAt,
                        )
                }
            }
            // Requests and gates need a decision; failed runs follow. Fresh first, then oldest, then stable identity.
            items.sortWith(
                compareBy<AttentionFeedItem> { urgency(it) }
                    .thenBy { if (it.isFresh) 0 else 1 }
                    .thenBy { it.observedAt }
                    .thenBy { it.identity },
            )
            checks.sortBy { it.profileId }
            return AttentionFeed(items.toImmutableList(), checks.toImmutableList())
        }

        private fun urgency(item: AttentionFeedItem): Int =
            if (item.kind == AttentionKind.FailedRun || item.phase == AttentionPhase.Failed) 1 else 0

        private fun recent(at: Instant?, now: Instant, maxAge: Duration): Boolean = at != null && at <= now && now - at <= maxAge

        private fun checkState(s: AttentionServerSnapshot, current: Boolean, coverage: Boolean): AttentionCheckState =
            when (s.status) {
                AttentionSourceStatus.Disabled -> AttentionCheckState.Disabled
                AttentionSourceStatus.Waiting -> AttentionCheckState.Waiting
                AttentionSourceStatus.Checking -> AttentionCheckState.Checking
                AttentionSourceStatus.Unavailable -> AttentionCheckState.Unavailable
                AttentionSourceStatus.WifiRequired -> AttentionCheckState.WifiRequired
                AttentionSourceStatus.Paused -> AttentionCheckState.Paused
                AttentionSourceStatus.Current ->
                    when {
                        !s.complete -> AttentionCheckState.Partial
                        !current -> AttentionCheckState.Stale
                        !coverage -> AttentionCheckState.Partial
                        else -> AttentionCheckState.Current
                    }
            }
    }
}
