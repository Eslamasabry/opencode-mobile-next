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

    private fun ledger(kv: TestKv) = StarPromptLedger(kv, kv.lanes)

    @Test
    fun `B-STAR-001 the first prompt never asks`() = runTest {
        val kv = kv()
        assertFalse(ledger(kv).promptStarted())
    }

    @Test
    fun `B-STAR-002 the second prompt asks when the first got a good reply`() = runTest {
        val kv = kv()
        val ledger = ledger(kv)
        assertFalse(ledger.promptStarted())
        ledger.replyEnded(success = true)
        assertTrue(ledger.promptStarted())
    }

    @Test
    fun `B-STAR-003 a failed first reply means the second prompt does not ask`() = runTest {
        val kv = kv()
        val ledger = ledger(kv)
        assertFalse(ledger.promptStarted())
        ledger.replyEnded(success = false)
        assertFalse(ledger.promptStarted())
        // And it never comes back later.
        ledger.replyEnded(success = true)
        assertFalse(ledger.promptStarted())
        assertFalse(ledger.promptStarted())
    }

    @Test
    fun `B-STAR-004 it asks once ever, also after a restart`() = runTest {
        val kv = kv()
        val first = ledger(kv)
        first.promptStarted()
        first.replyEnded(success = true)
        assertTrue(first.promptStarted())
        first.replyEnded(success = true)
        assertFalse(first.promptStarted())
        val restarted = ledger(kv)
        assertFalse(restarted.promptStarted())
        assertEquals("p=2;r=1;s=1", kv.string(StarPromptRules.KEY))
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
        ledger.replyEnded(success = true)
        assertFalse(ledger.promptStarted())
        assertFalse(ledger.promptStarted())
    }

    @Test
    fun `B-STAR-007 the rules round trip and a refused write asks nothing`() = runTest {
        val state = StarPromptState(prompts = 1, replies = 1, shown = false)
        assertEquals(state, StarPromptRules.parse(StarPromptRules.encode(state)))
        val kv = kv()
        val ledger = ledger(kv)
        ledger.promptStarted()
        ledger.replyEnded(success = true)
        kv.disk.refuse = { key, _ -> key == StarPromptRules.KEY }
        assertFalse(ledger.promptStarted(), "an ask that could not be remembered would repeat")
    }
}
