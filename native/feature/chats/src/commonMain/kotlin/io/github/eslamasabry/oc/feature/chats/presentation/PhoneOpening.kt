package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Failure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What home knows about the built-in server ("This phone", the Linux server the app itself launches) while it waits for
 * that server's first answer (E05-S20). Every field is a reading taken by the wiring; [OpeningRules] decides the words.
 */
public data class PhoneStartWatch(
    /** When home began waiting for this phone's first answer (this app open, or the switch to it). */
    val since: Instant,
    /** When the app's one start path was first seen running in this wait; null when no start is running. */
    val startingSince: Instant? = null,
    /** The server process is running; null until the phone's status was first read. */
    val processAlive: Boolean? = null,
    /** How long the server process has been running, when the phone reports it. */
    val uptime: Duration? = null,
    /** The process was seen running, or a start was seen running, since [since]: if it is gone now, it exited. */
    val seenAlive: Boolean = false,
    /** The person stopped it: nothing will start it, so waiting is pointless. */
    val stoppedByPerson: Boolean = false,
)

/**
 * The home opening decision (E05-S20). A server the app itself just started is not broken; it is starting. Remote
 * servers keep the plain rule: connecting while a connection runs, failed otherwise.
 */
public object OpeningRules {
    /**
     * How long a started phone server may take to answer: Flutter's `BuiltinServer.readyTimeout` (90 s,
     * lib/builtin/builtin_server.dart), the same value as `LaunchStartRules.staleAfter`.
     */
    public val startWindow: Duration = 90.seconds

    /**
     * Between the app opening and its one start beginning, the phone server is down and nothing is starting yet. For
     * this long the wait still reads "starting"; after it, a server nobody is starting has failed.
     */
    public val launchGrace: Duration = 15.seconds

    public fun phase(busy: Boolean, failure: Failure?, phone: PhoneStartWatch?, now: Instant): ChatsOpeningPhase {
        val plain = if (busy) ChatsOpeningPhase.Connecting else ChatsOpeningPhase.Failed
        if (phone == null) return plain
        // A refused password is not fixed by waiting; its own action is on the failure card.
        if (failure is Failure.SignIn) return plain
        return if (starting(phone, now)) ChatsOpeningPhase.Starting else plain
    }

    private fun starting(phone: PhoneStartWatch, now: Instant): Boolean {
        phone.startingSince?.let { return now - it < startWindow }
        if (phone.stoppedByPerson) return false
        return when (phone.processAlive) {
            // Not read yet: the app has only just opened.
            null -> now - phone.since < launchGrace
            true -> (phone.uptime ?: (now - phone.since)) < startWindow
            // Seen alive, now gone: the process exited. Never seen: the app-open start may not have begun yet.
            false -> !phone.seenAlive && now - phone.since < launchGrace
        }
    }
}
