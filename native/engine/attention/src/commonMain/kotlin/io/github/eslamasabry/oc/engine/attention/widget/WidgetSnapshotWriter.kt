package io.github.eslamasabry.oc.engine.attention.widget

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock

/** The launcher side: redraw the widgets, and clear one profile's native copy. */
public interface WidgetHost {
    public suspend fun refresh()

    /** Null: the host has no native copy to speak of. False: an explicit refusal. A throw is a failure too. */
    public suspend fun clearProfile(profileId: String): Boolean?
}

/** What [WidgetSnapshotWriter.clearForProfile] actually did: a deletion flow must tell the person the difference. */
public enum class WidgetSnapshotClear { NothingToClear, Cleared, Failed }

/** One session of the selected connection. */
public data class WidgetSessionRow(val id: String, val title: String?, val updatedAt: Long?, val createdAt: Long = 0)

/** A conversation of another project or connection: a plain row, no preview, no inline actions, no paths. */
public data class WidgetOtherConversation(
    val id: String,
    val title: String,
    val project: String,
    val updatedAt: Long,
    val busy: Boolean,
    val needsYou: Boolean,
    /** The connection's display name, never its address. */
    val server: String = "",
)

/** The explicit tap target: an opaque fingerprint, plus the private location kept in the app-only target key. */
public data class WidgetOnDemandTarget(val fingerprint: String, val directory: String? = null, val workspace: String? = null)

/**
 * Persists a compact recent-sessions snapshot for the home-screen widget (port of `WidgetSessionSnapshot`,
 * B-SEC-181/182). Titles, the project display name and a short redacted assistant excerpt only; user prompts, tool
 * input, reasoning, raw routing and errors never cross. Routing (directory, workspace) stays in the app-private
 * `oc.widgetTarget.<profileId>` key; the launcher snapshot carries only an opaque scope token and fingerprint. Writes
 * are serialized; a profile clear drains queued writes first.
 */
