package io.github.eslamasabry.oc.android.widgets.snapshot

import io.github.eslamasabry.oc.android.widgets.PublishResult
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One conversation of the snapshot's own connection: its rich details come from [WidgetSnapshotInput.details]. */
public data class WidgetSession(val id: String, val title: String?, val updatedAt: Long)

/**
 * A conversation of another project or connection (Flutter `WidgetOtherConversation`): a plain row with no preview,
 * no inline actions and no paths. [server] is the connection's display name, never its address. [profileId] is the
 * native addition the renderer uses to open that server's conversation.
 */
public data class WidgetOtherConversation(
    val id: String,
    val title: String?,
    val project: String,
    val updatedAt: Long,
    val busy: Boolean,
    val needsYou: Boolean,
    val server: String = "",
    val profileId: String = "",
)

/** A Status widget line: a server's name, its state word and, when it does not answer, since when. */
public data class WidgetServerLine(
    val name: String,
    /** "online", "connecting" or "offline"; anything else reads offline. */
    val state: String,
    val since: Long? = null,
    val phone: Boolean = false,
)

public data class WidgetTodo(val title: String, val done: Boolean)

/**
 * The reviewed compact fields of one row (Flutter `_safeWidgetDetails`). Prompt text, tool input and output,
 * reasoning, raw routing and errors have no field here, so they cannot reach launcher storage.
 */
public data class WidgetRowDetails(
    val project: String? = null,
    val preview: String? = null,
    /** The model's display name; never a raw id (callers pass null when no name is known). */
    val model: String? = null,
    val step: String? = null,
    val startedAt: Long? = null,
    val requestId: String? = null,
    /** A permission ask as built by [WidgetText.askFor], or a question `{kind: other, q}`; extra keys are dropped. */
    val ask: Map<String, Any?>? = null,
    /** A question or form waits on the person; the app answers it, so the row carries no request identity. */
    val needsYou: Boolean = false,
    val onDemandActions: List<String> = emptyList(),
    val runMarker: String? = null,
    val steerMode: String? = null,
    val todos: List<WidgetTodo> = emptyList(),
    val tasksDone: Int? = null,
    val tasksTotal: Int? = null,
)

/** Everything one snapshot is built from (the arguments of Flutter `WidgetSessionSnapshot.update`). */
public data class WidgetSnapshotInput(
    val sessions: List<WidgetSession> = emptyList(),
    val busy: Set<String> = emptySet(),
    val connected: Boolean = false,
    val profileId: String = "",
    /** The caller's words for an untitled conversation; [WidgetSnapshotWords.newConversation] when null. */
    val untitledLabel: String? = null,
    val scopeToken: String = "",
    val lastProjectId: String = "",
    val pendingCount: Int = 0,
    val details: Map<String, WidgetRowDetails> = emptyMap(),
    val others: List<WidgetOtherConversation> = emptyList(),
    val appTheme: Map<String, Int> = emptyMap(),
    /** Opaque tap-target fingerprint; directories and workspaces stay in the private target key. */
    val onDemandFingerprint: String? = null,
    /** This connection's display name ("This phone", "MacBook"), never an address. */
    val server: String = "",
    val servers: List<WidgetServerLine> = emptyList(),
    val projects: List<String> = emptyList(),
)

/**
 * Builds the launcher snapshot `oc.widgetSessions` (Flutter `WidgetSessionSnapshot.update`, version 2). Pure: the
 * same input gives an equal [JsonObject], which is what lets [WidgetSnapshotPublisher] skip unchanged payloads.
 *
 * Rows are ranked needs-you, running, the rest; with other conversations present each rank is newest first, else the
 * caller's order holds. So the four rows a small widget shows keep what waits on the person and what runs.
 */
public class WidgetSnapshotBuilder(redactor: Redactor, private val words: WidgetSnapshotWords) {
    private val text = WidgetText(redactor)

