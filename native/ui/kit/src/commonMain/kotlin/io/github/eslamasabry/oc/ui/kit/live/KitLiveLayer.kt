package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * One painted look (Dart `_StylePainter` in `kit_live_background.dart`), drawn in its own graphics layer behind the
 * page: a tick of [KitLiveClock] changes only a state read in the draw phase, so it redraws this layer and nothing
 * else; the page above never recomposes or redraws because of the look. Paths, paints and shaders live in the look's
 * [KitLiveMemory] and the frame object is reused, so a tick allocates nothing.
 *
 * [animate] false (reduced motion, Calm, motion Off, a still sample) draws the look's still frame (t = 0) once; under
 * tests and screenshots (`KitMotionState.stillFrame`) it draws nothing, as Flutter's tests paint only the ground.
 */
@Composable
internal fun KitLiveLayer(
    style: KitLiveStyle,
    strength: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
    clock: KitLiveClock = KitLiveClock.instance,
) {
    // Tests and screenshots: Flutter paints no look there (kit_live_background.dart:256-261, KitMotion.loops).
    if (LocalKitMotion.current.stillFrame) {
        Spacer(modifier)
        return
    }
    val palette = KitLivePalette.of(LocalKitRoles.current)
    val memory = remember(style) { KitLiveMemory() }
    val frame = remember(memory) { KitLiveFrame(0.0, 0.0, strength.toDouble(), palette, memory) }
    var seconds by remember { mutableDoubleStateOf(clock.seconds) }
    if (animate) {
        LaunchedEffect(style, clock) {
            clock.attach()
            try {
                var seen = clock.ticks
                seconds = clock.seconds
                while (true) {
                    val next = withFrameNanos { clock.onFrame(it) }
                    if (clock.ticks != seen) {
                        seen = clock.ticks
                        if (clock.due(style.framesPerSecond)) seconds = clock.seconds
                    }
                    if (next < 0) {
                        // Resting: no tick and no frame until a touch wakes the clock.
                        snapshotFlow { clock.awake }.first { it }
                    } else {
                        delay(next / NANOS_PER_MILLI + 1)
                    }
                }
            } finally {
                clock.detach()
            }
        }
    }
    Spacer(
        modifier
            .clearAndSetSemantics {}
            .graphicsLayer()
            .drawWithCache {
                onDrawBehind {
                    frame.t = if (animate) seconds else 0.0
                    frame.strength = strength.toDouble()
                    frame.palette = palette
                    drawIntoCanvas { style.paint(it, size, frame) }
                }
            }
    )
}

private const val NANOS_PER_MILLI = 1_000_000L // NV: nanoseconds in a millisecond
