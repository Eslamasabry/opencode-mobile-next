package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** How a [KitWindowRow] is coloured (Dart `KitWindowTone`, lib/ui/kit/kit_window_row.dart). */
public enum class KitWindowTone {
    /** Plenty left: the accent role. */
    Normal,

    /** Past the person's alert level: a strong neutral bar and value (amber is kept for "needs you"). */
    Low,

    /** Nothing left: the bar is empty and the value is strong (never red). */
    Out,

    /** Old or offline numbers: muted. */
    Calm,
}

/** The bar's fill for [tone] (Dart `fill`, lib/ui/kit/kit_window_row.dart `_buildContent`). */
internal fun KitWindowTone.fill(colors: KitColors): Color =
    when (this) {
        KitWindowTone.Normal -> colors.accent
        KitWindowTone.Low, KitWindowTone.Out -> colors.text1
        KitWindowTone.Calm -> colors.text3
    }

/** The value's tone for [tone]: strong when low or out, else secondary (Dart `valueTone`). */
internal fun KitWindowTone.valueTone(): TextTone =
    when (this) {
        KitWindowTone.Low, KitWindowTone.Out -> TextTone.Primary
        else -> TextTone.Secondary
    }

/** What a screen reader hears: "{title}, {value}" and the note when there is one (Dart `Semantics.label`). */
internal fun windowRowLabel(title: String, spoken: String, note: String?): String =
    if (note == null) "$title, $spoken" else "$title, $spoken, $note"

/** The inline line stacks from 1.3x text (Dart `textScaler.scale(10) >= 13`). */
internal fun windowRowStacks(fontScale: Float): Boolean = fontScale * 10 >= 13 // F: lib/ui/kit/kit_window_row.dart:90

/**
 * One quota window: "5 hours ........ 18% left · 14:10" with a full-width bar under it (a port of Dart `KitWindowRow`,
 * lib/ui/kit/kit_window_row.dart). The bar fills with the share left, like a fuel gauge. It draws no surface and takes
 * no taps. Screen readers hear one label. [inline]: no side padding, and below 1.3x text the name, the bar and the
 * value share one line when they fit.
 */
@Composable
public fun KitWindowRow(
    title: String,
    left: Float,
    valueLabel: String,
    valueSemantics: String,
    note: String? = null,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
    tone: KitWindowTone = KitWindowTone.Normal,
) {
    val fraction = if (left.isFinite()) left.coerceIn(0f, 1f) else 0f
    val colors = LocalKitColors.current
    val density = LocalDensity.current
    val fill = tone.fill(colors)
    val valueTone = tone.valueTone()
    val stacked = windowRowStacks(density.fontScale)
    val padding =
        if (inline) Modifier.padding(vertical = Space.S1.dp)
        else Modifier.padding(horizontal = FlutterKitTokens.GUTTER.dp, vertical = Space.S1.dp)
    BoxWithConstraints(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = windowRowLabel(title, valueSemantics, note)
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .then(padding)
    ) {
        val nameWidth = (64 * density.fontScale).dp // F: lib/ui/kit/kit_window_row.dart:145
        val measurer = rememberTextMeasurer()
        val style = LocalKitTypography.current.style(TypeRole.Secondary)
        val valueWidth = with(density) { measurer.measure(valueLabel, style, maxLines = 1).size.width.toDp() }
        val fits = nameWidth + Space.S3.dp + valueWidth + 48.dp <= maxWidth // F: lib/ui/kit/kit_window_row.dart:150
        if (inline && !stacked && fits) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KitText(title, Modifier.width(nameWidth), role = TypeRole.Secondary)
                WindowBar(fraction, fill, Modifier.weight(1f))
                Spacer(Modifier.width(Space.S3.dp))
                KitText(valueLabel, role = TypeRole.Secondary, tone = valueTone, tabular = true)
            }
        } else {
            StackedWindow(title, fraction, fill, valueLabel, valueTone, note)
        }
    }
}

/** Dart `stackedColumn`: name and value on one line that wraps rather than cuts, the bar, then the note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackedWindow(
    title: String,
    fraction: Float,
    fill: Color,
    value: String,
    valueTone: TextTone,
    note: String?,
) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KitText(title, role = TypeRole.Secondary)
            KitText(value, role = TypeRole.Secondary, tone = valueTone, tabular = true)
        }
        Spacer(Modifier.height(Space.S1.dp))
        WindowBar(fraction, fill, Modifier.fillMaxWidth())
        note?.let {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(it, role = TypeRole.Caption, tone = TextTone.Secondary)
        }
    }
}

/** Dart `bar`: the surface3 track and the [fill] share from the reading edge, both at the progress-bar radius. */
@Composable
private fun WindowBar(fraction: Float, fill: Color, modifier: Modifier) {
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.height(FlutterKitTokens.PROGRESS_BAR_HEIGHT.dp)) {
        val radius = CornerRadius(FlutterKitTokens.PROGRESS_BAR_RADIUS.dp.toPx())
        drawRoundRect(colors.surface3, cornerRadius = radius)
        if (fraction > 0f)
            drawRoundRect(
                fill,
                topLeft = Offset(if (rtl) size.width * (1f - fraction) else 0f, 0f),
                size = Size(size.width * fraction, size.height),
                cornerRadius = radius,
            )
    }
}
