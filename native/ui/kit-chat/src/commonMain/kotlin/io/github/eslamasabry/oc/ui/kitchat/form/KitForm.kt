package io.github.eslamasabry.oc.ui.kitchat.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// Kit-local form model (plan 09 C5: kit parts take kit-local models, never engine types). The chat feature maps the
// requests engine's FormSpec/FieldSpec onto these; all words arrive already written (no copy in the kit).

public enum class KitFormFieldKind { Text, Multiline, Number, Integer, Toggle, Select, MultiSelect, Date, DateTime, ExternalLink }

public data class KitFormOption(val value: String, val label: String, val description: String? = null)

/**
 * One visible field. Hidden fields never reach the kit. [active] false folds the field away (a `when` condition does
 * not hold); its value stays with the caller. [sensitive] draws the secret field (no suggestions, no autofill, value
 * reported to the redactor). [ownLabel] on a select or multi-select offers "Something else" with a field for own words.
 * [linkLabel] is the external-link field's button words (naming the host); the tap goes to `onOpenLink`, which must
 * route through the app's external-link policy.
 */
public data class KitFormField(
    val key: String,
    val label: String,
    val kind: KitFormFieldKind,
    val description: String? = null,
    val placeholder: String? = null,
    val options: ImmutableList<KitFormOption> = persistentListOf(),
    val active: Boolean = true,
    val sensitive: Boolean = false,
    val error: String? = null,
    val maxLength: Int? = null,
    val ownLabel: String? = null,
    val linkLabel: String? = null,
)

/** A field's value as the kit edits it: raw text (numbers too), a toggle, or chosen values (own words last). */
public sealed interface KitFormValue {
    public data class Text(val text: String) : KitFormValue

    public data class Toggle(val on: Boolean) : KitFormValue

    public data class Choices(val values: ImmutableList<String>) : KitFormValue
}

/**
 * The one form renderer (plan 09 §5.5; replaces Flutter's `form_renderer.dart` and `agent_card_form.dart`): every
 * active field of [fields] in order, each drawn with the kit's own part — [KitField] for words, numbers and dates,
 * [KitSwitchRow] for toggles, radio [KitChoiceRow]s for a select, check rows for a multi-select, a secondary
 * [KitButton] for an external link. Every edit calls [onChange] with the field's whole new value; the caller owns the
 * draft (it survives folding, closing and reopening) and runs the rules.
 *
 * Text: frame (labels, errors), reading (values, descriptions)
 * Cap: Reading
 */
@Composable
public fun KitForm(
    fields: ImmutableList<KitFormField>,
    values: ImmutableMap<String, KitFormValue>,
    onChange: (key: String, value: KitFormValue) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (key: String) -> Unit = {},
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
        fields.filter { it.active }.forEach { field ->
            key(field.key) {
                FormField(field, values[field.key], onChange, onOpenLink, enabled)
            }
        }
    }
}

@Composable
private fun FormField(
    field: KitFormField,
    value: KitFormValue?,
    onChange: (String, KitFormValue) -> Unit,
    onOpenLink: (String) -> Unit,
    enabled: KitEnabled,
) {
    Column(Modifier.fillMaxWidth().testTag("kit-form-${field.key}"), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        when (field.kind) {
            KitFormFieldKind.Text, KitFormFieldKind.Multiline, KitFormFieldKind.Number, KitFormFieldKind.Integer,
            KitFormFieldKind.Date, KitFormFieldKind.DateTime -> WordsField(field, value, onChange, enabled)
            KitFormFieldKind.Toggle -> {
                KitSwitchRow(
                    title = field.label,
                    checked = (value as? KitFormValue.Toggle)?.on ?: false,
                    supporting = field.error ?: field.description,
                    enabled = enabled,
                    onCheckedChange = { onChange(field.key, KitFormValue.Toggle(it)) },
                )
            }
            KitFormFieldKind.Select -> SelectField(field, value, onChange, enabled)
            KitFormFieldKind.MultiSelect -> MultiSelectField(field, value, onChange, enabled)
            KitFormFieldKind.ExternalLink -> {
                Heading(field)
                KitButton(
                    label = field.linkLabel ?: field.label,
                    onClick = { onOpenLink(field.key) },
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                )
            }
        }
    }
}

/** Label and description above a group of rows (a [KitField] draws its own label). */
@Composable
private fun Heading(field: KitFormField) {
    KitText(field.label, role = TypeRole.Label, tone = TextTone.Secondary, kind = KitTextKind.Frame)
    field.description?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Reading) }
}

