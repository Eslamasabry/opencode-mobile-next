package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

// Requests the person answers: permissions, forms (OC2), legacy questions, inbox (OC2).
// Ports of models_events.dart, value_types_catalog.dart and api2/models/models_inbox.dart (moved out of OC2 types
// per plan 04 §11.1).

public data class PermissionTool(val messageId: MessageId, val callId: String)

/** Dart PermissionRequest. [metadata] is server data: never logged. */
public data class PermissionRequest(
    val id: RequestId,
    val sessionId: SessionId,
    val permission: String,
    val patterns: ImmutableList<String>,
    val metadata: JsonObject,
    val always: ImmutableList<String>,
    val tool: PermissionTool?,
    val canAlwaysAllow: Boolean,
    val message: String?,
)

/** The three wire replies (Dart passes `once` / `always` / `reject`). */
public enum class PermissionReply { Once, Always, Reject }

public data class SavedPermission(val id: String, val projectId: String, val action: String, val resource: String)

// ---- OpenCode 2 forms (the one form model; the forms engine owner refines FormField, plan 09/03 "FormSpec").

/** Open enum: string, number, boolean, select, multiselect, … */
@JvmInline public value class FormFieldType(public val raw: String)

public data class FormOption(val value: String, val label: String?, val description: String?)

public data class FormField(
    val key: String,
    val type: FormFieldType,
    val title: String?,
    val description: String?,
    val required: Boolean,
    val options: ImmutableList<FormOption>,
    /** The complete wire field (conditions, limits, pattern, default) for the forms engine. */
    val raw: JsonObject,
)

/** Dart Api2FormInfo. `sessionId == "global"` marks MCP elicitation forms. */
public data class FormSpec(
    val id: FormId,
    val sessionId: SessionId,
    val title: String?,
    val metadata: JsonObject?,
    val fields: ImmutableList<FormField>,
)

public enum class FormStatus { Pending, Answered, Cancelled, Unknown }

public data class FormState(val status: FormStatus, val answer: JsonObject?)

/** The assembled answer payload, key → value. */
public data class FormAnswer(val values: ImmutableMap<String, JsonElement>)

// ---- OpenCode 2 inbox

public enum class InboxDelivery { Steer, Queue }

/** Dart Api2InboxItem. [payload] is server data: never logged. */
public data class InboxItem(
    val id: InboxItemId,
    val sessionId: SessionId?,
    val createdAtMs: Long?,
    val type: String,
    val payload: JsonObject,
    val delivery: InboxDelivery?,
)

// ---- Legacy questions (OC1 dialogs; inert on OC2)

public data class QuestionChoice(val label: String, val description: String)

public data class QuestionPrompt(
    val title: String,
    val question: String,
    val multiple: Boolean,
    val custom: Boolean,
    val optional: Boolean,
    val choices: ImmutableList<QuestionChoice>,
    val markdown: Boolean,
)

public data class PendingQuestion(val id: RequestId, val sessionId: SessionId, val prompts: ImmutableList<QuestionPrompt>)
