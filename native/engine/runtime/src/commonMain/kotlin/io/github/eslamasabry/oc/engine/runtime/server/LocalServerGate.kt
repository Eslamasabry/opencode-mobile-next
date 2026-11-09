package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How waiting for this phone's own server ended. */
public enum class LocalServerWait {
    /** The server answered: the first connection may be made. */
    Ready,

    /** It never answered, even after the one restart (or the person had stopped it). Show Start again with Details. */
    NotAnswering,
}

/**
 * Phase 0 (first reply): the first connection to This phone is not attempted until the phone's own server answers a
 * health check, so a connection made seconds after setup or an app start is not "refused" (5 of 35 installs). It
 * polls with backoff for [firstWait]; if the server never answers it restarts it ONCE (via [restart]) and waits
 * [afterRestartWait] more. Nothing else restarts it: a server the person stopped is left alone ([mayWait] false).
 *
 * [onWaiting] is true while the plain "Starting this phone's server…" state should show, false when the wait ends.
 * Nothing here carries output, a password or a URL; the Details text stays with the failure the caller builds.
 */
public class LocalServerGate(
    private val probe: suspend (ProfileId) -> ProbeAnswer,
    private val restart: suspend (ProfileId) -> Boolean,
    private val clock: Clock,
    private val mayWait: suspend (ProfileId) -> Boolean = { true },
    private val onWaiting: (ProfileId, Boolean) -> Unit = { _, _ -> },
    private val firstWait: Duration = 30.seconds,
    private val afterRestartWait: Duration = 30.seconds,
    private val firstPoll: Duration = 500.milliseconds,
    private val maxPoll: Duration = 4.seconds,
) {
    public suspend fun awaitReady(profile: ProfileId): LocalServerWait {
        if (up(profile)) return LocalServerWait.Ready
        if (!mayWait(profile)) return LocalServerWait.NotAnswering
        onWaiting(profile, true)
        try {
            if (pollUntil(profile, firstWait)) return LocalServerWait.Ready
            if (restart(profile) && pollUntil(profile, afterRestartWait)) return LocalServerWait.Ready
            return LocalServerWait.NotAnswering
        } finally {
            onWaiting(profile, false)
        }
    }

    private suspend fun up(profile: ProfileId): Boolean = probe(profile) != ProbeAnswer.NoAnswer

    private suspend fun pollUntil(profile: ProfileId, limit: Duration): Boolean {
        val deadline = clock.now() + limit
        var pause = firstPoll
        while (clock.now() < deadline) {
            delay(pause)
            if (up(profile)) return true
            pause = (pause * 2).coerceAtMost(maxPoll)
        }
        return false
    }
}
