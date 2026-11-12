package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// Owner/device report: the first look said "the agent helper did not answer" 19 s into a helper's start-up.
class HelperAnswerWaitTest {
    private var clock = 0L

    private suspend fun run(answersAfterMs: Long): Pair<String, Int> {
        var calls = 0
        val result =
            HelperAnswerWait.until(
                now = { clock },
                pause = { clock += it },
                done = { it == "ok" },
            ) {
                calls++
                if (clock >= answersAfterMs) "ok" else "silent"
            }
        return result to calls
    }

    @Test
    fun `a helper that answers after 19 seconds is waited for`() = runTest {
        val (result, calls) = run(19_000)
        assertEquals("ok", result)
        assertTrue(calls > 30)
    }

    @Test
    fun `a helper that never answers is given up on after the budget, not before`() = runTest {
        val (result, _) = run(Long.MAX_VALUE)
        assertEquals("silent", result)
        assertTrue(clock >= HelperAnswerWait.BUDGET_MS)
    }

    @Test
    fun `only a missing answer is retried, a refused key is not`() {
        assertTrue(HelperAnswerWait.stillStarting(Failure.TimedOut()))
        assertTrue(HelperAnswerWait.stillStarting(Failure.ComputerDidNotAnswer()))
        assertFalse(HelperAnswerWait.stillStarting(Failure.SecretUnavailable()))
    }

    @Test
    fun `a half-ready helper's garbled first answer is tried again`() {
        assertTrue(HelperAnswerWait.stillStarting(Failure.UnreadableReply()))
        assertTrue(HelperAnswerWait.stillStarting(Failure.Unexpected()))
    }
}
