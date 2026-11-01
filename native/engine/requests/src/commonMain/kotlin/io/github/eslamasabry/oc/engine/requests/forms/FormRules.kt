package io.github.eslamasabry.oc.engine.requests.forms

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the person has entered so far, by field key, before any rule ran: text, number and date fields hold the raw
 * [FormValue.Text] they typed (so "12a" can say "Enter a number"), a select holds its option value or the "Something
 * else" text, a toggle its [FormValue.Toggle], a multi-select its [FormValue.Choices]. Inactive fields keep their draft
 * (plan 09 §5.5): it comes back when the condition holds again.
 */
public typealias FormDraft = ImmutableMap<String, FormValue>

/** Why one field cannot be sent yet. `:ui:words` has one sentence per case (Dart `e7Shared*`). */
public sealed interface FieldProblem {
    public data object Required : FieldProblem

    public data class TooShort(val minLength: Int) : FieldProblem

    public data class TooLong(val maxLength: Int) : FieldProblem

    public data object NoMatch : FieldProblem

    /** A select value the form does not offer (and it takes no own answer): never sent (B-CHAT-072). */
    public data object NotOffered : FieldProblem

    public data object NotANumber : FieldProblem

    public data object NotAWholeNumber : FieldProblem

    public data class OutOfRange(val min: Double?, val max: Double?) : FieldProblem

    public data class TooFewItems(val minItems: Int) : FieldProblem

    public data class TooManyItems(val maxItems: Int) : FieldProblem
}

/** One forward pass over the declared fields (Dart `_FormAnswers.evaluate`). */
public data class FormEvaluation(
    /** Keys whose `when` conditions all hold, hidden fields included. */
    val active: ImmutableSet<String>,
    /** The typed answer of every active field that has one; hidden fields carry their default. */
    val answers: ImmutableMap<String, FormValue>,
    /** Problems of active, visible fields, in declaration order. Empty means the form can be sent. */
    val problems: ImmutableMap<String, FieldProblem>,
) {
    val canSend: Boolean get() = problems.isEmpty()

    /** The first field to point at when Send is refused ("why it cannot send yet"). */
    val firstProblemKey: String? get() = problems.keys.firstOrNull()
}

/**
 * The rules every form follows, whatever its source (plan 09 §5.5):
 * - `hidden` fields are never shown and send their default (B-PROTO-022);
 * - every `when` condition must hold; an inactive field is neither required nor sent, and its draft is kept;
 *   conditions only point at earlier fields, so one forward pass settles it, and an unanswered reference is false;
 * - validation mirrors the schema on active, visible fields only; an external-link field never blocks;
 * - credential-like fields use the sensitive field; the sheet height ignores `when`, so it never jumps.
 */
public object FormRules {
    /** OpenCode's credential pattern (plan 09 §5.5). */
    private val SENSITIVE = Regex("password|passphrase|secret|token|api[_ -]?key|credential|private[_ -]?key", RegexOption.IGNORE_CASE)

    private const val FULL_HEIGHT_FIELDS = 5
    private const val LONG_DESCRIPTION = 140

    /** The fields a person sees, in order (hidden never). Whether each is active comes from [evaluate]. */
    public fun visible(spec: FormSpec): ImmutableList<FieldSpec> = spec.fields.filterNot { it.hidden }.toImmutableList()

    /** The draft a fresh sheet starts with: every visible default, in its draft shape. */
    public fun initialDraft(spec: FormSpec): FormDraft =
        spec.fields
            .filter { !it.hidden }
            .mapNotNull { field ->
                val d = field.default ?: return@mapNotNull null
                val draft =
                    when (field.kind) {
                        FieldKind.Number, FieldKind.Integer -> (d as? FormValue.Number)?.let { FormValue.Text(plain(it.value)) }
                        FieldKind.Toggle -> d as? FormValue.Toggle
                        FieldKind.MultiSelect -> d as? FormValue.Choices
                        FieldKind.ExternalLink -> null
                        else -> d as? FormValue.Text
                    } ?: return@mapNotNull null
                field.key to draft
            }.toMap().toImmutableMap()

    public fun evaluate(spec: FormSpec, draft: Map<String, FormValue>): FormEvaluation {
        val active = LinkedHashSet<String>()
        val answers = LinkedHashMap<String, FormValue>()
        val problems = LinkedHashMap<String, FieldProblem>()
        for (field in spec.fields) {
            if (!field.whenAll.all { holds(it, answers) }) continue
            active += field.key
            if (field.hidden) {
                field.default?.let { answers[field.key] = it }
                continue
            }
            val raw = draft[field.key]
            val answer = answerOf(field, raw)
            if (answer != null) answers[field.key] = answer
            problemOf(field, raw, answer)?.let { problems[field.key] = it }
        }
        return FormEvaluation(active.toImmutableSet(), answers.toImmutableMap(), problems.toImmutableMap())
    }

    /**
     * The wire payload: active fields with an answer only (hidden ones with their default). Integers and whole numbers
     * go without a fraction (Dart `num.tryParse` gives an int), multi-selects as string lists.
     */
    public fun payload(spec: FormSpec, draft: Map<String, FormValue>): ImmutableMap<String, JsonElement> {
        val evaluation = evaluate(spec, draft)
        if (evaluation.answers.isEmpty()) return persistentMapOf()
        return evaluation.answers.mapValues { (_, v) -> encode(v) }.toImmutableMap()
    }

