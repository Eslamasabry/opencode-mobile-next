package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/**
 * Flutter kit_start_box.dart: the question is inside the tall, card-shaped conversation field. A new
 * [focusKey] (a starter just filled the field) brings the cursor back so the person can keep typing.
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
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Field, KitShapeId.Card, ownFill = colors.surface1, selected = focused))
    val edge = if (LocalKitStyle.current.style == Style.Solid) Modifier.border(if (focused) kitFocusRingWidth() else kitHairline(), if (focused) colors.accent else colors.hairline, look.shape) else Modifier
    LaunchedEffect(autofocus, focusKey) { if (autofocus || focusKey != null) focus.requestFocus() }
    Box(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().heightIn(min = 80.dp).then(look.modifier).then(edge).padding(horizontal = Space.S5.dp, vertical = Space.S4.dp)) { // F: lib/ui/kit/kit_start_box.dart:54
        KitField(
            state = state,
            label = question,
            placeholder = question,
            kind = KitFieldKind.Multiline,
            enabled = enabled,
            focusRequester = focus,
            labelShown = false,
            framed = false,
            multilineLines = minLines..(minLines + 4), // F: lib/ui/kit/kit_start_box.dart:165
            modifier = Modifier.onFocusChanged { focused = it.hasFocus },
            onTextChanged = onTextChanged,
        )
    }
}
