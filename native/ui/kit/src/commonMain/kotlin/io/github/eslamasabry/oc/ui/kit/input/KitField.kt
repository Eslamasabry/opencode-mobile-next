package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.inputText
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitFieldChecking
import io.github.eslamasabry.oc.ui.words.res.kitFieldCount
import io.github.eslamasabry.oc.ui.words.res.kitFieldHideNamed
import io.github.eslamasabry.oc.ui.words.res.kitFieldPaste
import io.github.eslamasabry.oc.ui.words.res.kitFieldReplace
import io.github.eslamasabry.oc.ui.words.res.kitFieldSaved
import io.github.eslamasabry.oc.ui.words.res.kitFieldShowNamed
import io.github.eslamasabry.oc.ui.words.res.kitFieldStillChecking
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Dart `KitFieldKind`: what the words are, which decides keyboard, font and direction. */
public enum class KitFieldKind {
    Text,
    Multiline,
    Mono,
    Path,
    Url,
    Number,
    Secret,
}

/**
 * A secret field is never prefilled (16 SI-4): a saved secret shows "Saved · Replace" until the
 * person replaces it.
 */
public enum class SecretFieldPresence {
    Empty,
    Saved,
    Editing,
}

/**
 * Where a secret field reports every keystroke before the host hears it (plan 16 SI-4, B-SEC-204).
 * The shell provides one that synchronously registers the value with the app's `:core:security`
 * `Redactor`. A missing provider is an error; tests and previews must explicitly supply a sink too.
 */
public fun interface KitSecretSink {
    public fun register(value: String)
}

public val LocalKitSecretSink: ProvidableCompositionLocal<KitSecretSink> =
    staticCompositionLocalOf {
        error("KitField(Secret) requires the app redactor's LocalKitSecretSink")
    }

/** The counter shows from 80 % of the limit (Dart `KitField`). */
internal const val COUNTER_FROM: Float = 0.8f

/** "Checking…" escalates to "Still checking after N s" at 8 s (Dart `KitField`, STATE-6). */
internal const val CHECKING_ESCALATE_S: Int = 8

/** Pure rules of [KitField] (unit-tested). */
public object KitFieldRules {
    /** True when the counter line should show for [length] of [max]. */
    public fun showCounter(length: Int, max: Int?): Boolean =
        max != null && max > 0 && length >= (max * COUNTER_FROM)

    /**
     * Arabic-Indic (U+0660–0669) and Extended Arabic-Indic (U+06F0–06F9) digits become ASCII, the
     * Arabic decimal separator (U+066B) becomes '.', and anything that is not a digit, '.', or a
     * leading '-' is dropped (Dart `KitNumberFormatter`; B-L10N-012's input half).
     */
    public fun normalizeNumber(raw: String): String {
        val out = StringBuilder()
        raw.forEach { c ->
            when (c) {
                in '٠'..'٩' -> out.append('0' + (c - '٠'))
                in '۰'..'۹' -> out.append('0' + (c - '۰'))
                '٫',
                '.' -> if ('.' !in out) out.append('.')
                '-' -> if (out.isEmpty()) out.append('-')
                in '0'..'9' -> out.append(c)
                else -> Unit
            }
        }
        return out.toString()
    }
}

private object NumberInput : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val now = toString()
        val normal = KitFieldRules.normalizeNumber(now)
        if (normal != now) replace(0, length, normal)
    }
}

/**
 * A text field (Dart `KitField`; plan 06 §7.5) on `BasicTextField(TextFieldState)`. The label sits
 * above (never floating); the helper or error line below is a polite live region; the counter shows
 * from 80 % of [maxLength]. Kinds: Mono, Path and Url are monospaced and read left to right; Url
 * and Path never autocorrect; Number maps Arabic-Indic digits to ASCII; Multiline grows; Secret is
 * a `BasicSecureTextField` with reveal and Paste, no suggestions, value read left to right, every
 * keystroke reported to [LocalKitSecretSink] — and it is never prefilled: with
 * [SecretFieldPresence.Saved] it shows "Saved · Replace" and no input until Replace. [checking]
 * shows "Checking…", which escalates at 8 s.
 *
 * States: default, focused, error, disabled, checking, saved (secret) Text: frame (label, helper),
 * reading (value) Cap: Field Motion: none of its own Replaces: KitField, KitNumberFormatter
 * (lib/ui/kit/kit_field.dart)
 */
