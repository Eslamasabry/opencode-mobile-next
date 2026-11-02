package io.github.eslamasabry.oc.ui.kitchat.composer

/**
 * How a finger on the composer's mic is read (port of Dart `KitMicPress`, plan 06 §7.8): a quick touch is a tap, a
 * touch kept down [HOLD_AFTER_MS] is "hold to talk", and a held touch dragged [CANCEL_DISTANCE_DP] or more from where
 * it landed is "let go to cancel". Before the hold starts, a drift past [SLOP_DP] gives the touch up (the finger is
 * scrolling something else). Pure: the gesture detector feeds it, tests drive it directly.
 */
public class KitMicPress(
    private val onHoldStart: () -> Unit,
    private val onHoldEnd: (cancelled: Boolean) -> Unit,
    private val onArmedChanged: (armed: Boolean) -> Unit,
) {
    public enum class Phase { Idle, Down, Holding }

    public enum class Outcome { Tap, HoldCommitted, HoldCancelled, None }

    public var phase: Phase = Phase.Idle
        private set

    public var armed: Boolean = false
        private set

    public fun down() {
        phase = Phase.Down
        armed = false
    }

    /** The hold time passed with the finger still down. */
    public fun holdTimeReached() {
        if (phase != Phase.Down) return
        phase = Phase.Holding
        onHoldStart()
    }

    /** [distanceDp] from the landing point. */
    public fun move(distanceDp: Float) {
        when (phase) {
            Phase.Idle -> Unit
            Phase.Down -> if (distanceDp > SLOP_DP) phase = Phase.Idle
            Phase.Holding -> {
                val now = distanceDp >= CANCEL_DISTANCE_DP
                if (now != armed) {
                    armed = now
                    onArmedChanged(now)
                }
            }
        }
    }

    public fun up(): Outcome {
        val was = phase
        phase = Phase.Idle
        return when (was) {
            Phase.Idle -> Outcome.None
            Phase.Down -> Outcome.Tap
            Phase.Holding -> {
                val cancelled = armed
                armed = false
                onHoldEnd(cancelled)
                if (cancelled) Outcome.HoldCancelled else Outcome.HoldCommitted
            }
        }
    }

    /** The system took the touch away (a scroll claimed it, a call came in): a running hold is cancelled. */
    public fun cancel(): Outcome {
        val was = phase
        phase = Phase.Idle
        if (was != Phase.Holding) return Outcome.None
        armed = false
        onHoldEnd(true)
        return Outcome.HoldCancelled
    }

    public companion object {
        public const val HOLD_AFTER_MS: Long = 350
        public const val SLOP_DP: Float = 18f
        public const val CANCEL_DISTANCE_DP: Float = 80f
    }
}
