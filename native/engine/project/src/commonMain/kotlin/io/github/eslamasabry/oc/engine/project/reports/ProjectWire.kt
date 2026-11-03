package io.github.eslamasabry.oc.engine.project.reports

import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ids.WorkspaceId
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/*
 * The project-side wire shapes (B-PROTO-082; port of PromptAttachment.reference, FileContent, Session and FileDiff
 * parsing in lib/domain/models): a project reference is the upstream directory file part; a remote Windows server path
 * stays a Windows file URL; binary content keeps its metadata; sessions keep project and workspace ownership; the diff
 * keeps the patch and the server's counts.
 */

/** A file part of a prompt. A directory reference carries [DIRECTORY_MIME] and a `file:` URL. */
public data class PromptFilePart(val mime: String, val filename: String, val url: String) {
    val isDirectoryReference: Boolean get() = mime == DIRECTORY_MIME && url.startsWith("file:")

    public fun toJson(): JsonObject =
        buildJsonObject {
            put("type", "file")
            put("mime", mime)
            put("filename", filename)
            put("url", url)
        }

    public companion object {
        public const val DIRECTORY_MIME: String = "application/x-directory"

        /** A project folder named [name] at [path] on the server (POSIX or Windows), as OpenCode's directory file part. */
        public fun reference(name: String, path: String): PromptFilePart = PromptFilePart(DIRECTORY_MIME, name, fileUrl(path))

        private val WINDOWS_DRIVE = Regex("^[A-Za-z]:[\\\\/]")

        /** Dart `Uri.file(path, windows: <drive or UNC>)`: dot segments removed, each segment percent-encoded. */
        public fun fileUrl(path: String): String {
            val windows = WINDOWS_DRIVE.containsMatchIn(path) || path.startsWith("\\\\")
            if (windows) {
                val slashed = path.replace('\\', '/')
                if (slashed.startsWith("//")) {
                    val rest = slashed.removePrefix("//")
                    val host = rest.substringBefore('/')
                    return "file://${encode(host)}${normalized("/" + rest.substringAfter('/', ""))}"
                }
                return "file://${normalized("/$slashed")}"
            }
            if (!path.startsWith("/")) return normalized(path).removePrefix("/")
            return "file://${normalized(path)}"
        }

        private fun normalized(path: String): String {
            val out = ArrayList<String>()
            val parts = path.split('/')
            parts.forEachIndexed { i, part ->
                when (part) {
                    "", "." -> if (i == parts.lastIndex && part.isEmpty() && out.isNotEmpty() && path.endsWith("/")) out += ""
                    ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                    else -> out += encode(part)
                }
            }
            return "/" + out.joinToString("/")
        }

        private const val KEEP = "-._~!$&'()*+,;=:@"

        private fun encode(segment: String): String {
            val b = StringBuilder()
            for (byte in segment.encodeToByteArray()) {
                val c = (byte.toInt() and 0xff).toChar()
                if (c.isLetterOrDigit() && c.code < 128 || c in KEEP) b.append(c)
                else b.append('%').append((byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0'))
            }
            return b.toString()
        }
    }
}

private fun JsonObject.text(key: String): String? =
    when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.content
        else -> v.toString()
    }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.let { p -> if (p.isString) p.content.toIntOrNull() else p.content.toDoubleOrNull()?.toInt() }

/** `GET /file/content` (and the OpenCode 2 equivalent) as [FileContent], metadata kept. */
public fun parseFileContent(json: JsonObject): FileContent =
    FileContent(content = json.text("content").orEmpty(), type = json.text("type") ?: "text", encoding = json.text("encoding"), mimeType = json.text("mimeType"))

public val FileContent.isBinary: Boolean get() = type == "binary"

/** The content's bytes: base64-decoded when the server encoded it, else the UTF-8 text. Null for an undecodable body. */
public fun FileContent.bytes(): ByteArray? =
    if (encoding == "base64") {
        try {
            Base64.decode(content)
        } catch (e: IllegalArgumentException) {
            null
        }
    } else {
        content.encodeToByteArray()
    }

/** A session's server-owned scope: project, workspace, directory and path kept as sent. */
public fun parseSessionOwnership(json: JsonObject): Session {
    val time = json["time"] as? JsonObject
    fun long(key: String) = (time?.get(key) as? JsonPrimitive)?.longOrNull
    return Session(
        id = SessionId(json.text("id").orEmpty()),
        title = json.text("title"),
        projectId = json.text("projectID")?.let(::ProjectId),
        workspaceId = json.text("workspaceID")?.let(::WorkspaceId),
        parentId = json.text("parentID")?.let(::SessionId),
        directory = json.text("directory"),
        path = json.text("path"),
        time = time?.let { SessionTime(createdMs = long("created"), updatedMs = long("updated"), archivedMs = long("archived")) },
    )
}

public fun parseFileDiff(json: JsonObject): FileDiff =
    FileDiff(
        file = json.text("file").orEmpty(),
        before = json.text("before"),
        after = json.text("after"),
        patch = json.text("patch"),
        additions = json.int("additions"),
        deletions = json.int("deletions"),
        status = json.text("status"),
    )

public data class LineCounts(val added: Int, val removed: Int)

/** The server's counts when it sent any; else counted from before/after (common prefix and suffix trimmed). */
public val FileDiff.counts: LineCounts
    get() {
        if (additions != null || deletions != null) return LineCounts(additions ?: 0, deletions ?: 0)
        val before = before
        val after = after
        if (before == null && after == null) return LineCounts(0, 0)
        if (before == null) return LineCounts(after!!.split('\n').size, 0)
        if (after == null) return LineCounts(0, before.split('\n').size)
        val b = before.split('\n')
        val a = after.split('\n')
        var p = 0
        while (p < b.size && p < a.size && b[p] == a[p]) p++
        var s = 0
        while (s < b.size - p && s < a.size - p && b[b.size - 1 - s] == a[a.size - 1 - s]) s++
        return LineCounts(a.size - p - s, b.size - p - s)
    }
