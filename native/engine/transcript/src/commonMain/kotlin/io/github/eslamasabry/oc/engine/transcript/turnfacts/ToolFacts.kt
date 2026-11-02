package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of Flutter lib/ui/widgets/tool_card_contract.dart `_ToolContract.from` / `_ToolKind` and
// lib/domain/tool_label.dart (toolIdWords, isBackgroundTaskNotice, backgroundTaskEnd): what a tool step is, before
// any words. Every phrase below names the Flutter l10n key it stands for; the native resource has the same name in
// native/ui/words/src/commonMain/composeResources/values/strings_chat.xml unless noted (strings_common.xml).

/** Dart `_ToolKind`, same values in the same order. */
public enum class ToolKind {
    Read,
    List,
    Glob,
    Grep,
    Shell,
    Edit,
    Write,
    Patch,
    WebFetch,
    WebSearch,
    Task,
    Todo,
    Question,
    Lsp,
    Skill,
    Plan,
    WorktreeSetup,
    ConnectorSearch,
    BackgroundNotice,
    AgentMessage,
    Card,
    /** Dart `generic`: any other id, and Claude Code's `ToolSearch`. */
    Generic,
}

/**
 * Which title wording the step row uses (Dart `_ToolContract.title`). [ToolFacts.titleArgs] holds the phrase's
 * arguments in order; a phrase marked "server words" is the server's own text in `titleArgs[0]`, shown as it is.
 */
public enum class ToolPhrase {
    /** `chatUiRead` (strings_chat.xml `chatUiRead`, "Read"). */
    Read,
    /** `chatUiList` ("List"). */
    List,
    /** `chatUiFindFiles` ("Find files"). */
    FindFiles,
    /** `chatUiSearchText` ("Search text"). */
    SearchText,
    /** `activeContextShell` (strings_common.xml `activeContextShell`, "Shell"). */
    Shell,
    /** `chatUiEdit` ("Edit"). */
    Edit,
    /** `chatUiWrite` ("Write"). */
    Write,
    /** `chatUiApplyPatch` ("Apply patch"). */
    ApplyPatch,
    /** `chatUiFetchPage` ("Fetch page"). */
    FetchPage,
    /** `chatUiWebSearch` ("Web search"). */
    WebSearch,
    /** `chatUiProviderSearch` ("%1$s search"); args: provider. */
    ProviderSearch,
    /** Server words: the sub-agent's type (`subagent_type`, or a v2 subagent's `agent`). */
    AgentType,
    /** `chatUiAgent` ("Agent"): a sub-agent with no type. */
    Agent,
    /** `toolCardUpdatedTasks` ("Updated tasks"). */
    UpdatedTasks,
    /** `chatUiQuestions` ("Questions"). */
    Questions,
    /** Server words: the language-server `operation`. */
    LspOperation,
    /** `chatUiLanguageServer` ("Language server"). */
    LanguageServer,
    /** `toolCardPlan` ("Plan"). */
    Plan,
    /** `toolCardWorktreeSetup` ("Setting up a separate copy"). */
    WorktreeSetup,
    /** `activeContextSkill` (strings_common.xml `activeContextSkill`, "Skill"). */
    Skill,
    /** `chatUiToolBackgroundTaskCutOff`. */
    BackgroundTaskCutOff,
    /** `chatUiToolBackgroundNamedCutOff` ("%1$s stopped when …"); args: task name. */
    BackgroundNamedCutOff,
    /** `chatUiToolBackgroundNamedFinished`; args: task name. */
    BackgroundNamedFinished,
    /** `chatUiToolBackgroundNamedFailed`; args: task name. */
    BackgroundNamedFailed,
    /** `chatUiToolBackgroundNamedStopped`; args: task name. */
    BackgroundNamedStopped,
    /** `chatUiToolBackgroundNamedEnded`; args: task name. */
    BackgroundNamedEnded,
    /** `chatUiToolBackgroundTaskFinished`. */
    BackgroundTaskFinished,
    /** `chatUiToolBackgroundTaskFailed`. */
    BackgroundTaskFailed,
    /** `chatUiToolBackgroundTaskStopped`. */
    BackgroundTaskStopped,
    /** `chatUiToolBackgroundTaskEnded`. */
    BackgroundTaskEnded,
    /** `chatUiToolMessageToEveryone` ("Message to the whole team"). */
    MessageToEveryone,
    /** `chatUiToolMessageToMain` ("Message to the main conversation"). */
    MessageToMain,
    /** `chatUiToolMessageTo` ("Message to %1$s"); args: recipient name. */
    MessageTo,
    /** Server words: the agent message's own summary. */
    MessageSummary,
    /** `chatUiToolMessageToAgent` ("Message to an agent"). */
    MessageToAgent,
    /** `chatUiLoadTools` ("Load tools"). */
    LoadTools,
    /** `chatUiToolSearchedConnectors` ("Searched connectors"). */
    SearchedConnectors,
    /** `chatUiToolShowCard` ("Show card"). */
    ShowCard,
    /** Server words: the call's own title (not merely its id). */
    ServerTitle,
    /** `chatUiToolFrom` ("%1$s · %2$s"); args: the MCP tool's words, the MCP server's words. */
    McpTool,
    /** `chatUiTool` ("Tool"): an id with no letters or digits. */
    Tool,
    /** No key: the id as words, sentence-cased ([ToolFacts.idWords] with its first letter upper), in `titleArgs[0]`. */
    Generic,
}