    public fun build(input: WidgetSnapshotInput): JsonObject {
        val fallback = input.untitledLabel ?: words.newConversation
        fun title(raw: String?) = text.title(WidgetTitles.present(raw, fallback, words.newConversation))
        val server = text.title(input.server)
        fun rank(id: String) =
            when {
                input.details[id]?.requestId != null -> 0
                id in input.busy -> 1
                else -> 2
            }
        data class Candidate(val rank: Int, val time: Long, val index: Int, val row: JsonObject)
        val candidates =
            input.sessions.mapIndexed { index, session ->
                Candidate(
                    rank(session.id),
                    session.updatedAt,
                    index,
                    buildJsonObject {
                        put("id", session.id)
                        put("title", title(session.title))
                        put("busy", session.id in input.busy)
                        put("updatedAt", session.updatedAt)
                        if (server.isNotEmpty()) put("server", server)
                        input.details[session.id]?.let { details -> for ((k, v) in safeDetails(details)) put(k, v) }
                    },
                )
            } +
                input.others.mapIndexed { index, other ->
                    Candidate(
                        if (other.needsYou) 0 else if (other.busy) 1 else 2,
                        other.updatedAt,
                        input.sessions.size + index,
                        buildJsonObject {
                            put("id", other.id)
                            put("title", title(other.title))
                            put("busy", other.busy)
                            put("updatedAt", other.updatedAt)
                            text.title(other.server).takeIf { it.isNotEmpty() }?.let { put("server", it) }
                            if (other.profileId.isNotBlank()) put("profileID", other.profileId)
                            // Waiting on the person, answerable only in the app: no request identity crosses.
                            if (other.needsYou) put("needsYou", true)
                            for ((k, v) in safeDetails(WidgetRowDetails(project = other.project))) put(k, v)
                        },
                    )
                }
        val sorted =
            candidates.sortedWith { a, b ->
                val byRank = a.rank.compareTo(b.rank)
                if (byRank != 0) return@sortedWith byRank
                if (input.others.isNotEmpty()) {
                    val byTime = b.time.compareTo(a.time)
                    if (byTime != 0) return@sortedWith byTime
                }
                a.index.compareTo(b.index)
            }
        return buildJsonObject {
            put("version", 2)
            input.onDemandFingerprint?.let { put("onDemand", buildJsonObject { put("fingerprint", it) }) }
            put("appTheme", JsonObject(input.appTheme.mapValues { JsonPrimitive(it.value) }))
            put("scopeToken", input.scopeToken)
            put("lastProjectID", input.lastProjectId)
            put("runningCount", input.busy.size + input.others.count { it.busy })
            put("pendingCount", input.pendingCount + input.others.count { it.needsYou })
            put("connected", input.connected)
            put("profileID", input.profileId)
            put(
                "servers",
                buildJsonArray {
                    for (line in input.servers.take(MAX_SERVERS)) {
                        add(
                            buildJsonObject {
                                put("name", text.title(line.name))
                                put("state", if (line.state in SERVER_STATES) line.state else "offline")
                                line.since?.takeIf { it > 0 }?.let { put("since", it) }
                                if (line.phone) put("phone", true)
                            },
                        )
                    }
                },
            )
            put(
                "projects",
                JsonArray(
                    input.projects.map(text::project).distinct().filter { it.isNotEmpty() }.take(MAX_PROJECTS).map(::JsonPrimitive),
                ),
            )
            put("sessions", JsonArray(sorted.take(MAX_SESSIONS).map { it.row }))
        }
    }

    private fun safeDetails(d: WidgetRowDetails): Map<String, JsonElement> {
        val out = LinkedHashMap<String, JsonElement>()
        out["onDemandActions"] = JsonArray(d.onDemandActions.filter { it in ON_DEMAND }.map(::JsonPrimitive))
        d.runMarker?.takeIf { it.isNotEmpty() && it.length <= MAX_ID }?.let { out["runMarker"] = JsonPrimitive(it) }
        d.startedAt?.let { out["startedAt"] = JsonPrimitive(it.coerceAtLeast(0)) }
        d.step?.let { out["step"] = JsonPrimitive(text.title(it)) }
        d.project?.let { out["project"] = JsonPrimitive(text.project(it)) }
        d.preview?.let { out["preview"] = JsonPrimitive(text.preview(it)) }
        d.model?.let { out["model"] = JsonPrimitive(text.title(it)) }
        d.requestId?.takeIf { it.isNotEmpty() && it.length <= MAX_ID }?.let { out["requestID"] = JsonPrimitive(it) }
        d.ask?.let { ask -> out["ask"] = JsonObject(text.safeAsk(ask).mapValues { JsonPrimitive(it.value) }) }
        if (d.needsYou) out["needsYou"] = JsonPrimitive(true)
        out["steerMode"] = JsonPrimitive(d.steerMode?.takeIf { it in STEER_MODES } ?: "unknown")
        out["todos"] =
            JsonArray(
                d.todos.take(MAX_TODOS).map { todo ->
                    buildJsonObject {
                        put("title", text.title(todo.title))
                        put("done", todo.done)
                    }
                },
            )
        d.tasksDone?.let { out["tasksDone"] = JsonPrimitive(it.coerceIn(0, MAX_TASKS)) }
        d.tasksTotal?.let { out["tasksTotal"] = JsonPrimitive(it.coerceIn(0, MAX_TASKS)) }
        return out
    }

