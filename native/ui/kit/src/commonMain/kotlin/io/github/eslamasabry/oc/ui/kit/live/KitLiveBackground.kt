package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.LiveSpots
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBackdropBoundary
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBackdropSource
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassFrostAvailable
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
 * - Under Glass, where its panes blur (Android 12+, not held), a shader look is that picture too (owner 2026-11-09:
 *   in-flow panes show real glass over every live look): the glass backdrop records the look once per frame while it
 *   moves, at most [GLASS_PICTURE_FPS] times a second ([rememberLiveShaderBackdrop]), the page shows that recording and
 *   every pane blurs the same one. A still look records nothing. Everywhere else the shader keeps its own layer
 *   ([LiveShaderLayer]) exactly as before. Panes over it are thickened for that look's measured reach alone
 *   ([LiveSpots.forLook], glass-live 2026-11-09), so the motion shows through them.
 *
 * Moves only while motion is Full: Calm (which Battery Saver and heat force), Off, reduced motion, tests and
 * screenshots hold one still frame. The clock rests after half a minute with no touch and wakes at the next one, easing
 * into the rest and out of it ([LiveFrameClock]), so neither the page nor the glass over it snaps still or into motion; it
 * stops with the app (the frame clock pauses when the window is hidden). Saver hides the look; a style may cap or hide
 * it.
 *
 * [shared] (every framed page, `KitScreen`): the page shows the theme root's one live background ([KitLiveShare]): one
 * clock, and per page size one recorded picture, one shader frame and one set of blurred copies, so a push or a pop
 * draws the look once (smoothness round 2). Each page still draws that picture in its own frame, so nothing is seen
 * through a page. A thumbnail of a look ([KitLiveLookPreview]) keeps its own.
 *
 * States: off (flat ground), shader, still fallback, painted.
 */