/** A counted fact; each kind names its l10n key (same native resource name). */
public enum class ToolCountKind {
    /** `chatUiFromLine` ("from %1$s"). */
    FromLine,
    /** `chatUiLineCount` (plurals). */
    Lines,
    /** `chatUiEntryCount` (plurals). */
    Entries,
    /** `chatUiFoundCount` ("%1$s found"). */
    Found,
    /** `chatUiMatchCount` (plurals). */
    Matches,
    /** `chatUiFileCount` (plurals). */
    Files,
    /** `chatUiResultCount` (plurals). */
    Results,
    /** `chatUiAskedCount` ("%1$s asked"). */
    Asked,
    /** `chatUiAnsweredCount` ("%1$s answered"). */
    Answered,
}

public data class ToolCount(val kind: ToolCountKind, val value: Long)

/** How a connector search ended when it found nothing to count. */
public enum class ConnectorSearchOutcome {
    /** `chatUiConnectorSearchEmpty`. */
    Empty,
    /** `chatUiConnectorSearchNotLoaded`. */
    NotLoaded,
    /** `chatUiConnectorSearchOff`. */
    Off,
    /** `chatUiConnectorSearchUnavailable`. */
    Unavailable,
}

/** One of the row's other facts (Dart `_ToolContract.details`), in Dart's order. */
public sealed interface ToolDetail {
    public data class Count(val count: ToolCount) : ToolDetail

    /** `chatUiLineRange` ("L%1$s–%2$s"). */
    public data class LineRange(val start: Long, val end: Long) : ToolDetail

    /** `toolCardPageStatus` ("Page answered %1$s"), its argument `"$code $text"` (text omitted when null). */
    public data class PageStatus(val code: Long, val text: String?) : ToolDetail

    /** The server's own words (a bash purpose, a grep include, a fetch format). */
    public data class ServerText(val text: String) : ToolDetail

    /** `chatUiNewFile`. */
    public data object NewFile : ToolDetail

    /** `chatUiUpdated`. */
    public data object Updated : ToolDetail

    /** `chatUiTruncated`. */
    public data object Truncated : ToolDetail

    public data class ConnectorSearch(val outcome: ConnectorSearchOutcome) : ToolDetail
}

/**
 * How a background task ended (Dart `BackgroundTaskEnd`). [Ended] is the honest default when the notice does not
 * say: a task cut off with its session must never read as finished.
 */
public enum class BackgroundTaskEnd { Finished, Failed, Stopped, Ended }

/** Claude Code's background-task notice, read once (Dart `_TaskNotice`). */
public data class ToolNotice(
    val end: BackgroundTaskEnd,
    /** Claude's sentence about the task. */
    val summary: String?,
    /** The task's name, from the quotes in [summary]. */
    val name: String?,
    /** The task never finished: Claude's previous session ended under it. */
    val cutOff: Boolean,
    /** Whether [summary] belongs in the opened step too (Dart `summaryInBody`). */
    val summaryInBody: Boolean,
)

