package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.domain.unavailable
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoObject
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Reads only the named conversation's folder; gateway location changes retire outstanding reads. */
internal class PaseoFiles(private val gateway: PaseoGateway, private val location: ServerLocation) : FileGateway {
    private suspend inline fun <T> call(crossinline block: suspend () -> T): Outcome<T> =
        withContext(gateway.lane) { paseoAttempt { block() } }

    private val directory: String
        get() {
            if (!location.workspace.isNullOrEmpty()) paseoFail(PaseoFailureKind.ScopeMismatch)
            val dir = location.directory ?: paseoFail(PaseoFailureKind.ScopeMismatch)
            if (!dir.startsWith('/') || dir.length > 4096 || dir.any { it.code < 32 || it.code == 127 }) {
                paseoFail(PaseoFailureKind.ScopeMismatch)
            }
            return dir.trimEnd('/').ifEmpty { "/" }
        }

    private fun readTarget(path: String): Pair<String, String> {
        val value = paseoString(path.trim(), empty = true)
        val root = directory
        if (value.startsWith('/') && root != "/" && value != root && !value.startsWith("$root/")) return "/" to value
        var relative = if (value == root) "." else value.removePrefix(if (root == "/") "/" else "$root/")
        while (relative.startsWith("./")) relative = relative.removePrefix("./")
        return root to relative.ifEmpty { "." }
    }

    private suspend fun request(type: String, body: JsonObject): JsonObject {
        val scope = gateway.scopeDir
        val epoch = gateway.locationEpoch
        if (gateway.closed) paseoFail(PaseoFailureKind.Disconnected)
        val result = gateway.transport.request(type, body)
        gateway.checkLocation(scope, epoch)
        if (result["error"] != null && result["error"] != JsonNull) paseoFail(PaseoFailureKind.Unavailable)
        return result
    }

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> = call {
        val (cwd, relative) = readTarget(path)
        val reply = request("file_explorer_request", jsonOf("cwd" to cwd, "path" to relative, "mode" to "list"))
        val folder = paseoObject(reply["directory"])
        paseoList(folder["entries"], max = 50_000).map { raw ->
            val entry = paseoObject(raw)
            FileNode(paseoString(entry["name"], max = 1024), paseoString(entry["path"]), entry.str("kind") == "directory")
        }.sortedWith(compareBy<FileNode> { !it.isDir }.thenBy { it.name.lowercase() }).toImmutableList()
    }

    override suspend fun fileContent(path: String): Outcome<FileContent> = call {
        val (cwd, relative) = readTarget(path)
        val reply = request("file_explorer_request", jsonOf("cwd" to cwd, "path" to relative, "mode" to "file", "maxBytes" to 2 * 1024 * 1024))
        val file = paseoObject(reply["file"])
        val kind = file.str("kind")
        val content = file["content"].stringOrNull().orEmpty()
        if (content.length > 4 * 1024 * 1024) paseoFail(PaseoFailureKind.InvalidResponse)
        FileContent(
            content = if (kind == "binary") "" else content,
            type = when (kind) { "binary" -> "binary"; "image" -> "image"; else -> "text" },
            encoding = file.str("encoding")?.takeIf { it == "base64" },
            mimeType = file.str("mimeType"),
        )
    }

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> = call {
        val text = paseoString(query.trim(), empty = true)
        if (text.isEmpty()) return@call persistentListOf()
        val reply = request("directory_suggestions_request", jsonOf("query" to text, "cwd" to directory, "includeFiles" to true, "includeDirectories" to false, "limit" to 100))
        paseoList(reply["entries"], max = 1000).mapNotNull { raw ->
            val entry = raw.objectOrNull()
            if (entry?.str("kind") == "file") paseoString(entry["path"]) else null
        }.toImmutableList()
    }

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> = unavailable("textSearch")
}
