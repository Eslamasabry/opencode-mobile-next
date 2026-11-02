package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Port of lib/domain/run_result.dart (B-CHAT-100, B-CHAT-101, B-CHAT-068): the most recent run of a session, built only
// from the server's message and tool-part records. No model call, no prose parsing, no guessing from idleness.

/** What the server said about how a run ended (Dart RunOutcomeKind). */
public enum class RunOutcomeKind {
    /** The provider reported a normal stop. */
    Completed,

    /** The provider stopped for output length. */
    CutOff,

    /** The server attached an error to the newest step. */
    Failed,

    /** The run was aborted (server error kind). */
    Aborted,

    /** The newest step has no completion time and no error yet. */
    Running,

    /** The step completed but the provider gave no recognised finish reason. */
    NotReported,
}

/** Dart RunOutcome: [finish] is the raw server finish reason, [runErrorHeadline] the first meaningful error line. */
public data class RunOutcomeFact(val kind: RunOutcomeKind, val finish: String? = null, val runErrorHeadline: String? = null)

public enum class RunFileChange { Edited, Written, Patched }

/** A file a completed edit/write/patch tool reported touching: tool evidence, not a verified working-tree diff. */
public data class RunChangedFile(val path: String, val change: RunFileChange, val toolName: String, val partId: String?)

/** A file a turn changed with the line counts its tools reported; null where a tool did not report one. */
public data class ChangedFile(val path: String, val added: Int?, val removed: Int?) {
    /** Last path segment (Dart RunFileStat.name). */
    public val name: String
        get() = path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path
}

/** A bash/shell call of the run with what the server recorded (Dart RunCommand). */
public data class CommandFact(
    /** Bounded to [RunResult.MAX_COMMAND_LENGTH] plus an ellipsis. */
    val command: String,
    /** Null when the server recorded no exit code. */
    val exitCode: Int?,
    /** From the command TEXT only ([looksLikeTestCommand]); says nothing about whether tests ran or passed. */
    val isTest: Boolean,
    val toolName: String,
    /** The tool itself ended in the error state. */
    val failed: Boolean,
    /** The server pruned the output, so nothing can be opened. */
    val outputPruned: Boolean,
    val partId: String?,
)

/** Notes the feature words next to the outcome. */
public enum class RunNote {
    /** Steps before the newest one carried a server error; the outcome is still the newest step's. */
    EarlierStepErrors,

    /** The loaded history never reached the user message that started the run: counts are lower bounds. */
    BoundaryUnknown,

    /** Changed files or commands hit their bound. */
    Truncated,

    /** The run carried no tool parts at all: say "no evidence", never "no changes". */
    NoToolEvidence,
}

public data class RunResult(
    val sessionId: String,
    /** Id of the first assistant message of the run. */
    val runId: String,
    val userMessageId: String?,
    /** Creation time of the user message that started the run (else of the first step); null when unknown. */
    val startedAtMs: Long?,
    /** Completion time of the terminal newest step only; null while running. */
    val finishedAtMs: Long?,
    val stepCount: Int,
    val agent: String?,
    val model: String?,
    val outcome: RunOutcomeFact,
    val changedFiles: List<RunChangedFile>,
    val commands: List<CommandFact>,
    val toolCount: Int,
    val prunedToolCount: Int,
    val erroredToolCount: Int,
    val truncated: Boolean,
    val boundaryKnown: Boolean,
    val earlierStepErrors: Int,
    /** Id of the newest assistant message of the run. */
    val lastStepId: String,
) {
    public val hasToolEvidence: Boolean get() = toolCount > 0

    public val notes: Set<RunNote>
        get() =
            buildSet {
                if (earlierStepErrors > 0) add(RunNote.EarlierStepErrors)
                if (!boundaryKnown) add(RunNote.BoundaryUnknown)
                if (truncated) add(RunNote.Truncated)
                if (!hasToolEvidence) add(RunNote.NoToolEvidence)
            }

    public companion object {
        public const val MAX_CHANGED_FILES: Int = 50
        public const val MAX_COMMANDS: Int = 50
        public const val MAX_COMMAND_LENGTH: Int = 240
    }
}

/**
 * The run is the assistant steps after the latest user message, messages in any order (sorted by creation time,
 * server order kept for ties). [historyComplete] says the caller reached the start of the session, so a missing user
 * message still means the boundary is known. Null when no assistant step follows the boundary.
 */
