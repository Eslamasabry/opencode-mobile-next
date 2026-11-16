package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionUi
import io.github.eslamasabry.oc.feature.chats.presentation.OpeningRules
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneStartWatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** E05-S20: "This phone" says it is starting, never "isn't answering", while its own server boots. */
class PhoneOpeningTest {
    private val opened = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val noAnswer = Failure.Network()

    private fun phase(phone: PhoneStartWatch?, after: Int, busy: Boolean = false, failure: Failure? = noAnswer) =
        OpeningRules.phase(busy, failure, phone, opened + after.seconds)

    @Test
    fun `E05-S20 a phone server the app started 40 s ago and that has not answered is starting, not failed`() {
        // The owner's phone on 2026-11-07: the first connection was refused while proot and OpenCode booted.
        val booting = PhoneStartWatch(since = opened, startingSince = opened + 1.seconds, processAlive = true, uptime = 39.seconds, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Starting, phase(booting, after = 40))
        assertEquals(ChatsOpeningPhase.Starting, phase(booting, after = 40, busy = true))
        // The start returned and the process runs; home keeps retrying quietly until it answers.
        val started = booting.copy(startingSince = null, uptime = 60.seconds)
        assertEquals(ChatsOpeningPhase.Starting, phase(started, after = 61))
    }

    @Test
    fun `E05-S20 it fails only after the start limit or when the process stays gone`() {
        val booting = PhoneStartWatch(since = opened, startingSince = opened, processAlive = true, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Starting, phase(booting, after = 179))
        assertEquals(ChatsOpeningPhase.Failed, phase(booting, after = 180))
        // Issue 147: a server already running from before the app opened gets the start window to answer too.
        val running = PhoneStartWatch(since = opened, processAlive = true, uptime = 200.seconds, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Starting, phase(running, after = 16))
        assertEquals(ChatsOpeningPhase.Failed, phase(running, after = 90))
        assertEquals(ChatsOpeningPhase.Connecting, phase(running, after = 90, busy = true))
        val exited = PhoneStartWatch(since = opened, processAlive = false, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Failed, phase(exited, after = 3))
    }

    @Test
    fun `OWNER-1116 a start that takes two minutes on a loaded phone is starting, never isn't answering`() {
        // Owner's phone, build 10062: several servers saved, about 90 agent conversations, the agents' helper starting
        // beside the server. The start was still running when the old 90 s window ended.
        val slow = PhoneStartWatch(since = opened, startingSince = opened + 2.seconds, processAlive = false, seenAlive = true)
        for (second in 3..150 step 7) assertEquals(ChatsOpeningPhase.Starting, phase(slow, after = second), "at $second s")
        // The start returned at 120 s; the young process is still booting OpenCode and has not answered.
        val young = slow.copy(startingSince = null, processAlive = true, uptime = 30.seconds)
        assertEquals(ChatsOpeningPhase.Starting, phase(young, after = 150))
        // A process that has run past the limit and still does not answer has failed.
        assertEquals(ChatsOpeningPhase.Failed, phase(young.copy(uptime = 181.seconds), after = 301))
    }

    @Test
    fun `OWNER-1116 a process read as gone for a moment after its start returned is not a failure`() {
        // The phone's status is read a moment after the start path returns, and the app starts a stopped server again.
        val gone = PhoneStartWatch(since = opened, processAlive = false, seenAlive = true, goneSince = opened + 20.seconds)
        assertEquals(ChatsOpeningPhase.Starting, phase(gone, after = 21))
        assertEquals(ChatsOpeningPhase.Starting, phase(gone, after = 29))
        // It stayed gone: that is a failure, said after the grace.
        assertEquals(ChatsOpeningPhase.Failed, phase(gone, after = 30))
        // The person stopped it: said at once, no grace.
        assertEquals(ChatsOpeningPhase.Failed, phase(gone.copy(stoppedByPerson = true), after = 21))
    }

