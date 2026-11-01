package io.github.eslamasabry.oc.engine.composer.drafts

import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.asObject
import io.github.eslamasabry.oc.engine.composer.store.jsonString
import io.github.eslamasabry.oc.engine.composer.store.longOrNull
import io.github.eslamasabry.oc.engine.composer.store.loose
import io.github.eslamasabry.oc.engine.composer.store.strOrNull
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.attachmentRefFromJson
import io.github.eslamasabry.oc.engine.composer.vault.toJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Composer words typed in one conversation and not sent (Flutter `SessionDraft`, `lib/state/session_drafts.dart`).
 * [profileId] is empty for drafts written before drafts named their server ("older drafts").
 */
public data class SessionDraft(
    val sessionId: String,
    val profileId: String = "",
    val text: String,
    val updatedAt: Long,
    val attachments: List<AttachmentRef> = emptyList(),
    val directory: String? = null,
    val workspace: String? = null,
) {
    val storageKey: String get() = keyFor(profileId, sessionId)

    override fun toString(): String =
        "SessionDraft(${text.length} chars, ${attachments.size} attachments, updatedAt=$updatedAt)"

    public companion object {
        /** Flutter `SessionDraft.keyFor`: the session id alone for an older draft, else `jsonEncode([profile, session])`. */
        public fun keyFor(profileId: String, sessionId: String): String =
            if (profileId.isEmpty()) sessionId else JsonArray(listOf(JsonPrimitive(profileId), JsonPrimitive(sessionId))).toString()
    }
}

internal fun SessionDraft.toJson(): JsonObject =
    buildJsonObject {
        put("sessionID", JsonPrimitive(sessionId))
        if (profileId.isNotEmpty()) put("profileID", JsonPrimitive(profileId))
        put("text", JsonPrimitive(text))
        put("updatedAt", JsonPrimitive(updatedAt))
        if (attachments.isNotEmpty()) {
            put("attachments", JsonArray(attachments.map { it.toJson() }))
            put("directory", jsonString(directory))
            put("workspace", jsonString(workspace))
        }
    }

/** Null for an entry Dart's `fromJson` returns null for (no session, or neither words nor files). */
internal fun sessionDraftFromJson(value: JsonElement): SessionDraft? {
    if (value !is JsonObject) return null
    val sessionId = value.loose("sessionID").orEmpty()
    val text = value.loose("text").orEmpty()
    val rawAttachments = value["attachments"]
    val attachments =
        when (rawAttachments) {
            null, JsonNull -> emptyList()
            is JsonArray -> rawAttachments.map { attachmentRefFromJson(it.asObject()) }
            else -> throw StoredFormatException("attachments is not a list")
        }
    if (sessionId.isEmpty() || (text.isEmpty() && attachments.isEmpty())) return null
    return SessionDraft(
        sessionId = sessionId,
        profileId = value.loose("profileID").orEmpty(),
        text = text,
        updatedAt = value.longOrNull("updatedAt") ?: 0,
        attachments = attachments,
        directory = value.strOrNull("directory"),
        workspace = value.strOrNull("workspace"),
    )
}
