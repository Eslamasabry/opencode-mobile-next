package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import kotlin.time.Duration.Companion.seconds

/**
 * Every repeating job of :feature:phone (plan 13 §11.2; Konsist K-P2). None runs while the app is
 * hidden.
 */
public object PhoneTimers {
    /** Running on this phone reads again every 10 s while it is the shown page (plan 07 F08). */
    public val runningRefresh: TimerSpec =
        TimerSpec(
            name = "phone.runningRefresh",
            cadence = Cadence(foreground = 10.seconds, hiddenWatching = null, hiddenIdle = null),
            reason = "Running on this phone shows a stale list and a stale near-limit warning.",
            rows = listOf("B-LINUX-145"),
        )

    /** This phone's "It restarts by itself in about 40 s" counts down while the page is shown. */
    public val answerCountdown: TimerSpec =
        TimerSpec(
            name = "phone.answerCountdown",
            cadence = Cadence(foreground = 1.seconds, hiddenWatching = null, hiddenIdle = null),
            reason = "The restart countdown on This phone stops moving.",
            rows = listOf("B-LINUX-250"),
        )

    /** The server log is live only inside This phone's visible Details fold. */
    public val detailsLog: TimerSpec =
        TimerSpec(
            name = "phone.detailsLog",
            cadence = Cadence(foreground = 2.seconds, hiddenWatching = null, hiddenIdle = null),
            reason = "The visible This phone server log stops updating while its server runs.",
            rows = emptyList(),
        )

    /**
     * This phone's bounded foreground import read; stops as soon as it is finished, absent or
     * failed.
     */
    public val historyImport: TimerSpec =
        TimerSpec(
            name = "phone.historyImport",
            cadence = Cadence(foreground = 2.seconds, hiddenWatching = null, hiddenIdle = null),
            reason =
                "A pending import of earlier conversations on This phone never shows its final result.",
            rows = listOf("B-PROTO-063"),
        )

    public val ALL: List<TimerSpec> =
        listOf(runningRefresh, answerCountdown, detailsLog, historyImport)
}
