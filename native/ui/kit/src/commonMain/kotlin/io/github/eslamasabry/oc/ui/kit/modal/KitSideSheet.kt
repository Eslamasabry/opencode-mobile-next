package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Where an end-side sheet rests. */
public enum class SideSheetValue { Hidden, Shown }

/**
 * Plan 08 §5.5 `KitSideSheet` as pure rules (`SideSheetAnchorsTest`). Offsets are physical pixels along x from the
 * shown position: positive moves right. The sheet sits at the **end** edge, so it hides to the right in LTR and to the
 * left in RTL.
 */
public object SideSheetRules {
    /** How far the gesture's preview moves the sheet toward its edge, as a share of its width. */
    public const val PREVIEW_SHARE: Float = 0.1f

    /** A fling faster than this toward an edge decides the settle on its own (M3's 125 dp/s is the floor). */
    public const val FLING_PX_PER_S: Float = 400f

    private fun towardEnd(rtl: Boolean): Float = if (rtl) -1f else 1f

    public fun hiddenOffset(widthPx: Float, rtl: Boolean): Float = towardEnd(rtl) * widthPx

    /** A drag never pulls the sheet past its resting place, and never past hidden. */
    public fun clampDrag(offsetPx: Float, widthPx: Float, rtl: Boolean): Float =
        if (rtl) offsetPx.coerceIn(-widthPx, 0f) else offsetPx.coerceIn(0f, widthPx)

    /** The predictive-back preview: up to 10 % of the width toward the end edge, following the gesture. */
    public fun predictiveShift(progress: Float, widthPx: Float, rtl: Boolean): Float =
        towardEnd(rtl) * PREVIEW_SHARE * widthPx * progress.coerceIn(0f, 1f)

    /** Where a released drag settles: a fling decides; otherwise past half its width hides it. */
    public fun settle(offsetPx: Float, widthPx: Float, velocityPx: Float, rtl: Boolean, flingPx: Float = FLING_PX_PER_S): SideSheetValue {
        val v = velocityPx * towardEnd(rtl)
        return when {
            v > flingPx -> SideSheetValue.Hidden
            v < -flingPx -> SideSheetValue.Shown
            widthPx > 0f && abs(offsetPx) / widthPx > 0.5f -> SideSheetValue.Hidden
            else -> SideSheetValue.Shown
        }
    }
}

/**
 * A full-height sheet at the end edge for wide windows (plan 08 §5.5, plan 06 §7.4: `Full` sheets from Expanded up;
 * Material 3 has no side sheet). It slides in from the end edge on `LargeTravel` (mirrored in RTL), follows a drag and
 * settles on `LargeTravel`, and follows the predictive back gesture toward the end edge by up to 10 % of its width,
 * hiding on completion and springing back on cancel. A scrim at `DIM_MOST` dims the page under it; tapping it asks to
 * close. Under Off it snaps and Back commits only. [onDismissRequest] is called for a drag that settles hidden, a tap on
 * the scrim and a completed Back; the host then sets [visible] false.
 *
 * States: shown, hidden, dragging, previewing
 * Text: none (content decides)
 * Cap: Sheet (end sheet 400–480)
 * Motion: slide LargeTravel; drag follows; settle LargeTravel; preview follows; Off = snap, commit only (Follow + Settle)
 * Replaces: — (new; Flutter used a centred sheet)
 */
@Composable
public fun KitSideSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = SIDE_SHEET_DP,
    content: @Composable () -> Unit,
) {
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val widthPx = with(LocalDensity.current) { widthDp.dp.toPx() }
    val hidden = SideSheetRules.hiddenOffset(widthPx, rtl)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val offset = remember { Animatable(if (visible) 0f else hidden) }
    var present by remember { mutableStateOf(visible) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible, hidden, motion.reduced) {
        if (visible) present = true
        val target = if (visible) 0f else hidden
        if (motion.reduced) offset.snapTo(target) else offset.animateTo(target, specs.largeTravel())
        if (!visible) present = false
    }
    if (!present && !visible) return
    // Back: the topmost sheet owns it. The preview follows the gesture (not under Off); completion asks to close.
    val back =
        if (LocalNavigationEventDispatcherOwner.current != null) {
            val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
            NavigationBackHandler(state = state, isBackEnabled = visible, onBackCompleted = { dismiss() })
            state
        } else {
            null
        }
    val preview: () -> Float = {
        val t = back?.transitionState
        if (motion.reduced || t !is NavigationEventTransitionState.InProgress) 0f
        else SideSheetRules.predictiveShift(KitEasing.PredictiveBack.transform(t.latestEvent.progress), widthPx, rtl)
    }
    val drag =
        rememberDraggableState { delta ->
            scope.launch { offset.snapTo(SideSheetRules.clampDrag(offset.value + delta, widthPx, rtl)) }
        }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .testTag("kit-side-sheet-scrim")
                .drawBehind {
                    val shownShare = 1f - (abs(offset.value) / widthPx).coerceIn(0f, 1f)
                    drawRect(colors.scrim, alpha = MotionTokens.DIM_MOST * shownShare)
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
        )
        Box(
            Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(widthDp.dp)
                .testTag("kit-side-sheet")
                .graphicsLayer { translationX = offset.value + preview() }
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        when (SideSheetRules.settle(offset.value, widthPx, velocity, rtl)) {
                            SideSheetValue.Hidden -> dismiss()
                            SideSheetValue.Shown ->
                                if (motion.reduced) offset.snapTo(0f) else offset.animateTo(0f, specs.largeTravel())
                        }
                    },
                )
                .background(colors.surface1)
                .semantics { dismiss { dismiss(); true } },
        ) { content() }
    }
}

/** Plan 06 §7.4: the end sheet is 400–480 dp; 440 sits in the middle. */
public const val SIDE_SHEET_DP: Int = 440
