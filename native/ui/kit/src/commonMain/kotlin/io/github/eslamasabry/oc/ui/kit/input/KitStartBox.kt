package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/**
 * The field a new conversation starts with (Dart `KitStartBox`, lib/ui/kit/kit_start_box.dart): the question is the
 * placeholder ("What would you like to build?"), the field is [minLines] tall and grows as the person types (up to
 * [minLines] + 4 lines), in the style's field on the card radius, never under 80 dp. Solid paints the hairline at rest
 * and the accent at the focus ring's width while it has focus, eased over QUICK (instantly under reduced motion). A
 * new [focusKey] (a starter just filled the field) brings the cursor back so the person can keep typing. [question]
 * names the field for a screen reader; the draft stays in [state].
 *
 * States: none — empty, typed, focused and read-only ([enabled] off, while the conversation starts) draw one box
 * Text: reading
 * Cap: Conversation (700)
 * Motion: edge colour and width over QUICK; Off = instant
 * Replaces: KitStartBox (lib/ui/kit/kit_start_box.dart)
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
    val specs = KitMotion.specs
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Field, KitShapeId.Card, ownFill = colors.surface1, selected = focused))
    val solid = LocalKitStyle.current.style == Style.Solid
    val edgeColor by animateColorAsState(if (focused) colors.accent else colors.hairline, specs.quick(), label = "kit.startBox.edge")
    val edgeWidth by animateDpAsState(if (focused) kitFocusRingWidth() else kitHairline(), specs.quick(), label = "kit.startBox.width")
    val edge = if (solid) Modifier.border(edgeWidth, edgeColor, look.shape) else Modifier
    val body = LocalKitTypography.current.style(TypeRole.Body)
    val words = body.copy(color = look.words ?: colors.text1)
    val changed by rememberUpdatedState(onTextChanged)
    LaunchedEffect(autofocus, focusKey) { if (autofocus || focusKey != null) focus.requestFocus() }
    LaunchedEffect(state) {
        var last = state.text.toString()
        snapshotFlow { state.text.toString() }.collect { if (it != last) { last = it; changed?.invoke(it) } }
    }
    Box(
        modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()
            .heightIn(min = 80.dp) // F: lib/ui/kit/kit_start_box.dart:54
            .then(look.modifier)
            .then(edge)
            .padding(horizontal = Space.S5.dp, vertical = Space.S4.dp),
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.hasFocus }
                .semantics { contentDescription = question },
            enabled = enabled.isEnabled,
            textStyle = words,
            cursorBrush = SolidColor(colors.accent),
            lineLimits = TextFieldLineLimits.MultiLine(minLines, minLines + 4), // F: lib/ui/kit/kit_start_box.dart:165
            keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
            decorator = TextFieldDecorator { inner ->
                Box {
                    if (state.text.isEmpty()) BasicText(question, Modifier.clearAndSetSemantics { }, style = body.copy(color = colors.text2))
                    inner()
                }
            },
        )
    }
}
