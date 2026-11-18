package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.Alphas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The kit's flat press feedback (Dart `KitInk` under Off; desktop hover/press): the part's [ink] at 12 % while pressed,
 * 10 % focused, 8 % hovered. A press stays visible at least `PRESS_HOLD` (100 ms), so the quickest tap is still seen.
 * Android draws the platform ripple instead ([io.github.eslamasabry.oc.ui.kit.platform.KitPlatform.pressIndication]);
 * reduced motion switches to this fill on the same tree, with no remount of the part.
 */
public class KitFillIndication(private val ink: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FillNode(interactionSource, ink)

    override fun equals(other: Any?): Boolean = other is KitFillIndication && other.ink == ink

    override fun hashCode(): Int = ink.hashCode()
}

private class FillNode(private val source: InteractionSource, private val ink: Color) :
    Modifier.Node(), DrawModifierNode {
    private var pressed = 0
    private var hovered = 0
    private var focused = 0
    private var pressedAt = 0L

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> {
                        pressed++
                        coroutineScope.launch { pressedAt = withFrameMillis { it } }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        coroutineScope.launch {
                            val shown = withFrameMillis { it } - pressedAt
                            val rest = MotionTokens.PRESS_HOLD - shown
                            if (rest > 0) delay(rest)
                            pressed = (pressed - 1).coerceAtLeast(0)
                            invalidateDraw()
                        }
                    is HoverInteraction.Enter -> hovered++
                    is HoverInteraction.Exit -> hovered = (hovered - 1).coerceAtLeast(0)
                    is FocusInteraction.Focus -> focused++
                    is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha =
            when {
                pressed > 0 -> Alphas.PRESSED
                focused > 0 -> Alphas.FOCUSED
                hovered > 0 -> Alphas.HOVERED
                else -> 0f
            }
        if (alpha > 0f) drawRect(ink.copy(alpha = alpha))
    }
}

/**
 * Solid glass's press answer (design draft): the face reads pressed in, lightly. While pressed (at least `PRESS_HOLD`, as the
 * flat fill) a half-step of the part's [ink], a shade a few dp deep along the inside of the top edge and, in dark only, a faint
 * light along the bottom edge. [shade] and [light] are the theme's own roles. There is no motion in it, so reduced motion keeps
 * it as it is (a deliberate choice; `KitTappable` therefore does not swap it for the flat fill). It draws only while pressed,
 * focused or hovered: nothing at rest, nothing time-driven.
 */
public class KitPressedInIndication(private val ink: Color, private val shade: Color, private val light: Color, private val dark: Boolean) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressedInNode(interactionSource, ink, shade, light, dark)

    override fun equals(other: Any?): Boolean = other is KitPressedInIndication && other.ink == ink && other.shade == shade && other.light == light && other.dark == dark

    override fun hashCode(): Int = ((ink.hashCode() * 31 + shade.hashCode()) * 31 + light.hashCode()) * 31 + dark.hashCode()
}

private class PressedInNode(
    private val source: InteractionSource,
    private val ink: Color,
    private val shade: Color,
    private val light: Color,
    private val dark: Boolean,
) : Modifier.Node(), DrawModifierNode {
    private var pressed = false
    private var hovered = 0
    private var focused = 0
    private var pressedAt = 0L
    private var release: kotlinx.coroutines.Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> {
                        // A new press cancels a release that is still holding the old one, so it is never cleared late.
                        release?.cancel()
                        pressed = true
                        pressedAt = withFrameMillis { it }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        release?.cancel()
                        release =
                            coroutineScope.launch {
                                val rest = MotionTokens.PRESS_HOLD - (withFrameMillis { it } - pressedAt)
                                if (rest > 0) delay(rest)
                                pressed = false
                                invalidateDraw()
                            }
                    }
                    is HoverInteraction.Enter -> hovered++
                    is HoverInteraction.Exit -> hovered = (hovered - 1).coerceAtLeast(0)
                    is FocusInteraction.Focus -> focused++
                    is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha =
            when {
                pressed -> Alphas.PRESSED * .5f
                focused > 0 -> Alphas.FOCUSED
                hovered > 0 -> Alphas.HOVERED
                else -> 0f
            }
        if (alpha > 0f) drawRect(ink.copy(alpha = alpha))
        if (pressed) {
            val band = io.github.eslamasabry.oc.ui.design.style.SolidGlassSpec.PRESSED_BAND_DP.dp.toPx().coerceAtMost(size.height / 2f)
            val a = if (dark) io.github.eslamasabry.oc.ui.design.style.SolidGlassSpec.PRESSED_SHADE_DARK else io.github.eslamasabry.oc.ui.design.style.SolidGlassSpec.PRESSED_SHADE_LIGHT
            drawRect(Brush.verticalGradient(listOf(shade.copy(alpha = a), Color.Transparent), startY = 0f, endY = band), size = Size(size.width, band))
            if (dark) drawRect(light.copy(alpha = .08f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
        }
    }
}
