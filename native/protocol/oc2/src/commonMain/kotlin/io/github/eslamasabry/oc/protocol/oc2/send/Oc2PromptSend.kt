package io.github.eslamasabry.oc.protocol.oc2.send

import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Message
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Session
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The send half of the OpenCode 2 client (plan 04 §7.13, WP-04.8; plan 15 S2-DLV "send files"): the
 * `POST /session/{id}/prompt` body, the staged-revert preflight and the admission-receipt checks. Pure: the transport
 * (S1-NET) and the gateway call these, so the rules are proven against the recorded Flutter fixtures without a server.
 */
public object Oc2PromptSend {
    /** The path of the prompt POST, relative to the `/api` root. Session ids are opaque and never parsed. */
    public fun path(sessionId: String): String = "/session/$sessionId/prompt"

    /**
     * Dart `Api2Client.prompt` + `Api2Gateway._promptAsync`, key order kept: `id` (only with a client id), `text`,
     * `files` / `agents` (only when non-empty), `delivery` (only when chosen: an unset delivery is the server's default
     * steer, B-PROTO-025). Model and agent never ride on a v2 prompt: they are session state (`SessionSelectionGateway`).
     */
    public fun body(prompt: OutgoingPrompt, clientId: String? = prompt.clientMessageId?.raw): JsonObject =
        buildJsonObject {
            if (clientId != null) put("id", clientId)
            put("text", prompt.text)
            if (prompt.attachments.isNotEmpty()) {
                put(
                    "files",
                    buildJsonArray {
                        for (a in prompt.attachments) {
                            add(
                                buildJsonObject {
                                    put("uri", a.url)
                                    if (a.filename.isNotEmpty()) put("name", a.filename)
                                    val start = a.mentionStart
                                    val end = a.mentionEnd
                                    if (start != null && end != null) put("mention", mention(start, end, "@${a.mentionPath}"))
                                },
                            )
                        }
                    },
                )
            }
            if (prompt.mentions.isNotEmpty()) {
                put(
                    "agents",
                    buildJsonArray {
                        for (m in prompt.mentions) {
                            add(
                                buildJsonObject {
                                    put("name", m.name)
                                    put("mention", mention(m.start, m.end, m.value))
                                },
                            )
                        }
                    },
                )
            }
            when (prompt.delivery) {
                null -> Unit
                PromptDelivery.Steer -> put("delivery", "steer")
                PromptDelivery.Queue -> put("delivery", "queue")
            }
        }

    private fun mention(start: Int, end: Int, text: String): JsonObject =
        buildJsonObject {
            put("start", start)
            put("end", end)
            put("text", text)
        }

    /**
     * B-PROTO-023 / B-PROTO-043: a staged revert refuses the prompt **before** the POST, and the client never commits
     * the revert implicitly. Any non-null `revert` value counts (Dart `Api2Session.reverted`).
     */
    public fun revertBlocksPrompt(session: Oc2Session): Boolean = session.reverted

    /**
     * Inbox item types that are a person's prompt. The real server answers `user` (N4-D13, `prompt_receipt.json`,
     * `inbox_list.json`); `prompt` is what Flutter's check expected and its tests still send, so it is accepted too.
     * Every other type (`synthetic`, `compaction`, `move`, `shell`, unknown) never confirms (B-PROTO-135).
     */
    public val promptItemTypes: Set<String> = setOf("user", "prompt")

    /**
     * B-PROTO-042 / B-PROTO-135: the POST's receipt confirms admission only when it names **our** client id, **our**
     * session and a prompt type. Anything else (a missing receipt, another id, another session, another type) leaves the
     * send unconfirmed; it is never a reason to send again.
     */
    public fun receiptConfirms(receipt: Oc2InboxItem?, sessionId: String, clientId: String): Boolean =
        receipt != null && receipt.id == clientId && receipt.sessionID == sessionId && receipt.type in promptItemTypes

    /** The lookup's first read: a pending inbox item with our id, session and a prompt type (pending = admitted). */
    public fun inboxConfirms(items: List<Oc2InboxItem>, sessionId: String, clientId: String): Boolean =
        items.any { receiptConfirms(it, sessionId, clientId) }

    /**
     * The lookup's second read (`GET /session/{id}/message/{clientId}`): a promoted **user** message with our id. An
     * assistant message, another id, or equal text never confirms.
     */
    public fun messageConfirms(message: Oc2Message?, clientId: String): Boolean =
        message is Oc2Message.User && message.id == clientId

    /** Paths the lookup reads (GET only; a lookup never posts). */
    public fun lookupPaths(sessionId: String, clientId: String): List<String> =
        listOf("/session/$sessionId/inbox", "/session/$sessionId/message/$clientId")
}
