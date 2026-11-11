package io.github.eslamasabry.oc.engine.team.fleet

import kotlin.time.Instant

/*
 * Gas City's wire shapes (Dart lib/orchestration/adapters/gascity/dto): only the fields the mappers read, with
 * Dart's lenient readers (json_read.dart). One malformed list entry never sinks the list.
 */

internal typealias Json = Map<String, Any?>

internal object J {
    fun map(value: Any?): Json =
        when (value) {
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
            else -> emptyMap()
        }

    fun field(json: Json, key: String): Json = map(json[key])

    fun hasMap(json: Json, key: String): Boolean = json[key] is Map<*, *>

    /** Dart `readString`: strings, numbers and booleans as text. */
    fun string(json: Json, key: String): String? =
        when (val v = json[key]) {
            is String -> v
            is Number, is Boolean -> if (v is Double && v == kotlin.math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
            else -> null
        }

    /** Dart `readText`: [string] that is not blank. */
    fun text(json: Json, key: String): String? = string(json, key)?.takeIf { it.isNotBlank() }

    fun int(json: Json, key: String): Int? = toInt(json[key])

    fun toInt(value: Any?): Int? =
        when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> if (value.isFinite()) value.toInt() else null
            is String -> value.trim().toIntOrNull()
            else -> null
        }

    fun long(json: Json, key: String): Long? =
        when (val v = json[key]) {
            is Long -> v
            is Int -> v.toLong()
            is Double -> if (v.isFinite()) v.toLong() else null
            is String -> v.trim().toLongOrNull()
            else -> null
        }

