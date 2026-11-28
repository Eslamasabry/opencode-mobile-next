package io.github.eslamasabry.opencode_mobile

/**
 * B-486: what one tap on "Try fast mode again" (Settings › Help) may do. Pure: the phone is read elsewhere and the
 * restarts are the app's.
 */
object LinuxFastRetry {
    /** Which of the app's own long-running programs run in compatible mode now. */
    data class Running(val openCodeCompatible: Boolean, val helperCompatible: Boolean)

    /**
     * [probe]: fast mode is tested now; false refuses the tap and leaves the saved mode alone. [restartOpenCode] and
     * [restartHelper]: what has to start again before the phone runs in fast mode.
     */
    data class Plan(val probe: Boolean, val restartOpenCode: Boolean, val restartHelper: Boolean)

    /**
     * What runs in compatible mode, as the retry reads it today: OpenCode when the kernel shows it without proot's
     * filter ([liveProotMode] "ptrace"). The saved mode ([compatMarker]) and the agents' helper are not read.
     */
    fun running(
        compatMarker: Boolean,
        liveProotMode: String?,
        openCodeRunning: Boolean,
        helperRunning: Boolean,
    ): Running = Running(openCodeCompatible = openCodeRunning && liveProotMode == "ptrace", helperCompatible = false)

    /** The retry as it is decided today: always tested; OpenCode starts again when the phone's work is idle. */
    fun plan(running: Running, idle: Boolean): Plan =
        Plan(probe = true, restartOpenCode = running.openCodeCompatible && idle, restartHelper = false)

    /** Whether the row may read Fast after a test that passed: today, always. */
    fun fastReached(plan: Plan, openCodeRestarted: Boolean, helperRestarted: Boolean): Boolean = plan.probe
}