public fun runResult(messages: List<MessageWithParts>, historyComplete: Boolean = false): RunResult? {
    val ordered = messages.sortedBy { it.info.time?.createdMs ?: 0L } // stable: ties keep server order
    val lastUser = ordered.indexOfLast { it.info.role == Role.User }
    val run = ordered.drop(lastUser + 1).filter { it.info.role == Role.Assistant }
    if (run.isEmpty()) return null
    val user = if (lastUser >= 0) ordered[lastUser] else null
    val earlierErrors = run.dropLast(1).count { it.info.errorText != null }

    val changed = LinkedHashMap<String, RunChangedFile>()
    val commands = mutableListOf<CommandFact>()
    var toolCount = 0
    var pruned = 0
    var errored = 0
    var truncated = false
    for (message in run) {
        for (part in message.parts) {
            if (part.type != PartType.Tool) continue
            toolCount++
            val state = part.tool ?: ToolState(ToolStatus.Pending)
            if (state.pruned) pruned++
            if (state.status == ToolStatus.Error) errored++
            val tool = (part.toolName ?: "").lowercase()
            val change = runChangeOf(tool)
            if (change != null) {
                if (state.status != ToolStatus.Completed || !state.ran) continue
                for (path in runPathsOf(state)) {
                    if (changed.size >= RunResult.MAX_CHANGED_FILES && !changed.containsKey(path)) {
                        truncated = true
                        continue
                    }
                    changed[path] = RunChangedFile(path, change, tool, part.id.ifEmpty { part.callId })
                }
            } else if (tool == "bash" || tool == "shell") {
                if (state.status != ToolStatus.Completed && state.status != ToolStatus.Error) continue
                if (commands.size >= RunResult.MAX_COMMANDS) {
                    truncated = true
                    continue
                }
                val raw = runJsonText(state.input["command"])?.trim() ?: ""
                val command =
                    if (raw.length > RunResult.MAX_COMMAND_LENGTH) raw.substring(0, RunResult.MAX_COMMAND_LENGTH) + "…"
                    else raw
                commands +=
                    CommandFact(
                        command = command,
                        exitCode = runExitOf(state.metadata),
                        isTest = looksLikeTestCommand(raw),
                        toolName = tool,
                        failed = state.status == ToolStatus.Error,
                        outputPruned = state.pruned,
                        partId = part.id.ifEmpty { part.callId },
                    )
            }
        }
    }

    val last = run.last().info
    val outcome = runOutcomeOf(last)
    return RunResult(
        sessionId = last.sessionId,
        runId = run.first().info.id,
        userMessageId = user?.info?.id,
        startedAtMs = runPositive(user?.info?.time?.createdMs ?: run.first().info.time?.createdMs),
        finishedAtMs = if (outcome.kind == RunOutcomeKind.Running) null else runPositive(last.time?.completedMs),
        stepCount = run.size,
        agent = last.agent ?: run.first().info.agent,
        model = last.modelId ?: run.first().info.modelId,
        outcome = outcome,
        changedFiles = changed.values.toList(),
        commands = commands.toList(),
        toolCount = toolCount,
        prunedToolCount = pruned,
        erroredToolCount = errored,
        truncated = truncated,
        boundaryKnown = lastUser >= 0 || historyComplete,
        earlierStepErrors = earlierErrors,
        lastStepId = last.id,
    )
}

/** Paths completed edit/write/patch tools reported touching, first-seen order, at most [max]. */
public fun editedPaths(messages: List<MessageWithParts>, max: Int = RunResult.MAX_CHANGED_FILES): List<String> {
    val paths = mutableListOf<String>()
    for (message in messages) {
        for (part in message.parts) {
            if (part.type != PartType.Tool) continue
            if (runChangeOf((part.toolName ?: "").lowercase()) == null) continue
            val state = part.tool ?: continue
            if (state.status != ToolStatus.Completed || !state.ran) continue
            for (path in runPathsOf(state)) {
                if (path in paths) continue
                if (paths.size >= max) return paths
                paths += path
            }
        }
    }
    return paths
}

/**
 * B-CHAT-068: the files a turn's completed edit/write/patch tools touched, in first-touched order, with the line counts
 * the tools reported summed per file. A count is null unless every change to that file reported one (an edit's
 * `filediff`, a patch's per-file numbers, a write that made a new file): a missing number is never guessed.
 */
