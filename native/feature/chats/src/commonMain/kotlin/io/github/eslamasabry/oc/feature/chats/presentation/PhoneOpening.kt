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
    /**
     * When the process was first read as not running after it (or a start) had been seen in this wait; null while it
     * runs or a start runs. The phone's status is read a moment after a start returns, and the app starts a stopped
     * server again by itself, so "gone" is a failure only once it has stayed gone for [OpeningRules.goneGrace].
     */
    val goneSince: Instant? = null,
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
     * How long a start the app is running, or a server process that is young, may take before home calls it failed.
     * Owner's phone, build 10062 (2026-11-16): with several servers saved and the agents' helper starting beside it,
     * this phone's server was still coming up when the 90 s window ended and home said "isn't answering" with nothing
     * wrong. The emulator measured 68-89 s for the same start.
     */
    public val startLimit: Duration = 180.seconds

    /** How long a process that was seen and is now read as gone may stay gone before that is a failure. */
    public val goneGrace: Duration = 10.seconds

    public fun phase(busy: Boolean, failure: Failure?, phone: PhoneStartWatch?, now: Instant): ChatsOpeningPhase {
        val plain = if (busy) ChatsOpeningPhase.Connecting else ChatsOpeningPhase.Failed
        if (phone == null) return plain
        // A refused password is not fixed by waiting; its own action is on the failure card.
        if (failure is Failure.SignIn) return plain
        return if (starting(phone, now)) ChatsOpeningPhase.Starting else plain
    }

    private fun starting(phone: PhoneStartWatch, now: Instant): Boolean {
        phone.startingSince?.let { return now - it < startLimit }
        if (phone.stoppedByPerson) return false
        // Issue 147: after the app opened, the app itself starts this phone's server (also one an app update or the
        // app's own restart stopped): that is not a failure. It reads starting for the same window a start gets, unless
        // the process was seen running and then stayed gone. Galaxy S22 after an update: about 25 s.
        val exited = phone.processAlive == false && phone.seenAlive
        if (exited) return phone.goneSince?.let { now - it < goneGrace } ?: false
        if (now - phone.since < startWindow) return true
        // A process that is running and young is still booting; one that has run for long and does not
        // answer has failed.
        return phone.processAlive == true && (phone.uptime ?: (now - phone.since)) < startLimit
    }

    /**
     * Home keeps asking a phone server that reads failed but whose process runs: when it answers later, home shows it
     * without the person tapping Try again.
     */
    public fun worthAskingAgain(phase: ChatsOpeningPhase, phone: PhoneStartWatch?): Boolean =
        phase == ChatsOpeningPhase.Starting ||
            (phase == ChatsOpeningPhase.Failed && phone != null && !phone.stoppedByPerson && phone.processAlive == true)
}
