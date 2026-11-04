package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import org.jetbrains.compose.resources.stringResource

/**
 * The first line's visible Wrap toggle, sharing its corner with Copy without adding a header row.
 */
@Composable
internal fun BoxScope.CodeWrapCorner(on: Boolean, toggle: () -> Unit) {
    DisableSelection {
        KitIconButton(
            WrapGlyph,
            stringResource(Res.string.kitWrapLines),
            toggle,
            Modifier.align(Alignment.TopEnd)
                .offset(x = Space.S3.dp, y = (-14).dp)
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minHeight = 0))
                    layout(p.width, 0) { p.place(0, 0) }
                }
                .semantics {
                    toggleableState = if (on) ToggleableState.On else ToggleableState.Off
                },
            tone = if (on) TextTone.Accent else TextTone.Secondary,
        )
    }
}

private val WrapGlyph =
    ImageVector.Builder("wrap", 24.dp, 24.dp, 24f, 24f)
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            moveTo(3f, 11f)
            lineTo(17f, 11f)
            curveTo(23f, 11f, 23f, 18f, 17f, 18f)
            lineTo(13f, 18f)
            moveTo(16f, 15f)
            lineTo(13f, 18f)
            lineTo(16f, 21f)
            moveTo(3f, 16f)
            lineTo(9f, 16f)
        }
        .build()
