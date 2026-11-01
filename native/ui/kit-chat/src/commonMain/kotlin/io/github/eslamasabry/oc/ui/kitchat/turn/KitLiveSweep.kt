package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.rememberLoopPhase

/** The light's centre, as a share of the width, at lap share [t]: from −0.3 to 1.3 (reversed in RTL). */
internal fun liveSweepCentre(t: Float, rtl: Boolean): Float = SWEEP_FROM + SWEEP_SPAN * (if (rtl) 1f - t else t)

/** How fast the live-line sweep runs: Full 1 (2200 ms a pass), Calm 0.5 (4400 ms), Off 0 (still words). */
internal fun liveSweepSpeed(motion: KitMotionState): Float =
    when (motion.level) {
        MotionLevel.Full -> 1f
        MotionLevel.Calm -> 0.5f
        MotionLevel.Off -> 0f
    }

/**
 * The live line's sweep (plan 08 §5.15 `KitTurnLive`; Flutter `_LiveSweep`): while the agent is [working], a light passes
 * over the words — `text3 → text1 → text3`, a band of ±0.18 of the width whose centre travels from −0.3 to 1.3
 * (reversed in RTL), one pass per `LIVE_SWEEP_LAP` (2200 ms) on `Steady`. A progress loop: half speed under Calm, still
 * words under Off and when nothing works (the agent waits for the person). Still frames (tests, screenshots) draw the
 * light at the middle.
 *
 * Draw phase only: the words never recompose or re-lay out for the sweep; the band's brush is cached per size and each
 * frame only translates it. The words keep their own semantics.
 *
 * Motion: progress loop. Replaces: `_LiveSweep` (lib/ui/kit/chat/kit_turn.dart).
 */
@Composable
public fun KitLiveSweep(working: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = KitMotion.state
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val speed = liveSweepSpeed(motion)
    val moving = working && speed > 0f
    val phase = rememberLoopPhase(moving && !motion.stillFrame, MotionTokens.LIVE_SWEEP_LAP, speed, still = STILL_FRAME)
    // One Box either way, so the words keep their state when the sweep starts or stops.
    val sweep = if (!moving) Modifier else Modifier
            .testTag(SWEEP_TAG)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val half = BAND * size.width
                val band = Brush.horizontalGradient(
                    0f to colors.text3, 0.5f to colors.text1, 1f to colors.text3, startX = -half, endX = half,
                )
                val bandSize = Size(2 * half, size.height)
                onDrawWithContent {
                    drawContent()
                    // The words take text3 everywhere, then the band lights them where it passes (srcIn keeps glyph alpha).
                    drawRect(colors.text3, blendMode = BlendMode.SrcIn)
                    translate(left = liveSweepCentre(phase.floatValue, rtl) * size.width) {
                        drawRect(band, topLeft = Offset(-half, 0f), size = bandSize, blendMode = BlendMode.SrcIn)
                    }
                }
            }
    Box(modifier.then(sweep)) { content() }
}

/** Test handle of the sweeping layer (present only while it sweeps). */
public const val SWEEP_TAG: String = "kit-turn-live-sweep"

private const val SWEEP_FROM = -0.3f
private const val SWEEP_SPAN = 1.6f
private const val BAND = 0.18f
private const val STILL_FRAME = 0.5f
