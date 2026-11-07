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
    fun `E05-S20 it fails only after the 90 s start window or when the process exits`() {
        val booting = PhoneStartWatch(since = opened, startingSince = opened, processAlive = true, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Starting, phase(booting, after = 89))
        assertEquals(ChatsOpeningPhase.Failed, phase(booting, after = 90))
        val running = PhoneStartWatch(since = opened, processAlive = true, uptime = 95.seconds, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Failed, phase(running, after = 5))
        assertEquals(ChatsOpeningPhase.Connecting, phase(running, after = 5, busy = true))
        val exited = PhoneStartWatch(since = opened, processAlive = false, seenAlive = true)
        assertEquals(ChatsOpeningPhase.Failed, phase(exited, after = 3))
    }

    @Test
    fun `E05-S20 before the app-open start begins the wait still reads starting, briefly`() {
        val unread = PhoneStartWatch(since = opened)
        assertEquals(ChatsOpeningPhase.Starting, phase(unread, after = 2))
        assertEquals(ChatsOpeningPhase.Failed, phase(unread, after = 20))
        val down = PhoneStartWatch(since = opened, processAlive = false)
        assertEquals(ChatsOpeningPhase.Starting, phase(down, after = 2))
        assertEquals(ChatsOpeningPhase.Failed, phase(down, after = 16))
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
