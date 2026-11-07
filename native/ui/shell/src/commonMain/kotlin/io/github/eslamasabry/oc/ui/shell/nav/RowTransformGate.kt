package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel

/**
 * How much pages move (M-013): Full slides and grows, Calm only cross-fades, Off is instant. [motionLevelOf] reads
 * the system animator scale ("Remove animations" sets it to 0); [shellLevel] folds in the kit's resolved level (the
 * person's Animations choice, battery saver and heat), so Calm never turns page transitions off.
 */
public enum class ShellMotionLevel { Full, Calm, Off }

public fun motionLevelOf(animatorScale: Float): ShellMotionLevel =
    if (animatorScale == 0f) ShellMotionLevel.Off else ShellMotionLevel.Full

/** The stricter of the system level and the kit's resolved [MotionLevel]. */
public fun shellLevel(system: ShellMotionLevel, kit: MotionLevel): ShellMotionLevel =
    when {
        system == ShellMotionLevel.Off || kit == MotionLevel.Off -> ShellMotionLevel.Off
        system == ShellMotionLevel.Calm || kit == MotionLevel.Calm -> ShellMotionLevel.Calm
        else -> ShellMotionLevel.Full
    }

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

/**
 * Shared bounds run at Full, including under a back gesture (motion lead 2026-11-07): the predictive-back gesture seeks
 * the container transform itself, so the chat shrinks toward its row under the finger and a cancel grows it back. The
 * gate still records the gesture so a tap or button back mid-gesture is ignored and the pop's direction is known.
 */
@Suppress("UNUSED_PARAMETER")
public fun sharedBoundsEnabled(s: GateState, level: ShellMotionLevel): Boolean = level == ShellMotionLevel.Full

/** Snapshot-backed holder; main thread only. Every event is logged to [trace] (tests and the debug overlay read it). */
@Stable
public class RowTransformGate(initial: GateState = GateState.Idle) {
    public var state: GateState by mutableStateOf(initial)
        private set

    /** Direction of the last navigation: true after a push, false after a pop (picks 250 ms vs 200 ms bounds). */
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
