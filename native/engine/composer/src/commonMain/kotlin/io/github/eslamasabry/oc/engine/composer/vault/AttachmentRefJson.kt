package io.github.eslamasabry.oc.engine.composer.vault

import io.github.eslamasabry.oc.engine.composer.store.longOrNull
import io.github.eslamasabry.oc.engine.composer.store.str
import io.github.eslamasabry.oc.engine.composer.store.strOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Flutter `DraftAttachmentRef.toJson`: `filename, mime, blob?, url?, bytes` (null keys omitted). */
internal fun AttachmentRef.toJson(): JsonObject =
    buildJsonObject {
        put("filename", JsonPrimitive(filename))
        put("mime", JsonPrimitive(mime))
        blob?.let { put("blob", JsonPrimitive(it)) }
        url?.let { put("url", JsonPrimitive(it)) }
        put("bytes", JsonPrimitive(bytes))
    }

internal fun attachmentRefFromJson(value: JsonObject): AttachmentRef =
    AttachmentRef(
        filename = value.str("filename"),
        mime = value.str("mime"),
        blob = value.strOrNull("blob"),
        url = value.strOrNull("url"),
        bytes = value.longOrNull("bytes") ?: 0,
    )
