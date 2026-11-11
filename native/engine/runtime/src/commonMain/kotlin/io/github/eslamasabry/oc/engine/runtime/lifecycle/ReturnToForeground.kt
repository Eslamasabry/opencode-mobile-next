package io.github.eslamasabry.oc.engine.runtime.lifecycle

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Flutter `ConnectionController._resumeFromLifecycle`: when the person comes back after the app was out of sight, a stream
 * that still looks connected may be dead (sleep, Wi-Fi to mobile), so it is woken once; the dial re-announces the
 * connection and every reader refetches (the stream is volatile; refetch, never replay). A blip shorter than
 * [minAway] changes nothing, and one wake per return keeps this from ever becoming a reconnect storm.
 */
public class ReturnToForeground(private val minAway: Duration = 20.seconds) {
    private var leftAt: Instant? = null

    /** The app left the screen. The first leave of a stretch counts; repeated pauses do not move it. */
    public fun left(now: Instant) {
        if (leftAt == null) leftAt = now
    }

    /** The app is back. True when the connected stream should be woken once. */
    public fun returned(now: Instant): Boolean {
        val left = leftAt ?: return false
        leftAt = null
        return now - left >= minAway
    }
}
