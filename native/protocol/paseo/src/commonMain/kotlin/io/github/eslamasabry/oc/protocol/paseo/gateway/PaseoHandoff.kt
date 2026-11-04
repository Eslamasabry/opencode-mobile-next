package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Writes one new, private handoff record; an existing file is never overwritten. */
public suspend fun PaseoGateway.writeAgentHandoff(
    directory: String,
    recordId: String,
    markdown: String,
): Outcome<String> = laneCall {
    withPayloadUse {
        if (
            !capabilities.value.agentHandoffFiles ||
                directory != scopeDir ||
                !Regex("^[a-zA-Z0-9_-]{1,128}$").matches(recordId) ||
                markdown.isEmpty() ||
                markdown.encodeToByteArray().size > 98_304 ||
                Redactor().opaqueTokens(markdown) != markdown
        )
            paseoFail(PaseoFailureKind.Unavailable)
        val scope = scopeDir
        val location = locationEpoch
        val connection = transport.epoch
        fun check() {
            checkLocation(scope, location)
            if (!capabilities.value.agentHandoffFiles || transport.epoch != connection)
                paseoFail(PaseoFailureKind.StaleRequest)
        }
        suspend fun request(type: String, body: JsonObject, mutation: Boolean = false): JsonObject {
            check()
            val result =
                transport.request(
                    type,
                    JsonObject(jsonOf("cwd" to scope) + body),
                    mutation = mutation,
                    answerErrors = true,
                    expectedEpoch = connection,
                    beforeSend = ::check,
                )
            check()
            return result
        }
        suspend fun create(parent: String, name: String, kind: String): Boolean {
            val result =
                request(
                    "fs.entry.create.request",
                    jsonOf("parentPath" to parent, "name" to name, "kind" to kind),
                    true,
                )
            if (result.str("cwd") != scope || result.str("parentPath") != parent)
                paseoFail(PaseoFailureKind.InvalidResponse)
            val path = if (parent == ".") name else "$parent/$name"
            return result["success"].boolOrNull() == true && result.str("path") == path
        }
        suspend fun read(path: String): JsonObject {
            val result =
                request(
                    "file_explorer_request",
                    jsonOf("path" to path, "mode" to "file", "maxBytes" to 98_304),
                )
            val file = result["file"].objectOrNull()
            if (
                result["error"].let { it != null && it !is JsonNull } ||
                    result.str("cwd") != scope ||
                    result.str("path") != path ||
                    file == null ||
                    file.str("path") != path ||
                    file.str("kind") != "text" ||
                    file.str("encoding") != "utf-8" ||
                    file.str("content") == null ||
                    file.str("modifiedAt") == null
            )
                paseoFail(PaseoFailureKind.InvalidResponse)
            return file
        }
        suspend fun writeNew(parent: String, name: String, content: String) {
            if (!create(parent, name, "file")) paseoFail(PaseoFailureKind.Unavailable)
            val path = "$parent/$name"
            val empty = read(path)
            if (empty.str("content") != "") paseoFail(PaseoFailureKind.Unavailable)
            val result =
                request(
                    "fs.file.write.request",
                    jsonOf(
                            "path" to path,
                            "content" to content,
                            "expectedModifiedAt" to empty.str("modifiedAt"),
                            "expectedRevision" to empty.str("revision"),
                        )
                        .withoutNulls(),
                    true,
                )
            if (result["result"].objectOrNull()?.str("status") != "written")
                paseoFail(PaseoFailureKind.Unavailable)
            if (read(path).str("content") != content) paseoFail(PaseoFailureKind.InvalidResponse)
        }
        if (!create(".", ".handoff", "directory")) {
            val listing = request("file_explorer_request", jsonOf("path" to ".", "mode" to "list"))
            val entries = listing["directory"].objectOrNull()?.get("entries").arrayOrNull()
            if (
                listing["error"].let { it != null && it !is JsonNull } ||
                    listing.str("cwd") != scope ||
                    listing.str("path") != "." ||
                    entries == null ||
                    entries.none {
                        it.objectOrNull()?.let { entry ->
                            entry.str("name") == ".handoff" && entry.str("kind") == "directory"
                        } == true
                    }
            )
                paseoFail(PaseoFailureKind.Unavailable)
        }
        if (!create(".handoff", recordId, "directory")) paseoFail(PaseoFailureKind.Unavailable)
        val folder = ".handoff/$recordId"
        writeNew(folder, ".gitignore", "# OpenCode Mobile conversation handoff\n*\n")
        writeNew(folder, "record.md", markdown)
        "$folder/record.md"
    }
}