    @Test
    fun `OWNER-1116 home keeps asking a server that reads failed while its process runs`() {
        val running = PhoneStartWatch(since = opened, processAlive = true, uptime = 400.seconds, seenAlive = true)
        assertEquals(true, OpeningRules.worthAskingAgain(ChatsOpeningPhase.Starting, running))
        assertEquals(true, OpeningRules.worthAskingAgain(ChatsOpeningPhase.Failed, running))
        // Nothing runs, or the person stopped it: asking again cannot help; Try again is the way.
        assertEquals(false, OpeningRules.worthAskingAgain(ChatsOpeningPhase.Failed, running.copy(processAlive = false)))
        assertEquals(false, OpeningRules.worthAskingAgain(ChatsOpeningPhase.Failed, running.copy(stoppedByPerson = true)))
        assertEquals(false, OpeningRules.worthAskingAgain(ChatsOpeningPhase.Failed, null))
        assertEquals(false, OpeningRules.worthAskingAgain(ChatsOpeningPhase.Connecting, running))
    }

    @Test
    fun `I147-AGY-13 after an app update the stopped server the app starts again never reads failed`() {
        // Galaxy S22, build 10047 after an update from 10046: the install stopped the server; the app starts it
        // again on opening and it answers at 25 s. Read every 3 s from the app opening until the answer.
        var watch = PhoneStartWatch(since = opened, processAlive = false)
        for (second in 0..25 step 3) {
            if (second >= 9) watch = watch.copy(startingSince = watch.startingSince ?: (opened + 9.seconds), seenAlive = true)
            if (second >= 15) watch = watch.copy(processAlive = true, uptime = (second - 15).seconds)
            for (failure in listOf(noAnswer, Failure.ComputerDidNotAnswer(), null))
                assertEquals(ChatsOpeningPhase.Starting, phase(watch, after = second, failure = failure), "at $second s")
        }
        // The app's start running late (the phone's Linux was still coming up) is not a failure either.
        val late = PhoneStartWatch(since = opened, processAlive = false)
        assertEquals(ChatsOpeningPhase.Starting, phase(late, after = 25))
    }

    @Test
    fun `I147-AGY-10 an ordinary cold start never reads couldn't load while the phone's server is not answering yet`() {
        // Galaxy S22: the server ran from before the app opened; the first try failed before it answered.
        val running = PhoneStartWatch(since = opened, processAlive = true, uptime = 2.seconds * 3600, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Starting, phase(running, after = 3))
        assertEquals(ChatsOpeningPhase.Starting, phase(running, after = 3, failure = Failure.ComputerDidNotAnswer()))
        // After the start window a server that still does not answer has failed.
        assertEquals(ChatsOpeningPhase.Failed, phase(running, after = 90))
    }

    @Test
    fun `E05-S20 before the app-open start begins the wait still reads starting, briefly`() {
        val unread = PhoneStartWatch(since = opened)
        assertEquals(ChatsOpeningPhase.Starting, phase(unread, after = 2))
        assertEquals(ChatsOpeningPhase.Failed, phase(unread, after = 90))
        val down = PhoneStartWatch(since = opened, processAlive = false)
        assertEquals(ChatsOpeningPhase.Starting, phase(down, after = 2))
        assertEquals(ChatsOpeningPhase.Failed, phase(down, after = 91))
        // The person stopped it: nothing will start it, so home says so at once.
        assertEquals(ChatsOpeningPhase.Failed, phase(down.copy(stoppedByPerson = true), after = 2))
    }

    @Test
    fun `E05-S20 remote servers and refused passwords keep today's rule`() {
        assertEquals(ChatsOpeningPhase.Failed, phase(null, after = 1))
        assertEquals(ChatsOpeningPhase.Connecting, phase(null, after = 1, busy = true))
        val booting = PhoneStartWatch(since = opened, startingSince = opened, processAlive = true, seenAlive = true)
        assertEquals(
            ChatsOpeningPhase.Failed,
            phase(booting, after = 10, failure = Failure.SignIn(SignInWhy.PasswordRejected)),
        )
    }

    @Test
    fun `E05-S20 the server pill reads connecting while this phone starts`() {
        assertEquals(
            HomeConnectionState.Connecting,
            HomeConnectionUi.of(ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Starting, "This phone")))?.state,
        )
    }
}
