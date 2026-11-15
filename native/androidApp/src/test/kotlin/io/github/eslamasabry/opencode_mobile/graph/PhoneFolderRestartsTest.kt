package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * W3 (device run on the S22, build 10050): a folder made in the phone's storage was missing on both running sides,
 * and "Restart the phone's agents" restarted only the agents, so OpenCode later asked for a second restart.
 */
class PhoneFolderRestartsTest {
    private val folder = "/storage/emulated/0/Documents/w3b"

    /** The phone: which sides run without the folder, whether OpenCode writes a reply or an agent works. */
    private inner class Phone {
        val without = mutableSetOf(PhoneFolderSide.Server, PhoneFolderSide.Agents)
        var replying = false
        var agentWorking = false
        val restarted = mutableListOf<PhoneFolderSide>()
        var interrupted = 0

        fun restarts(scope: TestScope) =
            PhoneFolderRestarts(
                scope = scope.backgroundScope,
                misses = { side, path -> path == folder && side in without },
                serverBusy = { replying },
                restartServer = {
                    if (replying) interrupted++
                    restarted += PhoneFolderSide.Server
                    without -= PhoneFolderSide.Server
                    Outcome.Ok(Unit)
                },
                restartAgents = {
                    if (agentWorking) Outcome.Failed(Failure.Busy(BusyKind.SessionBusy))
                    else {
                        restarted += PhoneFolderSide.Agents
                        without -= PhoneFolderSide.Agents
                        Outcome.Ok(Unit)
                    }
                },
                every = 5.seconds,
            )
    }

    @Test
    fun `W3-STORAGE both sides missing the folder, one action, both see it`() = runTest {
        val phone = Phone()
        val restarts = phone.restarts(this)
        // The person chose Antigravity and taps "Restart the phone's agents and OpenCode" once.
        assertIs<Outcome.Ok<Unit>>(restarts.restart(PhoneFolderSide.Agents, folder))
        assertEquals(listOf(PhoneFolderSide.Agents, PhoneFolderSide.Server), phone.restarted)
        assertTrue(phone.without.isEmpty(), "OpenCode chosen afterwards sees the folder too: no second restart")
        assertEquals(emptySet(), restarts.waiting.value)
        // Chosen from OpenCode's side, the same: one action for both.
        val other = Phone()
        other.restarts(this).restart(PhoneFolderSide.Server, folder)
        assertEquals(listOf(PhoneFolderSide.Server, PhoneFolderSide.Agents), other.restarted)
    }

    @Test
    fun `W3-STORAGE a reply running on the phone's OpenCode is not interrupted, OpenCode restarts when it ends`() = runTest {
        val phone = Phone()
        phone.replying = true
        val restarts = phone.restarts(this)
        assertIs<Outcome.Ok<Unit>>(restarts.restart(PhoneFolderSide.Agents, folder))
        assertEquals(listOf(PhoneFolderSide.Agents), phone.restarted, "the agents restart now")
        assertEquals(setOf(PhoneFolderSide.Server), restarts.waiting.value, "OpenCode waits for its reply")
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(listOf(PhoneFolderSide.Agents), phone.restarted, "never while the reply is being written")
        phone.replying = false
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(listOf(PhoneFolderSide.Agents, PhoneFolderSide.Server), phone.restarted)
        assertEquals(emptySet(), restarts.waiting.value)
        assertEquals(0, phone.interrupted)
        // OpenCode chosen with a reply running: the tap restarts nothing there now either.
        val chosen = Phone()
        chosen.without -= PhoneFolderSide.Agents
        chosen.replying = true
        val second = chosen.restarts(this)
        assertIs<Outcome.Ok<Unit>>(second.restart(PhoneFolderSide.Server, folder))
        assertEquals(emptyList(), chosen.restarted)
        assertEquals(setOf(PhoneFolderSide.Server), second.waiting.value)
        assertEquals(0, chosen.interrupted)
    }

    @Test
    fun `W3-STORAGE an agent working keeps the chosen agents' restart refused, and the other side's waits`() = runTest {
        val phone = Phone()
        phone.agentWorking = true
        val restarts = phone.restarts(this)
        val refused = restarts.restart(PhoneFolderSide.Agents, folder)
        assertIs<Failure.Busy>((refused as Outcome.Failed).failure, "as before: try again when the agent is done")
        assertEquals(emptyList(), phone.restarted)
        // OpenCode chosen: OpenCode restarts now, the agents when the working agent is done.
        restarts.restart(PhoneFolderSide.Server, folder)
        assertEquals(listOf(PhoneFolderSide.Server), phone.restarted)
        assertEquals(setOf(PhoneFolderSide.Agents), restarts.waiting.value)
        phone.agentWorking = false
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(listOf(PhoneFolderSide.Server, PhoneFolderSide.Agents), phone.restarted)
        assertEquals(emptySet(), restarts.waiting.value)
    }
}
