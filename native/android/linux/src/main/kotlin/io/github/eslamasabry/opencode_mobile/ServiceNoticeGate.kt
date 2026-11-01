package io.github.eslamasabry.opencode_mobile

/**
 * Keeps the phone server's foreground service from being started again, and
 * its shared status notification from being posted again, every time the set
 * of programs under it changes (perf audit 2026-11-01).
 *
 * A measurement on the owner's phone showed `BuiltinServerService` at
 * `lastStartId=804` in 1 h 50 min and 3015 notification updates, 755 of them
 * refused by Android's rate limit. Each start re-posts the row, and a busy
 * agent changes the set many times a minute. The row's words rarely change.
 *
 * Rules: while the service shows, a call with the same words as the last
 * start does nothing; different words go out at once if the last start was
 * more than [windowMs] ago, and otherwise wait (the caller asks again after
 * the delay, and then sends the latest words once). A service that is not
 * showing is always started. Pure: the caller owns the clock and the timer.
 */
internal class ServiceNoticeGate(private val windowMs: Long = WINDOW_MS) {
    sealed interface Decision {
        data object Send : Decision
        data object Skip : Decision
        data class Wait(val delayMs: Long) : Decision
    }

    private var sent = false
    private var lastNotice: String? = null
    private var lastAtMs = 0L

    @Synchronized
    fun decide(notice: String?, serviceShown: Boolean, nowMs: Long): Decision {
        if (!serviceShown || !sent) return Decision.Send
        if (notice == lastNotice) return Decision.Skip
        val since = nowMs - lastAtMs
        return if (since < 0 || since >= windowMs) Decision.Send else Decision.Wait(windowMs - since)
    }

    /** The caller started the service with [notice]. */
    @Synchronized
    fun sent(notice: String?, nowMs: Long) {
        sent = true
        lastNotice = notice
        lastAtMs = nowMs
    }

    /** The service stopped: the next call starts it. */
    @Synchronized
    fun reset() {
        sent = false
        lastNotice = null
    }

    companion object {
        const val WINDOW_MS = 5_000L
    }
}
