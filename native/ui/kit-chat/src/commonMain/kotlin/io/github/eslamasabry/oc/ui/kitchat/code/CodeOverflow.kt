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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlinx.coroutines.launch

/**
 * Horizontal source scroller: edge fades reveal overflow and a separate thumb strip never paints
 * over the last line.
 */
@Composable
internal fun CodeOverflow(
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    val fine by LocalKitPlatform.current.signals.collectAsState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hasOverflow = scroll.maxValue in 1 until Int.MAX_VALUE
        val thumbOn = hasOverflow && (fine.finePointer || scroll.isScrollInProgress)
        Box(
            Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                if (hasOverflow) {
                    val fade = minOf(24.dp.toPx(), size.width / 4f)
                    if (scroll.value > 0)
                        drawRect(
                            Brush.horizontalGradient(listOf(fill, fill.copy(alpha = 0f)), 0f, fade),
                            size = Size(fade, size.height),
                        )
                    if (scroll.value < scroll.maxValue)
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(fill.copy(alpha = 0f), fill),
                                size.width - fade,
                                size.width,
                            ),
                            Offset(size.width - fade, 0f),
                            Size(fade, size.height),
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
                    .padding(bottom = if (hasOverflow) Space.S2.dp else 0.dp)
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
