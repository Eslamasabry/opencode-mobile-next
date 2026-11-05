package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Flutter's measured one-row composer; growing text puts the controls below the full-width field. */
@Composable
public fun KitComposerTextBox(
    text: String,
    running: Boolean,
    modifier: Modifier = Modifier,
    hasLeading: Boolean = false,
    wide: Boolean = false,
    field: @Composable (stacked: Boolean) -> Unit,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val measure = rememberTextMeasurer()
    val tokens = LocalKitTokens.current
    val style = LocalKitTypography.current.style(TypeRole.Body)
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
        val width = maxWidth - tokens.minTarget.dp - (if (hasLeading) tokens.minTarget.dp else Space.S3.dp) - Space.S2.dp
        val px = with(LocalDensity.current) { width.roundToPx() }.coerceAtLeast(1)
        val layout = measure.measure(AnnotatedString(text), style = style, maxLines = 1, constraints = Constraints(maxWidth = px))
        val stacked = text.contains('\n') || layout.hasVisualOverflow
        KitComposerBox(running, { field(stacked) }, wide = wide, leading = leading, trailing = trailing, stacked = stacked)
    }
}
