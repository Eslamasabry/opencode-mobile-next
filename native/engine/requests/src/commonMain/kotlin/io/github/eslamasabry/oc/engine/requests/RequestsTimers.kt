package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The requests engine's repeating jobs (plan 13 §11.2; Konsist K-P2 wants every TimerSpec in a `*Timers.kt`). */
public object RequestsTimers {
    /**
     * Asks are never replayed and a question has no event at all, so the waiting list is re-read while the person can
     * act on it (plan 09 §5.1 "Lost asks", §3.10 15 s). Not while hidden and idle: the next Connected refetches.
     */
    public val REREAD: TimerSpec =
        TimerSpec(
            name = "requests.reread",
            cadence = Cadence(foreground = 15.seconds, hiddenWatching = 1.minutes, hiddenIdle = null, jitter = 0.1),
            reason = "a missed ask or a question with no event would wait unseen until the next reconnect",
            rows = listOf("B-PROTO-034", "B-CHAT-075"),
        )

    public val ALL: List<TimerSpec> = listOf(REREAD)
}
