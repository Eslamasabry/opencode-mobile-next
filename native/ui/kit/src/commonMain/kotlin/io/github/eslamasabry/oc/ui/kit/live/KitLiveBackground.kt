package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBackdropSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The ground of a page (Dart `KitLiveBackground`, `lib/ui/kit/kit_live_background.dart`): the page's flat ground and,
 * when the person turned Background on, the chosen look on it: one of the drawn looks of [kitLiveStyles] or the painted
 * aurora (Glass's own look, [AuroraImage]). Off by default: a person who never turns it on sees exactly the flat ground.
 *
 * The look moves on the shared [KitLiveClock] (12 ticks a second, fewer for a look that asks for fewer; it rests after
 * half a minute with no touch, scroll or key and carries on at the next one) while motion is Full; under Calm, Off or
 * reduced motion it shows its still frame (the look at second 0); Saver hides it; a school's live policy caps its
 * strength or hides it. In tests and screenshots ([io.github.eslamasabry.oc.ui.kit.motion.KitMotionState.stillFrame]) the
 * page keeps its flat ground, as Flutter's goldens do. [replyRunning] brightens the look over 600 ms while a reply runs.
 *
 * Smooth by construction: the look draws in its own graphics layer below the page, reading the clock only in its draw
 * phase, so a tick redraws that layer alone and the page above never recomposes or redraws because of it.
 *
 * States: off (flat ground), painted (a look), still (its first frame).
 */
@Composable
public fun KitLiveBackground(modifier: Modifier = Modifier, replyRunning: Boolean = false, content: @Composable () -> Unit) {
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val motion = LocalKitMotion.current
    val backdrop = rememberKitGlassBackdrop()
    // Hook H7: a style may cap the look or hide it (kit_live_background.dart:266-271).
    val strength = style.spec.tokens.live.cap(appearance.liveStrength)
    val on = appearance.liveBackground && !appearance.saver && strength > 0f && !motion.stillFrame
    val plate = rememberAuroraPlate(if (on && appearance.liveStyle == AuroraImage.ID) appearance.liveStyle else null)
    val drawn = if (on && plate == null) kitLiveStyleOrNull(appearance.liveStyle) else null
    val painted = plate != null
    val ground = colors.ground
    val accent = roles.accent
    SideEffect {
        backdrop.painted = painted
        backdrop.frostedSpots =
            if (painted) AuroraImage.spotsOf(roles.ground, accent, roles.dark, frosted = true).toList() else emptyList<Argb>()
    }
    // The painted aurora's drift, in seconds; it only runs while motion is Full and the picture is up.
    var seconds by remember { mutableFloatStateOf(0f) }
    val drifts = painted && motion.ambientLoops
    val lastTouch = remember { arrayOf(TimeSource.Monotonic.markNow()) }
    if (drifts) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                delay((1000 / AuroraImage.FRAMES_PER_SECOND).toLong())
                val now = withFrameNanos { it }
                // A clock never jumps: at most a quarter second per tick (kit_live_support.dart:61).
                val step = min((now - last) / 1e9, .25)
                last = now
                if (lastTouch[0].elapsedNow() < AuroraImage.IDLE_AFTER_MS.milliseconds) seconds += step.toFloat()
            }
        }
    }
    val alpha = AuroraImage.alphaFor(strength)
    val ground0 = if (painted) Modifier else Modifier.background(ground)
    val source =
        if (plate == null) {
            Modifier
        } else {
            val (image, filter) = plate
            Modifier.kitGlassBackdropSource(backdrop) {
                drawRect(ground)
                drawAuroraPlate(image, filter, alpha, AuroraImage.driftAt(if (drifts) seconds.toDouble() else 0.0))
            }
        }
    val moves = (drifts || (drawn != null && motion.ambientLoops))
    val clock = KitLiveClock.instance
    // A touch, a scroll or a key wakes a resting look; it never consumes the event.
    val touch =
        if (!moves) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch[0] = TimeSource.Monotonic.markNow()
                        clock.input()
                    }
                }
            }.onPreviewKeyEvent {
                clock.input()
                false
            }
        }
    // One shape whether or not a look is painted, so the page keeps its state when it turns on or loads.
    Box(modifier.then(ground0).then(source).then(touch)) {
        KitLiveLayer(
            style = drawn,
            strength = strength.toDouble(),
            moves = motion.ambientLoops,
            running = replyRunning,
            modifier = Modifier.matchParentSize(),
        )
        CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop) { content() }
    }
}

/** How long the look takes to brighten or settle when a reply starts or ends. */
internal const val LIVE_BRIGHTEN_MS: Int = 600 // F: lib/ui/kit/kit_live_background.dart:75

/**
 * One drawn look in its own layer (Dart `_StylePainter` under a `RepaintBoundary`): [style] at [strength], moving on the
 * shared clock while [moves], else its still frame; null draws nothing (the layer stays, so the tree keeps its shape).
 */
@Composable
internal fun KitLiveLayer(style: KitLiveStyle?, strength: Double, moves: Boolean, running: Boolean, modifier: Modifier) {
    val colors = LocalKitColors.current
    val palette = remember(colors) { KitLivePalette.of(colors) }
    val memory = remember(style) { KitLiveMemory() }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val mono = LocalKitTypography.current.style(TypeRole.Mono).fontFamily
    val text = remember(measurer, mono) { mono?.let { KitLiveText(measurer, it) } }
    val time = remember { mutableDoubleStateOf(0.0) }
    val lift = remember { Animatable(if (running) 1f else 0f) }
    LaunchedEffect(running, moves) {
        val target = if (running) 1f else 0f
        if (moves) lift.animateTo(target, tween(LIVE_BRIGHTEN_MS, easing = LinearEasing)) else lift.snapTo(target)
    }
    val clock = KitLiveClock.instance
    if (style != null && moves) {
        DisposableEffect(Unit) {
            clock.attach()
            onDispose { clock.detach() }
        }
        LaunchedEffect(style) {
            time.value = clock.seconds
            while (true) {
                if (!clock.awake) snapshotFlow { clock.awake }.first { it }
                delay(KitLiveClock.INTERVAL_MICROS.microseconds)
                val now = withFrameNanos { it }
                clock.advance(now)
                if (clock.due(style.framesPerSecond) && time.value != clock.seconds) time.value = clock.seconds
            }
        }
    }
    if (style == null) {
        Spacer(modifier)
        return
    }
    Spacer(
        modifier
            .clearAndSetSemantics {}
            .graphicsLayer()
            .drawWithCache {
                onDrawBehind {
                    // Reading the clock and the lift here, in the draw phase, redraws this layer alone.
                    val frame =
                        KitLiveFrame(
                            t = if (moves) time.value else 0.0,
                            running = KitEasing.Pulse.transform(lift.value).toDouble(),
                            strength = strength,
                            palette = palette,
                            memory = memory,
                            text = text,
                        )
                    // Looks draw in logical pixels, as Flutter's do.
                    scale(density, density, pivot = Offset.Zero) {
                        style.paint(this, Size(size.width / density, size.height / density), frame)
                    }
                }
            }
    )
}