@Composable
public fun KitField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    kind: KitFieldKind = KitFieldKind.Text,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    maxLength: Int? = null,
    imeAction: ImeAction = ImeAction.Default,
    secretPresence: SecretFieldPresence = SecretFieldPresence.Empty,
    enabled: KitEnabled = KitEnabled.Enabled,
    checking: Boolean = false,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
    onReplaceSecret: (() -> Unit)? = null,
    /**
     * Accepted user edits only, after validation (and secret registration). Never mutate this field
     * in the callback.
     */
    onTextChanged: ((String) -> Unit)? = null,
    /**
     * Multiline only: the field's height in lines, from empty to its scroll point. A message box starts at one line
     * (QA wave 1 B6: three empty lines left the conversation no room above the keyboard).
     */
    multilineLines: IntRange = 3..8,
    /**
     * QA wave 4 B6: false draws no label row above the box; the label is then the empty field's placeholder (unless
     * [placeholder] is given) and stays the field's accessible name. For a message box that must stay one row tall.
     */
    labelShown: Boolean = true,
    /**
     * False draws the words only, with no fill, edge or inner padding: for a field that sits inside another part's
     * face (the message box pill). The host draws the face and the focus cue.
     */
    framed: Boolean = true,
) {
    kitAssert(label.isNotBlank()) { "KitField: a field needs its label" }
    kitAssert(
        kind != KitFieldKind.Secret ||
            secretPresence != SecretFieldPresence.Saved ||
            onReplaceSecret != null
    ) {
        "KitField: a saved secret needs onReplaceSecret"
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (labelShown) KitText(label, role = TypeRole.Label, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        if (kind == KitFieldKind.Secret && secretPresence == SecretFieldPresence.Saved) {
            SavedSecretLine(onReplace = { onReplaceSecret?.invoke() })
        } else {
            FieldBox(
                state,
                label,
                kind,
                placeholder ?: label.takeIf { !labelShown },
                error != null,
                maxLength,
                imeAction,
                enabled,
                focusRequester,
                onSubmit,
                onTextChanged,
                multilineLines,
                framed,
            )
        }
        val note = error ?: helper
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            Box(Modifier.weight(1f)) {
                when {
                    checking -> CheckingLine()
                    note != null ->
                        KitText(
                            note,
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                if (error != null) error(error)
                            },
                            role = TypeRole.Secondary,
                            tone = if (error != null) TextTone.Primary else TextTone.Secondary,
                            kind = KitTextKind.Frame,
                        )
                }
            }
            val length = state.text.length
            if (KitFieldRules.showCounter(length, maxLength)) {
                KitText(
                    pluralStringResource(Res.plurals.kitFieldCount, length, Numbers.format(length), Numbers.format(maxLength ?: 0)),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    role = TypeRole.Caption,
                    tone = TextTone.Secondary,
                    kind = KitTextKind.Frame,
                )
            }
        }
    }
}

