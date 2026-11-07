package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The glyph inside each 48 dp corner button: the reply footer's action size, so every code corner reads alike. */
private const val CORNER_GLYPH = 20

/** The fade in front of the corner buttons, so a line running under them dissolves instead of being cut. */
private const val CORNER_FADE = 24

/**
 * P-001: how much of the first line's width the corner's [buttons] (48 dp each) cover. The buttons sit flush with the
 * block's outer end edge, so they cover their width less the block's own end padding (S3). Only the first line keeps
 * clear of them; every other line runs the full width.
 */
internal fun codeCornerReserve(buttons: Int): Int =
    if (buttons <= 0) 0 else buttons * FlutterKitTokens.MIN_TARGET - Space.S3

/** The first line's height in [style] (where the corner centres its buttons); 20 dp when the style has none. */
@Composable
internal fun codeFirstLine(style: TextStyle): Dp =
    with(LocalDensity.current) { style.lineHeight.takeIf { it.isSp }?.toDp() } ?: 20.dp

/**
 * The code block's corner (P-001): Copy and, when lines overflow, Wrap, as 48 dp icon buttons at the end of the first
 * line, centred on it and flush with the block's end edge. They float: they add no height (a one-line block stays one
 * line tall, QA wave 4 N9) and the first line runs under them behind a short fade of the block's [fill], so no line is
 * cut mid-letter. Copy turns into a check for [COPIED_MS] and its name says [copiedLabel] (announced politely). The
 * caller's content sits in the block's padded box; [firstLine] is that box's first line height.
 */
@Composable
internal fun BoxScope.CodeCorner(
    fill: Color,
    firstLine: Dp,
    copyLabel: String?,
    copiedLabel: String?,
    onCopy: (() -> Unit)?,
    wrapOn: Boolean? = null,
    onWrap: (() -> Unit)? = null,
) {
    val copying = copyLabel != null && onCopy != null
    val wrapping = wrapOn != null && onWrap != null
    if (!copying && !wrapping) return
    var taps by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        copied = true
        delay(COPIED_MS)
        copied = false
    }
    val target = FlutterKitTokens.MIN_TARGET
    val buttons = (if (copying) 1 else 0) + (if (wrapping) 1 else 0)
    // The fade covers the first line (and the padding above it) up to the block's end edge.
    val fadeWidth = (buttons * target + CORNER_FADE).dp
    Box(
        Modifier.align(Alignment.TopEnd)
            .floating(dx = Space.S3.dp, dy = -Space.S2.dp)
            .width(fadeWidth)
            .height(firstLine + Space.S2.dp)
            .background(
                Brush.horizontalGradient(
                    0f to fill.copy(alpha = 0f),
                    CORNER_FADE / fadeWidth.value to fill,
                    1f to fill,
                ),
                // The block's own top-end rounding: the fade never squares its corner.
                RoundedCornerShape(topEnd = 8.dp),
            )
    )
    DisableSelection {
        Row(
            Modifier.align(Alignment.TopEnd)
                .floating(dx = Space.S3.dp, dy = (firstLine - target.dp) / 2)
                .testTag(CODE_CORNER_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (copying) {
                val label = if (copied && copiedLabel != null) copiedLabel else copyLabel!!
                KitIconButton(
                    if (copied) KitIcons.check else KitIcons.copy,
                    label,
                    {
                        onCopy!!()
                        taps++
                    },
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    tone = TextTone.Secondary,
                    size = CORNER_GLYPH,
                )
            }
            if (wrapping) {
                KitIconButton(
                    KitIcons.wrapText,
                    stringResource(Res.string.kitWrapLines),
                    onWrap!!,
                    Modifier.semantics {
                        toggleableState = if (wrapOn == true) ToggleableState.On else ToggleableState.Off
                    },
                    tone = if (wrapOn == true) TextTone.Accent else TextTone.Secondary,
                    size = CORNER_GLYPH,
                )
            }
        }
    }
}

/**
 * Lays the corner out without taking room: measured at its natural size, reported as zero high, and shifted by
 * ([dx], [dy]) toward the end and top (mirrors under right to left; the block itself is always left to right).
 */
private fun Modifier.floating(dx: Dp, dy: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Int.MAX_VALUE))
    layout(p.width, 0) { p.placeRelative(dx.roundToPx(), dy.roundToPx()) }
}

/** How long Copy shows its check after a tap. */
internal const val COPIED_MS: Long = 2_000

/** The corner's buttons, for tests. */
public const val CODE_CORNER_TAG: String = "kit-code-corner"
