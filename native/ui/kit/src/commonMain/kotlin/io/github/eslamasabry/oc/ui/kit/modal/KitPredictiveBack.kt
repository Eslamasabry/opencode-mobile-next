package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing

/** How far a modal shrinks at the end of a back gesture (M3's predictive-back sheet and dialog scale). */
internal const val PREDICTIVE_SHRINK: Float = 0.1f

/** The live back-gesture progress a modal reads in its draw phase (0 = resting, 1 = about to close). */
@Stable
internal fun interface KitBackProgress {
    fun value(): Float
}

/**
 * Who answers Back. The topmost open thing owns it (plan 06 §7.4): with a sheet, menu, dialog or picker open, one Back
 * closes that and nothing else (cut audit 2, finding 12).
 *
 * A page's own Back ([KitBackGuard], a search field's clear, the navigation itself) registers at the dispatcher's
 * default priority, where the handler added last wins. A modal ([overlay]) registers at the overlay priority, which the
 * dispatcher always asks before any default handler, whatever was composed first. Order alone could not hold that:
 * every screen's sheets live in the window's one host, whose handler was added when the first sheet of the session
 * opened, so a page opened later (Settings › This app) put its own Back above it and one press left the page while its
 * sheet was still open. Each time an overlay becomes enabled it is added again, so among open modals the one opened
 * last answers first.
 *
 * The system's predictive-back gesture reports progress, which the modal reads to shrink toward its origin (eased on
 * the predictive-back curve, plan 08 §2.7); completing the gesture calls [onBack]; cancelling springs back. Desktop
 * Escape and a hardware Back reach it through the same dispatcher.
 */
@Composable
internal fun KitPredictiveBack(enabled: Boolean, overlay: Boolean = true, onBack: () -> Unit): KitBackProgress {
    // No dispatcher (a bare preview or a test without one): nothing to register; Close and the scrim still work.
    val owner = LocalNavigationEventDispatcherOwner.current ?: return NoBack
    if (!overlay) {
        val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onBack)
        return KitBackProgress {
            when (val t = state.transitionState) {
                is NavigationEventTransitionState.InProgress -> KitEasing.PredictiveBack.transform(t.latestEvent.progress.coerceIn(0f, 1f))
                else -> 0f
            }
        }
    }
    val dispatcher = owner.navigationEventDispatcher
    val handler = remember { ModalBackHandler() }
    SideEffect { handler.onBack = onBack }
    // Registered while enabled and added again each time it is enabled: the modal opened last is asked first.
    DisposableEffect(dispatcher, enabled) {
        if (enabled) {
            handler.isBackEnabled = true
            dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        }
        onDispose {
            handler.isBackEnabled = false
            handler.remove()
            handler.progress = 0f
        }
    }
    return remember(handler) { KitBackProgress { KitEasing.PredictiveBack.transform(handler.progress.coerceIn(0f, 1f)) } }
}

/** A modal's Back at the overlay priority; [progress] is state, read in the draw phase. */
private class ModalBackHandler : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, false) {
    var onBack: () -> Unit = {}
    var progress: Float by mutableFloatStateOf(0f)

    override fun onBackStarted(event: NavigationEvent) {
        progress = event.progress
    }

    override fun onBackProgressed(event: NavigationEvent) {
        progress = event.progress
    }

    override fun onBackCancelled() {
        progress = 0f
    }

    override fun onBackCompleted() {
        progress = 0f
        onBack()
    }
}

private val NoBack = KitBackProgress { 0f }

/** Under motion Off a modal has no enter or exit transition at all, so its first frame is final (MOT-7). */
internal class ModalMotion(private val off: Boolean) {
    inline fun enter(t: () -> androidx.compose.animation.EnterTransition): androidx.compose.animation.EnterTransition =
        if (off) androidx.compose.animation.EnterTransition.None else t()

    inline fun exit(t: () -> androidx.compose.animation.ExitTransition): androidx.compose.animation.ExitTransition =
        if (off) androidx.compose.animation.ExitTransition.None else t()
}
