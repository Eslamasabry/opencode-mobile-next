package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.FormGateway
import io.github.eslamasabry.oc.core.model.FormAnswer
import io.github.eslamasabry.oc.core.model.FormField
import io.github.eslamasabry.oc.core.model.FormFieldType
import io.github.eslamasabry.oc.core.model.FormSpec as WireForm
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Wire forms as OpenCode 2 sends them: each field is its complete JSON object. */
internal fun wireForm(
    id: String = "frm_1",
    session: String = "ses_1",
    title: String? = "Deploy settings",
    metadata: String? = null,
    fields: Array<String> = emptyArray(),
): WireForm =
    WireForm(
        id = FormId(id),
        sessionId = SessionId(session),
        title = title,
        metadata = metadata?.let { Json.parseToJsonElement(it).jsonObject },
        fields = fields.map { wireField(it) }.toImmutableList(),
    )

internal fun wireField(json: String): FormField {
    val raw: JsonObject = Json.parseToJsonElement(json).jsonObject
    return FormField(
        key = raw["key"]?.jsonPrimitive?.content ?: "",
        type = FormFieldType(raw["type"]?.jsonPrimitive?.content ?: "string"),
        title = null,
        description = null,
        required = false,
        options = persistentListOf(),
        raw = raw,
    )
}

/** A scripted forms gateway: records every write; [next] answers the next write (default Ok). */
internal class ScriptedForms(var listed: List<WireForm> = emptyList()) : FormGateway {
    val replies = mutableListOf<Pair<FormId, FormAnswer>>()
    val cancels = mutableListOf<FormId>()
    var next: Outcome<Unit> = Unit.ok()
    var listFails: Outcome.Failed? = null

    /** When set, the next write waits for it (a slow server). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun pendingForms(): Outcome<ImmutableList<WireForm>> = listFails ?: listed.toImmutableList().ok()

    override suspend fun replyForm(session: SessionId, form: FormId, answer: FormAnswer): Outcome<Unit> {
        gate?.await()
        replies += form to answer
        return next
    }

    override suspend fun cancelForm(session: SessionId, form: FormId): Outcome<Unit> {
        gate?.await()
        cancels += form
        return next
    }
}
