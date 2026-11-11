package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.CountDownLatch

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

    /** Open while [stop] runs a check's end: [detach] waits for it, so the check's own cleanup never races it. */
    private var ending: CountDownLatch? = null

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

    /**
     * The check is done with its process (it ended on its own, or it is about to clean it up). When [stop] is ending
     * it right now, this waits until that is over: the check's cleanup (which frees the process's slot) and the
     * stopper never act on the same process at once, and the stopper never acts on a slot already handed to another
     * process. Call it outside any lock the end takes.
     */
    fun detach() {
        val inFlight =
            synchronized(lock) {
                end = null
                ending
            } ?: return
        var interrupted = false
        while (true) {
            try {
                inFlight.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    /** Blocks until the running check's process has been ended (a few seconds at most). */
    fun stop() {
        val done = CountDownLatch(1)
        val running =
            synchronized(lock) {
                stopped = true
                end.also {
                    end = null
                    if (it != null) ending = done
                }
            } ?: return
        try {
            running()
        } catch (_: Exception) {
            // The check's own thread drains it as well and reports what it could not end.
        } finally {
            done.countDown()
        }
    }
}
