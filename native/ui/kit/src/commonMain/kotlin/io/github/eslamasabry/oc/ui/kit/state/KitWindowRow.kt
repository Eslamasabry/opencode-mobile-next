package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** A measured quota window. Unlike job progress, a new snapshot may move in either direction. */
@Composable
public fun KitWindowRow(
    title: String,
    left: Float,
    valueLabel: String,
    valueSemantics: String,
    note: String? = null,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
) {
    val fraction = if (left.isFinite()) left.coerceIn(0f, 1f) else 0f
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    if (inline) {
        InlineWindow(title, fraction, valueLabel, valueSemantics, note, modifier)
        return
    }
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

/** Flutter kit_window_row.dart:90-184: compact label/bar/value, stacking before words can cut. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineWindow(
    title: String,
    fraction: Float,
    value: String,
    spoken: String,
    note: String?,
    modifier: Modifier,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val style = LocalKitTypography.current.style(TypeRole.Secondary)
    val nameWidth = (64 * density.fontScale).dp // F: lib/ui/kit/kit_window_row.dart:145
    val valueWidth = with(density) { measurer.measure(value, style).size.width.toDp() }
    val bar: @Composable (Modifier) -> Unit = { barModifier ->
        Canvas(barModifier.height(tokens.progressBar.dp)) {
            val radius = CornerRadius(FlutterKitTokens.PROGRESS_BAR_RADIUS.dp.toPx())
            drawRoundRect(colors.surface3, cornerRadius = radius)
            if (fraction > 0f)
                drawRoundRect(
                    colors.accent,
                    topLeft = Offset(if (rtl) size.width * (1f - fraction) else 0f, 0f),
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = radius,
                )
        }
    }
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(vertical = Space.S1.dp).clearAndSetSemantics {
            contentDescription = listOfNotNull(title, spoken, note).joinToString(", ")
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        }
    ) {
        val fits =
            nameWidth + Space.S3.dp + valueWidth + 48.dp <=
                maxWidth // F: lib/ui/kit/kit_window_row.dart:149-152
        if (density.fontScale < 1.3f && fits) { // F: lib/ui/kit/kit_window_row.dart:90
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KitText(title, Modifier.width(nameWidth), role = TypeRole.Secondary)
                bar(Modifier.weight(1f))
                Spacer(Modifier.width(Space.S3.dp))
                BasicText(
                    value,
                    style = style.copy(color = colors.text2, fontFeatureSettings = "tnum"),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KitText(title, role = TypeRole.Secondary)
                    BasicText(
                        value,
                        style = style.copy(color = colors.text2, fontFeatureSettings = "tnum"),
                    )
                }
                Spacer(Modifier.height(Space.S1.dp))
                bar(Modifier.fillMaxWidth())
                note?.let {
                    Spacer(Modifier.height(Space.S1.dp))
                    KitText(it, role = TypeRole.Caption, tone = TextTone.Secondary)
                }
            }
        }
    }
}
