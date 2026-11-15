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
        // Issue 147: after the app opened, the app itself starts this phone's server (also one an app update or the
        // app's own restart stopped): that is not a failure. It reads starting for the same window a start gets, unless
        // the process was seen running and then exited. Galaxy S22 after an update: about 25 s.
        val exited = phone.processAlive == false && phone.seenAlive
        if (!exited && now - phone.since < startWindow) return true
        return phone.processAlive == true && (phone.uptime ?: (now - phone.since)) < startWindow
    }
}