    fun double(json: Json, key: String): Double? =
        when (val v = json[key]) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }

    fun bool(json: Json, key: String): Boolean? =
        when (val v = json[key]) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String ->
                when (v.trim().lowercase()) {
                    "true", "yes", "1" -> true
                    "false", "no", "0", "" -> false
                    else -> null
                }
            else -> null
        }

    /** Dart `DateTime.tryParse` for the host's ISO-8601 times; a time without an offset is read as UTC. */
    fun timeMs(json: Json, key: String): Long? {
        val text = (json[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = Instant.parseOrNull(text) ?: Instant.parseOrNull(text + "Z")
        return parsed?.toEpochMilliseconds()
    }

    fun strings(json: Json, key: String): List<String> =
        (json[key] as? List<*>)?.mapNotNull {
            when (it) {
                is String -> it
                is Number, is Boolean -> it.toString()
                else -> null
            }
        } ?: emptyList()

    fun stringMap(json: Json, key: String): Map<String, String> =
        (json[key] as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> v?.let { k.toString() to (it as? String ?: it.toString()) } }?.toMap()
            ?: emptyMap()

    fun <T> list(json: Json, key: String, decode: (Json) -> T): List<T> =
        (json[key] as? List<*>)?.mapNotNull { item ->
            if (item !is Map<*, *>) null else runCatching { decode(map(item)) }.getOrNull()
        } ?: emptyList()

    /** A list answer under [key]; a bare JSON array arrives under `items` (Dart `getJson`). */
    fun <T> items(json: Json, decode: (Json) -> T, key: String = "items"): List<T> = list(json, key, decode)
}

internal data class WireDependency(val issueId: String, val dependsOnId: String, val type: String?)

internal data class WireBead(
    val id: String,
    val title: String,
    val status: String?,
    val issueType: String?,
    val description: String?,
    val assignee: String?,
    val parent: String?,
    val isBlocked: Boolean,
    val labels: List<String>,
    val dependencies: List<WireDependency>,
    val metadata: Map<String, String>,
    val createdAtMs: Long?,
    val updatedAtMs: Long?,
) {
    fun meta(key: String): String? = metadata[key]?.takeIf { it.isNotBlank() }

    val routedTo: String? get() = meta("gc.routed_to")
    val sessionId: String? get() = meta("gc.session_id")
    val sessionName: String? get() = meta("gc.session_name")
    val closedReason: String? get() = meta("closed_reason") ?: meta("close_reason")
    val lastError: String? get() = meta("last_error")
    val isClosed: Boolean get() = status?.lowercase() == "closed"
    val isConvoy: Boolean get() = issueType == "convoy"
    val isGate: Boolean get() = issueType == "gate" || meta("gc.kind") == "gate" || "gate" in labels || "gc:gate" in labels
    val needsReview: Boolean get() = "needs-review" in labels
    val dependsOn: List<String>
        get() = dependencies.filter { (it.issueId.isEmpty() || it.issueId == id) && it.dependsOnId.isNotEmpty() }.map { it.dependsOnId }

    /** Dart `isInternalBead`: the host's own bookkeeping (sessions, convoys, mail, molecules, nudges). */
    val internal: Boolean
        get() = issueType in setOf("session", "convoy", "message", "molecule") || "gc:session" in labels || "gc:nudge" in labels

    companion object {
        fun parse(json: Json): WireBead =
            WireBead(
                id = J.text(json, "id") ?: "",
                title = J.string(json, "title") ?: "",
                status = J.text(json, "status"),
                issueType = J.text(json, "issue_type"),
                description = J.text(json, "description"),
                assignee = J.text(json, "assignee"),
                parent = J.text(json, "parent"),
                isBlocked = J.bool(json, "is_blocked") ?: false,
                labels = J.strings(json, "labels"),
                dependencies =
                    J.list(json, "dependencies") {
                        WireDependency(J.text(it, "issue_id") ?: "", J.text(it, "depends_on_id") ?: "", J.text(it, "type"))
                    },
                metadata = J.stringMap(json, "metadata"),
                createdAtMs = J.timeMs(json, "created_at"),
                updatedAtMs = J.timeMs(json, "updated_at"),
            )
    }
}

internal data class WireConvoy(val bead: WireBead, val children: List<WireBead>, val progressClosed: Int?, val progressTotal: Int?) {
    val id: String get() = bead.id

    val trackedIds: List<String>
        get() = bead.dependencies.filter { (it.type == "tracks" || it.type == null) && it.dependsOnId.isNotEmpty() }.map { it.dependsOnId }

    companion object {
        fun parse(json: Json): WireConvoy {
            if (J.hasMap(json, "convoy")) {
                val progress = J.field(json, "progress")
                return WireConvoy(
                    WireBead.parse(J.field(json, "convoy")),
                    J.list(json, "children", { WireBead.parse(it) }),
                    J.int(progress, "closed"),
                    J.int(progress, "total"),
                )
            }
            return WireConvoy(WireBead.parse(json), emptyList(), null, null)
        }
    }
}

internal data class WireRun(
    val runId: String,
    val title: String,
    val status: String?,
    val formula: String?,
    val scopeKind: String?,
    val scopeRef: String?,
    val target: String?,
    val lastError: String?,
    val startedAtMs: Long?,
    val updatedAtMs: Long?,
    val tracksWork: Boolean,
) {
    companion object {
        fun parse(json: Json): WireRun {
            val scope = J.field(json, "scope")
            val error =
                when (val e = json["last_error"]) {
                    is Map<*, *> -> J.map(e).let { J.text(it, "message") ?: J.text(it, "code") }
                    is String -> e.takeIf { it.isNotBlank() }
                    else -> null
                }
            // Dart `_runTracksWork`: a payload that names work it tracks.
            val tracks =
                listOf("bead", "bead_id", "work_id", "tracks", "beads").any { key ->
                    when (val v = json[key]) {
                        is String -> v.isNotBlank()
                        is Collection<*> -> v.isNotEmpty()
                        is Map<*, *> -> v.isNotEmpty()
                        else -> false
                    }
                }
            return WireRun(
                runId = J.text(json, "run_id") ?: J.text(json, "id") ?: "",
                title = J.string(json, "title") ?: "",
                status = J.text(json, "status"),
                formula = J.text(json, "formula"),
                scopeKind = J.text(scope, "kind"),
                scopeRef = J.text(scope, "ref"),
                target = J.text(json, "target"),
                lastError = error,
                startedAtMs = J.timeMs(json, "started_at"),
                updatedAtMs = J.timeMs(json, "updated_at"),
                tracksWork = tracks,
            )
        }
    }
}

internal data class WireAgent(
    val name: String,
    val state: String?,
    val running: Boolean,
    val suspended: Boolean,
    val available: Boolean,
    val unavailableReason: String?,
    val displayName: String?,
    val provider: String?,
    val model: String?,
    val pack: String?,
    val pool: String?,
    val sessionName: String?,
    val sessionLastActivityMs: Long?,
    val hasSession: Boolean,
    val activeBead: String?,
    val contextPct: Int?,
) {
    companion object {
        fun parse(json: Json): WireAgent {
            val session = if (J.hasMap(json, "session")) J.field(json, "session") else null
            return WireAgent(
                name = J.text(json, "name") ?: "",
                state = J.text(json, "state"),
                running = J.bool(json, "running") ?: false,
                suspended = J.bool(json, "suspended") ?: false,
                available = J.bool(json, "available") ?: true,
                unavailableReason = J.text(json, "unavailable_reason"),
                displayName = J.text(json, "display_name"),
                provider = J.text(json, "provider"),
                model = J.text(json, "model"),
                pack = J.text(json, "pack"),
                pool = J.text(json, "pool"),
                sessionName = session?.let { J.text(it, "name") },
                sessionLastActivityMs = session?.let { J.timeMs(it, "last_activity") },
                hasSession = session != null,
                activeBead = J.text(json, "active_bead"),
                contextPct = J.int(json, "context_pct"),
            )
        }
    }
}

internal data class WireSession(
    val id: String,
    val template: String?,
    val state: String?,
    val title: String?,
    val alias: String?,
    val provider: String?,
    val displayName: String?,
    val model: String?,
    val sessionName: String?,
    val workDir: String?,
    val createdAtMs: Long?,
    val lastActiveMs: Long?,
    val pool: String?,
    val agentKind: String?,
    val running: Boolean,
    val activeBead: String?,
    val contextPct: Int?,
    val metadata: Map<String, String>,
) {
    val isPoolInstance: Boolean get() = agentKind == "pool" || pool != null

    companion object {
        fun parse(json: Json): WireSession =
            WireSession(
                id = J.text(json, "id") ?: "",
                template = J.text(json, "template"),
                state = J.text(json, "state"),
                title = J.text(json, "title"),
                alias = J.text(json, "alias"),
                provider = J.text(json, "provider"),
                displayName = J.text(json, "display_name"),
                model = J.text(json, "model"),
                sessionName = J.text(json, "session_name"),
                workDir = J.text(json, "work_dir"),
                createdAtMs = J.timeMs(json, "created_at"),
                lastActiveMs = J.timeMs(json, "last_active"),
                pool = J.text(json, "pool"),
                agentKind = J.text(json, "agent_kind"),
                running = J.bool(json, "running") ?: false,
                activeBead = J.text(json, "active_bead"),
                contextPct = J.int(json, "context_pct"),
                metadata = J.stringMap(json, "metadata"),
            )
    }
}

internal data class WirePending(
    val requestId: String,
    val kind: String?,
    val sessionId: String?,
    val prompt: String?,
    val options: List<String>,
    val createdAtMs: Long?,
) {
    companion object {
        fun parse(json: Json): WirePending {
            val options =
                (json["options"] as? List<*>)?.mapNotNull { option ->
                    when (option) {
                        is String -> option
                        is Map<*, *> -> J.map(option).let { J.text(it, "label") ?: J.text(it, "title") ?: J.text(it, "value") ?: J.text(it, "id") }
                        else -> null
                    }
                } ?: emptyList()
            return WirePending(
                requestId = J.text(json, "request_id") ?: J.text(json, "id") ?: "",
                kind = J.text(json, "kind"),
                sessionId = J.text(json, "session_id"),
                prompt = J.text(json, "prompt") ?: J.text(json, "question"),
                options = options,
                createdAtMs = J.timeMs(json, "created_at"),
            )
        }
    }
}

internal data class WireWait(val sessionId: String?, val sessionName: String?, val state: String?) {
    val active: Boolean
        get() = (state ?: "").lowercase().let { it.isEmpty() || it == "waiting" || it == "active" || it == "pending" }

    companion object {
        fun parse(json: Json): WireWait =
            WireWait(J.text(json, "session_id"), J.text(json, "session_name"), J.text(json, "state") ?: J.text(json, "status"))
    }
}

internal data class WireRig(val name: String, val path: String?, val suspended: Boolean)

internal data class WireStatus(
    val name: String?,
    val version: String?,
    val running: Int?,
    val agentsRunning: Int?,
    val workOpen: Int?,
    val workReady: Int?,
    val workInProgress: Int?,
    val rigs: List<WireRig>,
) {
    companion object {
        fun parse(json: Json): WireStatus {
            val agents = J.field(json, "agents")
            val work = J.field(json, "work")
            return WireStatus(
                name = J.text(json, "name"),
                version = J.text(json, "version"),
                running = J.int(json, "running"),
                agentsRunning = J.int(agents, "running"),
                workOpen = J.int(work, "open"),
                workReady = J.int(work, "ready"),
                workInProgress = J.int(work, "in_progress"),
                rigs = J.list(json, "rig_details") { WireRig(J.text(it, "name") ?: "", J.text(it, "path"), J.bool(it, "suspended") ?: false) },
            )
        }
    }
}

internal data class WireHealth(val status: String?, val version: String?, val city: String?) {
    /** Dart `_looksLikeHealth`: a status and a version or a city. */
    val looksLikeGasCity: Boolean
        get() = status != null && (version != null || city != null)

    companion object {
        fun parse(json: Json): WireHealth = WireHealth(J.text(json, "status"), J.text(json, "version"), J.text(json, "city"))
    }
}

internal data class WireEvent(
    val type: String,
    val seq: Long?,
    val tsMs: Long?,
    val subject: String?,
    val runId: String?,
    val sessionId: String?,
    val payload: Json,
) {
    val family: String
        get() = type.substringBefore('.')

    companion object {
        fun parse(json: Json): WireEvent =
            WireEvent(
                type = J.text(json, "type") ?: "unknown",
                seq = J.long(json, "seq"),
                tsMs = J.timeMs(json, "ts") ?: J.timeMs(json, "timestamp"),
                subject = J.text(json, "subject"),
                runId = J.text(json, "run_id"),
                sessionId = J.text(json, "session_id"),
                payload = J.field(json, "payload"),
            )
    }
}

internal data class WireOrder(
    val name: String,
    val scopedName: String?,
    val enabled: Boolean,
    val trigger: String?,
    val rig: String?,
    val schedule: String?,
    val interval: String?,
    val on: String?,
    val description: String?,
) {
    companion object {
        fun parse(json: Json): WireOrder =
            WireOrder(
                name = J.text(json, "name") ?: "",
                scopedName = J.text(json, "scoped_name"),
                enabled = J.bool(json, "enabled") ?: true,
                trigger = J.text(json, "trigger") ?: J.text(json, "gate"),
                rig = J.text(json, "rig"),
                schedule = J.text(json, "schedule"),
                interval = J.text(json, "interval"),
                on = J.text(json, "on"),
                description = J.text(json, "description"),
            )
    }
}