/**
 * What a tool call says about itself, before any words (Dart `_ToolContract`).
 *
 * [subject] is Dart's `subtitle` when it is server text; a counted subtitle (a patch's file count, a question's
 * asked/answered count) is [subjectCount] instead. [detail] is the first server-words detail (a bash call's
 * purpose, a grep include, a fetch format) for a one-line consumer; [details] has all of them in order.
 */
public data class ToolFacts(
    val kind: ToolKind,
    val toolName: String,
    val phrase: ToolPhrase,
    val subject: String?,
    val detail: String?,
    val added: Int?,
    val removed: Int?,
    val childSessionId: String?,
    val idWords: String,
    val titleArgs: List<String> = emptyList(),
    val subjectCount: ToolCount? = null,
    /** [subject] is a value the app did not write (a path, a pattern, a command, a URL): mono, left-to-right. */
    val technical: Boolean = false,
    val details: List<ToolDetail> = emptyList(),
    /** A shell command's exit code, when the server reported one. */
    val exitCode: Int? = null,
    /** The native v2 `subagent` tool (not OpenCode's `task`). */
    val nativeSubagent: Boolean = false,
    val notice: ToolNotice? = null,
)

/** [toolFacts] of a tool part; a part with no state reads as a finished call (Dart `toolLabel` with no state). */
public fun toolFacts(part: Part): ToolFacts = toolFacts(part.toolName ?: "", part.tool ?: ToolState(ToolStatus.Completed))

/**
 * Dart `_ToolContract.from(rawName, state)`. B-CHAT-111: no raw id is ever the title; an id the app has no words
 * for becomes [ToolPhrase.ServerTitle], [ToolPhrase.McpTool], [ToolPhrase.Generic] or [ToolPhrase.Tool].
 */
