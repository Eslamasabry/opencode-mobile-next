package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.paseo.*
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * A fresh agent may be created by this one reviewed handoff, fenced immediately before its first
 * prompt.
 */
public suspend fun PaseoGateway.promptNewHandoff(
    sessionId: String,
    prompt: OutgoingPrompt,
    beforeSend: () -> Boolean,
): Outcome<Unit> =
    withContext(lane) {
        when (
            val sent = paseoAttempt {
                sendPrompt(
                    sessionId,
                    paseoUuid(random),
                    prompt,
                    emptyList(),
                    existingOnly = false,
                    beforeSend = beforeSend,
                )
            }
        ) {
            is Outcome.Failed -> sent
            is Outcome.Ok ->
                when (sent.value) {
                    is PromptAdmission.Confirmed -> Outcome.Ok(Unit)
                    PromptAdmission.Uncertain ->
                        Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                }
        }
    }

/**
 * Any location replacement or connection epoch invalidates a reviewed transfer, even after
 * returning to the same folder.
 */
public val PaseoGateway.handoffGeneration: Long
    get() = (transport.epoch.toLong() shl 32) xor locationEpoch.toLong()

/**
 * Writes one new, private handoff record through the gateway's single writer ([PaseoHandoff]); an
 * existing file is never overwritten.
 */
public suspend fun PaseoGateway.writeAgentHandoff(
    directory: String,
    recordId: String,
    markdown: String,
): Outcome<String> = PaseoHandoff(this).write(directory, recordId, markdown)

/**
 * Exclusively creates one ignored handoff directory; existing records are never overwritten or
 * retried.
 */
internal class PaseoHandoff(private val g: PaseoGateway) {
    private val redactor = Redactor()

    suspend fun write(directory: String, recordId: String, markdown: String): Outcome<String> =
        g.withPayloadLease {
            withContext(g.lane) {
                paseoAttempt {
                    if (
                        !g.handoffFilesReady ||
                            directory != g.scopeDir ||
                            !Regex("[a-zA-Z0-9_-]{1,128}").matches(recordId) ||
                            markdown.isEmpty() ||
                            markdown.length > 98_304 ||
                            markdown.encodeToByteArray().size > 98_304 ||
                            redactor.opaqueTokens(markdown) != markdown
                    )
                        paseoFail(PaseoFailureKind.Unavailable)
                    val scope = g.scopeDir
                    val location = g.locationEpoch
                    val connection = g.transport.epoch
                    fun check() {
                        g.checkLocation(scope, location)
                        if (!g.handoffFilesReady || g.transport.epoch != connection)
                            paseoFail(PaseoFailureKind.StaleRequest)
                    }
                    suspend fun request(
                        type: String,
                        body: JsonObject,
                        mutation: Boolean = false,
                    ): JsonObject {
                        check()
                        val result =
                            g.transport.request(
                                type,
                                body.with("cwd", kotlinx.serialization.json.JsonPrimitive(scope)),
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
                        val path = if (parent == ".") name else "$parent/$name"
                        if (result.str("cwd") != scope || result.str("parentPath") != parent)
                            paseoFail(PaseoFailureKind.InvalidResponse)
                        return result["success"].boolOrNull() == true && result.str("path") == path
                    }
                    suspend fun read(path: String): JsonObject {
                        val result =
                            request(
                                "file_explorer_request",
                                jsonOf("path" to path, "mode" to "file", "maxBytes" to 98_304),
                            )
                        val file = result.obj("file") ?: paseoFail(PaseoFailureKind.InvalidResponse)
                        if (
                            (result["error"] != null &&
                                result["error"] != kotlinx.serialization.json.JsonNull) ||
                                result.str("cwd") != scope ||
                                result.str("path") != path ||
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
                        val body =
                            jsonOf(
                                    "path" to path,
                                    "content" to content,
                                    "expectedModifiedAt" to empty["modifiedAt"],
                                    "expectedRevision" to empty.str("revision"),
                                )
                                .withoutNulls()
                        val result = request("fs.file.write.request", body, true)
                        if (result.obj("result")?.str("status") != "written")
                            paseoFail(PaseoFailureKind.Unavailable)
                        if (read(path).str("content") != content)
                            paseoFail(PaseoFailureKind.InvalidResponse)
                    }
                    if (!create(".", ".handoff", "directory")) {
                        val listing =
                            request(
                                "file_explorer_request",
                                jsonOf("path" to ".", "mode" to "list"),
                            )
                        val entries = listing.obj("directory")?.get("entries")
                        if (
                            (listing["error"] != null &&
                                listing["error"] != kotlinx.serialization.json.JsonNull) ||
                                listing.str("cwd") != scope ||
                                listing.str("path") != "." ||
                                entries !is kotlinx.serialization.json.JsonArray ||
                                entries.none {
                                    it.objectOrNull()?.str("name") == ".handoff" &&
                                        it.objectOrNull()?.str("kind") == "directory"
                                }
                        )
                            paseoFail(PaseoFailureKind.Unavailable)
                    }
                    val folder = ".handoff/$recordId"
                    if (!create(".handoff", recordId, "directory"))
                        paseoFail(PaseoFailureKind.Unavailable)
                    writeNew(folder, ".gitignore", "# OpenCode Mobile conversation handoff\n*\n")
                    writeNew(folder, "record.md", markdown)
                    "$folder/record.md"
                }
            }
        }
}