public fun editedFileStats(messages: List<MessageWithParts>, max: Int = RunResult.MAX_CHANGED_FILES): List<ChangedFile> {
    val order = mutableListOf<String>()
    val added = HashMap<String, Int?>()
    val removed = HashMap<String, Int?>()
    fun count(path: String, plus: Int?, minus: Int?) {
        if (!added.containsKey(path)) {
            if (order.size >= max) return
            order += path
            added[path] = 0
            removed[path] = 0
        }
        val a = added[path]
        val r = removed[path]
        added[path] = if (a == null || plus == null) null else a + plus
        removed[path] = if (r == null || minus == null) null else r + minus
    }

    for (message in messages) {
        for (part in message.parts) {
            if (part.type != PartType.Tool) continue
            val change = runChangeOf((part.toolName ?: "").lowercase()) ?: continue
            val state = part.tool ?: continue
            if (state.status != ToolStatus.Completed || !state.ran) continue
            val metadata = state.metadata
            val perFile = HashMap<String, JsonObject>()
            (metadata?.get("files") as? JsonArray)?.forEach { file ->
                if (file is JsonObject) {
                    val path = runFilePathOf(file)?.trim()
                    if (!path.isNullOrEmpty()) perFile[path] = file
                }
            }
            for (path in runPathsOf(state)) {
                val lines: Pair<Int?, Int?> =
                    when (change) {
                        RunFileChange.Edited ->
                            when (val diff = metadata?.get("filediff")) {
                                is JsonObject -> runCountOf(diff["additions"]) to runCountOf(diff["deletions"])
                                else -> null to null
                            }
                        RunFileChange.Patched ->
                            when (val file = perFile[path]) {
                                null -> null to null
                                else -> runCountOf(file["additions"]) to runCountOf(file["deletions"])
                            }
                        // Only a new file's lines are a known count; an overwrite's difference was never reported.
                        RunFileChange.Written -> {
                            val content = state.input["content"]
                            val exists = (metadata?.get("exists") as? JsonPrimitive)?.takeIf { !it.isString }
                            if (exists?.booleanOrNull == false && content is JsonPrimitive && content.isString) {
                                runLineCount(content.content) to 0
                            } else {
                                null to null
                            }
                        }
                    }
                count(path, lines.first, lines.second)
            }
        }
    }
    return order.map { ChangedFile(it, added[it], removed[it]) }
}

/**
 * True only when the command TEXT names a common test runner or a `<tool> test` invocation. A label derived from this
 * must say it came from the command text.
 */
public fun looksLikeTestCommand(command: String): Boolean {
    val text = command.lowercase()
    return RUN_TEST_RUNNER.containsMatchIn(text) || RUN_TOOL_TEST.containsMatchIn(text)
}

private val RUN_TEST_RUNNER = Regex("""(^|[\s;&|(])(pytest|jest|vitest|mocha|rspec|phpunit|ctest|tox|nose2?)\b""")
private val RUN_TOOL_TEST =
    Regex(
        """(^|[\s;&|(])(flutter|dart|npm|pnpm|yarn|bun|deno|cargo|go|mvn|gradlew?|""" +
            """dotnet|make|poetry|uv|swift|mix|sbt|lein|stack|cabal)""" +
            """(\s+run)?\s+tests?\b"""
    )

private fun runPositive(raw: Long?): Long? = if (raw == null || raw <= 0) null else raw

private fun runChangeOf(tool: String): RunFileChange? =
    when (tool) {
        "edit", "multiedit" -> RunFileChange.Edited
        "write" -> RunFileChange.Written
        "patch", "apply_patch" -> RunFileChange.Patched
        else -> null
    }

/** Dart `raw?.toString()`: a JSON null is null, a primitive is its content, anything else its JSON text. */
private fun runJsonText(raw: JsonElement?): String? =
    when (raw) {
        null, JsonNull -> null
        is JsonPrimitive -> raw.content
        else -> raw.toString()
    }

private fun runFilePathOf(file: JsonObject): String? =
    runJsonText(file["path"]) ?: runJsonText(file["filePath"]) ?: runJsonText(file["file"])

private fun runPathsOf(state: ToolState): List<String> {
    val paths = mutableListOf<String>()
    fun add(raw: String?) {
        val path = raw?.trim()
        if (!path.isNullOrEmpty() && path !in paths) paths += path
    }
    (state.metadata?.get("files") as? JsonArray)?.forEach { file -> if (file is JsonObject) add(runFilePathOf(file)) }
    if (paths.isEmpty()) {
        val input = state.input
        add(runJsonText(input["filePath"]) ?: runJsonText(input["path"]) ?: runJsonText(input["file"]))
    }
    return paths
}

