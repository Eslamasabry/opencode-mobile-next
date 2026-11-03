package io.github.eslamasabry.oc.feature.devservices.presentation

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.seconds

public object DevServicesTimers {
    /**
     * Development services refresh every 5 s while shown (Flutter `Timer.periodic(5 s)`); the phone scan, which costs
     * a script, runs every other tick. Nothing runs while the page is hidden.
     */
    public val refresh: TimerSpec =
        TimerSpec(
            name = "devservices.refresh",
            cadence = Cadence(foreground = 5.seconds, hiddenWatching = null, hiddenIdle = null),
            reason = "Service status and the Also running list go stale while the page is open.",
            rows = listOf("B-LINUX-054", "B-LINUX-060"),
        )
}