@Composable
private fun FieldBox(
    state: TextFieldState,
    label: String,
    kind: KitFieldKind,
    placeholder: String?,
    hasError: Boolean,
    maxLength: Int?,
    imeAction: ImeAction,
    enabled: KitEnabled,
    focusRequester: FocusRequester?,
    onSubmit: (() -> Unit)?,
    onTextChanged: ((String) -> Unit)?,
    multilineLines: IntRange,
    framed: Boolean = true,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val on = enabled.isEnabled
    val technical =
        kind == KitFieldKind.Mono ||
            kind == KitFieldKind.Path ||
            kind == KitFieldKind.Url ||
            kind == KitFieldKind.Secret
    val baseStyle = typography.style(if (technical) TypeRole.Mono else TypeRole.Body)
    val style =
        baseStyle.copy(
            color = if (on) colors.text1 else colors.text3,
            textDirection = if (technical) TextDirection.Ltr else TextDirection.Content,
        )
    // Flutter's field radius is the control's (14), not the icon tile's.
    val shape = KitShapeId.Button.shape()
    val fieldLook = rememberFieldLook(colors.surface1, focused || hasError)
    val edge =
        when {
            hasError -> colors.text1
            focused -> colors.accent
            else -> colors.hairline
        }
    val keyboard =
        KeyboardOptions(
            capitalization =
                if (kind == KitFieldKind.Text || kind == KitFieldKind.Multiline)
                    KeyboardCapitalization.Sentences
                else KeyboardCapitalization.None,
            autoCorrectEnabled = kind == KitFieldKind.Text || kind == KitFieldKind.Multiline,
            keyboardType =
                when (kind) {
                    KitFieldKind.Url -> KeyboardType.Uri
                    KitFieldKind.Number -> KeyboardType.Decimal
                    KitFieldKind.Secret -> KeyboardType.Password
                    else -> KeyboardType.Text
                },
            imeAction =
                if (kind == KitFieldKind.Multiline && imeAction == ImeAction.Default)
                    ImeAction.Default
                else imeAction,
        )
    val limit = maxLength?.let { InputTransformation.maxLength(it) }
    val transformation: InputTransformation? =
        when {
            kind == KitFieldKind.Number && limit != null -> NumberInput.then(limit)
            kind == KitFieldKind.Number -> NumberInput
            else -> limit
        }
    val report =
        remember(onTextChanged) {
            onTextChanged?.let { callback ->
                InputTransformation {
                    val accepted = toString()
                    if (accepted != originalText.toString()) callback(accepted)
                }
            }
        }
    val finalInput = if (report == null) transformation else transformation?.then(report) ?: report
    val submit = onSubmit?.let { KeyboardActionHandler { _ -> it() } }
    val box =
        Modifier.then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .fillMaxWidth()
            .heightIn(min = tokens.minTarget.dp)
            .then(
                when {
                    !framed -> Modifier.padding(vertical = Space.S3.dp)
                    // The style draws the field (B-KIT-043..045); Solid keeps the surface1 fill and the edge above.
                    fieldLook.isSolid ->
                        Modifier.clip(shape)
                            .background(colors.surface1)
                            .border(if (focused || hasError) 2.dp else 1.dp, edge, shape)
                            .padding(horizontal = Space.S3.dp, vertical = Space.S3.dp)
                    else -> Modifier.clip(shape).then(fieldLook.look.modifier).padding(horizontal = Space.S3.dp, vertical = Space.S3.dp)
                },
            )
    val decorator: @Composable (inner: @Composable () -> Unit) -> Unit = { inner ->
        Box(Modifier.fillMaxWidth()) {
            if (state.text.isEmpty() && placeholder != null) {
                KitText(
                    placeholder,
                    role = if (technical) TypeRole.Mono else TypeRole.Body,
                    tone = TextTone.Tertiary,
                    maxLines = 1,
                )
            }
            inner()
        }
    }
    if (kind == KitFieldKind.Secret) {
        val sink = LocalKitSecretSink.current
        // Runs after validation, inside the edit transaction and before TextFieldState publishes to
        // any consumer.
        val register =
            remember(sink) {
                InputTransformation {
                    val accepted = toString()
                    if (accepted.isNotEmpty() && accepted != originalText.toString())
                        sink.register(accepted)
                }
            }
        var revealed by rememberSaveable { mutableStateOf(false) }
        // The value is technical: left to right in an Arabic interface too, bidi-isolated by the
        // LTR box.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(box.padding(end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicSecureTextField(
                    state = state,
                    modifier =
                        Modifier.weight(1f).testTag("kit-field-secret").semantics {
                            contentDescription = label
                            // QA wave 4 N6: the accessibility tree gets dots, never the value (any accessibility
                            // service could read it); the password flag makes TalkBack say "password".
                            password()
                            val exposed =
                                if (revealed) AnnotatedString(state.text.toString())
                                else AnnotatedString(SECRET_DOT.repeat(state.text.length))
                            editableText = exposed
                            inputText = exposed
                        },
                    enabled = on,
                    inputTransformation =
                        (transformation?.then(register) ?: register).let { secured ->
                            if (report == null) secured else secured.then(report)
                        },
                    textStyle = style,
                    keyboardOptions = keyboard,
                    onKeyboardAction = submit,
                    interactionSource = interactions,
                    cursorBrush = SolidColor(colors.accent),
                    textObfuscationMode =
                        if (revealed) TextObfuscationMode.Visible
                        else TextObfuscationMode.RevealLastTyped,
                    decorator = { decorator(it) },
                )
                PasteButton(state, on, maxLength, sink)
                KitIconButton(
                    icon = if (revealed) KitPartGlyphs.EyeOff else KitPartGlyphs.Eye,
                    label =
                        if (revealed) stringResource(Res.string.kitFieldHideNamed, label)
                        else stringResource(Res.string.kitFieldShowNamed, label),
                    onClick = { revealed = !revealed },
                    tone = TextTone.Secondary,
                    enabled = enabled,
                )
            }
        }
        return
    }
    val lines =
        if (kind == KitFieldKind.Multiline)
            TextFieldLineLimits.MultiLine(
                minHeightInLines = multilineLines.first.coerceAtLeast(1),
                maxHeightInLines = multilineLines.last.coerceAtLeast(multilineLines.first.coerceAtLeast(1)),
            )
        else TextFieldLineLimits.SingleLine
    BasicTextField(
        state = state,
        modifier = box.semantics { contentDescription = label },
        enabled = on,
        inputTransformation = finalInput,
        textStyle = style,
        keyboardOptions = keyboard,
        onKeyboardAction = submit,
        lineLimits = lines,
        interactionSource = interactions,
        cursorBrush = SolidColor(colors.accent),
        decorator = { decorator(it) },
    )
}

