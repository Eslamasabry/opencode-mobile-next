package io.github.eslamasabry.oc.feature.chats.presentation

/** What one Back on the bare conversation list does (E05-S21). */
public enum class BackExitStep {
    /** The first Back: the status line says "Press back again to exit"; the app stays. */
    ShowHint,

    /** A second Back inside the window: the system takes it and the app leaves. */
    LeaveApp,
}

/**
 * The double-back exit guard on the root conversation list, as the released app does it
 * (home_screen.dart `_onRootPop`, `_backExitWindow`, `_clearBackExit`): the first Back shows the hint for
 * [WINDOW_MS]; a second Back inside that window leaves the app; once the window ends the hint folds away and the
 * next Back starts over. Pure value: the screen keeps it and ends the window after [WINDOW_MS].
 *
 * While the hint shows the home stops holding Back ([holdsBack] false), so the second Back reaches the system: on
 * Android 14+ the predictive back preview shows the app closing only on that second swipe, never on the first.
 */
public data class HomeBackExit(val hintShowing: Boolean = false) {
    /** Whether the home catches Back itself. False only while the hint shows, so the system can close the app. */
    val holdsBack: Boolean
        get() = !hintShowing

    /** What this Back does. */
    public fun step(): BackExitStep = if (hintShowing) BackExitStep.LeaveApp else BackExitStep.ShowHint

    /** The state after a Back. */
    public fun afterBack(): HomeBackExit = HomeBackExit(hintShowing = true)

    /** The window ended, or the person went somewhere else: the hint folds away and Back starts over. */
    public fun cleared(): HomeBackExit = Idle

    public companion object {
        /** How long the hint stays and a second Back exits (Flutter `_backExitWindow`, 2 s). */
        public const val WINDOW_MS: Long = 2_000L

        public val Idle: HomeBackExit = HomeBackExit()
    }
}
