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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** How a [KitWindowRow] is coloured (Dart `KitWindowTone`). */
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

/**
 * One quota window (Dart `KitWindowRow`, lib/ui/kit/kit_window_row.dart): "5 hours ........ 18% left · 14:10" with a
 * full-width bar under it. The bar fills with the share LEFT, like a fuel gauge, so a short bar always means little
 * left; every bar has the same width. It draws no surface and takes no taps. Screen readers hear one label,
 * "{title}, {valueSemantics}[, {note}]". [inline]: inside a row or block that already has the gutter, no side padding,
 * and at ordinary text sizes the name, the bar and the value share one line; from 1.3× text it stacks.
 *
 * States: none — the tone carries normal, low, out and calm
 * Text: reading
 * Cap: List (960)
 * Motion: none
 * Replaces: KitWindowRow (lib/ui/kit/kit_window_row.dart)
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
    val tokens = LocalKitTokens.current
    val fill = windowFill(tone, colors)
    val valueTone = windowValueTone(tone)
    val stacked = windowStacks(kitTextScale())
    val gutter = if (inline) 0.dp else tokens.gutter.dp
    Column(
        modifier.kitMaxWidth(CapRole.List).fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = windowLabel(title, valueSemantics, note)
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .padding(horizontal = gutter, vertical = Space.S1.dp),
    ) {
        if (inline && !stacked) InlineWindow(title, fraction, fill, valueLabel, valueTone, note)
        else StackedWindow(title, fraction, fill, valueLabel, valueTone, note)
    }
}

/** The bar's fill per tone (kit_window_row.dart:79-84). */
internal fun windowFill(tone: KitWindowTone, colors: KitColors): Color =
    when (tone) {
        KitWindowTone.Normal -> colors.accent
        KitWindowTone.Low, KitWindowTone.Out -> colors.text1
        KitWindowTone.Calm -> colors.text3
    }

/** The value's tone (kit_window_row.dart:85-88): strong when low or out, else secondary. */
internal fun windowValueTone(tone: KitWindowTone): TextTone =
    if (tone == KitWindowTone.Low || tone == KitWindowTone.Out) TextTone.Primary else TextTone.Secondary

/** From 1.3× text the row stacks (Dart `textScaler.scale(10) >= 13`). */
internal fun windowStacks(scale: Float): Boolean = scale * 10f >= 13f // F: lib/ui/kit/kit_window_row.dart:90

/** What a screen reader hears (kit_window_row.dart:176-178). */
internal fun windowLabel(title: String, value: String, note: String?): String =
    if (note == null) "$title, $value" else "$title, $value, $note"

/** One line fits when the name, a bar of some length and the value all fit (kit_window_row.dart:149-152). */
internal fun windowInlineFits(nameWidth: Dp, valueWidth: Dp, maxWidth: Dp): Boolean =
    nameWidth + Space.S3.dp + valueWidth + 48.dp <= maxWidth // F: lib/ui/kit/kit_window_row.dart:150

@Composable
private fun WindowBar(fraction: Float, fill: Color, modifier: Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.height(tokens.progressBar.dp)) {
        val radius = CornerRadius(FlutterKitTokens.PROGRESS_BAR_RADIUS.dp.toPx())
        drawRoundRect(colors.surface3, cornerRadius = radius)
        if (fraction > 0f) {
            val w = size.width * fraction
            drawRoundRect(fill, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height), cornerRadius = radius)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackedWindow(title: String, fraction: Float, fill: Color, value: String, valueTone: TextTone, note: String?) {
    // Name and value share one line and wrap rather than cut.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // Dart Wrap(spacing: space2): the gap rides on the name, so a wrapped value starts at the edge.
        KitText(title, Modifier.padding(end = Space.S2.dp), role = TypeRole.Secondary)
        KitText(value, role = TypeRole.Secondary, tone = valueTone, tabular = true)
    }
    Spacer(Modifier.height(Space.S1.dp))
    WindowBar(fraction, fill, Modifier.fillMaxWidth())
    note?.let {
        Spacer(Modifier.height(Space.S1.dp))
        KitText(it, role = TypeRole.Caption, tone = TextTone.Secondary)
    }
}

@Composable
private fun InlineWindow(title: String, fraction: Float, fill: Color, value: String, valueTone: TextTone, note: String?) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalKitTypography.current.style(TypeRole.Secondary)
    val nameWidth = (64 * density.fontScale).dp // F: lib/ui/kit/kit_window_row.dart:145
    val valueWidth = with(density) { measurer.measure(value, style, maxLines = 1).size.width.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (!windowInlineFits(nameWidth, valueWidth, maxWidth)) {
            Column { StackedWindow(title, fraction, fill, value, valueTone, note) }
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KitText(title, Modifier.width(nameWidth), role = TypeRole.Secondary)
            WindowBar(fraction, fill, Modifier.weight(1f))
            Spacer(Modifier.width(Space.S3.dp))
            KitText(value, role = TypeRole.Secondary, tone = valueTone, tabular = true)
        }
    }
}