public fun toolFacts(rawName: String, state: ToolState): ToolFacts {
    val name = rawName.trim().lowercase()
    val input = state.input
    val metadata = state.metadata ?: JsonObject(emptyMap())
    val details = mutableListOf<ToolDetail>()

    val kind: ToolKind
    var phrase: ToolPhrase
    var titleArgs: List<String> = emptyList()
    var subject: String? = null
    var subjectCount: ToolCount? = null
    var technical = false
    var added: Int? = null
    var removed: Int? = null
    var exitCode: Int? = null
    var notice: ToolNotice? = null

    fun count(kind: ToolCountKind, value: Long) {
        details += ToolDetail.Count(ToolCount(kind, value))
    }

    when {
        name == "read" -> {
            kind = ToolKind.Read
            phrase = ToolPhrase.Read
            val path = inputPath(input)
            subject = if (path != null) fileNameOf(path) else titleNamingAFile(state, name)
            technical = true
            jsonWholeNumber(input["offset"])?.let { count(ToolCountKind.FromLine, it) }
            jsonWholeNumber(input["limit"])?.let { count(ToolCountKind.Lines, it) }
            val display = metadata["display"]
            if (display is JsonObject) {
                val start = jsonWholeNumber(display["lineStart"])
                val end = jsonWholeNumber(display["lineEnd"])
                if (start != null && end != null) details += ToolDetail.LineRange(start, end)
                val entries = display["entries"]
                if (entries is JsonArray) count(ToolCountKind.Entries, entries.size.toLong())
            }
        }
        name == "list" -> {
            kind = ToolKind.List
            phrase = ToolPhrase.List
            subject = jsonText(input["path"]) ?: state.title
            technical = true
        }
        name == "glob" -> {
            kind = ToolKind.Glob
            phrase = ToolPhrase.FindFiles
            subject = jsonText(input["pattern"])
            technical = true
            jsonWholeNumber(metadata["count"])?.let { count(ToolCountKind.Found, it) }
        }
        name == "grep" -> {
            kind = ToolKind.Grep
            phrase = ToolPhrase.SearchText
            subject = jsonText(input["pattern"])
            technical = true
            jsonWholeNumber(metadata["matches"])?.let { count(ToolCountKind.Matches, it) }
            jsonWholeNumber(metadata["count"])?.takeIf { it > 0 }?.let { count(ToolCountKind.Files, it) }
            jsonText(input["include"])?.let { details += ToolDetail.ServerText(it) }
        }
        name == "bash" || name == "shell" -> {
            kind = ToolKind.Shell
            phrase = ToolPhrase.Shell
            subject = jsonText(input["command"])
            technical = true
            // OpenCode's bash tool says in words what the command is for.
            jsonText(input["description"])?.let { details += ToolDetail.ServerText(it) }
            exitCode = jsonWholeNumber(metadata["exit"])?.toInt()
        }
        name == "edit" -> {
            kind = ToolKind.Edit
            phrase = ToolPhrase.Edit
            val path = inputPath(input)
            subject = if (path == null) titleNamingAFile(state, name) else fileNameOf(path)
            technical = path != null
            val filediff = metadata["filediff"]
            if (filediff is JsonObject) {
                added = positive(filediff["additions"])
                removed = positive(filediff["deletions"])
            }
        }
        name == "write" -> {
            kind = ToolKind.Write
            phrase = ToolPhrase.Write
            val path = inputPath(input)
            subject = if (path == null) titleNamingAFile(state, name) else fileNameOf(path)
            technical = path != null
            // Only what the host said: no "Updated" guess when it is silent.
            if (jsonIsFalse(metadata["exists"])) details += ToolDetail.NewFile
            if (jsonIsTrue(metadata["exists"])) details += ToolDetail.Updated
        }
        name == "patch" || name == "apply_patch" -> {
            kind = ToolKind.Patch
            phrase = ToolPhrase.ApplyPatch
            val files = metadata["files"]
            if (files is JsonArray) {
                subjectCount = ToolCount(ToolCountKind.Files, files.size.toLong())
                var additions = 0L
                var deletions = 0L
                for (file in files) {
                    if (file !is JsonObject) continue
                    additions += jsonWholeNumber(file["additions"]) ?: 0
                    deletions += jsonWholeNumber(file["deletions"]) ?: 0
                }
                if (additions > 0) added = additions.toInt()
                if (deletions > 0) removed = deletions.toInt()
            }
        }
        name == "webfetch" -> {
            kind = ToolKind.WebFetch
            phrase = ToolPhrase.FetchPage
            subject = jsonText(input["url"])
            technical = true
            jsonText(input["format"])?.let { details += ToolDetail.ServerText(it) }
            jsonWholeNumber(metadata["httpCode"])?.let {
                details += ToolDetail.PageStatus(it, jsonText(metadata["httpText"]))
            }
        }
        name == "websearch" -> {
            kind = ToolKind.WebSearch
            if (jsonText(metadata["provider"]) == null) {
                phrase = ToolPhrase.WebSearch
            } else {
                phrase = ToolPhrase.ProviderSearch
                titleArgs = listOf(rawText(metadata["provider"]))
            }
            subject = jsonText(input["query"])
            jsonWholeNumber(metadata["numResults"])?.let { count(ToolCountKind.Results, it) }
        }
        name == "task" || name == "subagent" -> {
            kind = ToolKind.Task
            val type = jsonText(input[if (name == "subagent") "agent" else "subagent_type"])
            if (type != null) {
                phrase = ToolPhrase.AgentType
                titleArgs = listOf(type)
            } else {
                phrase = ToolPhrase.Agent
            }
            subject = jsonText(input["description"])
        }
        name == "todowrite" || name == "todo" -> {
            kind = ToolKind.Todo
            // One line: the list itself is the tasks chip and its sheet.
            phrase = ToolPhrase.UpdatedTasks
        }
        name == "question" -> {
            kind = ToolKind.Question
            phrase = ToolPhrase.Questions
            val questions = input["questions"]
            val answers = metadata["answers"]
            if (questions is JsonArray) {
                subjectCount =
                    ToolCount(
                        if (answers is JsonArray && answers.isNotEmpty()) ToolCountKind.Answered
                        else ToolCountKind.Asked,
                        questions.size.toLong(),
                    )
            }
        }
        name == "lsp" -> {
            kind = ToolKind.Lsp
            val operation = jsonText(input["operation"])
            if (operation != null) {
                phrase = ToolPhrase.LspOperation
                titleArgs = listOf(operation)
            } else {
                phrase = ToolPhrase.LanguageServer
            }
            subject = jsonText(input["filePath"])?.let(::fileNameOf)
            technical = true
        }
        name == "plan" -> {
            // The agent's plan: the first line of it is the step's one line.
            kind = ToolKind.Plan
            phrase = ToolPhrase.Plan
            subject = firstWords(state.output)
        }
        name == "worktree_setup" -> {
            kind = ToolKind.WorktreeSetup
            phrase = ToolPhrase.WorktreeSetup
            subject = jsonText(metadata["branchName"])
            technical = true
        }
        name == "skill" -> {
            kind = ToolKind.Skill
            phrase = ToolPhrase.Skill
            subject = jsonText(input["name"])
        }
        isBackgroundTaskNotice(name) -> {
            // Claude Code's notice that a background task it started ended: its name and how it ended, said once.
            kind = ToolKind.BackgroundNotice
            val read = taskNotice(input)
            notice = read
            phrase = noticePhrase(read)
            titleArgs = listOfNotNull(read.name)
            subject = if (read.cutOff || read.name != null) null else read.summary
        }
        name == "sendmessage" || name == "send_message" -> {
            // Claude Code writing to another agent: who it goes to, in words, and its one-line summary.
            kind = ToolKind.AgentMessage
            val message = agentMessage(state)
            val to = message.displayName
            when {
                message.broadcast -> phrase = ToolPhrase.MessageToEveryone
                message.recipient == "main" -> phrase = ToolPhrase.MessageToMain
                to != null -> {
                    phrase = ToolPhrase.MessageTo
                    titleArgs = listOf(to)
                }
                message.summary != null -> {
                    phrase = ToolPhrase.MessageSummary
                    titleArgs = listOf(message.summary)
                }
                else -> phrase = ToolPhrase.MessageToAgent
            }
            subject = if (phrase == ToolPhrase.MessageSummary) null else message.summary
        }
        name == "toolsearch" -> {
            // Claude Code looking up which of its tools to load.
            kind = ToolKind.Generic
            phrase = ToolPhrase.LoadTools
            subject = jsonText(input["query"])
        }
        isConnectorSearchTool(rawName) -> {
            // The agent searching the connector catalogue already on this phone.
            kind = ToolKind.ConnectorSearch
            phrase = ToolPhrase.SearchedConnectors
            subject = jsonText(input["query"])
            if (state.status == ToolStatus.Completed) {
                connectorSearchDetail(state)?.let { details += it }
            }
        }
        isAgentCardTool(rawName) -> {
            // The agent card tool before (or without) its card: in words, with the card's own title.
            kind = ToolKind.Card
            phrase = ToolPhrase.ShowCard
            subject = jsonText(input["title"])
        }
        else -> {
            kind = ToolKind.Generic
            // The server's own title for the call, unless it only repeats the tool's id.
            val own = state.title?.trim()
            val mcp = MCP_ID.find(rawName)
            val words = toolIdWords(rawName)
            when {
                own != null && own.isNotEmpty() && own != rawName.trim() -> {
                    phrase = ToolPhrase.ServerTitle
                    titleArgs = listOf(own)
                }
                mcp != null -> {
                    phrase = ToolPhrase.McpTool
                    titleArgs = listOf(mcpWords(mcp.groupValues[2]), mcpWords(mcp.groupValues[1]))
                }
                words.isEmpty() -> phrase = ToolPhrase.Tool
                else -> {
                    phrase = ToolPhrase.Generic
                    titleArgs = listOf(words.substring(0, 1).uppercase() + words.substring(1))
                }
            }
        }
    }
    if (jsonIsTrue(metadata["truncated"]) && ToolDetail.Truncated !in details) details += ToolDetail.Truncated
    return ToolFacts(
        kind = kind,
        toolName = rawName,
        phrase = phrase,
        subject = subject,
        detail = details.firstNotNullOfOrNull { (it as? ToolDetail.ServerText)?.text },
        added = added,
        removed = removed,
        childSessionId = if (kind == ToolKind.Task) taskChildSessionId(state.metadata) else null,
        idWords = toolIdWords(rawName),
        titleArgs = titleArgs,
        subjectCount = subjectCount,
        technical = technical && subject != null,
        details = details,
        exitCode = exitCode,
        nativeSubagent = name == "subagent",
        notice = notice,
    )
}

