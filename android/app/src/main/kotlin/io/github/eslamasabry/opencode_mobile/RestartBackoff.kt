package io.github.eslamasabry.opencode_mobile

internal class RestartBackoff {
    private var nextDelayMs = 1000L
    private var restartAtMs = 0L
    fun exited(uptimeMs: Long, nowMs: Long): Long {
        if (uptimeMs >= 30000L) nextDelayMs = 1000L
        val delay = nextDelayMs
        restartAtMs = nowMs + delay
        nextDelayMs = (delay * 2).coerceAtMost(60000L)
        return delay
    }
    fun remainingMs(nowMs: Long) = (restartAtMs - nowMs).coerceAtLeast(0L)
    fun snapshot() = nextDelayMs to restartAtMs
    fun restore(nextDelay: Long, deadline: Long) {
        require(nextDelay in 1000L..60000L && deadline >= 0L)
        nextDelayMs = nextDelay; restartAtMs = deadline
    }
    fun reset() { nextDelayMs = 1000L; restartAtMs = 0L }
}
