package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * What a session in the brief waits for, in the order the app answers them (Dart
 * `ReturnBriefBlocker`).
 */
public enum class BriefBlocker {
    Permission,
    Question,
    Form,
}

/** One finished-and-unreviewed session with the server's idle watermark (epoch ms). */
public data class BriefRun(val session: Session, val idleAt: Long)

/** One session blocked on the person, with the exact request the brief shows. */
public data class BriefRequest(
    val session: Session,
    val blocker: BriefBlocker,
    val requestId: String,
) {
    /** Session, kind and request id (Dart `acknowledgementID`, a JSON triple). */
    val acknowledgementId: String
        get() =
            JsonArray(
                    listOf(
                        JsonPrimitive(session.id.raw),
                        JsonPrimitive(blocker.wire),
                        JsonPrimitive(requestId),
                    )
                )
                .toString()
}

private val BriefBlocker.wire: String
    get() = name.lowercase()

/** One (session, idle watermark) pair the person dismissed. */
public data class AckedRun(val sessionId: String, val idleAt: Long)

/**
 * Exact identities the person dismissed for one profile and location (Dart `ReturnBriefAck`). A
 * newer idle, a session never shown or a request with a new id is not covered. Bounded; overflow
 * stays unacknowledged.
 */
public data class ReturnBriefAck(
    val runs: ImmutableSet<AckedRun> = persistentSetOf(),
    val requestIds: ImmutableSet<String> = persistentSetOf(),
) {
    public fun coversRun(sessionId: SessionId, idle: Long): Boolean =
        AckedRun(sessionId.raw, idle) in runs

    public fun coversRequest(id: String): Boolean = id in requestIds

    /**
     * The union with what was shown, keeping the newest [MAX_RUNS] runs and the latest
     * [MAX_REQUESTS] request ids.
     */
    public fun merge(
        shownRuns: Collection<AckedRun>,
        shownRequestIds: Collection<String>,
    ): ReturnBriefAck {
        val mergedRuns =
            (runs + shownRuns).sortedWith(
                compareByDescending<AckedRun> { it.idleAt }.thenBy { it.sessionId }
            )
        val mergedRequests = requestIds.filter { it !in shownRequestIds } + shownRequestIds
        return ReturnBriefAck(
            runs = mergedRuns.take(MAX_RUNS).toImmutableSet(),
            requestIds = mergedRequests.takeLast(MAX_REQUESTS).toImmutableSet(),
        )
    }

    public fun toJson(): JsonElement =
        JsonObject(
            mapOf(
                "runs" to
                    JsonArray(
                        runs.map {
                            JsonArray(listOf(JsonPrimitive(it.sessionId), JsonPrimitive(it.idleAt)))
                        }
                    ),
                "requests" to JsonArray(requestIds.map(::JsonPrimitive)),
            )
        )

    public companion object {
        public const val MAX_RUNS: Int = 64
        public const val MAX_REQUESTS: Int = 32
        public val Empty: ReturnBriefAck = ReturnBriefAck()

        /**
         * Null for anything malformed: a broken entry reads as "never dismissed", which only shows
         * more.
         */
        public fun fromJson(raw: JsonElement?): ReturnBriefAck? {
            if (raw !is JsonObject) return null
            val runs = raw["runs"] as? JsonArray ?: return null
            val requests = raw["requests"] as? JsonArray ?: return null
            val pairs = mutableSetOf<AckedRun>()
            for (entry in runs.take(MAX_RUNS)) {
                if (entry !is JsonArray || entry.size != 2) return null
                val id = (entry[0] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                val idle = (entry[1] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                if (id.isNullOrEmpty() || idle == null || idle <= 0) return null
                pairs += AckedRun(id, idle)
            }
            val ids = mutableSetOf<String>()
            for (entry in requests.take(MAX_REQUESTS)) {
                val id = (entry as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                if (id.isNullOrEmpty()) return null
                ids += id
            }
            return ReturnBriefAck(pairs.toImmutableSet(), ids.toImmutableSet())
        }
    }
}

/** Source and persisted location of a brief; carried unchanged by a rendered acknowledgement. */
public data class BriefScope(val profileId: ProfileId, val sourceId: String, val location: String)

/**
 * "While you were away" for one profile and location (Dart `ReturnBrief`; 07 §5 B15). Built only
 * from state the app already holds: idle watermarks, read state and pending requests. It never
 * computes an absence interval, never marks anything viewed, and says "unknown" when the server
 * cannot report read state (B-CHAT-093…095).
 */
public data class ReturnBrief(
    /**
     * Unreviewed runs not covered by the acknowledgement, newest first, at most [MAX_SHOWN_RUNS].
     */
    val unreviewed: ImmutableList<BriefRun>,
    val requests: ImmutableList<BriefRequest>,
    /**
     * False when the server cannot say which runs were seen: [unreviewed] is empty and the line
     * says so.
     */
    val readStateKnown: Boolean,
    val inventoryPartial: Boolean,
    val hiddenUnreviewed: Int,
    val hiddenRequests: Int,
    /**
     * Exact origin of these identities; absent scope must never join a merged feed by session id
     * alone.
     */
    val scope: BriefScope? = null,
    val overflowRuns: ImmutableList<BriefRun> = persistentListOf(),
    val overflowRequests: ImmutableList<BriefRequest> = persistentListOf(),
) {
    val isEmpty: Boolean
        get() = unreviewed.isEmpty() && requests.isEmpty()

    /** "Can't tell what's new": distinct from an empty brief, which shows nothing. */
    val unknown: Boolean
        get() = !readStateKnown || inventoryPartial

    /**
     * What dismissing THIS snapshot acknowledges: exactly the rows on screen, never the "N more".
     */
    public fun acknowledgement(previous: ReturnBriefAck?): ReturnBriefAck =
        (previous ?: ReturnBriefAck.Empty).merge(
            shownRuns = unreviewed.map { AckedRun(it.session.id.raw, it.idleAt) },
            shownRequestIds = requests.map { it.acknowledgementId },
        )

    public companion object {
        public const val MAX_SHOWN_RUNS: Int = 3
        public const val MAX_SHOWN_REQUESTS: Int = 3

        /** A finished (idle, not busy), unread, undismissed root conversation, or null. */
        public fun unreviewedRun(
            session: Session,
            readStateKnown: Boolean,
            isUnread: (Session) -> Boolean,
            isBusy: (SessionId) -> Boolean,
            ack: ReturnBriefAck?,
        ): BriefRun? {
            if (session.parentId != null || session.time?.archivedMs != null) return null
            val idle = session.time?.idleMs
            if (
                !readStateKnown ||
                    idle == null ||
                    idle <= 0 ||
                    isBusy(session.id) ||
                    !isUnread(session)
            )
                return null
            if (ack != null && ack.coversRun(session.id, idle)) return null
            return BriefRun(session, idle)
        }

        public fun build(
            sessions: List<Session>,
            readStateKnown: Boolean,
            inventoryPartial: Boolean,
            isUnread: (Session) -> Boolean,
            isBusy: (SessionId) -> Boolean,
            blockerOf: (SessionId) -> Pair<BriefBlocker, String>?,
            ack: ReturnBriefAck? = null,
            scope: BriefScope? = null,
        ): ReturnBrief {
            val runs = mutableListOf<BriefRun>()
            val requests = mutableListOf<BriefRequest>()
            for (session in sessions) {
                if (session.parentId != null || session.time?.archivedMs != null) continue
                val blocker = blockerOf(session.id)
                if (blocker != null) {
                    val request = BriefRequest(session, blocker.first, blocker.second)
                    if (ack == null || !ack.coversRequest(request.acknowledgementId))
                        requests += request
                    continue
                }
                unreviewedRun(session, readStateKnown, isUnread, isBusy, ack)?.let(runs::add)
            }
            runs.sortWith(compareByDescending<BriefRun> { it.idleAt }.thenBy { it.session.id.raw })
            requests.sortWith(
                compareBy<BriefRequest> { it.blocker.ordinal }.thenBy { it.session.id.raw }
            )
            val shown = runs.take(MAX_SHOWN_RUNS)
            return ReturnBrief(
                unreviewed = shown.toImmutableList(),
                requests = requests.take(MAX_SHOWN_REQUESTS).toImmutableList(),
                readStateKnown = readStateKnown,
                inventoryPartial = inventoryPartial,
                hiddenUnreviewed = runs.size - shown.size,
                hiddenRequests = (requests.size - MAX_SHOWN_REQUESTS).coerceAtLeast(0),
                scope = scope,
                overflowRuns = runs.drop(MAX_SHOWN_RUNS).toImmutableList(),
                overflowRequests = requests.drop(MAX_SHOWN_REQUESTS).toImmutableList(),
            )
        }

        public val None: ReturnBrief =
            ReturnBrief(
                persistentListOf(),
                persistentListOf(),
                readStateKnown = true,
                inventoryPartial = false,
                0,
                0,
            )
    }
}