/**
 * B-CHAT-110, Dart `taskChildSessionId`: the child session the OpenCode `task` or native v2 `subagent` tool spawned.
 * The server writes `sessionId`; `sessionID` (native v2) and `session_id` (older builds) are read too.
 */
public fun taskChildSessionId(metadata: JsonObject?): String? {
    if (metadata == null) return null
    for (key in CHILD_SESSION_KEYS) {
        jsonText(metadata[key])?.let { return it }
    }
    return null
}

/**
 * B-CHAT-111, Dart `toolIdWords`: a tool id as lower-case words. "render_mermaid_diagram" → "render mermaid
 * diagram", "ExitWorktree" → "exit worktree", "mcp__github__create_issue" → "create issue". Empty when the id has
 * no letters or digits.
 */
public fun toolIdWords(id: String): String {
    var name = id.trim()
    MCP_ID.find(name)?.let { name = it.groupValues[2] }
    return name
        .replace(CAMEL_BOUNDARY) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        .replace(ID_SEPARATORS, " ")
        .trim()
        .lowercase()
}

/** Dart `isBackgroundTaskNotice`: Claude Code's notice that a background task it started has ended. */
public fun isBackgroundTaskNotice(id: String): Boolean = id.trim().lowercase() == "task_notification"

/** Dart `isAgentCardTool`: the agent card `show` tool, by any agent's name for it. */
public fun isAgentCardTool(id: String): Boolean = id.trim() in CARD_SHOW_NAMES

