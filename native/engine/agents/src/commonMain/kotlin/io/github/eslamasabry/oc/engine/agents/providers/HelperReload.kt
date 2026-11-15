package io.github.eslamasabry.oc.engine.agents.providers

/**
 * Makes the phone's agent helper take a changed own agent (issue 147): it reads its agents only when it starts. A
 * helper that does not run takes it at its next start; an idle one is stopped with the guarded stop (refused while a
 * turn or a permission reply runs) and started again, as `AgentHelper.rotate` does; never while the agents work.
 */
public class HelperReload(
    private val running: suspend () -> Boolean,
    private val busy: suspend () -> Boolean,
    /** The guarded stop; false when it was refused. */
    private val stopGuarded: suspend () -> Boolean,
    private val start: suspend () -> Unit,
) {
    /** True when the change is taken now (or at the next start of a helper that does not run); false: try when idle. */
    public suspend fun tryNow(): Boolean {
        if (!running()) return true
        if (busy()) return false
        if (!stopGuarded()) return false
        start()
        return true
    }
}
