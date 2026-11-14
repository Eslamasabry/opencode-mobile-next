package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlinx.coroutines.launch

/**
 * Horizontal source scroller (Dart `_CodeScroller`, kit_code_block_parts.dart): one sideways scroller for every line,
 * a `space6` fade of the block's [fill] at an edge while more content is off-screen there, and, while the lines
 * overflow or under a fine pointer, a `space2` strip under the last line for the thumb, so it never sits on text. The
 * thumb shows at rest only under a fine pointer; on touch, only while the lines move.
 */
@Composable
internal fun CodeOverflow(
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    /**
     * The corner where floating controls sit over the lines' top end: the lines beside them are cleared there (with the
     * same short fade at its edge) and have that much room after their last glyph to scroll out from under it.
     */
    reserve: DpSize? = null,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    val fine by LocalKitPlatform.current.signals.collectAsState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hasOverflow = scroll.maxValue in 1 until Int.MAX_VALUE
        val thumbOn = hasOverflow && (fine.finePointer || scroll.isScrollInProgress) // F: lib/ui/kit/kit_code_block_parts.dart:184
        Box(
            // The fades thin the lines out toward an edge (the content's own alpha, in its own layer), which reads as
            // Flutter's fade of the block's fill over them and leaves the fill itself untouched.
            Modifier.fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                drawContent()
                if (reserve != null) {
                    val room = Size(reserve.width.toPx(), reserve.height.toPx())
                    val fade = Space.S6.dp.toPx()
                    val left = size.width - room.width
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), left - fade, left),
                        Offset(left - fade, 0f),
                        Size(fade, room.height),
                        blendMode = BlendMode.DstOut,
                    )
                    drawRect(Color.Black, Offset(left, 0f), room, blendMode = BlendMode.DstOut)
                }
                // Read while drawing, not from the composition: the scroller learns its range while it is laid out, and
                // a fade that waited for a recomposition could stay off on a block that plainly runs past its edge
                // (device pictures 2026-11-13: a listing cut inside a glyph with no fade).
                if (scroll.maxValue in 1 until Int.MAX_VALUE) {
                    val fade = Space.S6.dp.toPx() // F: lib/ui/kit/kit_code_block.dart:1069
                    if (scroll.value > 0)
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), 0f, fade),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstOut,
                        )
                    if (scroll.value < scroll.maxValue)
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Black),
                                size.width - fade,
                                size.width,
                            ),
                            Offset(size.width - fade, 0f),
                            Size(fade, size.height),
                            blendMode = BlendMode.DstOut,
                        )
                    if (thumbOn) {
                        val thumb =
                            (size.width * size.width / (size.width + scroll.maxValue)).coerceIn(
                                minOf(24.dp.toPx(), size.width),
                                size.width,
                            )
                        val x = (size.width - thumb) * scroll.value / scroll.maxValue
                        drawRoundRect(
                            ink.copy(alpha = .45f),
                            Offset(x, size.height - 3.dp.toPx()),
                            Size(thumb, 3.dp.toPx()),
                            CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
        ) {
            Row(
                Modifier.horizontalScroll(scroll)
                    // After its last glyph the first lines have the controls' corner to scroll out of.
                    .padding(end = reserve?.width ?: 0.dp)
                    .padding(bottom = if (fine.finePointer || hasOverflow) Space.S2.dp else 0.dp) // F: lib/ui/kit/kit_code_block_parts.dart:191,192
            ) {
                content()
            }
            if (fine.finePointer && hasOverflow)
                Box(
                    Modifier.fillMaxWidth()
                        .height(Space.S3.dp)
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .pointerInput(scroll, width) {
                            detectHorizontalDragGestures { change, delta ->
                                change.consume()
                                scope.launch {
                                    scroll.scrollTo(
                                        (scroll.value + delta * (width + scroll.maxValue) / width)
                                            .toInt()
                                            .coerceIn(0, scroll.maxValue)
                                    )
                                }
                            }
                        }
                )
        }
    }
}
