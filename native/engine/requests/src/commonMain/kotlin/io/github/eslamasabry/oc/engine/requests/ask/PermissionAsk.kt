package io.github.eslamasabry.oc.engine.requests.ask

import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/** What the call asks to do (plan 09 §5.2). `:ui:words` has one question per value; nothing here is copy. */
public enum class AskVerb {
    Run, Edit, Create, Delete, Read, ReadFolder, Fetch, WebSearch, Search, Task, Skill, Mode,
    OutsideWrite, OutsideRead, OutsideRun, Outside, DoomLoop,

    /** Any other tool: the words name it in plain words from [AskView.tool], never the id itself. */
    Use,

    /** An empty permission id: "Permission needed". */
    Generic,
}

/** One thing the call runs or touches, shown in the card's mono block. [added]/[removed] only when the request says. */
public data class AskTarget(val text: String, val added: Int? = null, val removed: Int? = null, val file: Boolean = false)

/** The standing grant's name ("Always allow npm install"), from the request's own `always` patterns. */
public sealed interface AlwaysGrant {
    /** A command family ("npm install"). */
    public data class Named(val what: String) : AlwaysGrant

    public data object AnyCommand : AlwaysGrant

    public data object Edits : AlwaysGrant

    /** A command with no nameable family: "Always allow commands like this". */
    public data object Commands : AlwaysGrant
}

/**
 * A permission ask worded from the request alone (Dart `permissionAsk`, `permission_ask.dart`; B-CHAT-122, B-CHAT-073).
 * [name] is the command or file named in the question, [where] the project or the agent's folder with word joiners
 * around its hyphens, [count] the number of files an edit names. The words layer builds the sentence.
 */
public data class AskView(
    val verb: AskVerb,
    val name: String? = null,
    val where: String? = null,
    val count: Int = 0,
    val tool: String? = null,
    val targets: ImmutableList<AskTarget> = persistentListOf(),
    val always: AlwaysGrant? = null,
)

public object PermissionAsk {
    /** A command this short and on one line reads inside the question. */
    private const val COMMAND_IN_TITLE = 32
    private val shellActions = setOf("bash", "shell")
    private val editActions = setOf("edit", "write", "multiedit", "patch", "apply_patch")
    private val filePermissions = setOf("edit", "write", "read", "list", "ls", "multiedit", "patch", "external_directory")
    private val fileMetadataKeys = listOf("filePath", "filepath", "file_path", "path", "file")
    private val diffMetadataKeys = listOf("diff", "patch", "preview")

    /**
     * [project] is the folder the chat works in (its name ends a shell question); [directory] its path, so an absolute
     * path reads from the project. [toolName] is the tool whose call asks, when the transcript knows it (an
     * `external_directory` ask then says whether it reads, writes or runs); [target] the exact path that call touches.
     */
    public fun of(
        request: PermissionRequest,
        project: String? = null,
        directory: String? = null,
        toolName: String? = null,
        target: String? = null,
    ): AskView {
        val action = request.permission.trim().lowercase()
        if (action in shellActions) return shell(request, project)
        val paths = editPaths(request)
        if (paths.isNotEmpty()) return edit(request, paths, directory)
        if (action == "read" || action in listActions) {
            val read = filePath(request)
            // A listing of the chat's own folder names "." (or the folder's path): that is the folder, never a file
            // called "."; it is named by the project's name.
            if (read != null && isOwnFolder(read, directory)) return ownFolder(project)
            // A listing, or a path that ends in "/.", reads a folder: "Read this folder?" with the folder under it.
            val folder = read?.let(::folderOf) ?: read?.takeIf { action in listActions }
            if (folder != null) {
                return AskView(
                    AskVerb.ReadFolder,
                    targets = persistentListOf(AskTarget(projectPath(folder, directory), file = true)),
                )
            }
            if (read != null) {
                return AskView(
                    AskVerb.Read,
                    name = baseName(read),
                    targets = persistentListOf(AskTarget(projectPath(read, directory), file = true)),
                )
            }
        }
        val verb = verbOf(action, toolName)
        if (verb == AskVerb.ReadFolder) return ownFolder(project)
        val shown =
            target?.takeIf { it.isNotBlank() }
                ?: when (verb) {
                    // A search names what it looks for; its folder ("." or the project's path) is not the subject.
                    AskVerb.Search -> request.metadata.string("pattern")?.takeIf { it.isNotBlank() } ?: firstPattern(request)
                    // The question says it all; its only pattern is the id of the tool that keeps failing.
                    AskVerb.DoomLoop -> null
                    else -> summary(request)
                }
        return AskView(
            verb,
            tool = request.permission.takeIf { verb == AskVerb.Use },
            targets =
                listOfNotNull(
                    shown?.takeUnless { isOwnFolder(it, directory) }?.let { AskTarget(it, file = verb in outsideVerbs) }
                ).toImmutableList(),
        )
    }

