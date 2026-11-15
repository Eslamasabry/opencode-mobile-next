package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Issue 147 (owner's phone, build 10045): the home's chip read "Starting Claude Code…" for minutes while no agent
 * helper ran and nothing was starting it. A "starting" helper source must have a live start behind it, a time limit,
 * and a named failure past that limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelperJoinsTest {
    private val helper = ProfileId("phone-agents")

    private fun TestScope.joins(): HelperJoins = HelperJoins(backgroundScope, now = { testScheduler.currentTime })

    @Test
    fun `I147-AGY-5 a helper start that never returns stops reading Starting at its limit and names a failure`() =
        runTest {
            val joins = joins()
            var limits = 0
            assertTrue(joins.start(helper, onLimit = { limits++ }) { awaitCancellation() })
            runCurrent()
            assertEquals(setOf(helper), joins.starting.value, "starting while the start runs")
            advanceTimeBy(HelperJoins.LIMIT_MS + 1)
            runCurrent()
            assertEquals(emptySet(), joins.starting.value, "never Starting past the limit")
            assertEquals(1, limits, "the failure is named once")
            // The stuck start is not piled on, and the source is not due while it hangs.
            assertFalse(joins.start(helper, onLimit = { limits++ }) {})
            assertFalse(joins.due(helper))
        }

    @Test
    fun `I147-AGY-5b a start that ends stops reading Starting at once, with no failure`() = runTest {
        val joins = joins()
        val answer = CompletableDeferred<Unit>()
        var limits = 0
        joins.start(helper, onLimit = { limits++ }) { answer.await() }
        runCurrent()
        assertEquals(setOf(helper), joins.starting.value)
        advanceTimeBy(20_000)
        answer.complete(Unit)
        runCurrent()
        assertEquals(emptySet(), joins.starting.value)
        assertEquals(0, limits)
    }

    @Test
    fun `I147-AGY-5c a source with no start is due again only after the retry interval`() = runTest {
        val joins = joins()
        assertTrue(joins.due(helper), "never started: due")
        joins.start(helper, onLimit = {}) {}
        runCurrent()
        assertFalse(joins.due(helper), "just started")
        advanceTimeBy(HelperJoins.RETRY_AFTER_MS)
        runCurrent()
        assertTrue(joins.due(helper))
    }
}
