package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBackdropSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The ground of a page (Dart `KitLiveBackground`, `lib/ui/kit/kit_live_background.dart` at GLASS_REF 1bbdc3f9c): the
 * page's flat ground and, when the person turned Background on, the chosen look on it: the painted aurora (Glass's own
 * look, [AuroraImage]) or one of the painted looks in [kitLiveStyles], drawn in its own layer by [KitLiveLayer] on the
 * shared [KitLiveClock] (12 ticks a second, resting after half a minute with no touch). A stored look the native app
 * does not draw yet keeps the flat ground. Off by default: a person who never turns it on sees exactly the flat ground.
 *
 * The picture is the only thing Glass panes blur ([io.github.eslamasabry.oc.ui.kit.foundation.KitGlassBackdrop]); the
 * page's own content is never recorded. It drifts very slowly at six frames a second while motion is Full, rests after
 * half a minute with no touch and carries on at the next one (Dart `KitLiveClock.idleAfter`), and holds one still frame
 * under Calm, Off, reduced motion, tests and screenshots. A painted look shows its still frame under Calm, Off and
 * reduced motion, and nothing under tests and screenshots (Flutter paints only the flat ground there). Saver hides it.
 *
 * States: off (flat ground), painted (the aurora or a look), still (its first frame).
 */
@Composable
public fun KitLiveBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val motion = LocalKitMotion.current
    val backdrop = rememberKitGlassBackdrop()
    // Hook H7: a style may cap the look or hide it (kit_live_background.dart:266-271).
    val strength = style.spec.tokens.live.cap(appearance.liveStrength)
    val shows =
        appearance.liveBackground && !appearance.saver && appearance.liveStyle == AuroraImage.ID && strength > 0f &&
            !motion.stillFrame
    val plate = rememberAuroraPlate(if (shows) appearance.liveStyle else null)
    // Any other look the native app draws (kit_live_background.dart:257-262): still frame unless motion is Full.
    val chosen = if (appearance.liveBackground && !appearance.saver && strength > 0f) appearance.liveStyle else null
    val look = if (motion.stillFrame) null else kitLiveStyleById(chosen)
    val looks = look != null && motion.ambientLoops
    val painted = plate != null
    val ground = colors.ground
    val accent = roles.accent
    SideEffect {
        backdrop.painted = painted
        backdrop.frostedSpots =
            if (painted) AuroraImage.spotsOf(roles.ground, accent, roles.dark, frosted = true).toList() else emptyList<Argb>()
    }
    // The drift's clock, in seconds; it only runs while motion is Full and the picture is up.
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
    // A touch wakes a resting drift or look; it never consumes the touch.
    val touch =
        if (!drifts && !looks) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch[0] = TimeSource.Monotonic.markNow()
                        KitLiveClock.instance.input()
                    }
                }
            }
        }
    // One shape whether or not the look is painted, so the page keeps its state when it turns on or loads.
    Box(modifier.then(ground0).then(source).then(touch)) {
        if (look != null) KitLiveLayer(look, strength, animate = motion.ambientLoops, modifier = Modifier.matchParentSize())
        CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop) { content() }
    }
}
