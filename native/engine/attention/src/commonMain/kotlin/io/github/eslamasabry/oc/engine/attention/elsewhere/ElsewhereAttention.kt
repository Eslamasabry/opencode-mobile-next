package io.github.eslamasabry.oc.engine.attention.elsewhere

import io.github.eslamasabry.oc.engine.attention.feed.AttentionKind
import io.github.eslamasabry.oc.engine.attention.feed.AttentionObservation
import io.github.eslamasabry.oc.engine.attention.feed.AttentionPhase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/** One event from the server-wide channel, stamped with its folder (the attention view of `EventEnvelope`). */
public data class AttentionEvent(
    val type: String,
    val directory: String?,
    val properties: JsonObject,
    val workspace: String? = null,
)

/** What is going on in a project other than the selected one. */
public data class ProjectActivity(
    val directory: String,
    /** Conversations with a run in progress. */
    val running: ImmutableSet<String>,
    /** Conversations stopped on a person: a permission, question or form. */
    val waiting: ImmutableSet<String>,
) {
    val isEmpty: Boolean get() = running.isEmpty() && waiting.isEmpty()
}

/**
 * Keeps track of the server's OTHER projects from its server-wide event channel (port of `ElsewhereAttention`,
 * B-AGENT-077/078/079). It reads the few events that say "running", "needs you" and "done" and keeps a small tally per
 * project: ids and folders only, never request bodies or raw error text. Every project is tallied, the selected one
 * included; readers leave it out with `except`. Not thread-safe: feed it from the connection's one lane.
 */
public class ElsewhereAttention(private val clock: Clock) {
    private val runningIn = mutableMapOf<String, MutableSet<String>>()
    private val waitingRequests = LinkedHashMap<String, AttentionObservation>()
    private val failedRuns = LinkedHashMap<String, AttentionObservation>()
    private val revisions = MutableStateFlow(0)

    /** Bumps on every change (Flutter's notifyListeners). */
    public val revision: StateFlow<Int> = revisions.asStateFlow()

    /** Feeds the all-server projection: observed requests/failures, never proof other folders are empty. */
    public fun observations(except: String? = null): ImmutableList<AttentionObservation> =
        (waitingRequests.values + failedRuns.values).filter { it.directory != except }.toImmutableList()

    /** A lost/replaced stream cannot refresh its old observations: keep them, but only a new event refreshes a row. */
    public fun markStale() {
        var changed = false
        for (rows in listOf(waitingRequests, failedRuns)) {
            for ((key, value) in rows.entries.toList()) {
                if (!value.isFresh) continue
                rows[key] = value.copy(isFresh = false)
                changed = true
            }
        }
        if (changed) notifyChanged()
    }

    /** Every other project with something going on, the one that needs you first. */
    public fun activity(except: String? = null): ImmutableList<ProjectActivity> {
        val directories = (runningIn.keys + waitingRequests.values.mapNotNull { it.directory }).toMutableSet()
        directories.remove(except)
        return directories
            .map { directory ->
                ProjectActivity(
                    directory = directory,
                    running = (runningIn[directory] ?: emptySet()).toImmutableSet(),
                    waiting = waitingRequests.values.filter { it.directory == directory }.mapNotNull { it.sessionId }.toImmutableSet(),
                )
            }.filter { !it.isEmpty }
            .sortedWith(compareByDescending<ProjectActivity> { it.waiting.size }.thenByDescending { it.running.size })
            .toImmutableList()
    }

    /** Conversations in other projects that are stopped on a person. */
    public fun waitingCount(except: String? = null): Int =
        waitingRequests.values.filter { it.directory != except }.mapNotNull { it.sessionId }.toSet().size

    public fun forProject(directory: String): ProjectActivity? = activity().firstOrNull { it.directory == directory }

    public fun handle(event: AttentionEvent) {
        val directory = event.directory
        if (directory.isNullOrEmpty()) return
        // The AI Team's agents run and ask in their own folders; their work is on the AI Team screen.
        if (isAiTeamDirectory(directory)) return
        val props = event.properties
        val sessionId = props.text("sessionID")
        val changed: Boolean =
            when (event.type) {
                in ASKED -> {
                    observe(
                        id = props.text("id") ?: props.text("requestID"),
                        sessionId = sessionId,
                        kind = if (event.type.startsWith("permission.")) AttentionKind.Permission else AttentionKind.Question,
                        directory = directory,
                        workspace = event.workspace,
                    )
                }
                "form.v2.created" -> {
                    val form = props["form"] as? JsonObject
                    form != null &&
                        observe(form.text("id"), form.text("sessionID"), AttentionKind.Form, directory, event.workspace)
                }
                in SETTLED -> {
                    val id = props.text("requestID") ?: props.text("permissionID") ?: props.text("id")
                    val kind =
                        when {
                            event.type.startsWith("permission.") -> AttentionKind.Permission
                            event.type.startsWith("question.") -> AttentionKind.Question
                            else -> AttentionKind.Form
                        }
                    removeWhere(waitingRequests) {
                        it.requestId == id && it.kind == kind && it.directory == directory &&
                            it.workspace == event.workspace && (sessionId == null || it.sessionId == sessionId)
                    }
                }
                "session.status" -> {
                    if (sessionId.isNullOrEmpty()) {
                        false
                    } else {
                        val raw = props["status"]
                        val status = if (raw is JsonObject) raw.text("type") else raw.asText()
                        when (status) {
                            "idle" -> settle(directory, sessionId)
                            "busy", "retry" -> {
                                val cleared = clearFailure(directory, sessionId)
                                runningIn.getOrPut(directory) { mutableSetOf() }.add(sessionId) || cleared
                            }
                            else -> false
                        }
                    }
                }
                "session.idle" -> sessionId != null && settle(directory, sessionId)
                "session.error" -> {
                    if (sessionId.isNullOrEmpty()) {
                        false
                    } else if (sessionErrorIsStop(props["error"])) {
                        // A Stop ends the run; it is not a failure to look at (F3).
                        settle(directory, sessionId)
                    } else {
                        settle(directory, sessionId)
                        val failure =
                            AttentionObservation(
                                id = "session-failure:$sessionId",
                                kind = AttentionKind.FailedRun,
                                phase = AttentionPhase.Failed,
                                observedAt = clock.now(),
                                sessionId = sessionId,
                                directory = directory,
                                workspace = event.workspace,
                            )
                        failedRuns[failure.identity] = failure
                        true
                    }
                }
                "message.updated" -> {
                    val info = props["info"] as? JsonObject
                    if (info == null || info.text("role") != "user") {
                        false
                    } else {
                        val id = info.text("sessionID")
                        !id.isNullOrEmpty() && clearFailure(directory, id)
                    }
                }
                "session.deleted" -> {
                    val info = props["info"]
                    val id = if (info is JsonObject) info.text("id") else sessionId
                    if (id == null) {
                        false
                    } else {
                        val cleared = clearFailure(directory, id)
                        settle(directory, id) || cleared
                    }
                }
                else -> false
            }
        if (changed) notifyChanged()
    }

