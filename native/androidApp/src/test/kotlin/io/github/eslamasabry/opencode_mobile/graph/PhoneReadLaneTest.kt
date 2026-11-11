@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.opencode_mobile.ProbeStop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "I had to press 4 times on Codex install" (owner, 2026-11-11), second half: an Install pressed while the Agents page
 * read the phone waited for the whole read, one sign-in check per agent at up to 65 s each. A phone with the real lane
 * ([PhoneReadLane], [ProbeStop]) and checks that take [Phone.checkMillis] unless ended: a step stops the read, starts
 * as soon as the running check's process has ended, and the read is redone after it. Never two processes at once:
 * Android stops an app's child processes past a limit, which is why reads and steps share one lane at all.
 */
class PhoneReadLaneTest {
    private class Phone(private val test: TestScope, private val agents: List<String>, val checkMillis: Long = 60_000) {
        val lane = PhoneReadLane { it() }
        var alive = 0
        var mostAlive = 0
        val ended = mutableListOf<String>()
        val answeredAt = mutableMapOf<String, Long>()
        val steps = mutableListOf<Pair<String, Long>>()

        /** One child process; true when it answered by itself, false when it was ended (or never started). */
        private suspend fun process(name: String, millis: Long, stop: ProbeStop?): Boolean {
            if (stop?.isStopped == true) return false
            val exit = CompletableDeferred<Boolean>()
            alive++
            mostAlive = maxOf(mostAlive, alive)
            try {
                if (stop != null && !stop.attach { ended += name; exit.complete(false) }) return false
                return withTimeoutOrNull(millis) { exit.await() } ?: true
            } finally {
                stop?.detach()
                alive--
            }
        }

        /** The read's body, as AppPhoneAgents runs it: one check at a time, none after a stop, a cut one dropped. */
        private suspend fun readBody(stop: ProbeStop): Boolean {
            for (agent in agents) {
                if (stop.isStopped) return false
                val answered = process("check $agent", checkMillis, stop)
                if (stop.isStopped || !answered) return false
                answeredAt[agent] = test.currentTime
            }
            return true
        }

        suspend fun read(): Boolean? = lane.read { readBody(it) }

        /** A step (install, removal): its own short process, then its re-read. */
        suspend fun step(name: String): Boolean? =
            lane.step {
                steps += name to test.currentTime
                process("step $name", 1_000, null)
                lane.readHeld { readBody(it) }
            }
    }

    @Test
    fun `an install asked during a read of three slow agents starts at once and the read is redone after it`() =
        runTest {
            val phone = Phone(this, listOf("claude", "codex", "kilo"))
            val read = async { phone.read() }
            advanceTimeBy(1_000)
            val install = async { phone.step("install codex") }
            runCurrent()

            // The bound: the time it takes to end the one check running (instant here; on a phone its process tree is
            // drained within about 2.4 s), never the read's remaining checks (3 x 60 s).
            assertEquals(listOf("install codex" to 1_000L), phone.steps)
            assertEquals(listOf("check claude"), phone.ended, "the running check's process was ended")
            assertEquals(false, read.await(), "the read gave way")
            assertTrue(phone.answeredAt.isEmpty(), "a check cut short is no answer")

            advanceUntilIdle()
            assertEquals(true, install.await(), "the step's own re-read finished")
            assertEquals(setOf("claude", "codex", "kilo"), phone.answeredAt.keys)
            assertTrue(phone.answeredAt.values.all { it > 1_000 })
            assertEquals(1, phone.mostAlive, "never more processes at once than one")
        }

    @Test
    fun `a read never starts while a step waits, steps keep their order, and only the last step reads`() = runTest {
        val phone = Phone(this, listOf("claude", "codex"), checkMillis = 10_000)
        val first = async { phone.step("install codex") }
        runCurrent()
        val read = async { phone.read() }
        val second = async { phone.step("remove kilo") }
        runCurrent()
        advanceUntilIdle()

        assertNull(first.await(), "the first step's re-read gave way to the second step")
        assertNull(read.await(), "the read asked meanwhile gave way too")
        assertEquals(true, second.await())
        assertEquals(listOf("install codex" to 0L, "remove kilo" to 1_000L), phone.steps)
        assertTrue(phone.ended.isEmpty(), "nothing had to be cut: no read ran beside a step")
        assertEquals(1, phone.mostAlive)
    }

    @Test
    fun `a read with no step waiting runs to its end`() = runTest {
        val phone = Phone(this, listOf("claude", "codex"))
        val read = async { phone.read() }
        advanceUntilIdle()

        assertEquals(true, read.await())
        assertEquals(mapOf("claude" to 60_000L, "codex" to 120_000L), phone.answeredAt)
        assertTrue(phone.ended.isEmpty())
    }
}
