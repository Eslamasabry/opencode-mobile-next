package io.github.eslamasabry.oc.ui.kit.teaching

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.motion.tipFade
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.delay

/** Dart `KitTip` numbers and placement (plan 06 §7.2; plan 08 §5.15). */
public object TipRules {
    /** The bubble's widest (Cap Popover, bubble 280). */
    public const val MAX_WIDTH_DP: Int = 280

    /** How long a tip stays by itself (`KitTip.defaultHold`, 4 s). */
    public const val HOLD_MS: Long = MotionTokens.TIP_HOLD.toLong()

    /** Above the anchor when it sits below the top quarter of the window, else below. */
    public fun above(anchorTopPx: Float, windowHeightPx: Float): Boolean = anchorTopPx > windowHeightPx / 4f

    /** How far the tip rises toward its anchor as it appears: Full only. */
    public fun riseDp(state: KitMotionState): Int = if (state.travels) MotionTokens.TIP_RISE_DP else 0
}

/**
 * One sentence anchored to a control (Dart `KitTip`; plan 06 §7.2, owner rule "teach what is hidden"), shown while
 * [visible]. It never blocks: it takes no focus, any touch fades it, and it fades by itself after [holdMs]; each end
 * calls [onDismissed] once. Above the anchor when the anchor is below the top quarter, else below; aligned with the
 * anchor's nearer edge so it never leaves the window; at most 280 dp wide. Announced politely when it appears. Never
 * amber, never red. Which tip shows, and how often, is the teaching controller's (S2-TEACH); this is the drawing.
 *
 * States: shown, hidden
 * Text: frame
 * Cap: Popover (bubble 280)
 * Motion: fade TIP_FADE + 4 dp rise toward the anchor; Calm = fade; Off = instant (class Travel)
 * Replaces: KitTip (lib/ui/kit/kit_tip.dart)
 */
@Composable
public fun KitTip(
    message: String,
    visible: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMs: Long = TipRules.HOLD_MS,
    anchor: @Composable () -> Unit,
) {
    kitAssert(message.isNotBlank()) { "KitTip: a tip is one sentence" }
    val dismiss by rememberUpdatedState(onDismissed)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val above = TipRules.above(bounds.top, windowHeight)
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    LaunchedEffect(visible, holdMs) {
        if (visible) {
            delay(holdMs)
            dismiss()
        }
    }
    Box(modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) {
        anchor()
        if (shown.currentState || shown.targetState) {
            val gap = with(LocalDensity.current) { Space.S2.dp.roundToPx() }
            val rise = with(LocalDensity.current) { TipRules.riseDp(motion).dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(above, gap) { TipPosition(above, gap) },
                onDismissRequest = { dismiss() },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
            ) {
                val enter: EnterTransition =
                    if (motion.reduced) EnterTransition.None
                    else fadeIn(specs.tipFade()) +
                        (if (rise != 0) slideInVertically(specs.pace<IntOffset>(KitPace.Standard)) { if (above) -rise else rise } else EnterTransition.None)
                val exit: ExitTransition = if (motion.reduced) ExitTransition.None else fadeOut(specs.tipFade())
                AnimatedVisibility(shown, enter = enter, exit = exit, label = "kit.tip") {
                    KitTipBubble(message, Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } })
                }
            }
        }
    }
}

/**
 * The tip's look: the sentence on the panel surface with a hairline edge, at most 280 dp, wrapping at large text. A
 * polite live region.
 *
 * States: one
 * Text: frame
 * Cap: Popover (280)
 * Motion: none of its own
 * Replaces: KitTipBubble (lib/ui/kit/kit_tip.dart)
 */
@Composable
public fun KitTipBubble(message: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Panel.shape()
    Box(
        modifier
            .testTag("kit-tip")
            .widthIn(max = TipRules.MAX_WIDTH_DP.dp)
            .background(colors.surface2, shape)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        KitText(message, role = TypeRole.Secondary, tone = TextTone.Primary, kind = KitTextKind.Frame)
    }
}

/** Above or below the anchor, aligned with the anchor's nearer edge, kept inside the window. */
private class TipPosition(private val above: Boolean, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val nearStart = anchorBounds.center.x < windowSize.width / 2
        val x =
            (if (nearStart) anchorBounds.left else anchorBounds.right - popupContentSize.width)
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y =
            (if (above) anchorBounds.top - gap - popupContentSize.height else anchorBounds.bottom + gap)
                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
