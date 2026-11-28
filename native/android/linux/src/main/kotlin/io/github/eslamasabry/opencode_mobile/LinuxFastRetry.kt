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
     * What runs in compatible mode, read before the saved mode is touched. OpenCode: the kernel shows it without
     * proot's filter ([liveProotMode] "ptrace"), or the kernel does not say and the saved mode ([compatMarker])
     * decides. The agents' helper: always by the saved mode, the only record of how it was started (it keeps that
     * mode until its next start).
     */
    fun running(
        compatMarker: Boolean,
        liveProotMode: String?,
        openCodeRunning: Boolean,
        helperRunning: Boolean,
    ): Running =
        Running(
            openCodeCompatible = openCodeRunning && (compatMarker || liveProotMode == "ptrace"),
            helperCompatible = helperRunning && compatMarker,
        )

    /**
     * Something that runs in compatible mode has to start again, which is safe only while the phone's work is idle
     * ([idle]): a phone that is not idle refuses the tap and nothing is touched. With nothing to restart, fast mode
     * is tested whatever is running.
     */
    fun plan(running: Running, idle: Boolean): Plan {
        val openCode = running.openCodeCompatible
        val helper = running.helperCompatible
        if (!idle && (openCode || helper)) return Plan(probe = false, restartOpenCode = false, restartHelper = false)
        return Plan(probe = true, restartOpenCode = openCode, restartHelper = helper)
    }

    /**
     * Whether the row may read Fast after a test that passed: only when everything that ran in compatible mode
     * started again ([openCodeRestarted], [helperRestarted]); what did not run needs no restart.
     */
    fun fastReached(plan: Plan, openCodeRestarted: Boolean, helperRestarted: Boolean): Boolean =
        plan.probe &&
            (!plan.restartOpenCode || openCodeRestarted) &&
            (!plan.restartHelper || helperRestarted)
}