/** Dart `isConnectorSearchTool`: the read-only connector catalogue search, by its exact qualified name. */
public fun isConnectorSearchTool(id: String): Boolean = id.trim() in CONNECTOR_SEARCH_NAMES

/** Dart `backgroundTaskEnd`: how a background task ended, by its notice's `status`. */
public fun backgroundTaskEnd(input: JsonObject): BackgroundTaskEnd {
    val status = input["status"]?.takeIf { it != JsonNull }?.let(::rawText)?.trim()?.lowercase()
    return when (status) {
        "completed", "complete", "finished", "success", "succeeded", "done" -> BackgroundTaskEnd.Finished
        "failed", "error" -> BackgroundTaskEnd.Failed
        "killed", "stopped", "cancelled", "canceled", "interrupted", "lost", "orphaned" -> BackgroundTaskEnd.Stopped
        else -> BackgroundTaskEnd.Ended
    }
}

// Card and connector-search tools: the `oc-ui` phone server under Claude's `mcp__server__tool` naming and
// OpenCode 2's `server_tool` naming (Dart AgentToolAdapters.cardShowNames / connectorSearchName).
private val CARD_SHOW_NAMES = setOf("mcp__oc-ui__show", "oc-ui_show")
private val CONNECTOR_SEARCH_NAMES = setOf("mcp__oc-ui__find_connectors", "oc-ui_find_connectors")
private val CHILD_SESSION_KEYS = listOf("sessionId", "sessionID", "session_id")

private val MCP_ID = Regex("^mcp__(.+?)__(.+)$")
private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
private val ID_SEPARATORS = Regex("[_\\-.\\s]+")
private val UNDERSCORES = Regex("[_]+")

private fun mcpWords(raw: String): String = raw.replace(UNDERSCORES, " ").trim()