@Composable
private fun ErrorLine(error: String?) {
    error?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Primary, kind = KitTextKind.Frame) }
}

@Composable
private fun WordsField(field: KitFormField, value: KitFormValue?, onChange: (String, KitFormValue) -> Unit, enabled: KitEnabled) {
    val kind =
        when {
            field.sensitive -> KitFieldKind.Secret
            field.kind == KitFormFieldKind.Multiline -> KitFieldKind.Multiline
            field.kind == KitFormFieldKind.Number || field.kind == KitFormFieldKind.Integer -> KitFieldKind.Number
            else -> KitFieldKind.Text
        }
    val state = rememberTextFieldState((value as? KitFormValue.Text)?.text.orEmpty())
    SyncText(state, field.key, onChange)
    KitField(
        state = state,
        label = field.label,
        kind = kind,
        placeholder = field.placeholder,
        helper = field.description,
        error = field.error,
        maxLength = field.maxLength,
        enabled = enabled,
    )
}

/** Reports every edit of [state] as the field's new text (not the first, unchanged read). */
@Composable
private fun SyncText(state: TextFieldState, key: String, onChange: (String, KitFormValue) -> Unit) {
    val report by rememberUpdatedState(onChange)
    LaunchedEffect(state, key) {
        var last = state.text.toString()
        snapshotFlow { state.text.toString() }.collect { now ->
            if (now != last) {
                last = now
                report(key, KitFormValue.Text(now))
            }
        }
    }
}

@Composable
private fun SelectField(field: KitFormField, value: KitFormValue?, onChange: (String, KitFormValue) -> Unit, enabled: KitEnabled) {
    Heading(field)
    val current = (value as? KitFormValue.Text)?.text
    val offered = field.options.any { it.value == current }
    val ownOn = field.ownLabel != null && current != null && !offered
    field.options.forEach { option ->
        KitChoiceRow(
            title = option.label,
            selected = option.value == current,
            supporting = option.description,
            mark = KitChoiceMark.Radio,
            enabled = enabled,
            onClick = { onChange(field.key, KitFormValue.Text(option.value)) },
        )
    }
    field.ownLabel?.let { own ->
        KitChoiceRow(
            title = own,
            selected = ownOn,
            mark = KitChoiceMark.Radio,
            enabled = enabled,
            onClick = { if (!ownOn) onChange(field.key, KitFormValue.Text("")) },
        )
        if (ownOn) OwnWords(field, own, current.orEmpty(), enabled) { onChange(field.key, KitFormValue.Text(it)) }
    }
    ErrorLine(field.error)
}

@Composable
private fun MultiSelectField(
    field: KitFormField,
    value: KitFormValue?,
    onChange: (String, KitFormValue) -> Unit,
    enabled: KitEnabled,
) {
    Heading(field)
    val chosen = (value as? KitFormValue.Choices)?.values.orEmpty()
    val offeredValues = field.options.map { it.value }
    val own = chosen.firstOrNull { it !in offeredValues }
    fun emit(picked: List<String>, ownWords: String?) {
        val ordered = offeredValues.filter { it in picked } + listOfNotNull(ownWords?.takeIf { it.isNotBlank() })
        onChange(field.key, KitFormValue.Choices(ordered.toImmutableList()))
    }
    field.options.forEach { option ->
        val on = option.value in chosen
        KitChoiceRow(
            title = option.label,
            selected = on,
            supporting = option.description,
            mark = KitChoiceMark.Check,
            enabled = enabled,
            onClick = { emit(if (on) chosen - option.value else chosen + option.value, own) },
        )
    }
    field.ownLabel?.let { label ->
        OwnWords(field, label, own.orEmpty(), enabled) { words -> emit(chosen.filter { it in offeredValues }, words) }
    }
    ErrorLine(field.error)
}

/** The "Something else" words of a select (shown once chosen) or a multi-select (always offered). */
@Composable
private fun OwnWords(field: KitFormField, label: String, initial: String, enabled: KitEnabled, onWords: (String) -> Unit) {
    val state = rememberTextFieldState(initial)
    val report by rememberUpdatedState(onWords)
    LaunchedEffect(state) {
        var last = state.text.toString()
        snapshotFlow { state.text.toString() }.collect { now ->
            if (now != last) {
                last = now
                report(now)
            }
        }
    }
    KitField(
        state = state,
        label = label,
        modifier = Modifier.padding(start = Space.S2.dp).testTag("kit-form-${field.key}-own"),
        kind = KitFieldKind.Text,
        enabled = enabled,
    )
}
