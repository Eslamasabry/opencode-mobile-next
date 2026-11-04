package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.seconds

/** Only the currently visible Linux page collects this cadence. */
public object LinuxVitalsTimers {
    public val poll: TimerSpec = TimerSpec(
        name = "linux.vitalsPoll",
        cadence = Cadence(foreground = 5.seconds, hiddenWatching = null, hiddenIdle = null),
        reason = "Visible Linux heat and memory stop reflecting the phone.",
    )
}