    /** A new server, or a lost connection: what was known may be stale. */
    public fun clear() {
        if (runningIn.isEmpty() && waitingRequests.isEmpty() && failedRuns.isEmpty()) return
        runningIn.clear()
        waitingRequests.clear()
        failedRuns.clear()
        notifyChanged()
    }

    private fun observe(id: String?, sessionId: String?, kind: AttentionKind, directory: String, workspace: String?): Boolean {
        if (id.isNullOrEmpty() || sessionId.isNullOrEmpty()) return false
        val observation =
            AttentionObservation(
                id = id,
                kind = kind,
                phase = AttentionPhase.NeedsYou,
                observedAt = clock.now(),
                sessionId = sessionId,
                requestId = id,
                directory = directory,
                workspace = workspace,
            )
        waitingRequests[observation.identity] = observation // a duplicate refreshes its one row in place
        return true
    }

    private fun settle(directory: String, sessionId: String): Boolean {
        val running = runningIn[directory]
        var changed = running?.remove(sessionId) ?: false
        if (running != null && running.isEmpty()) runningIn.remove(directory)
        // A run that ended is no longer waiting on anything.
        changed = removeWhere(waitingRequests) { it.sessionId == sessionId && it.directory == directory } || changed
        return changed
    }

    private fun clearFailure(directory: String, sessionId: String): Boolean =
        removeWhere(failedRuns) { it.directory == directory && it.sessionId == sessionId }

    private fun removeWhere(rows: MutableMap<String, AttentionObservation>, test: (AttentionObservation) -> Boolean): Boolean {
        val before = rows.size
        rows.entries.removeAll { test(it.value) }
        return before != rows.size
    }

    private fun notifyChanged() {
        revisions.value += 1
    }

    public companion object {
        private val ASKED =
            setOf("permission.asked", "permission.v2.asked", "permission.updated", "question.asked", "question.updated", "question.v2.asked")
        private val SETTLED =
            setOf(
                "permission.replied", "permission.v2.replied", "question.replied", "question.rejected",
                "question.v2.replied", "question.v2.rejected", "form.v2.replied", "form.v2.cancelled",
            )

        /** The in-app AI Team's home in the Linux container. */
        public const val AI_TEAM_HOME: String = "/root/aiteam"

        /** True when [directory] is one of the AI Team's own folders (Flutter `isAiTeamDirectory`). */
        public fun isAiTeamDirectory(directory: String?): Boolean {
            val segments = segments(directory)
            if (segments.isNullOrEmpty()) return false
            val home = segments(AI_TEAM_HOME)!!
            if (segments.size >= home.size && home.indices.all { segments[it] == home[it] }) return true
            for (i in segments.indices) {
                if (segments[i] == ".gc") return true
                if (segments[i] == ".oc" && i + 1 < segments.size && segments[i + 1] == "aiteam") return true
            }
            return false
        }

        /** A Stop ends the run (MessageErrorKind.aborted); it is not a failure. */
        public fun sessionErrorIsStop(error: JsonElement?): Boolean {
            if (error !is JsonObject) return false
            val name = (error.text("name") ?: error.text("type"))?.trim()
            if (name.isNullOrEmpty()) return false
            val key = name.lowercase().replace(Regex("[^a-z]"), "")
            if (key.contains("contextoverflow") || key.contains("contextlength")) return false
            if (key.contains("outputlength") || key == "length") return false
            if (key.contains("providerauth") || key.contains("needsauth") || key.contains("unauthorized") || key.contains("authentication")) return false
            if (key.contains("contentfilter")) return false
            return key.contains("abort")
        }

        private fun segments(directory: String?): List<String>? {
            val value = directory?.trim()?.replace('\\', '/')
            if (value.isNullOrEmpty()) return null
            val out = mutableListOf<String>()
            for (part in value.split('/')) {
                if (part.isEmpty() || part == ".") continue
                if (part == "..") {
                    if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                    continue
                }
                out += part
            }
            return out
        }

        internal fun JsonObject.text(key: String): String? = this[key].asText()

        internal fun JsonElement?.asText(): String? =
            when (this) {
                null, JsonNull -> null
                is JsonPrimitive -> content
                else -> toString()
            }
    }
}
