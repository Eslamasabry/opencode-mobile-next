package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.seconds

/** The agents' update sheet's repeating job (plan 13 §11.2; Konsist K-P2). Never while the app is hidden. */
public object AgentUpdateTimers {
    /**
     * While the sheet is open, the engine's reading (free space, versions) is asked again every 30 s, and at once when
     * the app comes back: the room line and the button's state follow by themselves (10110: freed space stayed "not
     * enough room" until the next tap).
     */
    public val sheetReading: TimerSpec =
        TimerSpec(
            name = "home.agentUpdateSheetReading",
            cadence = Cadence(foreground = 30.seconds, hiddenWatching = null, hiddenIdle = null),
            reason = "The open update sheet keeps a stale free-space line and a wrongly disabled button.",
        )
}
