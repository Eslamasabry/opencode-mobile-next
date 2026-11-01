package io.github.eslamasabry.oc.android.agenthost

/**
 * The idle snapshot [NativeAgentHostAdmission] reads. Twin of `NativeIdleState.Snapshot` (the Flutter app's
 * `NativeIdleState.kt`, copied in `:android:linux`): the same fields, defaults and [Snapshot.holds], nothing else. The
 * durable idle state machine stays with the runtime; the runtime hands its snapshot in through [AgentRuntimeFacts].
 * When CR11-0 lets this module see `:android:linux`, this file is deleted and the admission imports the runtime's class.
 */
internal object NativeIdleState {
    data class Snapshot(
        val enabled: Boolean = false,
        val idleMinutes: Int = 5,
        val counter: Long = 0,
        val generation: Long = 0,
        val stopped: Boolean = false,
        val helperStopped: Boolean = false,
        val owner: String? = null,
        val helper: String? = null,
    ) {
        /** Whether this snapshot is the live idle token of [owner] at [generation]. */
        fun holds(owner: String, generation: Long) =
            generation > 0 && this.generation == generation && this.owner == owner
    }
}
