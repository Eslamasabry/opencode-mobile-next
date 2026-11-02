package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of lib/domain/background_tasks.dart and lib/domain/background_agent_result.dart (B-CHAT-007, B-CHAT-008,
// B-CHAT-011). Work counts as still running only while: its starting step said it went on in the background (the
// protocol mapper's [BACKGROUND_TASK_METADATA_KEY] marker); no ending notice names it; it started less than six
// hours ago; and the agent's process has not been seen down since it started. Not ported: the Paseo mapper that
// sets the marker and words the notice (lib/paseo/mappers.dart), and BackgroundTaskGateway's change stream (the
// book exposes a revision counter instead).

/** The step metadata key a mapper sets on a step whose work went on in the background: {id?, label?}. */
public const val BACKGROUND_TASK_METADATA_KEY: String = "backgroundTask"

/** The longest the app believes background work is still running without its notice (6 hours). */
public const val BACKGROUND_TASK_MAX_AGE_MS: Long = 6L * 60 * 60 * 1000

public data class BackgroundTask(
    /** The starting step's call id: what the ending notice names. */
    val callId: String,
    /** The message that holds the starting step. */
    val messageId: String,
    val sinceMs: Long,
    /** Short plain name ("build_tv_release.sh"); never a path. */
    val name: String,
    /** The full command, for a details view only. */
    val command: String = "",
    val taskId: String? = null,
    /** A shell loop that waits on [name] ("Waiting for grep"). */
    val waiting: Boolean = false,
)

/** What an ending notice names; either may be missing (how it ended is ToolFacts' [backgroundTaskEnd]). */
public data class BackgroundTaskEndedRef(val callId: String?, val taskId: String?)

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The background work [part] (a step of [message]) started, or null. */
public fun backgroundTaskStarted(message: MessageWithParts, part: Part): BackgroundTask? {
    val tool = part.tool
    if (part.type != PartType.Tool || tool == null || tool.status != ToolStatus.Completed) return null
    val marker = tool.metadata?.get(BACKGROUND_TASK_METADATA_KEY) as? JsonObject ?: return null
    val created = message.info.time?.createdMs ?: return null
    val callId = part.callId ?: part.id
    if (callId.isEmpty()) return null
    val commandText = tool.input["command"].stringOrNull() ?: ""
    val label = marker["label"].stringOrNull()
    val id = marker["id"].stringOrNull()
    val labelled = label != null && label.isNotBlank()
    val waitsOn = if (labelled) null else backgroundTaskWaitsOn(commandText)
    return BackgroundTask(
        callId = callId,
        messageId = message.info.id,
        sinceMs = created,
        name = if (labelled) oneLine(label!!) else waitsOn ?: backgroundTaskName(commandText),
        command = commandText,
        taskId = id?.takeIf { it.isNotEmpty() },
        waiting = waitsOn != null,
    )
}

/** What an ending notice names, or null when [part] is not a notice. */
public fun backgroundTaskEnded(part: Part): BackgroundTaskEndedRef? {
    if (part.type != PartType.Tool || !isBackgroundTaskNotice(part.toolName ?: "")) return null
    val input = part.tool?.input ?: JsonObject(emptyMap())
    fun text(value: JsonElement?): String? = value.stringOrNull()?.takeIf { it.isNotBlank() }?.trim()
    return BackgroundTaskEndedRef(callId = text(input["toolUseId"]), taskId = text(input["taskId"]))
}

/** Whether [task] may still be running at [nowMs] (age and process-gap rules). */
public fun backgroundTaskBelieved(task: BackgroundTask, nowMs: Long, runtimeGapAtMs: Long? = null): Boolean {
    if (nowMs - task.sinceMs >= BACKGROUND_TASK_MAX_AGE_MS) return false
    if (runtimeGapAtMs != null && task.sinceMs <= runtimeGapAtMs) return false
    return true
}

/** The background work still running in [messages], oldest first. */
public fun runningBackgroundTasks(
    messages: Iterable<MessageWithParts>,
    nowMs: Long,
    runtimeGapAtMs: Long? = null,
): List<BackgroundTask> {
    val book = TaskLedger()
    messages.forEach(book::observe)
    return book.started.values.filter { book.isOpen(it) && backgroundTaskBelieved(it, nowMs, runtimeGapAtMs) }
}

private val CHAIN = Regex("&&|\\|\\||;|\\n")
private val PLAIN_WORD = Regex("^[A-Za-z][\\w:.-]*$")

private fun segmentsOf(command: String): List<String> = command.split(CHAIN).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * A command as a short plain name: the program or script it runs, without its folder, environment or wrappers, and
 * at most two plain words after it. Empty text reads "a command" (a fixed token, as in Dart).
 */
public fun backgroundTaskName(command: String): String {
    val segments = segmentsOf(command)
    var words = emptyList<String>()
    for (segment in segments.asReversed()) {
        val candidate = commandWords(segment.split('|').first())
        if (candidate.isNotEmpty() && candidate.first() != "cd") {
            words = candidate
            break
        }
    }
    if (words.isEmpty()) return "a command"
    val program = words.first().substringAfterLast('/')
    val rest = mutableListOf<String>()
    for (word in words.drop(1)) {
        if (rest.size == 2 || !PLAIN_WORD.matches(word)) break
        if (word.contains('/') || word.contains('.')) break
        rest += word
    }
    val name = (listOf(program) + rest).joinToString(" ")
    return if (name.length > 48) name.substring(0, 47) + "…" else name
}

private val LOOP_END_STRIP = Regex("[\"')]+")
private val LOOP_DONE = Regex("^done\\b")
private val LOOP_HEAD = Regex("(?:^|[\\s'\"(])(?:until|while)\\s+(.+)$")
private val LEADING_BANG = Regex("^!\\s*")
private val LEADING_DO = Regex("^do\\s+")
private val BRACKET_TESTS = setOf("[", "[[", "test", "true", ":")
private val BRACKET_WORDS = setOf("]", "]]", "!")

/** The program a command waits on when it ends in a shell loop, or null. */
public fun backgroundTaskWaitsOn(command: String): String? {
    val segments = segmentsOf(command)
    val last = segments.lastOrNull()?.replace(LOOP_END_STRIP, "")
    if (last == null || !LOOP_DONE.containsMatchIn(last)) return null
    for (i in segments.size - 2 downTo 0) {
        val test = LOOP_HEAD.find(segments[i])?.groupValues?.get(1) ?: continue
        val words = commandWords(test.replaceFirst(LEADING_BANG, "").split('|').first())
        if (words.isEmpty()) return null
        var program = words.first().substringAfterLast('/')
        if (program in BRACKET_TESTS) {
            val operands = words.drop(1).filter { !it.startsWith("-") && it !in BRACKET_WORDS }
            if (operands.isNotEmpty()) program = operands.last().substringAfterLast('/')
            val body = if (operands.isNotEmpty()) emptyList() else segments.subList(i + 1, segments.size - 1)
            for (segment in body) {
                val bodyWords = commandWords(segment.replaceFirst(LEADING_DO, "").split('|').first())
                if (bodyWords.isNotEmpty() && bodyWords.first() != "sleep" && bodyWords.first() != "do") {
                    program = bodyWords.first().substringAfterLast('/')
                    break
                }
            }
        }
        return if (program.length > 48) program.substring(0, 47) + "…" else program
    }
    return null
}

private val WRAPPERS = setOf("sudo", "nohup", "env", "time", "exec", "command", "nice", "setsid", "stdbuf")
private val INTERPRETERS =
    setOf("bash", "sh", "zsh", "dash", "python", "python3", "node", "deno", "ruby", "perl", "npx", "bunx")
private val WHITESPACE = Regex("\\s+")
private val EDGE_QUOTES = Regex("^[\"']|[\"']$")
private val ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=")
private val TIMEOUT_ARG = Regex("^(-\\S+|\\d+\\w?)$")

/** [segment]'s words from the program on: env assignments, wrappers and a script's interpreter left out. */
private fun commandWords(segment: String): List<String> {
    val words = segment.trim().split(WHITESPACE).map { it.replace(EDGE_QUOTES, "") }.filter { it.isNotEmpty() }
    var i = 0
    while (i < words.size) {
        val word = words[i]
        if (ASSIGNMENT.containsMatchIn(word) || word in WRAPPERS) {
            i++
        } else if (word == "timeout") {
            i++
            while (i < words.size && TIMEOUT_ARG.containsMatchIn(words[i])) i++
        } else {
            break
        }
    }
    var rest = words.subList(i, words.size)
    if (rest.size > 1 && rest.first().substringAfterLast('/') in INTERPRETERS) {
        val script = rest.drop(1).filter { !it.startsWith("-") }
        if (script.isNotEmpty()) rest = listOf(script.first())
    }
    return rest
}

private fun oneLine(text: String): String {
    val line = text.trim().split('\n').first().trim()
    return if (line.length > 64) line.substring(0, 63) + "…" else line
}

/**
 * What [message] says about whether its turn failed: true for an agent message with an error, false for a prompt or
 * the agent's words after it, null for anything else (a step, a background task's notice).
 */
public fun turnFailedAfter(message: MessageWithParts): Boolean? {
    if (message.info.role == Role.User) return false
    if (message.info.errorText != null) return true
    return if (message.parts.any { it.type == PartType.Text && it.text.isNotBlank() }) false else null
}

/** Whether the last turn in [messages] ended on the agent's failure. */
public fun lastTurnFailedIn(messages: Iterable<MessageWithParts>): Boolean {
    var failed = false
    for (message in messages) failed = turnFailedAfter(message) ?: failed
    return failed
}

private class TaskLedger {
    val started = LinkedHashMap<String, BackgroundTask>()
    val endedCalls = LinkedHashSet<String>()
    val endedTasks = LinkedHashSet<String>()
    var gapMs: Long? = null
    var failed = false

    val signature: String
        get() = "${started.keys.joinToString(",")}|${endedCalls.joinToString(",")}|${endedTasks.joinToString(",")}|$failed"

    fun isOpen(task: BackgroundTask): Boolean =
        task.callId !in endedCalls && !(task.taskId != null && task.taskId in endedTasks)

    fun observe(message: MessageWithParts) {
        failed = turnFailedAfter(message) ?: failed
        for (part in message.parts) {
            val start = backgroundTaskStarted(message, part)
            if (start != null) {
                started[start.callId] = start
                continue
            }
            val end = backgroundTaskEnded(part) ?: continue
            end.callId?.let(endedCalls::add)
            end.taskId?.let(endedTasks::add)
        }
    }

    fun trim(max: Int) {
        while (started.size > max) started.remove(started.keys.first())
        while (endedCalls.size > max * 4) endedCalls.remove(endedCalls.first())
        while (endedTasks.size > max * 4) endedTasks.remove(endedTasks.first())
    }
}

/**
 * What the app has seen of each agent's background work, keyed by the agent's own id on its helper. Bounded (256
 * agents, 64 tasks each); nothing is saved. Dart BackgroundTaskBook without its stream: [revision] counts the
 * changes it would have announced.
 */
public class BackgroundTaskBook {
    private val agents = LinkedHashMap<String, TaskLedger>()

    public var revision: Int = 0
        private set

    private fun agent(key: String): TaskLedger {
        val agent = agents.remove(key) ?: TaskLedger()
        agents[key] = agent
        while (agents.size > MAX_AGENTS) agents.remove(agents.keys.first())
        return agent
    }

    /** [messages] is everything the app read of [key]'s conversation; it replaces what was known. */
    public fun replace(key: String, messages: Iterable<MessageWithParts>) {
        val agent = agent(key)
        val before = agent.signature
        agent.started.clear()
        agent.endedCalls.clear()
        agent.endedTasks.clear()
        agent.failed = false
        messages.forEach(agent::observe)
        agent.trim(MAX_TASKS)
        if (agent.signature != before) revision++
    }

    /** One more message of [key]'s conversation, as it streams. */
    public fun observe(key: String, message: MessageWithParts) {
        val agent = agent(key)
        val before = agent.signature
        agent.observe(message)
        agent.trim(MAX_TASKS)
        if (agent.signature != before) revision++
    }

    /** [key]'s agent was seen with no process behind it at [atMs]. */
    public fun runtimeGap(key: String, atMs: Long) {
        val agent = agent(key)
        val had = running(key, atMs)
        val gap = agent.gapMs
        if (gap == null || atMs > gap) agent.gapMs = atMs
        if (had && !running(key, atMs)) revision++
    }

    public fun gapAt(key: String): Long? = agents[key]?.gapMs

    /** Whether [key]'s last turn, as read, ended on a failure, until the next prompt. */
    public fun turnFailed(key: String): Boolean = agents[key]?.failed ?: false

    public fun running(key: String, nowMs: Long): Boolean {
        val agent = agents[key] ?: return false
        return agent.started.values.any { agent.isOpen(it) && backgroundTaskBelieved(it, nowMs, agent.gapMs) }
    }

    public fun forget(key: String) {
        if (agents.remove(key) != null) revision++
    }

    public fun clear() {
        agents.clear()
    }

    private companion object {
        const val MAX_AGENTS = 256
        const val MAX_TASKS = 64
    }
}

// ---- Background agent results (lib/domain/background_agent_result.dart) ----

private val SUBAGENT_METADATA_KEYS = listOf("source", "childID", "agent", "state")
private val CHILD_SESSION_ID = Regex("^ses_[A-Za-z0-9_-]+$")
private val RESULT_STATES = setOf("completed", "error", "cancelled")

/**
 * The OC2 synthetic message metadata a mapper keeps on the `v2:notice` part: only the result's identity and status
 * keys, and only string values (gateway_mappers.dart Api2SyntheticMessage).
 */
public fun subagentNoticeMetadata(metadata: JsonObject?): Map<String, String> =
    buildMap {
        for (key in SUBAGENT_METADATA_KEYS) metadata?.get(key).stringOrNull()?.let { put(key, it) }
    }

/** Presentation of the canonical OC2 synthetic subagent completion. Never creates or renames a message. */
public data class BackgroundAgentResult(
    val childId: String,
    val agent: String,
    val state: String,
    val description: String,
    val body: String,
) {
    /** Navigation only after the catalog confirms the exact child-parent relation ([parentOf]: child id to parent). */
    public fun isKnownChild(parentId: String?, parentOf: Map<String, String?>): Boolean =
        parentId != null && parentId != childId && parentOf[childId] == parentId

    public companion object {
        public fun fromPart(part: Part): BackgroundAgentResult? {
            if (part.rawType != "v2:notice" || part.toolName != "synthetic") return null
            val metadata = part.noticeMetadata
            if (metadata["source"] != "subagent") return null
            val childId = metadata["childID"]
            val agent = metadata["agent"]
            val state = metadata["state"]
            val description = part.filename
            if (childId == null || !CHILD_SESSION_ID.matches(childId) || agent == null || agent.isBlank() ||
                state !in RESULT_STATES || description == null
            ) {
                return null
            }
            // The server interpolates description verbatim: rebuild the exact header instead of parsing XML.
            val header = "<subagent sessionID=\"$childId\" state=\"$state\" description=\"$description\">\n"
            val footer = "\n</subagent>"
            if (!part.text.startsWith(header) || !part.text.endsWith(footer)) return null
            val end = part.text.length - footer.length
            if (end < header.length) return null
            return BackgroundAgentResult(childId, agent, state!!, description, part.text.substring(header.length, end))
        }
    }
}
