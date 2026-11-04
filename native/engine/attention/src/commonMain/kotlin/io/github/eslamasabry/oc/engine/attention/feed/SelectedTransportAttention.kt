package io.github.eslamasabry.oc.engine.attention.feed

import io.github.eslamasabry.oc.engine.attention.elsewhere.AttentionEvent
import io.github.eslamasabry.oc.engine.attention.elsewhere.ElsewhereAttention
import io.github.eslamasabry.oc.engine.attention.elsewhere.ElsewhereAttention.Companion.asText
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

/** A conversation the transport knows: only its title and location reach the feed. */
public data class AttentionSessionFacts(val title: String? = null, val directory: String? = null, val workspace: String? = null)

/**
 * The selected server's attention evidence from its live transport (port of the `connection/attention.dart` active
 * snapshot, B-STATE-010). Successful request reads refresh rows; a reconnect bumps the transport revision, so cached
 * requests and failures turn stale until the next successful read or event. A confirmed `session.error` becomes a
 * failed-run row with no error text; newer work (busy), a new prompt or deletion retires it. Not thread-safe: feed it
 * from the connection's one lane.
 */
public class SelectedTransportAttention(private val clock: Clock) {
    private var revision = 0
    private var connected = false
    private val reads = mutableMapOf<AttentionKind, Instant>()
    private val readRevisions = mutableMapOf<AttentionKind, Int>()
    private val readFailed = mutableSetOf<AttentionKind>()
    private val pending = mutableMapOf<AttentionKind, List<AttentionRequest>>()
    private val failures = LinkedHashMap<String, Pair<Instant, Int>>()
    private val busy = mutableSetOf<String>()
    private val deleted = mutableSetOf<String>()

    /** Connected, or a reconnect/disconnect: every change of transport makes what was read stale. */
    public fun streamConnected(value: Boolean) {
        if (value == connected) return
        connected = value
        revision++
    }

    /** A successful read of one request kind (permissions, questions or forms). */
    public fun requestsRead(kind: AttentionKind, requests: List<AttentionRequest>) {
        pending[kind] = requests
        reads[kind] = clock.now()
        readRevisions[kind] = revision
        readFailed -= kind
    }

    /** A failed read keeps the cached rows but marks the server unavailable. */
    public fun requestsReadFailed(kind: AttentionKind) {
        readFailed += kind
    }

    public fun handle(event: AttentionEvent) {
        val props = event.properties
        val sessionId = props["sessionID"].asText()
        when (event.type) {
            "session.error" -> {
                if (sessionId.isNullOrEmpty()) return
                if (ElsewhereAttention.sessionErrorIsStop(props["error"])) return
                busy -= sessionId
                failures[sessionId] = clock.now() to revision
            }
            "session.status" -> {
                if (sessionId.isNullOrEmpty()) return
                val raw = props["status"]
                when (if (raw is JsonObject) raw["type"].asText() else raw.asText()) {
                    "busy", "retry" -> {
                        busy += sessionId
                        failures.remove(sessionId)
                    }
                    "idle" -> busy -= sessionId
                }
            }
            "session.idle" -> if (sessionId != null) busy -= sessionId
            "message.updated" -> {
                val info = props["info"] as? JsonObject ?: return
                if (info["role"].asText() == "user") info["sessionID"].asText()?.let { failures.remove(it) }
            }
            "session.deleted" -> {
                val info = props["info"]
                val id = (if (info is JsonObject) info["id"].asText() else sessionId) ?: return
                failures.remove(id)
                busy -= id
                deleted += id
            }
        }
    }

    /**
     * The active profile's snapshot for [AttentionFeed.fromServers]. [elsewhere] adds the other projects' observations
     * (leaving out [directory]); [sessions] gives titles and locations; [supportsForms] says whether forms are read.
     */
    public fun snapshot(
        profileId: String,
        directory: String?,
        workspace: String?,
        supportsForms: Boolean,
        sessions: (String) -> AttentionSessionFacts? = { null },
        elsewhere: ElsewhereAttention? = null,
    ): AttentionServerSnapshot {
        fun readCurrent(kind: AttentionKind) = readRevisions[kind] == revision
        val complete =
            readCurrent(AttentionKind.Permission) && readCurrent(AttentionKind.Question) &&
                (!supportsForms || readCurrent(AttentionKind.Form))
        val observations = mutableListOf<AttentionObservation>()
        for ((kind, requests) in pending) {
            val at = reads[kind]
            for (r in requests) {
                val session = sessions(r.sessionId)
                observations +=
                    AttentionObservation(
                        id = r.id,
                        kind = kind,
                        phase = AttentionPhase.NeedsYou,
                        observedAt = at ?: Instant.fromEpochMilliseconds(0),
                        isFresh = at != null && readCurrent(kind),
                        sessionId = r.sessionId,
                        requestId = r.id,
                        title = session?.title,
                        directory = session?.directory ?: r.directory ?: directory,
                        workspace = session?.workspace ?: r.workspace ?: workspace,
                    )
            }
        }
        elsewhere?.let { observations += it.observations(except = directory) }
        for ((id, failure) in failures) {
            if (id in busy || id in deleted) continue
            val session = sessions(id)
            observations +=
                AttentionObservation(
                    id = "session-failure:$id",
                    kind = AttentionKind.FailedRun,
                    phase = AttentionPhase.Failed,
                    observedAt = failure.first,
                    isFresh = failure.second == revision,
                    sessionId = id,
                    title = session?.title,
                    directory = session?.directory ?: directory,
                    workspace = session?.workspace ?: workspace,
                )
        }
        val checkedAt = (reads.values).maxOrNull()
        return AttentionServerSnapshot(
            profileId = profileId,
            status =
                when {
                    !connected -> AttentionSourceStatus.Paused
                    readFailed.isNotEmpty() -> AttentionSourceStatus.Unavailable
                    complete -> AttentionSourceStatus.Current
                    else -> AttentionSourceStatus.Checking
                },
            checkedAt = checkedAt,
            directory = directory,
            workspace = workspace,
            attention = observations,
            complete = complete,
            // The selected transport does not enumerate all run histories.
            attentionComplete = false,
        )
    }
}
