package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** A measured quota window. Unlike job progress, a new snapshot may move in either direction. */
@Composable
public fun KitWindowRow(
    title: String,
    left: Float,
    valueLabel: String,
    valueSemantics: String,
    note: String? = null,
    modifier: Modifier = Modifier,
) {
    val fraction = if (left.isFinite()) left.coerceIn(0f, 1f) else 0f
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KitText(title, Modifier.weight(1f), role = TypeRole.Secondary)
            KitText(valueLabel, role = TypeRole.Secondary)
        }
        Canvas(
            Modifier.fillMaxWidth().height(tokens.progressBar.dp).clearAndSetSemantics {
                contentDescription = "$title, $valueSemantics"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(colors.surface3, cornerRadius = radius)
            if (fraction > 0f)
                drawRoundRect(
                    colors.accent,
                    topLeft = Offset(if (rtl) size.width * (1f - fraction) else 0f, 0f),
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = radius,
                )
        }
        note?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
    }
}
