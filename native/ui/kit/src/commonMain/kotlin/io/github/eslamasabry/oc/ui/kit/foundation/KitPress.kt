package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
