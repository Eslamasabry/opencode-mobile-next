package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ConnectorSearchOutcome
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolCount
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolCountKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolDetail
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolIdWords
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFile
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The live step says what is happening, with a short subject and the server's technical text
 * masked.
 */
@Composable
internal fun runningStepWords(f: ToolFacts): String {
    val mask = rememberKitMask()
    var name = f.subject?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    if (f.kind == ToolKind.Shell)
        name =
            name?.replace(Regex("^cd\\s+(\"[^\"]*\"|\\S+)\\s*(;|&&)\\s*"), "")?.trim()?.takeIf {
                it.isNotEmpty()
            }
    if (name != null) {
        name = mask(if (name.length > 40) name.take(39) + "…" else name)
        if (f.technical) name = "\u2066$name\u2069"
    }
    return when (f.kind) {
        ToolKind.Read,
        ToolKind.WebFetch ->
            if (name == null) stringResource(Res.string.chatLiveReadingAny)
            else stringResource(Res.string.chatLiveReading, name)
        ToolKind.Edit,
        ToolKind.Patch ->
            if (name == null) stringResource(Res.string.chatLiveEditingAny)
            else stringResource(Res.string.chatLiveEditing, name)
        ToolKind.Write ->
            if (name == null) stringResource(Res.string.chatLiveWritingAny)
            else stringResource(Res.string.chatLiveWriting, name)
        ToolKind.Shell ->
            if (name == null) stringResource(Res.string.chatLiveRunningAny)
            else stringResource(Res.string.chatLiveRunning, name)
        ToolKind.Grep,
        ToolKind.Glob,
        ToolKind.List ->
            if (name == null) stringResource(Res.string.chatLiveSearchingAny)
            else stringResource(Res.string.chatLiveSearching, name)
        ToolKind.WebSearch -> stringResource(Res.string.chatLiveSearchingWeb)
        ToolKind.Todo,
        ToolKind.Plan -> stringResource(Res.string.chatLivePlanning)
        else -> if (name == null) stepTitle(f) else "${stepTitle(f)} · $name"
    }
}

@Composable
internal fun stepSubjectWords(f: ToolFacts): String? =
    f.subject ?: f.subjectCount?.let { countWords(it) }

@Composable
internal fun stepDetailWords(f: ToolFacts): String? =
    f.details
        .map { detail ->
            when (detail) {
                is ToolDetail.Count -> countWords(detail.count)
                is ToolDetail.LineRange ->
                    stringResource(Res.string.chatUiLineRange, detail.start, detail.end)
                is ToolDetail.PageStatus ->
                    stringResource(
                        Res.string.toolCardPageStatus,
                        listOfNotNull(detail.code.toString(), detail.text).joinToString(" "),
                    )
                is ToolDetail.ServerText -> detail.text
                ToolDetail.NewFile -> stringResource(Res.string.chatUiNewFile)
                ToolDetail.Updated -> stringResource(Res.string.chatUiUpdated)
                ToolDetail.Truncated -> stringResource(Res.string.chatUiTruncated)
                is ToolDetail.ConnectorSearch ->
                    stringResource(
                        when (detail.outcome) {
                            ConnectorSearchOutcome.Empty -> Res.string.chatUiConnectorSearchEmpty
                            ConnectorSearchOutcome.NotLoaded ->
                                Res.string.chatUiConnectorSearchNotLoaded
                            ConnectorSearchOutcome.Off -> Res.string.chatUiConnectorSearchOff
                            ConnectorSearchOutcome.Unavailable ->
                                Res.string.chatUiConnectorSearchUnavailable
                        }
                    )
            }
        }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

@Composable
private fun countWords(count: ToolCount): String {
    val n = count.value
    val quantity = n.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    return when (count.kind) {
        ToolCountKind.FromLine -> stringResource(Res.string.chatUiFromLine, n)
        ToolCountKind.Lines -> pluralStringResource(Res.plurals.chatUiLineCount, quantity, n)
        ToolCountKind.Entries -> pluralStringResource(Res.plurals.chatUiEntryCount, quantity, n)
        ToolCountKind.Found -> stringResource(Res.string.chatUiFoundCount, n)
        ToolCountKind.Matches -> pluralStringResource(Res.plurals.chatUiMatchCount, quantity, n)
        ToolCountKind.Files -> pluralStringResource(Res.plurals.chatUiFileCount, quantity, n)
        ToolCountKind.Results -> pluralStringResource(Res.plurals.chatUiResultCount, quantity, n)
        ToolCountKind.Asked -> stringResource(Res.string.chatUiAskedCount, n)
        ToolCountKind.Answered -> stringResource(Res.string.chatUiAnsweredCount, n)
    }
}