/** A non-negative number the tool reported; strings and negatives are not counts. */
private fun runCountOf(raw: JsonElement?): Int? {
    val value = (raw as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
    value.longOrNull?.let { return if (it >= 0) it.toInt() else null }
    value.doubleOrNull?.let { return if (it >= 0) it.toInt() else null }
    return null
}

private fun runLineCount(text: String): Int {
    if (text.isEmpty()) return 0
    val lines = text.count { it == '\n' }
    return if (text.endsWith('\n')) lines else lines + 1
}

private fun runExitOf(metadata: JsonObject?): Int? {
    val raw = metadata?.get("exit")?.takeIf { it != JsonNull } ?: metadata?.get("exitCode") ?: return null
    val value = raw as? JsonPrimitive ?: return null
    if (value == JsonNull) return null
    if (value.isString) return value.content.trim().toIntOrNull()
    return value.longOrNull?.toInt() ?: value.doubleOrNull?.toInt()
}

/** The newest step alone decides: an error on it fails the run, a stop on it completes the run. */
private fun runOutcomeOf(last: MessageInfo): RunOutcomeFact {
    val errorText = last.errorText
    if (errorText != null) {
        val kind = runRefineErrorKind(last.errorKind ?: MessageErrorKind.Unknown, errorText)
        return RunOutcomeFact(
            kind =
                when (kind) {
                    MessageErrorKind.Aborted -> RunOutcomeKind.Aborted
                    MessageErrorKind.OutputLength -> RunOutcomeKind.CutOff
                    else -> RunOutcomeKind.Failed
                },
            finish = last.finish,
            runErrorHeadline = runErrorHeadline(errorText),
        )
    }
    val completed = last.time?.completedMs
    if (completed == null || completed <= 0) return RunOutcomeFact(RunOutcomeKind.Running, finish = last.finish)
    val kind =
        when (last.finish?.trim()?.lowercase()) {
            "stop", "end_turn", "end-turn" -> RunOutcomeKind.Completed
            "length", "max_tokens", "max-tokens" -> RunOutcomeKind.CutOff
            else -> RunOutcomeKind.NotReported
        }
    return RunOutcomeFact(kind, finish = last.finish)
}

/** The first meaningful line of an error without a stack trace; "SomethingError: msg" reads as "msg". */
private fun runErrorHeadline(text: String): String {
    val lines = text.trim().split('\n')
    val first = (lines.firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("at ") } ?: lines.first()).trim()
    val colon = first.indexOf(": ")
    if (colon > 0 && RUN_ERROR_CLASS.matches(first.substring(0, colon))) return first.substring(colon + 2)
    return first
}

private val RUN_ERROR_CLASS = Regex("^[A-Za-z]+Error$")

private val RUN_SIGN_IN_GONE =
    Regex(
        "not logged in|please run /login|failed to authenticate|oauth (session|token) (has )?expired|" +
            "could not be refreshed|invalid api key|authentication_error",
        RegexOption.IGNORE_CASE,
    )

/** Dart `MessageErrorKind.refineFromText`. */
private fun runRefineErrorKind(kind: MessageErrorKind, text: String): MessageErrorKind {
    if (kind != MessageErrorKind.Unknown) return kind
    if (RUN_SIGN_IN_GONE.containsMatchIn(text)) return MessageErrorKind.ProviderAuth
    val head = text.trimStart().split(Regex("[:\\s]")).first()
    return runErrorKindFromName(head) ?: MessageErrorKind.Unknown
}

/** Dart `MessageErrorKind.fromName`. */
private fun runErrorKindFromName(name: String?): MessageErrorKind? {
    val trimmed = name?.trim()
    if (trimmed.isNullOrEmpty()) return null
    val key = trimmed.lowercase().replace(Regex("[^a-z]"), "")
    return when {
        "contextoverflow" in key || "contextlength" in key -> MessageErrorKind.ContextOverflow
        "outputlength" in key || key == "length" -> MessageErrorKind.OutputLength
        "providerauth" in key || "needsauth" in key || "unauthorized" in key || "authentication" in key ->
            MessageErrorKind.ProviderAuth
        "contentfilter" in key -> MessageErrorKind.ContentFilter
        "abort" in key -> MessageErrorKind.Aborted
        "modelnotfound" in key -> MessageErrorKind.ModelNotFound
        else -> MessageErrorKind.Unknown
    }
}
