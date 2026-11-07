package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Whether the message box is Flutter's one row or two (`lib/ui/kit/chat/kit_composer.dart:796-808`:
 * `Row(leading, field, trailing)` unless `stacked`). The words share the row with [leadingTargets] 48 dp controls
 * (the plus, and Stop beside it while words wait for a run) and the one round control; once they run past that row's
 * field, or [stack] asks for it, [content] gets `stacked = true` and the controls move under a full-width field.
 * Measured against the one-row field's width however the box is laid out now, so the two layouts never take turns.
 */
@Composable
public fun KitComposerRowFit(
    text: String,
    modifier: Modifier = Modifier,
    leadingTargets: Int = 1,
    stack: Boolean = false,
    content: @Composable (stacked: Boolean) -> Unit,
) {
    val measure = rememberTextMeasurer()
    val tokens = LocalKitTokens.current
    val style = LocalKitTypography.current.style(TypeRole.Body)
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
        val width = composerRowFieldWidth(maxWidth.value, tokens.minTarget.toFloat(), leadingTargets).dp
        val px = with(LocalDensity.current) { width.roundToPx() }.coerceAtLeast(1)
        val one = Constraints(maxWidth = px)
        val layout = measure.measure(AnnotatedString(text), style = style, maxLines = 1, constraints = one)
        content(stack || text.contains('\n') || layout.hasVisualOverflow)
    }
}

/**
 * The one-row field's width in a box [boxWidth] wide (F: kit_composer.dart:760-764 `rowField`): the box's own S1
 * padding, one 48 dp target per leading control and one for the trailing control, the field's S2 end gap, and its S3
 * start gap when nothing leads.
 */
internal fun composerRowFieldWidth(boxWidth: Float, minTarget: Float, leadingTargets: Int): Float =
    boxWidth - Space.S1 * 2 - minTarget * (leadingTargets + 1) - Space.S2 - (if (leadingTargets == 0) Space.S3 else 0)
