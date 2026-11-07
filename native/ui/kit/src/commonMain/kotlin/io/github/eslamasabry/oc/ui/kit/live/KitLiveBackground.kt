package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBackdropSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlin.math.floor

/**
 * The ground of a page: the flat ground and, when the person turned Background on, their chosen look on it
 * (E20-S28). Off by default: a person who never turns it on sees exactly the flat ground as before.
 *
 * - A shader look ([LiveLooks]) is drawn by [LiveShaderLayer] in its own layer behind the content: one full-size rect
 *   with the look's runtime shader, updated from the page's one [LiveFrameClock]. The content above never recomposes
 *   or redraws because the background moved. Below Android 13 the look shows its still fallback ([LiveRender.Still]).
 * - The painted aurora ([AuroraImage], Glass's own look) is the picture Glass panes blur
 *   ([io.github.eslamasabry.oc.ui.kit.foundation.KitGlassBackdrop]); it drifts on the same clock at six steps a second.
 *
 * Moves only while motion is Full: Calm (which Battery Saver and heat force), Off, reduced motion, tests and
 * screenshots hold one still frame. The clock rests after half a minute with no touch and wakes at the next one; it
 * stops with the app (the frame clock pauses when the window is hidden). Saver hides the look; a style may cap or hide
 * it.
 *
 * States: off (flat ground), shader, still fallback, painted.
 */
@Composable
public fun KitLiveBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val motion = LocalKitMotion.current
    val platform = LocalKitPlatform.current
    val backdrop = rememberKitGlassBackdrop()
    val clock = rememberLiveFrameClock()
    // Hook H7: a style may cap the look or hide it (kit_live_background.dart:266-271).
    val strength = style.spec.tokens.live.cap(appearance.liveStrength)
    val on = appearance.liveBackground && !appearance.saver && strength > 0f
    val shadersSupported = remember(platform) { platform.liveShaders?.compile(LiveShaderLib.PROBE) != null }
    val render = liveRenderFor(appearance.liveStyle, on, shadersSupported)
    // The painted aurora keeps its earlier gate: it is not drawn under a still-frame (test or screenshot) host.
    val paints = render == LiveRender.Painted && !motion.stillFrame
    val plate = rememberAuroraPlate(if (paints) appearance.liveStyle else null)
    val painted = plate != null
    val ground = colors.ground
    val accent = roles.accent
    SideEffect {
        backdrop.painted = painted
        backdrop.frostedSpots =
            if (painted) AuroraImage.spotsOf(roles.ground, accent, roles.dark, frosted = true).toList() else emptyList<Argb>()
    }
    val animates = motion.ambientLoops && (painted || render == LiveRender.Shader)
    if (animates) {
        DisposableEffect(clock) {
            clock.acquire()
            onDispose { clock.release() }
        }
    }
    val alpha = AuroraImage.alphaFor(strength)
    val ground0 = if (painted) Modifier else Modifier.background(ground)
    val source =
        if (plate == null) {
            Modifier
        } else {
            val (image, filter) = plate
            // Six steps a second for the picture: Glass panes re-blur it on every step.
            val steps = AuroraImage.FRAMES_PER_SECOND
            val drift = remember(clock) { derivedStateOf { floor(clock.seconds * steps) / steps } }
            Modifier.kitGlassBackdropSource(backdrop) {
                drawRect(ground)
                val at = if (animates) drift.value.toDouble() else 0.0
                drawAuroraPlate(image, filter, alpha, AuroraImage.driftAt(at))
            }
        }
    // A touch wakes a resting clock; it never consumes the touch.
    val touch =
        if (!animates) {
            Modifier
        } else {
            Modifier.pointerInput(clock) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        clock.touch()
                    }
                }
            }
        }
    // One shape whatever is drawn, so the page keeps its state when the look turns on or loads.
    Box(modifier.then(ground0).then(source).then(touch)) {
        val look = LiveLooks.shader(appearance.liveStyle)
        if (look != null && render == LiveRender.Shader) {
            val time = rememberLiveTime(clock, animates)
            LiveShaderLayer(look, strength, time, Modifier.matchParentSize())
        } else if (render == LiveRender.Still) {
            LiveStill(rememberLivePalette(), strength, Modifier.matchParentSize())
        }
        CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop, LocalLiveClock provides clock) { content() }
    }
}
