package io.github.eslamasabry.oc.engine.settings.teaching

import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.engine.settings.consent.TestKv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StarPromptTest {
    private fun TestScope.kv(stored: Map<String, Any> = emptyMap()) = TestKv(stored, UnconfinedTestDispatcher(testScheduler))

    private val day = 24L * 60 * 60 * 1000
    private var now = 1_000L

    private fun ledger(kv: TestKv) = StarPromptLedger(kv, kv.lanes) { now }

    private suspend fun StarPromptLedger.finished(replies: Int) = repeat(replies) { promptStarted(); replyEnded(success = true) }

    @Test
    fun `B-STAR-001 ten good replies on the first day never ask`() = runTest {
        val ledger = ledger(kv())
        val asked = (1..10).map { ledger.promptStarted(); ledger.replyEnded(success = true) }
        assertFalse(asked.any { it }, "the first days of use are never the moment")
    }

    @Test
    fun `B-STAR-002 after three days and ten good replies the finishing reply asks`() = runTest {
        val ledger = ledger(kv())
        ledger.finished(9)
        now += 3 * day
        assertFalse(ledger.replyEnded(success = false), "a failed reply is not the moment")
        ledger.promptStarted()
        assertTrue(ledger.replyEnded(success = true), "the tenth good reply, three days in")
    }

    @Test
    fun `B-STAR-003 three days with fewer than ten replies do not ask`() = runTest {
        val ledger = ledger(kv())
        ledger.finished(5)
        now += 4 * day
        assertFalse(ledger.replyEnded(success = true))
    }

    @Test
    fun `B-STAR-004 it asks once ever, also after a restart`() = runTest {
        val kv = kv()
        val first = ledger(kv)
        first.finished(10)
        now += 3 * day
        assertTrue(first.replyEnded(success = true))
        assertFalse(first.replyEnded(success = true))
        assertFalse(ledger(kv).replyEnded(success = true))
        assertTrue(StarPromptRules.parse(kv.string(StarPromptRules.KEY)).shown)
    }

    @Test
    fun `B-STAR-005 the state is one app-wide key, not a profile key`() = runTest {
        val kv = kv()
        ledger(kv).promptStarted()
        assertEquals(setOf("oc.starPrompt"), kv.keys("oc."))
    }

    @Test
    fun `B-STAR-006 an unreadable value never asks`() = runTest {
        val kv = kv(mapOf(StarPromptRules.KEY to "garbage"))
        val ledger = ledger(kv)
        now += 10 * day
        repeat(12) { assertFalse(ledger.replyEnded(success = true)) }
    }

    @Test
    fun `B-STAR-007 the rules round trip and a refused write asks nothing`() = runTest {
        val state = StarPromptState(prompts = 4, replies = 3, shown = false, firstMs = 42)
        assertEquals(state, StarPromptRules.parse(StarPromptRules.encode(state)))
        assertEquals(StarPromptState(2, 1, true, 0), StarPromptRules.parse("p=2;r=1;s=1"), "a value without a first day")
        val kv = kv()
        val ledger = ledger(kv)
        ledger.finished(10)
        now += 3 * day
        kv.disk.refuse = { key, _ -> key == StarPromptRules.KEY }
        assertFalse(ledger.replyEnded(success = true), "an ask that could not be remembered would repeat")
    }
}