/**
 * The opened step reads the tool's structured facts; it never replays a call or invents an outcome.
 */
@Composable
internal fun ToolStepBody(step: TurnStep, reply: ReplyActions?) {
    val output = step.output
    val input = output.input
    val metadata = output.metadata ?: JsonObject(emptyMap())
    val kind = step.facts.kind
    val text = output.text
    if (output.pruned) KitQuietLine(stringResource(Res.string.chatUiOutputPruned))
    if (kind == ToolKind.Card || step.state == StepState.NotRun || step.state == StepState.Stopped)
        return
    if (kind == ToolKind.Task) {
        TaskBody(step, reply)
        return
    }
    if (output.errorText != null) {
        if (kind == ToolKind.Generic)
            Readable(input, reply, stringResource(Res.string.toolCardWhatWasSent))
        if (kind == ToolKind.AgentMessage) AgentMessageBody(step, reply)
        TechnicalText(text.removePrefix("Error:").trim(), reply)
        return
    }
    when (kind) {
        ToolKind.Read -> {
            val display = metadata["display"] as? JsonObject
            val entries = display?.get("entries") as? JsonArray
            val displayed = display?.word("text")
            when {
                display?.word("type") == "directory" && entries != null ->
                    StepOutput(entries.joinToString("\n") { it.words() }, reply)
                !displayed.isNullOrEmpty() -> StepOutput(displayed, reply, code = true)
                text.contains("<entries>") ->
                    StepOutput(
                        text.substringAfter("<entries>").substringBefore("</entries>").trim(),
                        reply,
                    )
                text.contains("<content>") ->
                    StepOutput(
                        text
                            .substringAfter("<content>")
                            .substringBefore("</content>")
                            .trim('\n')
                            .lineSequence()
                            .map { it.replace(Regex("^\\d+: "), "") }
                            .filterNot { it.startsWith("(End of file") }
                            .joinToString("\n"),
                        reply,
                        code = true,
                    )
                text.isNotBlank() -> StepOutput(text, reply, code = true)
            }
        }
        ToolKind.Edit,
        ToolKind.Write,
        ToolKind.Patch -> {
            val files = remember(kind, input, metadata) { stepDiffs(kind, input, metadata) }
            if (files.isEmpty()) StepOutput(text, reply) else files.forEach { StepDiff(it) }
            metadata["diagnostics"]
                ?.takeIf { it != JsonNull }
                ?.let { TechnicalText(it.toString(), reply) }
        }
        ToolKind.WebFetch,
        ToolKind.WebSearch,
        ToolKind.Plan,
        ToolKind.Skill -> {
            if (kind == ToolKind.WebFetch) input.word("url")?.let { MaskedLine(it) }
            if (text.isNotBlank()) {
                if (input.word("format") == "html") StepOutput(text, reply)
                else StepProse(text, reply, step.partId)
            } else if (metadata["httpCode"] == null) Readable(input, reply)
        }
        ToolKind.Question -> {
            val questions = input["questions"] as? JsonArray
            val answers = metadata["answers"] as? JsonArray
            if (questions.isNullOrEmpty()) StepOutput(text, reply)
            else
                questions.forEachIndexed { i, value ->
                    val question = (value as? JsonObject)?.word("question") ?: value.words()
                    MaskedLine(question)
                    val answer = answers?.getOrNull(i)
                    val words =
                        if (answer is JsonArray) answer.joinToString(", ") { it.words() }
                        else answer?.words()
                    if (words.isNullOrBlank())
                        KitQuietLine(stringResource(Res.string.chatUiNoAnswer))
                    else MaskedLine(stringResource(Res.string.chatUiAnsweredDetail, words))
                }
        }
        ToolKind.Todo -> {
            val todos = (metadata["todos"] ?: input["todos"]) as? JsonArray
            if (todos.isNullOrEmpty()) StepOutput(text, reply)
            else
                todos.forEach { value ->
                    val todo = value as? JsonObject
                    val content = todo?.word("content") ?: todo?.word("text") ?: value.words()
                    val mark = if (todo?.word("status") == "completed") "☑" else "☐"
                    MaskedLine("$mark $content")
                }
        }
        ToolKind.WorktreeSetup -> {
            metadata.word("worktreePath")?.let {
                SafeRow(stringResource(Res.string.workspaceContextFolder), it)
            }
            metadata.word("branchName")?.let {
                SafeRow(stringResource(Res.string.managedWorkspacesBranchLabel), it)
            }
            (metadata["commands"] as? JsonArray)?.forEach { value ->
                val command = value as? JsonObject ?: return@forEach
                command.word("command")?.let { StepOutput(it, reply, command = true) }
                val exit = command.word("exitCode")?.toIntOrNull()
                if (exit != null)
                    KitQuietLine(
                        stepStatus(if (exit == 0) StepState.Done else StepState.Failed, exit)
                    )
                command.word("log")?.let { StepOutput(it, reply) }
            }
            if (text.isNotBlank()) StepOutput(text, reply)
        }
        ToolKind.AgentMessage -> AgentMessageBody(step, reply)
        ToolKind.ConnectorSearch -> ConnectorSearchBody(text, reply)
        ToolKind.Lsp ->
            StepOutput(
                (metadata["result"] ?: decoded(text))?.let {
                    if (it is JsonPrimitive) it.words() else it.toString()
                } ?: text,
                reply,
            )
        ToolKind.Generic -> {
            Readable(input, reply, stringResource(Res.string.toolCardWhatWasSent))
            val value = remember(text) { decoded(text) }
            if (value is JsonObject || value is JsonArray)
                Readable(
                    value,
                    reply,
                    stringResource(Res.string.toolCardWhatCameBack),
                    simpleValues(input),
                )
            else if (text.isNotBlank()) StepOutput(text, reply)
        }
        ToolKind.List,
        ToolKind.Glob,
        ToolKind.Grep -> {
            input.word("path")?.let { MaskedLine(it) }
            input.word("include")?.let { MaskedLine(it) }
            StepOutput(text, reply)
        }
        ToolKind.BackgroundNotice -> {
            val seen = mutableSetOf<String>()
            listOfNotNull(
                    input.word("summary"),
                    input.word("description"),
                    input.word("name"),
                    text.takeIf { it.isNotBlank() },
                )
                .filter { seen.add(it) }
                .forEach { MaskedLine(it) }
        }
        ToolKind.Shell,
        ToolKind.Card,
        ToolKind.Task -> Unit
    }
}

