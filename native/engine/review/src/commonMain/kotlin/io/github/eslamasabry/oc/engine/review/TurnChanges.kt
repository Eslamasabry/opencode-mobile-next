package io.github.eslamasabry.oc.engine.review

import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** How a completed tool changed a file (Dart `RunFileChange`). */
public enum class FileChangeKind { Edited, Written, Patched }

/**
 * A file a turn's COMPLETED edit/write/patch tools touched (Dart `RunFileStat`), tool evidence only: never a verified
 * working-tree diff. [added]/[removed] are null unless every change to the file reported a number (B-CHAT-068).
 * The diff text is what the tools reported: [before]/[after] from the first and last full-file pair, else the [patches]
 * in order. Both empty: the server reported no diff for this file.
 */
public data class ChangedFile(
    val path: String,
    val kind: FileChangeKind,
    val added: Int?,
    val removed: Int?,
    val before: String? = null,
    val after: String? = null,
    val patches: ImmutableList<String> = kotlinx.collections.immutable.persistentListOf(),
    /** True when [before] is the file as it was before the first change (a pair, or a new file). */
    val hasPair: Boolean = false,
) {
    val name: String get() = path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path

    /** The folder under the name, or null at the top. */
    val folder: String? get() = path.trimEnd('/', '\\').let { p -> p.lastIndexOfAny(charArrayOf('/', '\\')).takeIf { it > 0 }?.let { p.substring(0, it) } }

    /** True when the tools left something a diff can show. */
    val hasDiff: Boolean get() = hasPair || patches.isNotEmpty()

    override fun toString(): String = "ChangedFile(${name.length} chars, $kind, +$added -$removed, pair=$hasPair, patches=${patches.size})"
}

/**
 * Turn and conversation file facts from the server's message records (Dart `RunResult.editedPaths` and
 * `editedFileStats`, `chat_files.dart` `_turnChangedFiles`). Pure; every input is copied or marked unknown, never
 * guessed.
 */
public object TurnChanges {
    /** Dart `RunResult.maxChangedFiles`. */
    public const val MAX_FILES: Int = 50

    /** Dart `RunResult._changeOf`. */
    public fun changeOf(tool: String?): FileChangeKind? =
        when (tool?.lowercase()) {
            "edit", "multiedit" -> FileChangeKind.Edited
            "write" -> FileChangeKind.Written
            "patch", "apply_patch" -> FileChangeKind.Patched
            else -> null
        }

    /** A completed, executed edit-family tool (B-CHAT-101: changed files come only from completed executed edit tools). */
    private fun editTool(part: Part): Pair<FileChangeKind, ToolState>? {
        if (part.type != PartType.Tool) return null
        val kind = changeOf(part.toolName) ?: return null
        val state = part.tool ?: return null
        if (state.status != ToolStatus.Completed || !state.ran) return null
        return kind to state
    }

    /** Paths first seen, once, at most [max] (B-CHAT-120). */
    public fun editedPaths(messages: Iterable<MessageWithParts>, max: Int = MAX_FILES): ImmutableList<String> {
        val out = ArrayList<String>()
        for (message in messages) for (part in message.parts) {
            val (_, state) = editTool(part) ?: continue
            for (path in pathsOf(state)) {
                if (path in out) continue
                if (out.size >= max) return out.toImmutableList()
                out += path
            }
        }
        return out.toImmutableList()
    }

    /** The files [messages] changed in the order first touched, with the counts the tools reported (B-CHAT-068). */
    public fun editedFiles(messages: Iterable<MessageWithParts>, max: Int = MAX_FILES): ImmutableList<ChangedFile> {
        val order = LinkedHashMap<String, Acc>()
        for (message in messages) for (part in message.parts) {
            val (kind, state) = editTool(part) ?: continue
            val metadata = state.metadata
            val perFile = HashMap<String, JsonObject>()
            (metadata?.get("files") as? JsonArray)?.forEach { file ->
                val obj = file as? JsonObject ?: return@forEach
                val path = pathField(obj) ?: return@forEach
                perFile[path] = obj
            }
            for (path in pathsOf(state)) {
                val acc = order[path] ?: if (order.size >= max) continue else Acc(path, kind).also { order[path] = it }
                when (kind) {
                    FileChangeKind.Edited -> {
                        val diff = metadata?.get("filediff") as? JsonObject
                        acc.count(diff?.let { count(it["additions"]) }, diff?.let { count(it["deletions"]) })
                        val before = diff?.string("before")
                        val after = diff?.string("after")
                        acc.pair(before, after)
                        metadata?.string("diff")?.let(acc::patch)
                    }
                    FileChangeKind.Patched -> {
                        val file = perFile[path]
                        acc.count(file?.let { count(it["additions"]) }, file?.let { count(it["deletions"]) })
                        (file?.string("patch") ?: file?.string("diff"))?.let(acc::patch)
                        if (file != null) acc.pair(file.string("before"), file.string("after"))
                        else acc.pair(null, null)
                    }
                    FileChangeKind.Written -> {
                        val content = state.input.string("content")
                        // Only a new file's lines are a known count; an overwrite's difference was never reported.
                        val created = (metadata?.get("exists") as? JsonPrimitive)?.booleanOrNull == false
                        if (created && content != null) {
                            acc.count(lineCount(content), 0)
                            acc.pair("", content)
                        } else {
                            acc.count(null, null)
                            acc.pair(null, null)
                        }
                    }
                }
            }
        }
        return order.values.map { it.build() }.toImmutableList()
    }

