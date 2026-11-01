package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
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
 * The topmost modal owns Back (plan 06 §7.4): a sheet or dialog registers this while it is up. Composed after
 * `NavDisplay`, it wins over the page behind it. The system's predictive-back gesture reports progress, which the modal
 * reads to shrink toward its origin (eased on the predictive-back curve, plan 08 §2.7); completing the gesture calls
 * [onBack]; cancelling springs back. Desktop Escape reaches it through the same dispatcher.
 */
@Composable
internal fun KitPredictiveBack(enabled: Boolean, onBack: () -> Unit): KitBackProgress {
    val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onBack)
    return KitBackProgress {
        when (val t = state.transitionState) {
            is NavigationEventTransitionState.InProgress -> KitEasing.PredictiveBack.transform(t.latestEvent.progress.coerceIn(0f, 1f))
            else -> 0f
        }
    }
}