@Composable
private fun TaskBody(step: TurnStep, reply: ReplyActions?) {
    val input = step.output.input
    val meta = step.output.metadata
    val model = meta?.get("model")
    input.word("description")?.let { MaskedLine(it) }
    val modelName = (model as? JsonObject)?.word("modelID") ?: (model as? JsonPrimitive)?.content
    modelName?.let { SafeRow(stringResource(Res.string.chatUiModel), it) }
    input.word("prompt")?.let {
        TechnicalText(it, reply, stringResource(Res.string.chatUiPromptFromParentAgent))
    }
    val launched =
        step.facts.nativeSubagent &&
            meta?.word("status") == "running" &&
            step.state == StepState.Done
    val raw = step.output.text
    val result =
        if (step.facts.nativeSubagent)
            raw.replace(Regex("^\\s*<subagent\\b[^>]*>\\n?"), "")
                .replace(Regex("\\n?</subagent>\\s*$"), "")
                .trim()
        else
            Regex(
                    "<task_(?:result|error)>\\n?(.*?)\\n?</task_(?:result|error)>",
                    RegexOption.DOT_MATCHES_ALL,
                )
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.trim() ?: raw.trim()
    when {
        launched -> KitQuietLine(stringResource(Res.string.workStartedInBackground))
        step.output.errorText != null -> TechnicalText(result, reply)
        result.isNotEmpty() -> StepProse(result, reply, step.partId)
        step.state == StepState.Running || step.state == StepState.Pending ->
            KitQuietLine(stringResource(Res.string.chatUiSubagentWorking))
        else -> KitQuietLine(stringResource(Res.string.chatUiNoResult))
    }
    val actions = meta?.get("actions") as? JsonArray
    if (!actions.isNullOrEmpty()) {
        KitQuietLine(stringResource(Res.string.toolCardWhatItDid))
        actions.take(12).forEach { value ->
            val action = value as? JsonObject ?: return@forEach
            action.word("tool")?.let { SafeRow(toolIdWords(it), action.word("summary") ?: "") }
        }
    }
}