    public companion object {
        /** Enough rows that a Conversations widget scoped to one project still has some. */
        public const val MAX_SESSIONS: Int = 12
        public const val MAX_PROJECTS: Int = 12
        public const val MAX_SERVERS: Int = 6
        private const val MAX_ID = 512
        private const val MAX_TODOS = 5
        private const val MAX_TASKS = 100_000
        private val SERVER_STATES = setOf("online", "connecting", "offline")
        private val ON_DEMAND = setOf("allowOnce", "reject", "stop")
        private val STEER_MODES = setOf("steer", "afterReply", "restart", "unknown")
    }
}

/**
 * Writes a built snapshot only when its content changed (B-ANDR-117): equal content is neither rewritten nor
 * redrawn. What is written also carries `lastSeenAt` (when the app last looked, read by the offline header), which
 * is left out of the comparison so a clock tick alone never redraws. A platform without home widgets writes nothing.
 * Callers serialize publishes (one feed collector); [write] is [io.github.eslamasabry.oc.android.widgets.AndroidWidgetSurface.publish].
 */
public class WidgetSnapshotPublisher(
    private val capabilities: PlatformCapabilities,
    private val write: suspend (snapshotJson: String, profileId: String) -> PublishResult,
    private val now: () -> Long,
) {
    private var last: Pair<String, JsonObject>? = null

    public suspend fun publish(snapshot: JsonObject, profileId: String): PublishResult {
        if (!capabilities.homeWidgets) return PublishResult.Unchanged
        if (last == (profileId to snapshot)) return PublishResult.Unchanged
        val stamped = JsonObject(snapshot + ("lastSeenAt" to JsonPrimitive(now())))
        val result = write(stamped.toString(), profileId)
        last = if (result == PublishResult.Refused) null else profileId to snapshot
        return result
    }

    /** The native copy was changed elsewhere (paused, cleared): the next publish writes the full current truth. */
    public fun invalidate() {
        last = null
    }
}

/**
 * The live details of one conversation row (Flutter `_widgetPreview`, `_widgetSessionDetails`): while it runs a tool,
 * the generic step for that tool (never its input); otherwise the latest assistant prose. The model shows by its
 * display name only. No navigation happens here: publishing a preview never opens the app.
 */
public class WidgetLiveDetails(private val text: WidgetText, private val words: WidgetSnapshotWords) {
    public fun details(
        messages: List<MessageWithParts>,
        busy: Boolean,
        directory: String?,
        startedAt: Long = 0,
        modelName: String? = null,
    ): WidgetRowDetails {
        val step = if (busy) runningTool(messages)?.let { WidgetToolStep.sentence(it, words) }.orEmpty() else ""
        val preview = if (busy && step.isNotEmpty()) step else text.assistantPreview(messages)
        return WidgetRowDetails(
            project = directory.orEmpty(),
            preview = preview,
            model = modelName?.trim()?.takeIf { it.isNotEmpty() },
            step = step,
            startedAt = if (busy) startedAt else 0,
        )
    }

    /** The name of the tool the latest assistant message is running, if any. */
    private fun runningTool(messages: List<MessageWithParts>): String? {
        val latest = messages.lastOrNull { it.info.role == Role.Assistant } ?: return null
        val part = latest.parts.lastOrNull { it.type == PartType.Tool && it.tool?.status == ToolStatus.Running } ?: return null
        return part.toolName.orEmpty()
    }
}
