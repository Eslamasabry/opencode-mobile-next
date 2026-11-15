package io.github.eslamasabry.opencode_mobile

/**
 * signin-truth: the bounded wait of a polite stop. A program sent SIGTERM (a Claude Code CLI may be writing a refreshed
 * sign-in) gets until [untilNanos] to end by itself; only then may the caller force it.
 */
internal object PoliteStop {
    /** Waits while [alive] and before [untilNanos]; true when everything ended by itself. */
    fun settle(
        alive: () -> Boolean,
        untilNanos: Long,
        nano: () -> Long = System::nanoTime,
        pause: (Long) -> Unit = { Thread.sleep(it) },
    ): Boolean {
        while (alive()) {
            if (nano() >= untilNanos) return false
            pause(POLL_MILLIS)
        }
        return true
    }

    private const val POLL_MILLIS = 20L
}
