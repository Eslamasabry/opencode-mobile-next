package io.github.eslamasabry.opencode_mobile

/**
 * Ends one read's checks from another thread. A read of the phone's agents runs its sign-in and version checks one
 * at a time, each a process in the phone's Linux, and Android stops an app's child processes past a limit, so a
 * person's step (an install, a removal) never runs beside them: it stops the read instead of waiting for it. [stop]
 * ends the process a check is running now (its whole tree) and no later check under this handle starts one.
 */
class ProbeStop {
    private val lock = Any()
    private var stopped = false
    private var end: (() -> Unit)? = null

    val isStopped: Boolean
        get() = synchronized(lock) { stopped }

    /** Called right after a check started its process; [ends] ends it. False when [stop] came first: end it now. */
    fun attach(ends: () -> Unit): Boolean =
        synchronized(lock) {
            if (stopped) false
            else {
                end = ends
                true
            }
        }

    /** The check's process ended on its own. */
    fun detach() {
        synchronized(lock) { end = null }
    }

    /** Blocks until the running check's process has been ended (a few seconds at most). */
    fun stop() {
        val running =
            synchronized(lock) {
                stopped = true
                end.also { end = null }
            }
        try {
            running?.invoke()
        } catch (_: Exception) {
            // The check's own thread drains it as well and reports what it could not end.
        }
    }
}
