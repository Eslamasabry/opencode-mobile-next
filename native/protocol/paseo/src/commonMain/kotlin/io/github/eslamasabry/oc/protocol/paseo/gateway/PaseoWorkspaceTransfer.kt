package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.WorkspaceTransferRequest
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import kotlinx.serialization.json.JsonObject

internal fun PaseoGateway.workspaceTransferEpoch(): Long =
    ((locationEpoch.toLong() and 0xffffffffL) shl 32) or (transport.epoch.toLong() and 0xffffffffL)

private fun PaseoGateway.checkWorkspaceTransfer(directory: String, expected: Long) {
    if (
        closed ||
            !transport.connected ||
            !transport.workspaceFileEditing ||
            directory != scopeDir ||
            expected != workspaceTransferEpoch() ||
            !directory.startsWith('/') ||
            directory.split('/').any { it == "." || it == ".." }
    ) {
        paseoFail(PaseoFailureKind.StaleRequest)
    }
}

/**
 * The Flutter handoff RPC contract, restricted to the app's transfer tree. Replies are validated by
 * the engine.
 */
internal suspend fun PaseoGateway.workspaceTransferRequest(
    directory: String,
    epoch: Long,
    request: WorkspaceTransferRequest,
): Outcome<JsonObject> = onLane {
    paseoAttempt {
        checkWorkspaceTransfer(directory, epoch)
        val connectionEpoch = transport.epoch
        val type: String
        val body: JsonObject
        val mutation: Boolean
        when (request) {
            is WorkspaceTransferRequest.Create -> {
                val valid =
                    if (request.directory) {
                        (request.parent == "." && request.name == ".handoff") ||
                            (request.parent == ".handoff" && RECORD_ID.matches(request.name))
                    } else {
                        RECORD_FOLDER.matches(request.parent) &&
                            request.name in setOf(".gitignore", "record.md")
                    }
                if (!valid) paseoFail(PaseoFailureKind.Unavailable)
                type = "fs.entry.create.request"
                body =
                    jsonOf(
                        "cwd" to directory,
                        "parentPath" to request.parent,
                        "name" to request.name,
                        "kind" to if (request.directory) "directory" else "file",
                    )
                mutation = true
            }
            is WorkspaceTransferRequest.ListEntries -> {
                if (request.path != ".") paseoFail(PaseoFailureKind.Unavailable)
                type = "file_explorer_request"
                body = jsonOf("cwd" to directory, "path" to request.path, "mode" to "list")
                mutation = false
            }
            is WorkspaceTransferRequest.ReadFile -> {
                if (
                    !RECORD_FILE.matches(request.path) || request.maxBytes !in 1..MAX_TRANSFER_BYTES
                ) {
                    paseoFail(PaseoFailureKind.Unavailable)
                }
                type = "file_explorer_request"
                body =
                    jsonOf(
                        "cwd" to directory,
                        "path" to request.path,
                        "mode" to "file",
                        "maxBytes" to request.maxBytes,
                    )
                mutation = false
            }
            is WorkspaceTransferRequest.WriteFile -> {
                if (
                    !RECORD_FILE.matches(request.path) ||
                        request.content.encodeToByteArray().size > MAX_TRANSFER_BYTES ||
                        !stampSafe(request.expectedModifiedAt, 128) ||
                        request.expectedRevision?.let { !stampSafe(it, 1024) } == true
                ) {
                    paseoFail(PaseoFailureKind.Unavailable)
                }
                type = "fs.file.write.request"
                body =
                    jsonOf(
                            "cwd" to directory,
                            "path" to request.path,
                            "content" to request.content,
                            "expectedModifiedAt" to request.expectedModifiedAt,
                            "expectedRevision" to request.expectedRevision,
                        )
                        .withoutNulls()
                mutation = true
            }
        }
        val reply =
            transport.request(
                type,
                body,
                mutation = mutation,
                answerErrors = true,
                expectedEpoch = connectionEpoch,
                beforeSend = { checkWorkspaceTransfer(directory, epoch) },
            )
        checkWorkspaceTransfer(directory, epoch)
        reply
    }
}

/** A first prompt cannot accidentally become a continuation or consume another caller's draft. */
internal suspend fun PaseoGateway.workspaceTransferPrompt(
    session: SessionId,
    prompt: OutgoingPrompt,
    beforeSend: () -> Boolean,
): Outcome<PromptAdmission> = onLane {
    val outcome = paseoAttempt {
        val directory = scopeDir
        val epoch = workspaceTransferEpoch()
        checkWorkspaceTransfer(directory, epoch)
        val id = app(session.raw)
        if (id !in drafts || real(id) != id || prompt.attachments.isNotEmpty())
            paseoFail(PaseoFailureKind.Unavailable)
        sendPrompt(
            id,
            paseoUuid(random),
            prompt,
            images = emptyList(),
            existingOnly = false,
            beforeSend = {
                checkWorkspaceTransfer(directory, epoch)
                if (app(session.raw) != id || id !in drafts || real(id) != id || !beforeSend()) {
                    paseoFail(PaseoFailureKind.StaleRequest)
                }
                true
            },
        )
    }
    val failure = (outcome as? Outcome.Failed)?.failure
    if (failure is Failure.Refused && failure.reason == RefusalReason.DispatchUncertain)
        Outcome.Ok(PromptAdmission.Uncertain)
    else outcome
}

private fun stampSafe(value: String, limit: Int): Boolean =
    value.isNotEmpty() && value.length <= limit && value.none { it.code <= 0x1f || it.code == 0x7f }

private const val MAX_TRANSFER_BYTES = 98_304
private val RECORD_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
private val RECORD_FOLDER = Regex("""^\.handoff/[A-Za-z0-9_-]{1,128}$""")
private val RECORD_FILE = Regex("""^\.handoff/[A-Za-z0-9_-]{1,128}/(?:\.gitignore|record\.md)$""")