@Composable
public fun KitLiveBackground(modifier: Modifier = Modifier, shared: Boolean = false, content: @Composable () -> Unit) {
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val motion = LocalKitMotion.current
    val platform = LocalKitPlatform.current
    val backdrop = rememberKitGlassBackdrop()
    val share = if (shared) LocalKitLiveShare.current else null
    val clock = share?.clock ?: rememberLiveFrameClock()
    val pictures = share?.pictures
    // Hook H7: a style may cap the look or hide it (kit_live_background.dart:266-271).
    val strength = style.spec.tokens.live.cap(appearance.liveStrength)
    val on = appearance.liveBackground && !appearance.saver && strength > 0f
    val shadersSupported = remember(platform) { platform.liveShaders?.compile(LiveShaderLib.PROBE) != null }
    val render = liveRenderFor(appearance.liveStyle, on, shadersSupported)
    // The painted aurora keeps its earlier gate: it is not drawn under a still-frame (test or screenshot) host.
    val paints = render == LiveRender.Painted && !motion.stillFrame
    val plate = rememberAuroraPlate(if (paints) appearance.liveStyle else null)
    val ground = colors.ground
    val accent = roles.accent
    val look = LiveLooks.shader(appearance.liveStyle)
    // Glass blurs a shader look as it blurs the painted aurora, wherever its panes blur at all (kitGlassBlurs' holds).
    val glassBlurs = style.spec.tokens.blursBehind && kitGlassFrostAvailable()
    val animates = motion.ambientLoops && (plate != null || render == LiveRender.Shader)
    val time = rememberLiveTime(clock, animates)
    // The picture every pane blurs steps at the look's own pace up to GLASS_PICTURE_FPS: on a 120 Hz screen every other
    // frame re-records no picture and re-blurs no pane (the shader alone still follows every frame where it draws itself).
    val pictureTime = rememberLiveTime(clock, animates, GLASS_PICTURE_FPS)
    // A shared page (smoothness round 2) draws a shader look through the shared picture whatever the style: one shader
    // frame for every page of its size. Where no pane blurs it, it keeps every display frame, exactly as LiveShaderLayer.
    val shaderTime = if (glassBlurs) pictureTime else time
    val shaderPicture =
        if ((glassBlurs || pictures != null) && look != null && render == LiveRender.Shader) {
            rememberLiveShaderBackdrop(look, strength, shaderTime, pictures)
        } else {
            null
        }
    val painted = plate != null || shaderPicture != null
    // Six steps a second for the painted picture (its drift moves under a pixel a second): Glass panes re-blur it on
    // every step.
    val drift = remember(clock) { derivedStateOf { floor(clock.seconds * AuroraImage.FRAMES_PER_SECOND) / AuroraImage.FRAMES_PER_SECOND } }
    SideEffect {
        // What the picture steps with, so every pane over it draws again on the same frame as the picture (glass-live).
        backdrop.stepping =
            when {
                !animates -> NoSteps
                plate != null -> drift::value
                shaderPicture != null -> shaderTime
                else -> NoSteps
            }
        backdrop.painted = painted
        backdrop.frostedSpots =
            when {
                plate != null -> AuroraImage.clearSpotsOf(roles.ground, accent, roles.dark)
                // The look's own measured reach under a clear pane's frost (LiveReach), not the whole cap: a pane over
                // thin glyphs stays clear enough to show them move.
                shaderPicture != null -> LiveSpots.forLook(roles, appearance.liveStyle, lens = false)
                else -> emptyList<Argb>()
            }
    }
    // The window's sheets and menus follow this page's picture (glass-live: their glass is never a stale frame).
    val livePicture = io.github.eslamasabry.oc.ui.kit.foundation.LocalKitLivePicture.current
    if (livePicture != null) {
        DisposableEffect(livePicture, backdrop) {
            livePicture.backdrop = backdrop
            onDispose { if (livePicture.backdrop === backdrop) livePicture.backdrop = null }
        }
    }
    if (animates) {
        DisposableEffect(clock) {
            clock.acquire()
            onDispose { clock.release() }
        }
    }
    val alpha = AuroraImage.alphaFor(strength)
    val ground0 = if (painted) Modifier else Modifier.background(ground)
    // The picture is recorded on its own node behind the content, so a step of it never redraws the page's content.
    val source =
        when {
            plate != null -> {
                val (image, filter) = plate
                Modifier.kitGlassBackdropSource(backdrop, pictures) {
                    drawRect(ground)
                    val at = if (animates) drift.value.toDouble() else 0.0
                    drawAuroraPlate(image, filter, alpha, AuroraImage.driftAt(at))
                }
            }
            shaderPicture != null -> Modifier.kitGlassBackdropSource(backdrop, pictures, shaderPicture)
            else -> null
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
    Box(modifier.then(ground0).kitGlassBackdropBoundary().then(touch)) {
        // Its own layer: a step of the picture re-records this layer alone, never the page's content beside it.
        if (source != null) Spacer(Modifier.matchParentSize().graphicsLayer {}.then(source))
        if (look != null && render == LiveRender.Shader) {
            if (shaderPicture == null) LiveShaderLayer(look, strength, time, Modifier.matchParentSize())
        } else if (render == LiveRender.Still) {
            LiveStill(rememberLivePalette(), strength, Modifier.matchParentSize())
        }
        CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop, LocalLiveClock provides clock) { content() }
    }
}

/** The most steps a second the glass picture takes (the shader looks); a 60, 90 or 120 Hz screen draws it at most 60. */
internal const val GLASS_PICTURE_FPS: Int = 60

/**
 * What the page's live background can put behind a liquid face's content right now (glass-live 2026-11-09), for
 * [io.github.eslamasabry.oc.ui.design.effects.LiquidEdge.face]: nothing over the flat ground (Background off, Saver, a
 * style that hides it, a look the app does not draw), the frosted aurora's spots under the painted aurora, and a shader
 * look's own measured spots under the lens ([LiveSpots.forLook]). Read from the appearance, so a sheet or a menu outside
 * the page's [KitLiveBackground] knows it too.
 */
@Composable
internal fun kitLivePictureSpots(): List<Argb> {
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val strength = style.spec.tokens.live.cap(appearance.liveStrength)
    val on = appearance.liveBackground && !appearance.saver && strength > 0f
    val id = appearance.liveStyle
    return remember(on, id, roles) {
        when {
            !on || !LiveLooks.draws(id) -> emptyList()
            id == AuroraImage.ID -> AuroraImage.spotsOf(roles.ground, roles.accent, roles.dark, frosted = true).toList()
            else -> LiveSpots.forLook(roles, id, lens = true)
        }
    }
}

private val NoSteps: () -> Any? = { null }