@Composable
private fun AgentMessageBody(step: TurnStep, reply: ReplyActions?) {
    val input = step.output.input
    val result = remember(step.output.text) { decoded(step.output.text) as? JsonObject }
    val message = input["message"] ?: input["content"]
    val text = (message as? JsonPrimitive)?.content ?: result?.word("content")
    if (!text.isNullOrBlank()) StepProse(text, reply, step.partId)
    if (message is JsonObject) TechnicalText(message.toString(), reply)
    val recipient =
        input.word("to")
            ?: input.word("recipient")
            ?: result?.word("to")
            ?: result?.word("recipient")
    if (
        recipient != null &&
            !recipient.contains(' ') &&
            Regex("(?=[0-9a-f]*[0-9])[0-9a-f]{8,}", RegexOption.IGNORE_CASE)
                .containsMatchIn(recipient)
    )
        TechnicalText(recipient, reply)
}

@Composable
private fun ConnectorSearchBody(text: String, reply: ReplyActions?) {
    val result = remember(text) { if (text.length <= 32768) decoded(text) as? JsonObject else null }
    val matches = result?.get("matches") as? JsonArray
    if (!matches.isNullOrEmpty() && result?.word("status") == "ok")
        matches.take(10).forEach { value ->
            val match = value as? JsonObject ?: return@forEach
            val runtime =
                when (match.word("runtime")) {
                    "hosted" -> stringResource(Res.string.connectorCardRunsHosted)
                    "npx" -> stringResource(Res.string.connectorCardRunsNode)
                    "uvx" -> stringResource(Res.string.connectorCardRunsPython)
                    "docker" -> stringResource(Res.string.connectorCardRunsDocker)
                    else -> stringResource(Res.string.connectorCardRunsNone)
                }
            val connected =
                when (match.word("connected")) {
                    "true" -> stringResource(Res.string.connectorCardConnected)
                    "false" -> stringResource(Res.string.chatUiConnectorSearchNotConnected)
                    else -> null
                }
            SafeRow(
                (match.word("name") ?: match.word("catalogId") ?: "").take(120),
                listOfNotNull(runtime, connected).joinToString(" · "),
            )
        }
    else if (result != null) {
        KitQuietLine(
            stringResource(
                when (result.word("status")) {
                    "ok" -> Res.string.chatUiConnectorSearchEmpty
                    "catalogue_not_loaded" -> Res.string.chatUiConnectorSearchNotLoaded
                    "catalogue_off" -> Res.string.chatUiConnectorSearchOffBody
                    else -> Res.string.chatUiConnectorSearchUnavailable
                }
            )
        )
    } else StepOutput(text, reply)
}

@Composable
private fun SafeRow(label: String, value: String) {
    val mask = rememberKitMask()
    if (label.isNotBlank())
        KitRow(title = mask(label), supporting = mask(value), supportingMaxLines = 12)
}

@Composable
private fun MaskedLine(text: String) {
    val mask = rememberKitMask()
    KitQuietLine(mask(text))
}

@Composable
private fun Readable(
    value: JsonElement,
    reply: ReplyActions?,
    label: String? = null,
    shown: Set<String> = emptySet(),
) {
    if (value is JsonObject) {
        val seen = shown.toMutableSet()
        val nested = linkedMapOf<String, JsonElement>()
        var rows = 0
        value.forEach { (key, item) ->
            if (
                item is JsonPrimitive &&
                    item != JsonNull &&
                    item.content.isNotBlank() &&
                    seen.add(item.content)
            ) {
                if (rows++ < 12)
                    SafeRow(
                        toolIdWords(key).replaceFirstChar { it.uppercase() },
                        item.content.take(1000),
                    )
                else nested[key] = item
            } else if (item !is JsonPrimitive && item != JsonNull) nested[key] = item
        }
        if (nested.isNotEmpty()) TechnicalText(JsonObject(nested).toString(), reply, label)
    } else if (value is JsonArray && value.all { it is JsonPrimitive })
        StepOutput(value.joinToString("\n") { it.words() }, reply)
    else TechnicalText(value.toString(), reply, label)
}

@Composable
private fun TechnicalText(text: String, reply: ReplyActions?, label: String? = null) {
    if (text.isBlank()) return
    var open by rememberSaveable(text) { mutableStateOf(false) }
    KitExpandRow(
        title = label ?: stringResource(Res.string.toolCardTechnicalDetails),
        expanded = open,
        onExpandedChange = { open = it },
    ) {
        StepOutput(text, reply)
    }
}

