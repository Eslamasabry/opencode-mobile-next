package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Android's status listener acts immediately; forecasts and advisory files have bounded refreshes. */
public object PhoneBudgetTimers {
    public val thermal: TimerSpec = TimerSpec(
        name = "runtime.agentHeat",
        cadence = Cadence(foreground = 30.seconds, hiddenWatching = 30.seconds, hiddenIdle = 60.seconds),
        reason = "Agent priority would miss changing battery and thermal forecasts.",
    )
    public val files: TimerSpec = TimerSpec(
        name = "runtime.phoneBudgetFiles",
        cadence = Cadence(foreground = 5.minutes, hiddenWatching = 5.minutes, hiddenIdle = 5.minutes),
        reason = "Newly created agent homes would miss the person's advisory thread caps.",
    )
}
