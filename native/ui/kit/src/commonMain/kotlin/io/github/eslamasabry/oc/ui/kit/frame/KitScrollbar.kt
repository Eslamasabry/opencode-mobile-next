package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlinx.coroutines.launch

/** Which way a [KitScrollbar]'s box scrolls: down the page, or sideways (a code block, a log, a table). */
public enum class KitScrollAxis { Vertical, Horizontal }

/**
 * The one scrollbar's rule (Dart `KitScrollBehavior`, `KitScrollbar`, lib/ui/kit/kit_scrollbar.dart), kept pure so it
 * is tested without a window: on a fine pointer (a mouse) the thumb is always shown and draggable; on touch it is the
 * platform's fading thumb, shown only while the box scrolls; nothing is shown when the content fits; a subtree that
 * draws its own thumb gets no second one.
 */
public object KitScrollbarRules {
    /** Whether the thumb is drawn (kit_scrollbar.dart:40,109). */
    public fun thumbShown(finePointer: Boolean, scrolling: Boolean, overflow: Boolean): Boolean =
        overflow && (finePointer || scrolling)

    /** Only a fine pointer drags the thumb; the mouse never drag-scrolls the content itself (kit_scrollbar.dart:17-19). */
    public fun draggable(finePointer: Boolean, overflow: Boolean): Boolean = finePointer && overflow

    /** The thumb's length along [track] px for [maxScroll] px of hidden content, never shorter than [minLength]. */
    public fun thumbLength(track: Float, maxScroll: Int, minLength: Float): Float {
        if (track <= 0f) return 0f
        if (maxScroll <= 0) return track
        return (track * track / (track + maxScroll)).coerceIn(minOf(minLength, track), track)
    }

    /** Where the thumb starts along [track] px at [value] of [maxScroll]. */
    public fun thumbOffset(track: Float, thumb: Float, value: Int, maxScroll: Int): Float =
        if (maxScroll <= 0) 0f else (track - thumb) * value.coerceIn(0, maxScroll) / maxScroll

    /** The scroll a drag of [delta] px along the track moves the content by. */
    public fun scrollForDrag(delta: Float, track: Float, thumb: Float, maxScroll: Int): Float =
        if (track <= thumb || maxScroll <= 0) 0f else delta * maxScroll / (track - thumb)
}

private val LocalKitOwnScrollbar = staticCompositionLocalOf { false }

/** Whether the subtree already draws its own thumb, so no second one is added (Dart `KitOwnScrollbar`). */
@Composable
public fun kitHasOwnScrollbar(): Boolean = LocalKitOwnScrollbar.current

/**
 * Marks [content] as drawing its own thumb, so the kit adds no second one inside it (Dart `KitOwnScrollbar`).
 *
 * States: none (configuration only; it draws nothing)
 * Text: none
 * Cap: wrapper
 * Motion: none
 * Replaces: KitOwnScrollbar (lib/ui/kit/kit_scrollbar.dart)
 */
@Composable
public fun KitOwnScrollbar(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitOwnScrollbar provides true, content = content)
}

/**
 * Gives a long scrollable its scroll state on a fine pointer, so a [KitScrollbar] can pin a draggable thumb to it
 * (Dart `KitScrollArea`). On touch [builder] gets null and the scrollable is built exactly as before.
 *
 * States: none
 * Text: none
 * Cap: wrapper
 * Motion: none
 * Replaces: KitScrollArea (lib/ui/kit/kit_scrollbar.dart)
 */
@Composable
public fun KitScrollArea(builder: @Composable (ScrollState?) -> Unit) {
    val state = rememberScrollState()
    builder(if (LocalKitWindow.current.finePointer) state else null)
}

/**
 * An explicit scrollbar for a box that scrolls inside a page (Dart `KitScrollbar`): the thumb rides the end edge (the
 * bottom edge for [KitScrollAxis.Horizontal]), always shown and draggable on a fine pointer, and the fading thumb that
 * shows only while scrolling on touch. [state] is the box's own scroll state, also given to its scrollable. Its
 * [content] is wrapped in [KitOwnScrollbar] so nothing inside adds a second thumb. The thumb is decoration: no
 * semantics (the scrollable carries its own scroll actions).
 *
 * States: idle, scrolling (touch), dragged (fine pointer)
 * Text: none
 * Cap: wrapper
 * Motion: the touch thumb fades in and out (QUICK); Off = shown or hidden at once
 * Replaces: KitScrollbar (lib/ui/kit/kit_scrollbar.dart)
 */
@Composable
public fun KitScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    axis: KitScrollAxis = KitScrollAxis.Vertical,
    content: @Composable BoxScope.() -> Unit,
) {
    val fine = LocalKitWindow.current.finePointer
    val ink = LocalKitColors.current.text3
    val overflow = state.maxValue in 1 until Int.MAX_VALUE
    val shown = KitScrollbarRules.thumbShown(fine, state.isScrollInProgress, overflow)
    val alpha by animateFloatAsState(if (shown) 1f else 0f, KitMotion.specs.quick(), label = "kit-scrollbar")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val vertical = axis == KitScrollAxis.Vertical
    // The thumb's thickness and the shortest it gets come from the kit's spacing steps (Material's 8 dp thumb).
    val thickness = Space.S2.dp
    val minLength = Space.S6.dp
    Box(
        modifier.drawWithContent {
            drawContent()
            if (alpha <= 0f || !overflow) return@drawWithContent
            val track = if (vertical) size.height else size.width
            val t = thickness.toPx()
            val thumb = KitScrollbarRules.thumbLength(track, state.maxValue, minLength.toPx())
            val at = KitScrollbarRules.thumbOffset(track, thumb, state.value, state.maxValue)
            val origin =
                when {
                    !vertical -> Offset(at, size.height - t)
                    rtl -> Offset(0f, at)
                    else -> Offset(size.width - t, at)
                }
            drawRoundRect(
                ink,
                origin,
                if (vertical) Size(t, thumb) else Size(thumb, t),
                CornerRadius(t),
                alpha = alpha,
            )
        }
    ) {
        KitOwnScrollbar { content() }
        if (KitScrollbarRules.draggable(fine, overflow)) {
            val drag =
                Modifier.testTag("kit-scrollbar-thumb").pointerInput(state, vertical) {
                    val track = if (vertical) size.height.toFloat() else size.width.toFloat()
                    val thumb = KitScrollbarRules.thumbLength(track, state.maxValue, minLength.toPx())
                    val move: (Float) -> Unit = { delta ->
                        val by = KitScrollbarRules.scrollForDrag(delta, track, thumb, state.maxValue)
                        scope.launch { state.scrollTo((state.value + by).toInt().coerceIn(0, state.maxValue)) }
                    }
                    if (vertical) detectVerticalDragGestures { change, delta -> change.consume(); move(delta) }
                    else detectHorizontalDragGestures { change, delta -> change.consume(); move(delta) }
                }
            if (vertical) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(Space.S3.dp).then(drag))
            } else {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(Space.S3.dp).then(drag))
            }
        }
    }
}