@Suppress(
    "DEPRECATION"
) // LocalClipboard has no common plain-text read yet; LocalClipboardManager.getText() does.
@Composable
private fun PasteButton(state: TextFieldState, on: Boolean, maxLength: Int?, sink: KitSecretSink) {
    val clipboard = LocalClipboardManager.current
    KitTappable(
        onClick = {
            clipboard.getText()?.text?.let { pasted ->
                if (maxLength == null || pasted.length <= maxLength) {
                    state.edit {
                        if (pasted.isNotEmpty() && pasted != toString()) sink.register(pasted)
                        replace(0, length, pasted)
                    }
                }
            }
        },
        enabled = on,
        disabledReason = if (on) null else "",
        shape = KitShapeId.Pill,
    ) {
        KitText(
            stringResource(Res.string.kitFieldPaste),
            Modifier.padding(horizontal = Space.S2.dp),
            role = TypeRole.Label,
            tone = TextTone.Accent,
            kind = KitTextKind.Frame,
        )
    }
}

@Composable
private fun SavedSecretLine(onReplace: () -> Unit) {
    val tokens = LocalKitTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitText(
            stringResource(Res.string.kitFieldSaved),
            Modifier.weight(1f),
            role = TypeRole.Body,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
        )
        KitTappable(
            onClick = onReplace,
            shape = KitShapeId.Pill,
            modifier = Modifier.testTag("kit-field-replace"),
        ) {
            KitText(
                stringResource(Res.string.kitFieldReplace),
                Modifier.padding(horizontal = Space.S2.dp),
                role = TypeRole.Label,
                tone = TextTone.Accent,
                kind = KitTextKind.Frame,
            )
        }
    }
}

@Composable
private fun CheckingLine() {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (seconds < CHECKING_ESCALATE_S) {
            delay(1_000)
            seconds += 1
        }
    }
    val words =
        if (seconds >= CHECKING_ESCALATE_S)
            stringResource(Res.string.kitFieldStillChecking, seconds)
        else stringResource(Res.string.kitFieldChecking)
    KitText(
        words,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        role = TypeRole.Secondary,
        tone = TextTone.Secondary,
        kind = KitTextKind.Frame,
    )
}

private const val SECRET_DOT = "\u2022"

private class FieldLook(val look: KitSurfaceLook, val isSolid: Boolean)

@Composable
private fun rememberFieldLook(ownFill: Color, selected: Boolean): FieldLook {
    val style = LocalKitStyle.current
    val look =
        kitSurfaceLook(KitSurfaceInput(SurfaceRole.Field, KitShapeId.Tile, SurfaceLevel.Surface1, ownFill = ownFill, selected = selected))
    return FieldLook(look, style.style == Style.Solid)
}