    public fun encode(value: FormValue): JsonElement =
        when (value) {
            is FormValue.Text -> JsonPrimitive(value.text)
            is FormValue.Toggle -> JsonPrimitive(value.on)
            is FormValue.Choices -> JsonArray(value.values.map(::JsonPrimitive))
            is FormValue.Number ->
                if (value.value % 1.0 == 0.0 && kotlin.math.abs(value.value) < 9.0E15) {
                    JsonPrimitive(value.value.toLong())
                } else {
                    JsonPrimitive(value.value)
                }
        }

    /** Credential-like text fields use the sensitive field and never autofill (plan 09 §5.5, 16 SI-4). */
    public fun isSensitive(field: FieldSpec): Boolean =
        field.kind in setOf(FieldKind.Text, FieldKind.Multiline) &&
            (SENSITIVE.containsMatchIn(field.key) || SENSITIVE.containsMatchIn(field.label))

    /**
     * Full height (an end-side sheet on wide windows) for five or more declared fields or any description past ~140
     * characters, else content height. Counts declared visible fields and ignores `when`, so a sheet never jumps as
     * conditions toggle (Dart `formPrefersFullScreen`).
     */
    public fun prefersFullHeight(spec: FormSpec): Boolean {
        val shown = visible(spec)
        return shown.size >= FULL_HEIGHT_FIELDS || shown.any { (it.description?.length ?: 0) > LONG_DESCRIPTION }
    }

    /** Dart `Api2FormCondition.holds`: no answer → false; `eq` on a multi-select answer means "includes". */
    internal fun holds(condition: FieldCondition, answers: Map<String, FormValue>): Boolean {
        val current = answers[condition.key] ?: return false
        val wanted = condition.value
        val matches =
            when {
                current is FormValue.Choices && wanted is FormValue.Text -> wanted.text in current.values
                current is FormValue.Number && wanted is FormValue.Number -> current.value == wanted.value
                else -> current == wanted
            }
        return if (condition.op == ConditionOp.Neq) !matches else matches
    }

    private fun answerOf(field: FieldSpec, raw: FormValue?): FormValue? =
        when (field.kind) {
            FieldKind.Text, FieldKind.Multiline, FieldKind.Date, FieldKind.DateTime, FieldKind.Select ->
                (raw as? FormValue.Text)?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(FormValue::Text)
            FieldKind.Number -> number(raw)?.let(FormValue::Number)
            FieldKind.Integer -> number(raw)?.takeIf { it % 1.0 == 0.0 }?.let(FormValue::Number)
            FieldKind.Toggle -> raw as? FormValue.Toggle ?: FormValue.Toggle(false)
            FieldKind.MultiSelect -> (raw as? FormValue.Choices)?.takeIf { it.values.isNotEmpty() }
            FieldKind.ExternalLink -> null
        }

    private fun problemOf(field: FieldSpec, raw: FormValue?, answer: FormValue?): FieldProblem? =
        when (field.kind) {
            FieldKind.Text, FieldKind.Multiline, FieldKind.Date, FieldKind.DateTime, FieldKind.Select -> {
                val text = (answer as? FormValue.Text)?.text
                when {
                    text == null -> if (field.required) FieldProblem.Required else null
                    field.kind == FieldKind.Select && !field.custom && field.options.none { it.value == text } ->
                        FieldProblem.NotOffered
                    field.minLength != null && text.length < field.minLength -> FieldProblem.TooShort(field.minLength)
                    field.maxLength != null && text.length > field.maxLength -> FieldProblem.TooLong(field.maxLength)
                    field.pattern != null && !matches(field.pattern, text) -> FieldProblem.NoMatch
                    else -> null
                }
            }
            FieldKind.Number, FieldKind.Integer -> {
                val typed = (raw as? FormValue.Text)?.text?.trim().orEmpty()
                val n = (answer as? FormValue.Number)?.value
                when {
                    typed.isEmpty() -> if (field.required) FieldProblem.Required else null
                    n == null -> if (field.kind == FieldKind.Integer) FieldProblem.NotAWholeNumber else FieldProblem.NotANumber
                    (field.min != null && n < field.min) || (field.max != null && n > field.max) ->
                        FieldProblem.OutOfRange(field.min, field.max)
                    else -> null
                }
            }
            FieldKind.MultiSelect -> {
                val count = (answer as? FormValue.Choices)?.values?.size ?: 0
                when {
                    count == 0 -> if (field.required) FieldProblem.Required else null
                    field.minItems != null && count < field.minItems -> FieldProblem.TooFewItems(field.minItems)
                    field.maxItems != null && count > field.maxItems -> FieldProblem.TooManyItems(field.maxItems)
                    else -> null
                }
            }
            FieldKind.Toggle, FieldKind.ExternalLink -> null
        }

    /** Arabic-Indic digits read as ASCII (B-L10N-012's input half, as `KitFieldRules.normalizeNumber`). */
    private fun number(raw: FormValue?): Double? {
        val text = (raw as? FormValue.Text)?.text?.trim() ?: return null
        if (text.isEmpty()) return null
        val ascii =
            buildString {
                text.forEach { c ->
                    append(
                        when (c) {
                            in '٠'..'٩' -> '0' + (c - '٠')
                            in '۰'..'۹' -> '0' + (c - '۰')
                            '٫' -> '.'
                            else -> c
                        },
                    )
                }
            }
        return ascii.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** An invalid server pattern is no constraint (it must not lock the form). */
    private fun matches(pattern: String, text: String): Boolean =
        try {
            Regex(pattern).containsMatchIn(text)
        } catch (_: IllegalArgumentException) {
            true
        }

    private fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
