package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Motion level as far as P2 needs it. The kit's `MotionPolicy` (Full/Calm/Off, Saver, heat; plan 08 §3, unit M0)
 * replaces this; until then the only input is the system animator scale ("Remove animations" sets it to 0).
 */
public enum class ShellMotionLevel { Full, Off }

public fun motionLevelOf(animatorScale: Float): ShellMotionLevel =
    if (animatorScale == 0f) ShellMotionLevel.Off else ShellMotionLevel.Full

/** Plan 08 §5.4: the predictive-back gate that replaces the demo's 700 ms latch. No timers; state only. */
public enum class GateState { Idle, Opening, Closing, Gesture, GestureSettling }

public sealed interface GateEvent {
    /** AppNavigator.openChat (a GrowFromRow route was pushed). */
    public data object TapOpen : GateEvent

    /** Back committed without a gesture: top-bar Back, 3-button tap, Esc. */
    public data object ButtonBack : GateEvent

    /** The dispatcher's transitionState became InProgress (a back swipe or Android 16 long-press Back). */
    public data object GestureStarted : GateEvent

    /** NavDisplay.onBack while in [GateState.Gesture]. */
    public data object GestureCommitted : GateEvent

    /** transitionState returned to Idle without onBack. */
    public data object GestureCancelled : GateEvent

    /** At most one entry is composed and nothing is mid-transition (the shell's entry decorator count). */
    public data object TransitionSettled : GateEvent
}

public fun reduce(s: GateState, e: GateEvent): GateState =
    when (e) {
        GateEvent.TapOpen -> if (s == GateState.Idle) GateState.Opening else s
        GateEvent.ButtonBack -> if (s == GateState.Idle || s == GateState.Opening) GateState.Closing else s
        GateEvent.GestureStarted -> GateState.Gesture // also mid-flight during Opening
        GateEvent.GestureCommitted, GateEvent.GestureCancelled ->
            if (s == GateState.Gesture) GateState.GestureSettling else s
        GateEvent.TransitionSettled -> if (s == GateState.Gesture) s else GateState.Idle
    }

/** Shared bounds may run only at Full, outside a gesture and its settling. */
public fun sharedBoundsEnabled(s: GateState, level: ShellMotionLevel): Boolean =
    level == ShellMotionLevel.Full && s != GateState.Gesture && s != GateState.GestureSettling

/** Snapshot-backed holder; main thread only. Every event is logged to [trace] (tests and the debug overlay read it). */
@Stable
public class RowTransformGate(initial: GateState = GateState.Idle) {
    public var state: GateState by mutableStateOf(initial)
        private set

    /** Direction of the last navigation: true after a push, false after a pop (picks 360 ms vs 300 ms bounds). */
    public var opening: Boolean by mutableStateOf(true)
        private set

    private val events = ArrayList<GateEvent>()
    public val trace: List<GateEvent>
        get() = events

    public fun on(event: GateEvent) {
        events += event
        if (events.size > 64) events.removeAt(0)
        when (event) {
            GateEvent.TapOpen -> opening = true
            GateEvent.ButtonBack, GateEvent.GestureStarted -> opening = false
            else -> Unit
        }
        state = reduce(state, event)
    }
}
