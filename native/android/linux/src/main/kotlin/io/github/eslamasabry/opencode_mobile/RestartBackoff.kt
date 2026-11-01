package io.github.eslamasabry.opencode_mobile

internal class RestartBackoff {
    private var nextDelayMs = INITIAL_DELAY_MS
    private var restartAtMs = 0L
    fun exited(uptimeMs: Long, nowMs: Long): Long {
        if (uptimeMs >= STABLE_UPTIME_MS) nextDelayMs = INITIAL_DELAY_MS
        val delay = nextDelayMs
        restartAtMs = nowMs + delay
        nextDelayMs = (delay * 2).coerceAtMost(MAXIMUM_DELAY_MS)
        return delay
    }
    fun remainingMs(nowMs: Long) = (restartAtMs - nowMs).coerceAtLeast(0L)
    fun snapshot() = nextDelayMs to restartAtMs
    fun restore(nextDelay: Long, deadline: Long) {
        require(nextDelay in INITIAL_DELAY_MS..MAXIMUM_DELAY_MS && deadline >= 0L)
        nextDelayMs = nextDelay; restartAtMs = deadline
    }
    fun reset() { nextDelayMs = INITIAL_DELAY_MS; restartAtMs = 0L }

    private companion object {
        const val INITIAL_DELAY_MS = 1000L
        const val STABLE_UPTIME_MS = 30000L
        const val MAXIMUM_DELAY_MS = 60000L
    }
}