    /**
     * The assistant steps of the turn [messageId] belongs to (a prompt or any of its replies): from its prompt to the
     * next prompt. History in any order; ties keep input order. Empty when the message is not in [messages].
     */
    public fun turnSteps(messages: List<MessageWithParts>, messageId: String): List<MessageWithParts> {
        val ordered = chronological(messages)
        val at = ordered.indexOfFirst { it.info.id == messageId }
        if (at < 0) return emptyList()
        var start = at
        while (start >= 0 && !isPrompt(ordered[start])) start--
        val out = ArrayList<MessageWithParts>()
        var i = start + 1
        while (i < ordered.size && !isPrompt(ordered[i])) {
            if (ordered[i].info.role == Role.Assistant) out += ordered[i]
            i++
        }
        return out
    }

    /** The prompt that starts the turn [messageId] belongs to, or null (B-CHAT-104: synthetic rows are not prompts). */
    public fun turnPrompt(messages: List<MessageWithParts>, messageId: String): String? {
        val ordered = chronological(messages)
        var at = ordered.indexOfFirst { it.info.id == messageId }
        while (at >= 0 && !isPrompt(ordered[at])) at--
        return if (at >= 0) ordered[at].info.id else null
    }

    /** Every assistant step after the prompt [promptId], to the end (Undo from here). */
    public fun stepsAfter(messages: List<MessageWithParts>, promptId: String): List<MessageWithParts> {
        val ordered = chronological(messages)
        val at = ordered.indexOfFirst { it.info.id == promptId }
        if (at < 0) return emptyList()
        return ordered.subList(at + 1, ordered.size).filter { it.info.role == Role.Assistant }
    }

    /** The conversation's first real prompt: "Undo all" goes back to before it. */
    public fun firstPrompt(messages: List<MessageWithParts>): String? = chronological(messages).firstOrNull(::isPrompt)?.info?.id

    /**
     * A person's prompt. A user message whose every part is synthetic (a server-made row) is not one (B-CHAT-104).
     * An attachment-only prompt (no text) still is.
     */
    public fun isPrompt(message: MessageWithParts): Boolean =
        message.info.role == Role.User && (message.parts.isEmpty() || message.parts.any { !it.synthetic })

    /** The prompt's words, or file names for an attachment-only prompt (Dart `sessionRevertPrompt`); "" if neither. */
    public fun promptText(message: MessageWithParts): String {
        val text = message.parts.filter { it.type == PartType.Text && !it.synthetic }.joinToString("\n") { it.text }.trim()
        if (text.isNotEmpty()) return text
        return message.parts.filter { it.type == PartType.File }.mapNotNull { it.filename?.takeIf(String::isNotBlank) }.joinToString(", ")
    }

    /** The diff of one changed file as the tools reported it, as a core FileDiff (for the kit diff view). */
    public fun asFileDiff(file: ChangedFile): FileDiff =
        FileDiff(
            file = file.path,
            before = if (file.hasPair) file.before else null,
            after = if (file.hasPair) file.after else null,
            patch = if (file.hasPair) null else file.patches.joinToString("\n").ifEmpty { null },
            additions = file.added,
            deletions = file.removed,
            status = if (file.hasPair && file.before.isNullOrEmpty() && file.kind == FileChangeKind.Written) "added" else null,
        )

    private fun chronological(messages: List<MessageWithParts>): List<MessageWithParts> =
        messages.withIndex().sortedWith(compareBy<IndexedValue<MessageWithParts>> { it.value.info.time?.createdMs ?: Long.MAX_VALUE }.thenBy { it.index }).map { it.value }

    /** Dart `RunResult._pathsOf`: metadata.files[] paths, else input filePath/path/file. */
    private fun pathsOf(state: ToolState): List<String> {
        val out = ArrayList<String>()
        fun add(raw: String?) {
            val path = raw?.trim()
            if (!path.isNullOrEmpty() && path !in out) out += path
        }
        (state.metadata?.get("files") as? JsonArray)?.forEach { (it as? JsonObject)?.let { obj -> add(pathField(obj)) } }
        if (out.isEmpty()) add(state.input.string("filePath") ?: state.input.string("path") ?: state.input.string("file"))
        return out
    }

    private fun pathField(obj: JsonObject): String? =
        (obj.string("path") ?: obj.string("filePath") ?: obj.string("file"))?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun count(raw: JsonElement?): Int? {
        if (raw == null || raw is JsonNull) return null
        val value = (raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
        return if (value >= 0) value.toInt() else null
    }

    private fun lineCount(text: String): Int {
        if (text.isEmpty()) return 0
        val lines = text.count { it == '\n' }
        return if (text.endsWith('\n')) lines else lines + 1
    }

    private class Acc(val path: String, val kind: FileChangeKind) {
        private var added: Int? = 0
        private var removed: Int? = 0
        private var first = true
        private var before: String? = null
        private var after: String? = null
        private var pairBroken = false
        private val patches = ArrayList<String>()

        fun count(plus: Int?, minus: Int?) {
            added = if (added == null || plus == null) null else added!! + plus
            removed = if (removed == null || minus == null) null else removed!! + minus
        }

        /** The pair spans the turn only when every change reported one: first before, last after. */
        fun pair(b: String?, a: String?) {
            if (b == null || a == null) pairBroken = true
            if (first) before = b
            after = a
            first = false
        }

        fun patch(text: String) {
            if (text.isNotBlank()) patches += text
        }

        fun build(): ChangedFile =
            ChangedFile(
                path = path,
                kind = kind,
                added = added,
                removed = removed,
                before = if (pairBroken) null else before,
                after = if (pairBroken) null else after,
                patches = patches.toImmutableList(),
                hasPair = !pairBroken && before != null && after != null,
            )
    }
}