public class WidgetSnapshotWriter(
    private val store: AttentionKeyStore,
    private val host: WidgetHost,
    redactor: Redactor,
    private val clock: Clock,
    private val isAndroid: Boolean = true,
) {
    private val text = WidgetText(redactor)
    private val lane = Mutex()
    private var lastWritten: String? = null

    /** A native service pause invalidated its disk copy: the next publication restores the whole current truth. */
    public fun invalidateNativeCopy() {
        lastWritten = null
    }

    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    public suspend fun update(
        sessions: List<WidgetSessionRow>,
        busySessions: Set<String>,
        connected: Boolean,
        /** The app-language "New conversation": the widget has no widget tree, the caller supplies the words. */
        untitled: String,
        profileId: String = "",
        scopeToken: String = "",
        pendingCount: Int = 0,
        details: Map<String, Map<String, Any?>> = emptyMap(),
        others: List<WidgetOtherConversation> = emptyList(),
        onDemand: WidgetOnDemandTarget? = null,
        preserveForOnDemand: Boolean = false,
        server: String = "",
    ) {
        if (!isAndroid) return
        fun rankOf(id: String) = if (details[id]?.get("requestID") != null) 0 else if (id in busySessions) 1 else 2
        data class Candidate(val rank: Int, val time: Long, val index: Int, val row: JsonObject)
        val candidates = mutableListOf<Candidate>()
        sessions.forEachIndexed { index, s ->
            val updated = s.updatedAt ?: s.createdAt
            candidates +=
                Candidate(
                    rankOf(s.id), updated, index,
                    buildJsonObject {
                        put("id", s.id)
                        put("title", text.sessionTitle(s.title, untitled))
                        put("busy", s.id in busySessions)
                        put("updatedAt", updated)
                        text.title(server).takeIf { it.isNotEmpty() }?.let { put("server", it) }
                        for ((k, v) in safeDetails(details[s.id])) put(k, v)
                    },
                )
        }
        others.forEachIndexed { index, o ->
            candidates +=
                Candidate(
                    if (o.needsYou) 0 else if (o.busy) 1 else 2, o.updatedAt, sessions.size + index,
                    buildJsonObject {
                        put("id", o.id)
                        put("title", text.sessionTitle(o.title, untitled))
                        put("busy", o.busy)
                        put("updatedAt", o.updatedAt)
                        text.title(o.server).takeIf { it.isNotEmpty() }?.let { put("server", it) }
                        // Waiting on the person, answerable only in the app: no request identity crosses.
                        if (o.needsYou) put("needsYou", true)
                        for ((k, v) in safeDetails(mapOf("project" to o.project))) put(k, v)
                    },
                )
        }
        val sorted =
            candidates.sortedWith { a, b ->
                val byRank = a.rank.compareTo(b.rank)
                if (byRank != 0) return@sortedWith byRank
                if (others.isNotEmpty()) {
                    val byTime = b.time.compareTo(a.time)
                    if (byTime != 0) return@sortedWith byTime
                }
                a.index.compareTo(b.index)
            }
        val payload =
            buildJsonObject {
                put("version", 2)
                onDemand?.let { put("onDemand", buildJsonObject { put("fingerprint", it.fingerprint) }) }
                put("scopeToken", scopeToken)
                put("runningCount", busySessions.size + others.count { it.busy })
                put("pendingCount", pendingCount + others.count { it.needsYou })
                put("connected", connected)
                put("profileID", profileId)
                putJsonArray("sessions") { sorted.take(MAX_SESSIONS).forEach { add(it.row) } }
            }.toString()
        lane.withLock {
            if (preserveForOnDemand) {
                suspend(profileId)
                return@withLock
            }
            // Routing first: a mismatched scope makes a partially failed write unusable, never a default folder.
            if (!saveTarget(profileId, scopeToken, onDemand)) return@withLock
            val stored = read(SNAPSHOT_KEY)
            if (payload == lastWritten && stored == payload) return@withLock
            if (!write(SNAPSHOT_KEY, payload)) return@withLock
            lastWritten = payload
            refresh()
        }
    }

    /**
     * Drops the snapshot when it belongs to [profileId] (or has no identifiable owner) and its private target, after
     * the queued writes. Native clear is asked even with no cached snapshot; an explicit refusal is a failure and the
     * stored snapshot is kept.
     */
    public suspend fun clearForProfile(profileId: String): WidgetSnapshotClear =
        lane.withLock {
            if (isAndroid) {
                when (val answer = attempt({ Failure.Device() }) { host.clearProfile(profileId) }) {
                    is Outcome.Failed -> return@withLock WidgetSnapshotClear.Failed
                    is Outcome.Ok -> if (answer.value == false) return@withLock WidgetSnapshotClear.Failed
                }
            }
            val key = targetKey(profileId)
            val hadTarget = read(key) != null
            if (hadTarget && !remove(key)) return@withLock WidgetSnapshotClear.Failed
            val raw = read(SNAPSHOT_KEY) ?: return@withLock if (hadTarget) WidgetSnapshotClear.Cleared else WidgetSnapshotClear.NothingToClear
            // Unreadable: it cannot be shown to be another profile's, so it is removable.
            val owner = (parse(raw) as? JsonObject)?.let { (it["profileID"] as? JsonPrimitive)?.contentOrNull ?: "" } ?: ""
            if (owner.isNotEmpty() && owner != profileId) {
                return@withLock if (hadTarget) WidgetSnapshotClear.Cleared else WidgetSnapshotClear.NothingToClear
            }
            if (!remove(SNAPSHOT_KEY)) return@withLock WidgetSnapshotClear.Failed
            lastWritten = null
            if (isAndroid) refresh()
            WidgetSnapshotClear.Cleared
        }

    /** Lifecycle suspension: keep the tap target, mark offline, and migrate a legacy raw route out of the snapshot. */
    private suspend fun suspend(profileId: String) {
        val raw = read(SNAPSHOT_KEY) ?: return
        val old = parse(raw) as? JsonObject ?: return
        if ((old["profileID"] as? JsonPrimitive)?.contentOrNull != profileId) return
        val oldTarget = old["onDemand"] as? JsonObject
        if (oldTarget != null && ("directory" in oldTarget || "workspace" in oldTarget)) {
            val target =
                (oldTarget["fingerprint"] as? JsonPrimitive)?.contentOrNull?.let {
                    WidgetOnDemandTarget(it, oldTarget.string("directory"), oldTarget.string("workspace"))
                }
            if (!saveTarget(profileId, old.string("scopeToken").orEmpty(), target)) return
        }
        val opaque = (oldTarget?.get("fingerprint") as? JsonPrimitive)?.let { buildJsonObject { put("fingerprint", it) } } ?: JsonNull
        val paused =
            JsonObject(
                old.toMutableMap().apply {
                    put("onDemand", if (oldTarget == null) JsonNull else opaque)
                    put("connected", JsonPrimitive(false))
                    put("pausedAt", JsonPrimitive(clock.now().toEpochMilliseconds()))
                },
            ).toString()
        if (paused != raw && write(SNAPSHOT_KEY, paused)) {
            lastWritten = paused
            refresh()
        }
    }

    private suspend fun saveTarget(profileId: String, scopeToken: String, target: WidgetOnDemandTarget?): Boolean {
        if (profileId.isEmpty()) return target == null
        val key = targetKey(profileId)
        if (target == null || scopeToken.isEmpty()) return read(key) == null || remove(key)
        val encoded =
            buildJsonObject {
                put("profileID", profileId)
                put("scopeToken", scopeToken)
                put("fingerprint", target.fingerprint)
                put("directory", target.directory)
                put("workspace", target.workspace)
            }.toString()
        return read(key) == encoded || write(key, encoded)
    }

    /** Only reviewed compact fields cross into launcher storage; identity and raw fields have no slot. */
    @Suppress("CyclomaticComplexMethod")
    private fun safeDetails(details: Map<String, Any?>?): Map<String, JsonElement> {
        if (details == null) return emptyMap()
        val out = LinkedHashMap<String, JsonElement>()
        out["onDemandActions"] =
            buildJsonArray { (details["onDemandActions"] as? List<*>)?.filter { it in ON_DEMAND_ACTIONS }?.forEach { add(JsonPrimitive(it as String)) } }
        (details["runMarker"] as? String)?.takeIf { it.isNotEmpty() && it.length <= 512 }?.let { out["runMarker"] = JsonPrimitive(it) }
        (details["startedAt"] as? Number)?.toLong()?.let { out["startedAt"] = JsonPrimitive(if (it < 0) 0 else it) }
        (details["step"] as? String)?.let { out["step"] = JsonPrimitive(text.title(it)) }
        (details["project"] as? String)?.let { out["project"] = JsonPrimitive(text.project(it)) }
        (details["preview"] as? String)?.let { out["preview"] = JsonPrimitive(text.preview(it)) }
        (details["model"] as? String)?.let { out["model"] = JsonPrimitive(text.title(it)) }
        (details["requestID"] as? String)?.takeIf { it.isNotEmpty() && it.length <= 512 }?.let { out["requestID"] = JsonPrimitive(it) }
        if (details["needsYou"] == true) out["needsYou"] = JsonPrimitive(true)
        out["steerMode"] = JsonPrimitive((details["steerMode"] as? String)?.takeIf { it in STEER_MODES } ?: "unknown")
        out["todos"] =
            buildJsonArray {
                (details["todos"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.take(5)?.forEach { task ->
                    val title = task["title"] as? String
                    val done = task["done"] as? Boolean
                    if (title != null && done != null) add(buildJsonObject { put("title", text.title(title)); put("done", done) })
                }
            }
        return out
    }

    private suspend fun read(key: String): String? = (attempt({ Failure.StorageRefused() }) { store.read(key) } as? Outcome.Ok)?.value

    private suspend fun write(key: String, value: String): Boolean =
        (attempt({ Failure.StorageRefused() }) { store.write(key, value) } as? Outcome.Ok)?.value == true

    private suspend fun remove(key: String): Boolean = (attempt({ Failure.StorageRefused() }) { store.remove(key) } as? Outcome.Ok)?.value == true

    private suspend fun refresh() {
        // No host (tests, desktop, activity gone): the widget keeps its last drawn state.
        attempt({ Failure.Device() }) { host.refresh() }
    }

    public companion object {
        /** Read by the native widget. */
        public const val SNAPSHOT_KEY: String = "oc.widgetSessions"
        public const val MAX_SESSIONS: Int = 12
        private val ON_DEMAND_ACTIONS = setOf("allowOnce", "reject", "stop")
        private val STEER_MODES = setOf("steer", "afterReply", "restart", "unknown")

        /** App-private connection routing, never part of the launcher snapshot (`oc.widgetTarget.<profileId>`). */
        public fun targetKey(profileId: String): String = "oc.widgetTarget.$profileId"

        private fun parse(raw: String): JsonElement? =
            try {
                Json.parseToJsonElement(raw)
            } catch (e: IllegalArgumentException) {
                null
            }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    }
}
