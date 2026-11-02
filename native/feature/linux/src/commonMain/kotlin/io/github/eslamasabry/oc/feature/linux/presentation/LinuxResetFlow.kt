package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import kotlin.time.Clock

/** How a reset ended, for the page that started it. */
public sealed interface LinuxResetResult {
    /** Erased, and setup runs (or finished) on a fresh Linux; [setupStarted] false keeps the marker for next start. */
    public data class Reinstalling(val setupStarted: Boolean) : LinuxResetResult

    /** The phone refused before erasing anything; Linux is as it was. [details] is for the Details fold only. */
    public data class NotErased(val details: Redacted?) : LinuxResetResult
}

/**
 * Reset Linux as one act that either finishes or leaves Linux as it was (port of linux_reset_flow.dart, B-LINUX-093):
 * a marker is saved before anything is erased, so a reset the app did not finish is finished on the next start
 * ([resumeIfPending]); a refusal before erasing clears the marker and changes nothing; a setup that cannot start keeps
 * the marker for the next start.
 */
public class LinuxResetFlow(
    private val lifecycle: LinuxLifecycle,
    private val marker: LinuxResetMarker,
    private val clock: Clock,
    /** Before erasing: leave the in-app server. */
    private val beforeErase: suspend () -> Unit = {},
) {
    private suspend fun mark(phase: String) = marker.write("$phase ${clock.now()}")

    /** Erases Linux and starts setup on a fresh one. */
    public suspend fun run(): LinuxResetResult {
        mark(ERASING)
        beforeErase()
        val erased = lifecycle.erase()
        if (erased is Outcome.Failed && lifecycle.installed() == true) {
            // Refused before erasing (the phone checks first): nothing changed.
            marker.clear()
            return LinuxResetResult.NotErased(erased.failure.details)
        }
        // Erased, or erasing stopped part way: Linux is gone either way and setup replaces what is left.
        return reinstall()
    }

    private suspend fun reinstall(): LinuxResetResult {
        mark(INSTALLING)
        marker.forgetReplySpeed()
        val started = (lifecycle.startSetup() as? Outcome.Ok)?.value == true
        // From here setup's own job record carries the reset. A start that failed keeps the marker.
        if (started) marker.clear()
        return LinuxResetResult.Reinstalling(started)
    }

    /** On app start: finishes a reset that was confirmed but not finished. True when it did something. */
    public suspend fun resumeIfPending(): Boolean {
        val phase = marker.read()?.substringBefore(' ') ?: return false
        if (phase == INSTALLING) {
            // A Linux installed again means setup got that far; its own record carries the rest.
            if (lifecycle.installed() == true) {
                marker.clear()
                return false
            }
            reinstall()
            return true
        }
        // Killed while erasing (or before): the person confirmed the reset, so it is finished, not undone.
        run()
        return true
    }

    public companion object {
        /** Frozen key, verbatim from the Flutter app. */
        public const val MARKER_KEY: String = "oc.linuxReset.pending"
        internal const val ERASING = "erasing"
        internal const val INSTALLING = "installing"
    }
}
