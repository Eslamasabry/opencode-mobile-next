package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Issue 147. Owner's phone (build 10045): the home's chip read "Starting Claude Code…" for minutes while no agent
 * helper ran and nothing started it. Galaxy S22 (build 10046, right after an update): the first try failed at once
 * because nothing listened yet, "isn't answering · Try again" showed within 4 s, and nothing started the helper until
 * the tap. A start loop reads "starting" for its first 90 s whatever the tries answer, names the failure only after
 * that (or at once when waiting cannot fix it), and keeps trying with a pause.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelperJoinsTest {
    private val helper = ProfileId("phone-agents")
    private val noAnswer = Failure.Runtime(RuntimeProblem.ServerStartUnavailable)

    private fun TestScope.joins(): HelperJoins = HelperJoins(backgroundScope, now = { testScheduler.currentTime })

    @Test
    fun `I147-AGY-12 after an app start a first try that fails at once is followed by another start by itself`() =
        runTest {
            // The saved source, the app just started (or was updated): nothing listens yet, then the helper answers.
            val joins = joins()
            var tries = 0
            val failures = mutableListOf<Failure>()
            joins.ensure(helper, HelperStartRules::keepTrying, { failures += it }) {
                tries++
                if (tries == 1) noAnswer else null
            }
            runCurrent()
            assertEquals(1, tries)
            assertEquals(setOf(helper), joins.starting.value, "still starting: no failure words")
            assertEquals(emptyList(), failures)
            advanceTimeBy(HelperJoins.QUICK_RETRY_MS + 1)
            runCurrent()
            assertEquals(2, tries, "a start followed by itself, no tap")
            assertEquals(emptySet(), joins.starting.value)
            assertFalse(joins.running(helper))
            assertEquals(emptyList(), failures)
        }

    @Test
    fun `I147-AGY-12b failures are named only after the 90 s window, then it keeps trying with a pause`() = runTest {
        val joins = joins()
        var tries = 0
        val failures = mutableListOf<Failure>()
        joins.ensure(helper, HelperStartRules::keepTrying, { failures += it }) {
            tries++
            noAnswer
        }
        runCurrent()
        advanceTimeBy(HelperJoins.LIMIT_MS - 1_000)
        runCurrent()
        assertEquals(setOf(helper), joins.starting.value)
        assertEquals(emptyList(), failures)
        advanceTimeBy(HelperJoins.QUICK_RETRY_MS + 1_000)
        runCurrent()
        assertEquals(emptySet(), joins.starting.value)
        assertEquals(1, failures.size, "named after the window")
        val before = tries
        advanceTimeBy(HelperJoins.pauseAfter(1) + 1)
        runCurrent()
        assertTrue(tries > before, "still trying after the window")
        assertTrue(joins.running(helper))
    }

    @Test
    fun `I147-AGY-12c a failure waiting cannot fix is named at once and ends the loop`() = runTest {
        val joins = joins()
        val failures = mutableListOf<Failure>()
        joins.ensure(helper, HelperStartRules::keepTrying, { failures += it }) {
            Failure.Runtime(RuntimeProblem.HelperUpdateRequired)
        }
        runCurrent()
        assertEquals(1, failures.size)
        assertEquals(emptySet(), joins.starting.value)
        assertFalse(joins.running(helper))
    }

    @Test
    fun `I147-AGY-5 a try that never returns stops reading Starting at the limit, names a failure, and is not piled on`() =
        runTest {
            val joins = joins()
            var tries = 0
            val failures = mutableListOf<Failure>()
            joins.ensure(helper, HelperStartRules::keepTrying, { failures += it }) {
                tries++
                awaitCancellation()
            }
            runCurrent()
            assertEquals(setOf(helper), joins.starting.value)
            advanceTimeBy(HelperJoins.LIMIT_MS + 1)
            runCurrent()
            assertEquals(emptySet(), joins.starting.value)
            assertEquals(1, failures.size)
            advanceTimeBy(10 * HelperJoins.LIMIT_MS)
            runCurrent()
            assertEquals(1, tries, "the stuck try is waited for, never piled on")
            assertFalse(joins.ensure(helper, HelperStartRules::keepTrying, {}) { null }, "one loop per source")
        }

    @Test
    fun `I147-AGY-12d a person's refresh wakes a loop that pauses between tries`() = runTest {
        val joins = joins()
        var tries = 0
        joins.ensure(helper, HelperStartRules::keepTrying, {}) {
            tries++
            noAnswer
        }
        advanceTimeBy(HelperJoins.LIMIT_MS + HelperJoins.QUICK_RETRY_MS)
        runCurrent()
        val before = tries
        joins.wake(helper)
        runCurrent()
        assertEquals(before + 1, tries)
    }
}
