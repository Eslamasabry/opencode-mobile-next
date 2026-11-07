package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow

/** The box never shrinks below this, so the question has room to breathe (Dart `KitStartBox.minHeight`). */
internal const val START_BOX_MIN_HEIGHT: Int = 80 // F: lib/ui/kit/kit_start_box.dart:54

/** The field grows by four lines past [minLines] before it scrolls (Dart `maxLines: minLines + 4`). */
internal fun startBoxLines(minLines: Int): IntRange = minLines..(minLines + 4) // F: lib/ui/kit/kit_start_box.dart:165

/**
 * The field a new conversation starts with (a port of Dart `KitStartBox`, lib/ui/kit/kit_start_box.dart): the
 * question is the placeholder and the field's screen-reader name, the field is [minLines] tall and grows as the
 * person types, and the box is the style's field. Solid paints the hairline at rest and the accent at the focus ring's
 * width while it has focus; a style whose field keeps a halo glows softly behind it, brighter over the quick pace when
 * focused. A new [focusKey] (a starter just filled the field) brings the cursor back so the person can keep typing.
 *
 * States: none — empty, typed, focused and read-only ([enabled] off while the conversation starts) draw one box.
 */
@Composable
public fun KitStartBox(
    state: TextFieldState,
    question: String,
    modifier: Modifier = Modifier,
    minLines: Int = 2, // F: lib/ui/kit/kit_start_box.dart:35
    enabled: KitEnabled = KitEnabled.Enabled,
    autofocus: Boolean = false,
    onTextChanged: ((String) -> Unit)? = null,
    focusKey: Any? = null,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val accent = colors.accent
    val style = LocalKitStyle.current
    val solid = style.style == Style.Solid
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Field, KitShapeId.Card, ownFill = colors.surface1, selected = focused))
    val pace = KitMotion.specs.pace<androidx.compose.ui.graphics.Color>(KitPace.Quick)
    val edgeColor by animateColorAsState(if (focused) accent else colors.hairline, pace, label = "kit.startBox.edge")
    val edge =
        if (solid) Modifier.kitHairlineBorder(if (focused) kitFocusRingWidth() else kitHairline(), edgeColor, look.shape)
        else Modifier
    // The halo behind the box, in a style whose field keeps one (kitFieldHalo); Solid's start box never glows.
    val halo =
        if (solid || !style.spec.tokens.fieldHaloShown) Modifier
        else
            Modifier.dropShadow(
                look.shape,
                ComposeShadow(
                    radius = (if (focused) 32 else 28).dp, // F: lib/ui/kit/kit_start_box.dart:130
                    color = accent.copy(alpha = if (focused) .3f else .22f), // F: lib/ui/kit/kit_start_box.dart:129
                ),
            ).dropShadow(
                look.shape,
                ComposeShadow(
                    radius = 8.dp, // F: lib/ui/kit/kit_start_box.dart:134
                    color = accent.copy(alpha = if (focused) .16f else .1f), // F: lib/ui/kit/kit_start_box.dart:133
                ),
            )
    val body = LocalKitTypography.current.style(TypeRole.Body)
    val words = look.words ?: colors.text1
    val changed = rememberUpdatedState(onTextChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.distinctUntilChanged().drop(1).collect { changed.value?.invoke(it) }
    }
    LaunchedEffect(autofocus, focusKey) { if (autofocus || focusKey != null) focus.requestFocus() }
    Box(
        modifier
            .kitMaxWidth(CapRole.Conversation)
            .fillMaxWidth()
            .heightIn(min = START_BOX_MIN_HEIGHT.dp)
            .then(halo)
            .then(look.modifier)
            .then(edge)
            .padding(horizontal = Space.S5.dp, vertical = Space.S4.dp)
    ) {
        BasicTextField(
            state = state,
            modifier =
                Modifier.fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.hasFocus }
                    .semantics { contentDescription = question },
            enabled = enabled.isEnabled,
            textStyle = body.copy(color = words),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
            lineLimits = startBoxLines(minLines).let { TextFieldLineLimits.MultiLine(it.first, it.last) },
            cursorBrush = SolidColor(accent),
            decorator = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (state.text.isEmpty()) {
                        androidx.compose.foundation.text.BasicText(
                            question,
                            Modifier.clearAndSetSemantics {},
                            style = body.copy(color = colors.text2),
                        )
                    }
                    inner()
                }
            },
        )
    }
}
