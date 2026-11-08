package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong

/**
 * S28 story 4 (Flutter `showPermissionDetails`, permission_sheet.dart:422-514): what a permission card's Details
 * sheet shows. [fullText] is the whole command, file or pattern list ([command] says it is a shell command, drawn
 * with its `$`); [diff] the change an edit asks to make; [facts] the tool's technical names, in Flutter's order. No
 * copy: the screen names each [PermissionFact]. All of it is server data and is masked before it is drawn.
 */
public data class PermissionDetailsUi(
    val fullText: String?,
    val command: Boolean,
    val diff: String?,
    val facts: List<PermissionFact>,
)

/** One technical value under Details. */
public sealed interface PermissionFact {
    /** "Runs in": the folder a command runs in (`metadata.cwd`). */
    public data class RunsIn(val folder: String) : PermissionFact

    /** "What it asks for": the agent's own words (`metadata.prompt`). */
    public data class Asks(val words: String) : PermissionFact

    /** "Part of the file": a read of part of a file, "from line [from], [lines] lines". */
    public data class Range(val from: Long?, val lines: Long?) : PermissionFact

    /** "Helper type": the subagent a task ask starts (`metadata.subagent_type`). */
    public data class Helper(val type: String) : PermissionFact

    /** "Tool": the permission's own name (`bash`, `edit`, …). */
    public data class Tool(val name: String) : PermissionFact

    /** "Requested patterns": the patterns besides the command, one per line. */
    public data class Patterns(val lines: List<String>) : PermissionFact

    /** "Always allow would also cover": what an Always answer saves. */
    public data class AlsoCovers(val lines: List<String>) : PermissionFact
}

public object PermissionDetails {
    private val commandPermissions = setOf("bash", "shell")
    private val filePermissions = setOf("edit", "write", "read", "multiedit", "patch", "external_directory")
    private val fileKeys = listOf("filePath", "filepath", "file_path", "path", "file")
    private val diffPermissions = setOf("edit", "write", "multiedit", "patch")
    private val diffKeys = listOf("diff", "patch", "preview")

    public fun of(request: PermissionRequest): PermissionDetailsUi {
        val command = text(request, listOf("command", "cmd"))
            ?: if (request.permission.lowercase() in commandPermissions) firstPattern(request) else null
        val path = text(request, fileKeys)
            ?: if (request.permission.lowercase() in filePermissions) firstPattern(request) else null
        val shown = setOfNotNull(command, path)
        val others = request.patterns.filter { it !in shown }
        val facts = buildList {
            text(request, listOf("cwd"))?.let { add(PermissionFact.RunsIn(it)) }
            text(request, listOf("prompt"))?.let { add(PermissionFact.Asks(it)) }
            range(request)?.let(::add)
            text(request, listOf("subagent_type"))?.let { add(PermissionFact.Helper(it)) }
            if (request.permission.isNotEmpty()) add(PermissionFact.Tool(request.permission))
            if (command != null && others.isNotEmpty()) add(PermissionFact.Patterns(others))
            if (request.always.isNotEmpty()) add(PermissionFact.AlsoCovers(request.always))
        }
        val fullText = command ?: path ?: others.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: request.message
        return PermissionDetailsUi(
            fullText = fullText,
            command = command != null,
            diff = diff(request),
            facts = facts,
        )
    }

    private fun text(request: PermissionRequest, keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key ->
            (request.metadata[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        }

    private fun number(request: PermissionRequest, key: String): Long? =
        (request.metadata[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.roundToLong()

    private fun firstPattern(request: PermissionRequest): String? =
        request.patterns.firstOrNull { it.isNotBlank() && it != "*" }

    private fun range(request: PermissionRequest): PermissionFact.Range? {
        if (request.permission != "read") return null
        val from = number(request, "offset")
        val lines = number(request, "limit")
        return if (from == null && lines == null) null else PermissionFact.Range(from, lines)
    }

    private fun diff(request: PermissionRequest): String? =
        if (request.permission.lowercase() !in diffPermissions) null else text(request, diffKeys)
}