    private val listActions = setOf("list", "ls")

    /** "Read this folder?" about the conversation's own folder, named by its project (no target when it has none). */
    private fun ownFolder(project: String?): AskView =
        AskView(
            AskVerb.ReadFolder,
            targets = listOfNotNull(project?.trim()?.takeIf { it.isNotEmpty() }?.let { AskTarget(it) }).toImmutableList(),
        )

    /** "." and its spellings ("./", "<folder>/."), or the folder's own path: the folder the conversation works in. */
    private fun isOwnFolder(path: String, directory: String?): Boolean {
        val raw = path.trim()
        if (raw == "." || raw == "./") return true
        val root = directory?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
        return (folderOf(raw) ?: raw.trimEnd('/')) == root
    }

    /** A path that names a folder by its trailing "/." ("src/."): the folder, else null. */
    private fun folderOf(path: String): String? =
        path.trim().takeIf { it.endsWith("/.") }?.dropLast(2)?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    /** The command, file or patterns in one line (lists, sheets): an edit of several files names every one. */
    public fun summary(request: PermissionRequest): String? {
        val paths = editPaths(request)
        if (paths.size > 1) return paths.joinToString(" · ")
        return commandPreview(request) ?: filePath(request) ?: request.patterns.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val outsideVerbs = setOf(AskVerb.OutsideWrite, AskVerb.OutsideRead, AskVerb.OutsideRun, AskVerb.Outside)

    private fun verbOf(action: String, toolName: String?): AskVerb =
        when (action) {
            "edit", "write", "multiedit", "patch", "apply_patch" -> AskVerb.Edit
            "read" -> AskVerb.Read
            "external_directory" ->
                when (toolName?.trim()?.lowercase()) {
                    "write", "edit", "multiedit", "patch", "apply_patch" -> AskVerb.OutsideWrite
                    "read", "glob", "grep", "list", "ls" -> AskVerb.OutsideRead
                    "bash", "shell" -> AskVerb.OutsideRun
                    else -> AskVerb.Outside
                }
            "webfetch" -> AskVerb.Fetch
            "websearch" -> AskVerb.WebSearch
            "grep", "glob" -> AskVerb.Search
            "list", "ls" -> AskVerb.ReadFolder
            "task" -> AskVerb.Task
            "skill" -> AskVerb.Skill
            "mode" -> AskVerb.Mode
            "doom_loop" -> AskVerb.DoomLoop
            "" -> AskVerb.Generic
            else -> AskVerb.Use
        }

    private fun shell(request: PermissionRequest, project: String?): AskView {
        val command = commandPreview(request)?.trim()
        val cwd = request.metadata.string("cwd")?.trim()
        // A Paseo agent says where it runs; OpenCode runs in the project.
        val where = (if (!cwd.isNullOrEmpty()) baseName(cwd) else project)?.takeIf { it.isNotBlank() }?.let(::whole)
        val always = request.always
        var name: String? = null
        if (command != null && command.isNotEmpty() && '\n' !in command && command.length <= COMMAND_IN_TITLE) {
            name = command
        } else if (command != null) {
            // A long command reads by the family its own "always" pattern names ("npm install *"), only when it starts with it.
            name =
                always.firstNotNullOfOrNull { pattern ->
                    val trimmed = pattern.trim()
                    if (!trimmed.endsWith(" *")) return@firstNotNullOfOrNull null
                    val prefix = trimmed.dropLast(2).trim()
                    prefix.takeIf { it.isNotEmpty() && '*' !in it && it.length <= COMMAND_IN_TITLE && command.startsWith(it) }
                }
        }
        var grant: AlwaysGrant? = null
        if (always.size == 1) {
            val saved = always.single().trim()
            grant =
                if (saved == "*") {
                    AlwaysGrant.AnyCommand
                } else {
                    val family = if (saved.endsWith(" *")) saved.dropLast(2).trim() else saved
                    family
                        .takeIf { it.isNotEmpty() && '*' !in it && '\n' !in it && it.length <= COMMAND_IN_TITLE }
                        ?.let { AlwaysGrant.Named(it) }
                }
        }
        return AskView(
            AskVerb.Run,
            name = name,
            where = where,
            targets = listOfNotNull(command?.takeIf { it.isNotEmpty() }?.let { AskTarget(it) }).toImmutableList(),
            always = grant ?: AlwaysGrant.Commands,
        )
    }

    private fun edit(request: PermissionRequest, paths: List<String>, directory: String?): AskView {
        val facts = fileFacts(request)
        val diff = diffText(request) ?: facts.values.singleOrNull()?.let { it.string("patch") ?: it.string("diff") }
        val single = diffSize(diff)
        val kinds = paths.map { path -> facts[path]?.let(::changeKind) ?: if (paths.size == 1 && createsFile(diff)) "add" else null }.toSet()
        val kind = kinds.singleOrNull()

        fun change(path: String): Pair<Int, Int>? {
            val file = facts[path]
            val added = file?.number("additions")
            val removed = file?.number("deletions")
            if (added != null && removed != null) return added to removed
            return if (paths.size == 1) single else null
        }
        val verb =
            when (kind) {
                "add" -> AskVerb.Create
                "delete" -> AskVerb.Delete
                else -> AskVerb.Edit
            }
        val everyEdit = request.always.size == 1 && request.always.single().trim() == "*"
        return AskView(
            verb,
            name = baseName(paths.first()),
            count = paths.size,
            targets =
                paths.map { path ->
                    val size = change(path)
                    AskTarget(projectPath(path, directory), size?.first, size?.second, file = true)
                }.toImmutableList(),
            always = if (everyEdit) AlwaysGrant.Edits else null,
        )
    }

    /** Every path an edit names, in order, without wildcards: the patterns, else the one path in its metadata. */
    private fun editPaths(request: PermissionRequest): List<String> {
        if (request.permission.trim().lowercase() !in editActions) return emptyList()
        val paths = request.patterns.map { it.trim() }.filter { it.isNotEmpty() && '*' !in it }.toMutableList()
        val path = filePath(request)
        if (paths.isEmpty() && path != null) paths += path
        return paths.distinct()
    }

    /** Dart `PermissionRequest.commandPreview`. */
    private fun commandPreview(request: PermissionRequest): String? =
        request.metadata.firstString(listOf("command", "cmd"))
            ?: if (request.permission.trim().lowercase() in shellActions) firstPattern(request) else null

    /** Dart `PermissionRequest.filePath`. */
    private fun filePath(request: PermissionRequest): String? =
        request.metadata.firstString(fileMetadataKeys)
            ?: if (request.permission.trim().lowercase() in filePermissions) firstPattern(request) else null

    private fun firstPattern(request: PermissionRequest): String? = request.patterns.firstOrNull { it.isNotBlank() && it != "*" }

    /**
     * What a change says per file: OpenCode 1's apply_patch (`metadata.files[]{relativePath,type,additions,deletions}`)
     * and OpenCode 2's write, edit and patch (`metadata.files[]{file,status,additions,deletions,patch}`).
     */
    private fun fileFacts(request: PermissionRequest): Map<String, JsonObject> {
        val raw = request.metadata["files"] as? JsonArray ?: return emptyMap()
        return raw.filterIsInstance<JsonObject>()
            .mapNotNull { file ->
                (file.string("relativePath") ?: file.string("file"))?.trim()?.takeIf { it.isNotEmpty() }?.let { it to file }
            }
            .toMap()
    }

    /** "add" / "delete" / null (a change), from OpenCode 1's `type` or OpenCode 2's `status`. */
    private fun changeKind(file: JsonObject): String? =
        when (file.string("type") ?: file.string("status")) {
            "add", "added" -> "add"
            "delete", "deleted" -> "delete"
            // The server said what it does (a change, a move): never guessed from the diff.
            null -> if (createsFile(file.string("patch") ?: file.string("diff"))) "add" else null
            else -> null
        }

    /** A unified diff whose one hunk starts from nothing (`@@ -0,0 +1,n @@`, `--- /dev/null`): a new file. */
    private fun createsFile(diff: String?): Boolean {
        if (diff.isNullOrBlank()) return false
        val hunks = diff.lineSequence().filter { it.startsWith("@@") }.toList()
        return "\n--- /dev/null" in "\n$diff" || (hunks.size == 1 && hunks.single().startsWith("@@ -0,0 "))
    }

    private fun diffText(request: PermissionRequest): String? = request.metadata.firstString(diffMetadataKeys)

    /** Lines added and removed in one file's unified diff. */
    private fun diffSize(diff: String?): Pair<Int, Int>? {
        if (diff.isNullOrBlank()) return null
        var added = 0
        var removed = 0
        for (line in diff.split('\n')) {
            if (line.startsWith("+++") || line.startsWith("---")) continue
            if (line.startsWith("+")) added++
            if (line.startsWith("-")) removed++
        }
        return added to removed
    }

    /** [path] inside [directory] as the project names it ("src/game.js"); any other path as given. */
    private fun projectPath(path: String, directory: String?): String {
        val root = directory?.trim()
        if (root.isNullOrEmpty()) return path
        val prefix = if (root.endsWith('/')) root else "$root/"
        return if (path.startsWith(prefix)) path.substring(prefix.length) else path
    }

    /** A name that never breaks at its hyphens: word joiners around each one. */
    private fun whole(name: String): String = name.replace("-", "⁠-⁠")

    private fun baseName(path: String): String = path.split('/').lastOrNull { it.isNotEmpty() } ?: path

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.roundToInt()

    private fun JsonObject.firstString(keys: List<String>): String? = keys.firstNotNullOfOrNull { key -> string(key)?.takeIf { it.isNotBlank() } }
}
