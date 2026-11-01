package io.github.eslamasabry.oc.engine.composer.stash

import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.references.ReviewReference
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceKind
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceScope
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.asArray
import io.github.eslamasabry.oc.engine.composer.store.asObject
import io.github.eslamasabry.oc.engine.composer.store.jsonString
import io.github.eslamasabry.oc.engine.composer.store.longOrNull
import io.github.eslamasabry.oc.engine.composer.store.str
import io.github.eslamasabry.oc.engine.composer.store.strOrNull
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.attachmentRefFromJson
import io.github.eslamasabry.oc.engine.composer.vault.schemeOf
import io.github.eslamasabry.oc.engine.composer.vault.toJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A deliberate saved prompt (Flutter `StashedPrompt`, `lib/state/prompt_shelf.dart`). [attachments] are legacy inline
 * attachments (kept inline, never truncated); [attachmentRefs] are vault references (`attachmentFormat: 1`).
 */
public data class StashedPrompt(
    val id: String,
    val text: String,
    val createdAt: Long,
    val directory: String? = null,
    val workspace: String? = null,
    val attachments: List<PromptAttachment> = emptyList(),
    val attachmentRefs: List<AttachmentRef> = emptyList(),
    val references: List<ReviewReference> = emptyList(),
) {
    val attachmentCount: Int get() = attachments.size + attachmentRefs.size

    val attachmentNames: List<String> get() = attachments.map { it.filename } + attachmentRefs.map { it.filename }

    val isEmpty: Boolean get() = text.isEmpty() && attachmentCount == 0 && references.isEmpty()

    /** File URLs and review references point into one folder: restoring elsewhere warns first. */
    val locationBound: Boolean
        get() = references.isNotEmpty() || attachments.any { schemeOf(it.url) == "file" } ||
            attachmentRefs.any { schemeOf(it.url.orEmpty()) == "file" }

    override fun toString(): String = "StashedPrompt($id, ${text.length} chars, $attachmentCount files, ${references.size} refs)"
}

internal fun StashedPrompt.toJson(): JsonObject =
    buildJsonObject {
        put("id", JsonPrimitive(id))
        put("text", JsonPrimitive(text))
        put("createdAt", JsonPrimitive(createdAt))
        put("directory", jsonString(directory))
        put("workspace", jsonString(workspace))
        put("attachments", JsonArray(attachments.map { it.inlineJson() }))
        if (attachmentRefs.isNotEmpty()) {
            put("attachmentFormat", JsonPrimitive(1))
            put("attachmentRefs", JsonArray(attachmentRefs.map { it.toJson() }))
        }
        put(
            "references",
            JsonArray(
                references.map { r ->
                    buildJsonObject {
                        put("id", JsonPrimitive(r.id))
                        put("kind", JsonPrimitive(r.kind.wire))
                        put("path", JsonPrimitive(r.path))
                        put("scope", JsonPrimitive(r.scope.wire))
                        put("lineLabel", jsonString(r.lineLabel))
                        put("snippet", jsonString(r.snippet))
                        put("comment", jsonString(r.comment))
                        put("added", r.added?.let(::JsonPrimitive) ?: JsonNull)
                        put("removed", r.removed?.let(::JsonPrimitive) ?: JsonNull)
                        put("status", jsonString(r.status))
                    }
                },
            ),
        )
    }

/** Dart `PromptAttachment.toJson` as the stash wrote inline attachments (`type`, `mime`, `filename`, `url`). */
private fun PromptAttachment.inlineJson(): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("file"))
        put("mime", JsonPrimitive(mime))
        put("filename", JsonPrimitive(filename))
        put("url", JsonPrimitive(url))
    }

/** Flutter `StashedPrompt.fromJson`; throws [StoredFormatException] where Dart throws. */
internal fun stashedPromptFromJson(value: JsonObject): StashedPrompt {
    val refs = value["attachmentRefs"]
    if (("attachmentRefs" in value || "attachmentFormat" in value) &&
        ((value["attachmentFormat"] as? JsonPrimitive)?.content != "1" || refs !is JsonArray)
    ) {
        throw StoredFormatException("Unsupported stash attachment metadata")
    }
    val inline = value["attachments"].asArray()
    if (refs != null && refs != JsonNull && inline.isNotEmpty()) throw StoredFormatException("Ambiguous stash attachments")
    return StashedPrompt(
        id = value.str("id"),
        text = value.str("text"),
        createdAt = value.longOrNull("createdAt") ?: throw StoredFormatException("createdAt missing"),
        directory = value.strOrNull("directory"),
        workspace = value.strOrNull("workspace"),
        attachments = inline.map {
            val a = it.asObject()
            PromptAttachment(mime = a.str("mime"), filename = a.str("filename"), url = a.str("url"))
        },
        attachmentRefs = (refs as? JsonArray)?.map { attachmentRefFromJson(it.asObject()) }.orEmpty(),
        references = value["references"].asArray().map {
            val r = it.asObject()
            ReviewReference(
                id = r.str("id"),
                kind = ReviewReferenceKind.fromWire(r.str("kind")) ?: throw StoredFormatException("kind"),
                path = r.str("path"),
                scope = ReviewReferenceScope.fromWire(r.str("scope")) ?: throw StoredFormatException("scope"),
                lineLabel = r.strOrNull("lineLabel"),
                snippet = r.strOrNull("snippet"),
                comment = r.strOrNull("comment"),
                added = r.longOrNull("added")?.toInt(),
                removed = r.longOrNull("removed")?.toInt(),
                status = r.strOrNull("status"),
            )
        },
    )
}