/** A JSON value as Dart's `toString()` would give it for a primitive. */
private fun rawText(value: JsonElement?): String =
    when (value) {
        null, JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

/** Dart `_inputPath`: OpenCode says `filePath`, other agents `file_path` or `path`. */
private fun inputPath(input: JsonObject): String? =
    jsonText(input["filePath"]) ?: jsonText(input["file_path"]) ?: jsonText(input["path"])

/** Dart `_titleNamingAFile`: the server's title when it names something other than the tool itself. */
private fun titleNamingAFile(state: ToolState, toolName: String): String? {
    val title = state.title?.trim()?.ifEmpty { null } ?: return null
    return if (title.lowercase() == toolName) null else title
}

private fun positive(raw: JsonElement?): Int? = jsonWholeNumber(raw)?.toInt()?.takeIf { it > 0 }

private val FIRST_WORDS_LEAD = Regex("^[\\s#>*\\-]+")

/** Dart `_firstWords`: the first line of Markdown as plain words ("# Add login" → "Add login"). */
private fun firstWords(markdown: String?): String? {
    for (line in (markdown ?: "").split('\n')) {
        val words = line.replaceFirst(FIRST_WORDS_LEAD, "").trim()
        if (words.isNotEmpty()) return words
    }
    return null
}

// Background-task notice (Dart `_TaskNotice`).

private val QUOTED = Regex("[\"“]([^\"”\n]{1,120})[\"”]")
private val PREVIOUS_SESSION = Regex("previous (claude code )?(session|process)", RegexOption.IGNORE_CASE)
private val UNFINISHED =
    Regex(
        "(didn't|did not|couldn't|could not) (finish|complete|be)|no completion record|was interrupted",
        RegexOption.IGNORE_CASE,
    )
private val RESTARTED =
    Regex("automatically restarted from|running in the background again", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("\\s+")

private fun taskNotice(input: JsonObject): ToolNotice {
    val summary = jsonText(input["summary"]) ?: jsonText(input["description"]) ?: jsonText(input["name"])
    val cutOff =
        summary != null &&
            PREVIOUS_SESSION.containsMatchIn(summary) &&
            UNFINISHED.containsMatchIn(summary) &&
            !RESTARTED.containsMatchIn(summary)
    val quoted = summary?.let { QUOTED.find(it)?.groupValues?.get(1)?.trim() }
    val name = quoted?.ifEmpty { null }
    return ToolNotice(
        end = if (cutOff) BackgroundTaskEnd.Stopped else backgroundTaskEnd(input),
        summary = summary,
        name = name,
        cutOff = cutOff,
        summaryInBody = summaryInBody(summary, name, cutOff),
    )
}

private fun noticePhrase(notice: ToolNotice): ToolPhrase {
    val named = notice.name != null
    if (notice.cutOff) return if (named) ToolPhrase.BackgroundNamedCutOff else ToolPhrase.BackgroundTaskCutOff
    return when (notice.end) {
        BackgroundTaskEnd.Finished ->
            if (named) ToolPhrase.BackgroundNamedFinished else ToolPhrase.BackgroundTaskFinished
        BackgroundTaskEnd.Failed -> if (named) ToolPhrase.BackgroundNamedFailed else ToolPhrase.BackgroundTaskFailed
        BackgroundTaskEnd.Stopped -> if (named) ToolPhrase.BackgroundNamedStopped else ToolPhrase.BackgroundTaskStopped
        BackgroundTaskEnd.Ended -> if (named) ToolPhrase.BackgroundNamedEnded else ToolPhrase.BackgroundTaskEnded
    }
}

/**
 * Dart `_TaskNotice.summaryInBody`: not when the title already says all of it, nor when the line shows it whole.
 * Dart counts grapheme clusters for the 40 limit; this counts code points (the same for ordinary text).
 */
private fun summaryInBody(summary: String?, name: String?, cutOff: Boolean): Boolean {
    if (summary == null || cutOff) return false
    if (name == null) return codePointCount(summary) > 40
    // What the sentence says besides the name: "completed" adds nothing; "failed: out of memory" does.
    val rest = summary.replaceFirst(Regex("^.*?" + Regex.escape(name) + "[\"”]?"), "").trim()
    return rest.split(WHITESPACE).count { it.isNotEmpty() } > 3
}

private fun codePointCount(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}

// Agent-to-agent message (Dart `_AgentMessage`).

private class AgentMessage(val recipient: String?, val summary: String?, val broadcast: Boolean) {
    /** [recipient] as a name a person reads; null for an id, `*` and none. */
    val displayName: String?
        get() = recipient?.takeIf { it != "*" && !looksLikeId(it) }
}

private fun agentMessage(state: ToolState): AgentMessage {
    val answer = state.outputValue as? JsonObject
    val sources = listOfNotNull(state.input, answer)

    fun pick(vararg keys: String): String? {
        for (source in sources) {
            for (key in keys) {
                val value = jsonString(source[key])
                if (value != null && jsonText(source[key]) != null) return value.trim()
            }
        }
        return null
    }

    val recipient = pick("to", "recipient")
    return AgentMessage(
        recipient = recipient,
        summary = pick("summary"),
        broadcast = recipient == "*" || pick("type")?.lowercase() == "broadcast",
    )
}

private val ID_LIKE = Regex("(?=[0-9a-f]*[0-9])[0-9a-f]{8,}", RegexOption.IGNORE_CASE)
private val ANY_SPACE = Regex("\\s")

/** "a1b2c3d4e5f6", "agent-7f3e9a01", a UUID: eight or more hex characters with a digit, and no spaces. */
private fun looksLikeId(value: String): Boolean = !ANY_SPACE.containsMatchIn(value) && ID_LIKE.containsMatchIn(value)

// Connector search result (Dart lib/domain/connector_search_result.dart, only what the row line needs).

private fun connectorSearchDetail(state: ToolState): ToolDetail? {
    var data: JsonElement? = state.outputValue
    if (data !is JsonObject) {
        val raw = state.output?.trim()
        if (raw.isNullOrEmpty() || raw.length > 32768) return null
        data =
            try {
                Json.parseToJsonElement(raw)
            } catch (_: IllegalArgumentException) {
                return null
            }
    }
    if (data !is JsonObject) return null
    when (jsonString(data["status"])) {
        "ok" -> Unit
        "catalogue_not_loaded" -> return ToolDetail.ConnectorSearch(ConnectorSearchOutcome.NotLoaded)
        "catalogue_off" -> return ToolDetail.ConnectorSearch(ConnectorSearchOutcome.Off)
        "unavailable" -> return ToolDetail.ConnectorSearch(ConnectorSearchOutcome.Unavailable)
        else -> return null
    }
    val rows = data["matches"] as? JsonArray
    var matches = 0L
    rows?.take(10)?.forEach { row ->
        if (row !is JsonObject) return@forEach
        val name = jsonString(row["name"])
        val id = jsonString(row["catalogId"])
        val label = if (name != null && name.isNotBlank()) name.trim() else id?.trim() ?: ""
        if (label.isNotEmpty()) matches++
    }
    return if (matches == 0L) {
        ToolDetail.ConnectorSearch(ConnectorSearchOutcome.Empty)
    } else {
        ToolDetail.Count(ToolCount(ToolCountKind.Found, matches))
    }
}

// JSON readers that read a value the way the Dart client's dynamic maps do.

/** Dart `_valueString`: the value as trimmed text, null when absent or blank. */
private fun jsonText(value: JsonElement?): String? {
    val raw =
        when (value) {
            null, JsonNull -> return null
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    val text = raw.trim()
    return text.ifEmpty { null }
}

/** Dart `value is String ? value : null`: only a JSON string, untrimmed. */
private fun jsonString(value: JsonElement?): String? =
    (value as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Dart `value == true`: only the JSON boolean true. */
private fun jsonIsTrue(value: JsonElement?): Boolean =
    value is JsonPrimitive && !value.isString && value.content == "true"

/** Dart `value == false`: only the JSON boolean false. */
private fun jsonIsFalse(value: JsonElement?): Boolean =
    value is JsonPrimitive && !value.isString && value.content == "false"

/**
 * Dart `_valueNumber` (`value is num ? value : num.tryParse('$value')`) as a whole number: a JSON number, or text
 * that parses as one. A fraction is cut to its whole part (the server sends whole counts).
 */
private fun jsonWholeNumber(value: JsonElement?): Long? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val text = primitive.content.trim()
    text.toLongOrNull()?.let { return it }
    val double = text.toDoubleOrNull() ?: return null
    return if (double.isNaN() || double.isInfinite()) null else double.toLong()
}

/** The last non-empty segment of a path, either slash; the value itself when it has none (Dart `_fileName`). */
private fun fileNameOf(value: String): String {
    val parts = value.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    return if (parts.isEmpty()) value else parts.last()
}
