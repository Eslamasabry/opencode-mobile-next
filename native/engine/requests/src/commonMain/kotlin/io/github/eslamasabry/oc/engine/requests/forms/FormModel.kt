package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The one form model (plan 09 §5.5). Every source (OpenCode 2 `Form.Info` and its `question` tool, later agent card
 * `form` asks, ACP and Codex elicitation) is adapted into this, and the one renderer (`KitForm`) draws it.
 *
 * Plan 09 homes it in `:core:model` `…core.model.forms`; `:core:model` belongs to the core owner and today carries the
 * wire-shaped `FormSpec` (fields keep their raw JSON for this engine). Until the core owner moves it, it lives here
 * (S3-REQFORMS report, "requests to other owners"); moving it is one import change.
 *
 * [revision] is the schema revision (B-CHAT-028): every constraint, nested metadata, value types, never key order.
 */
public data class FormSpec(
    val id: FormId,
    val owner: FormOwner,
    val title: String,
    val description: String?,
    val fields: ImmutableList<FieldSpec>,
    val submitLabel: String?,
    val revision: String,
    /** The OpenCode 2 `question` tool (`metadata.kind = "question"`): presented as the question sheet. */
    val isQuestion: Boolean = false,
)

/** Who asked, and therefore where the answer goes. */
public sealed interface FormOwner {
    /** An OpenCode 2 form in a conversation. */
    public data class Session(val sessionId: SessionId) : FormOwner

    /**
     * An OpenCode 2 MCP elicitation (`sessionID: "global"`): answered from attention as a connection-level form sheet,
     * never as a chat card (plan 09 §5.5).
     */
    public data object Connection : FormOwner
}

public enum class FieldKind { Text, Multiline, Number, Integer, Toggle, Select, MultiSelect, Date, DateTime, ExternalLink }

/** `format` on a text field. Unknown formats read as [Other] (they never block). */
public enum class StringFormat { Email, Uri, Date, DateTime, Other }

public data class FieldOption(val value: String, val label: String, val description: String?)

/**
 * One `when` condition. `Eq` against a multi-select answer means "includes" (Dart `Api2FormCondition.holds`). A
 * condition on a key with no answer never holds, so a field behind an unanswered or inactive field is inactive too.
 */
public data class FieldCondition(val key: String, val op: ConditionOp, val value: FormValue?)

public enum class ConditionOp { Eq, Neq }

/**
 * One field. Beyond plan 09's sketch it carries [custom] ("Something else" free text on a select, own values on a
 * multi-select), [minItems]/[maxItems] (multi-select limits) and [url] (external link fields) because OpenCode 2
 * sends them and Dart validates them (`form_renderer.dart`).
 */
public data class FieldSpec(
    val key: String,
    val label: String,
    val description: String?,
    val kind: FieldKind,
    val required: Boolean,
    val hidden: Boolean,
    val default: FormValue?,
    val placeholder: String?,
    val options: ImmutableList<FieldOption> = persistentListOf(),
    val min: Double? = null,
    val max: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val format: StringFormat? = null,
    val whenAll: ImmutableList<FieldCondition> = persistentListOf(),
    val custom: Boolean = false,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val url: String? = null,
)

/** A typed answer. Multi-select answers keep declaration order, then own values in the order they were added. */
public sealed interface FormValue {
    public data class Text(val text: String) : FormValue

    /** Integers stay [Number] with a whole value; the encoder sends them without a fraction. */
    public data class Number(val value: Double) : FormValue

    public data class Toggle(val on: Boolean) : FormValue

    public data class Choices(val values: ImmutableList<String>) : FormValue
}
