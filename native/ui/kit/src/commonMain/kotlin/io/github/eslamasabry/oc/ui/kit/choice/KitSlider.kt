package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt

/** The words a [KitSlider] needs beside its own label: the reset mark, and the dialog that types a number. */
@Immutable
public data class KitSliderWords(val reset: String, val typeTitle: String, val typeField: String, val typeConfirm: String)

/**
 * A continuous slider with its name, unit and number (the Glass lab, 2026-11-13): the [label] and [unit] on the start,
 * the value read-out on the end ([format] writes it), the track under them from [min] to [max] in steps of [step]. Drag
 * or tap the track to move it; double-tap the number to type one; the reset mark shows while the value is not
 * [default] and puts it back. Every number shown is the value [onChange] would report.
 *
 * States: at default, moved, typing
 * Text: frame (label, unit, number)
 * Cap: reading width
 */
@Composable
public fun KitSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    default: Float,
    onChange: (Float) -> Unit,
    words: KitSliderWords,
    modifier: Modifier = Modifier,
    unit: String? = null,
    format: (Float) -> String = { it.toString() },
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latest by rememberUpdatedState(onChange)
    var typing by remember { mutableStateOf(false) }
    fun snap(v: Float): Float {
        val n = ((v.coerceIn(min, max) - min) / step).roundToInt()
        return ((min + n * step).coerceIn(min, max) * 1000f).roundToInt() / 1000f
    }
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val share = ((value - min) / span).coerceIn(0f, 1f)
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().semantics(mergeDescendants = false) {}.testTag("kit-slider"),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KitText(label, role = TypeRole.Body, maxLines = 1)
                if (unit != null) KitText(unit, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
            }
            if (value != default) {
                KitIconButton(KitIcons.undo, words.reset, { latest(default) }, tone = TextTone.Secondary)
            }
            // The number: double-tap it to type one.
            KitText(
                format(value),
                Modifier.padding(start = Space.S2.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { typing = true }) }
                    .testTag("kit-slider-value"),
                role = TypeRole.Body,
                tone = if (value != default) TextTone.Accent else TextTone.Primary,
                tabular = true,
                maxLines = 1,
            )
        }
        val target = tokens.minTarget.dp
        val thumb = target * .5f
        val line = Space.S1.dp
        val railLook = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Track, KitShapeId.Pill, SurfaceLevel.Surface3, ownFill = colors.surface3))
        val thumbLook =
            kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Circle, SurfaceLevel.Surface2, ownFill = colors.accent, color = colors.accent))
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(target).semantics {
                contentDescription = label
                stateDescription = format(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value, min..max)
                setProgress { latest(snap(it)); true }
            }
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val thumbPx = with(density) { thumb.toPx() }
            val usable = (maxWidth - thumb).coerceAtLeast(0.dp)
            fun at(x: Float): Float {
                val from = if (rtl) widthPx - x else x
                val t = if (widthPx <= thumbPx) 0f else ((from - thumbPx / 2f) / (widthPx - thumbPx)).coerceIn(0f, 1f)
                return snap(min + t * span)
            }
            Box(
                Modifier.fillMaxSize().pointerInput(widthPx, rtl, min, max, step) {
                    detectHorizontalDragGestures(
                        onDragStart = { latest(at(it.x)) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            latest(at(change.position.x))
                        },
                    )
                }.pointerInput(widthPx, rtl, min, max, step) { detectTapGestures { latest(at(it.x)) } }
            ) {
                val startX = if (rtl) maxWidth - thumb / 2 - usable * share else thumb / 2
                Box(Modifier.offset(x = thumb / 2, y = (target - line) / 2).width(usable).height(line).then(railLook.modifier))
                Box(Modifier.offset(x = startX, y = (target - line) / 2).width(usable * share).height(line).background(colors.accent, RoundedCornerShape(line)))
                val thumbX = if (rtl) usable * (1f - share) else usable * share
                Box(Modifier.offset(x = thumbX, y = (target - thumb) / 2).size(thumb).then(thumbLook.modifier))
            }
        }
    }
    KitInputDialog(
        visible = typing,
        title = words.typeTitle,
        fieldLabel = words.typeField,
        confirmLabel = words.typeConfirm,
        initial = format(value),
        kind = KitFieldKind.Number,
        operationKey = label,
        onResult = { typed ->
            typing = false
            typed?.replace(',', '.')?.trim()?.toFloatOrNull()?.let { latest(snap(it)) }
        },
    )
}