@Composable
private fun StepOutput(
    text: String,
    reply: ReplyActions?,
    code: Boolean = false,
    command: Boolean = false,
) {
    if (text.isBlank()) return
    val mask = rememberKitMask()
    var full by rememberSaveable(text) { mutableStateOf(false) }
    val more = stringResource(Res.string.tools44ShowAll)
    val shown = if (full) text else text.lineSequence().take(200).joinToString("\n").take(20_000)
    KitCodeBlock(
        source = shown,
        kind =
            if (command) KitCodeKind.Command
            else if (code) KitCodeKind.Code else KitCodeKind.Output,
        language = CodeLanguage.Plain,
        maxLines = if (full) Int.MAX_VALUE else 12,
        copyLabel = reply?.copyLabel,
        onCopy = if (reply == null) null else { _ -> reply.copy(mask(text)) },
        moreLabel = { more },
        onMore = { full = true },
    )
    if (!full && shown.length < text.length && shown.lines().size <= 12)
        KitButton(more, { full = true }, role = KitButtonRole.Tertiary)
}

@Composable
private fun StepProse(text: String, reply: ReplyActions?, key: String) {
    if (reply == null) {
        StepOutput(text, null)
        return
    }
    var full by rememberSaveable(key, text) { mutableStateOf(false) }
    val mask = rememberKitMask()
    val shown = if (full) text else text.lineSequence().take(12).joinToString("\n").take(4000)
    val masked = mask(shown)
    val blocks =
        remember(key, masked) { KitMdSource.blocks("tool:$key", masked, streaming = false) }
    Column(Modifier.testTag("tool-prose")) {
        KitMarkdown(blocks, reply.markdown)
        if (shown.length < text.length)
            KitButton(
                stringResource(Res.string.tools44ShowAll),
                { full = true },
                role = KitButtonRole.Tertiary,
            )
    }
}

@Composable
private fun StepDiff(file: DiffFile) {
    var full by rememberSaveable(file.path) { mutableStateOf(false) }
    MaskedLine(file.path)
    val lines = stringResource(Res.string.tools44Lines)
    val hidden = stringResource(Res.string.tools44HiddenLines)
    KitDiffView(
        file,
        maxLines = if (full) Int.MAX_VALUE else 12,
        hunkLabel = { _, _, start, end -> "$lines $start–$end" },
        gapLabel = { "$it $hidden" },
    )
    if (!full && file.totalLines > 12)
        KitButton(
            stringResource(Res.string.tools44ShowAll),
            { full = true },
            role = KitButtonRole.Tertiary,
        )
}

private fun JsonObject.word(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { it != JsonNull }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun JsonElement.words(): String =
    if (this is JsonPrimitive && this != JsonNull) content
    else if (this == JsonNull) "" else toString()

private fun decoded(text: String): JsonElement? =
    try {
        Json.parseToJsonElement(text)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun simpleValues(value: JsonObject): Set<String> =
    value.values.filterIsInstance<JsonPrimitive>().map { it.content }.toSet()

private fun stepDiffs(kind: ToolKind, input: JsonObject, metadata: JsonObject): List<DiffFile> {
    val path =
        input.word("filePath") ?: input.word("file_path") ?: input.word("path") ?: "changes.diff"
    if (kind == ToolKind.Patch) {
        val files = metadata["files"] as? JsonArray
        val patches =
            files?.mapNotNull { value ->
                val file = value as? JsonObject ?: return@mapNotNull null
                val patch = file.word("patch") ?: return@mapNotNull null
                DiffModel.parseFilePatch(
                    file.word("relativePath")
                        ?: file.word("movePath")
                        ?: file.word("filePath")
                        ?: path,
                    patch,
                )
            } ?: emptyList()
        if (patches.isNotEmpty()) return patches
        val patch = metadata.word("diff") ?: input.word("patchText") ?: return emptyList()
        return listOf(DiffModel.parseFilePatch(path, patch))
    }
    if (kind == ToolKind.Write) {
        val content = (input["content"] as? JsonPrimitive)?.content ?: return emptyList()
        return listOf(DiffModel.fromBeforeAfter(path, null, content))
    }
    val patch = (metadata["filediff"] as? JsonObject)?.word("patch") ?: metadata.word("diff")
    if (patch != null) return listOf(DiffModel.parseFilePatch(path, patch))
    val before = (input["oldString"] as? JsonPrimitive)?.content
    val after = (input["newString"] as? JsonPrimitive)?.content
    return if (before == null && after == null) emptyList()
    else listOf(DiffModel.fromBeforeAfter(path, before, after))
}
