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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StarPromptTest {
    private fun TestScope.kv(stored: Map<String, Any> = emptyMap()) =
        TestKv(stored, UnconfinedTestDispatcher(testScheduler))

    private val day = 24L * 60 * 60 * 1000
    private var now = 1_000L

    private fun ledger(kv: TestKv) = StarPromptLedger(kv, kv.lanes) { now }

    @Test
    fun `B-STAR-004 it asks once ever, also after a restart`() = runTest {
        val kv = kv()
        val first = ledger(kv)
        first.promptStarted()
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
        assertEquals(
            StarPromptState(2, 1, true, 0),
            StarPromptRules.parse("p=2;r=1;s=1"),
            "a value without a first day",
        )
        val kv = kv()
        val ledger = ledger(kv)
        kv.disk.refuse = { key, _ -> key == StarPromptRules.KEY }
        assertFalse(ledger.replyEnded(success = true), "an ask that could not be remembered would repeat")
    }

    @Test
    fun `B-STAR-024 the first good reply asks on a fresh install`() = runTest {
        val kv = kv()
        val ledger = ledger(kv)
        ledger.promptStarted()
        assertTrue(ledger.replyEnded(success = true), "the first good reply asks with no day or count wait")
        assertFalse(ledger.replyEnded(success = true), "never asks a second time")
    }

    @Test
    fun `B-STAR-025 a failed first reply does not ask and the next good one does`() = runTest {
        val kv = kv()
        val ledger = ledger(kv)
        ledger.promptStarted()
        assertFalse(ledger.replyEnded(success = false), "a failed reply never asks")
        ledger.promptStarted()
        assertTrue(ledger.replyEnded(success = true), "the next good reply asks")
    }

    @Test
    fun `B-STAR-026 an install never asked is asked on its next good reply, an asked one never`() = runTest {
        val unaskedKv = kv(mapOf(StarPromptRules.KEY to "p=4;r=3;s=0;f=100"))
        assertTrue(ledger(unaskedKv).replyEnded(success = true), "s=0 asks on the next good reply")
        assertTrue(StarPromptRules.parse(unaskedKv.string(StarPromptRules.KEY)).shown)

        val askedKv = kv(mapOf(StarPromptRules.KEY to "p=4;r=3;s=1;f=100"))
        assertFalse(ledger(askedKv).replyEnded(success = true), "s=1 never asks again")
    }

    @Test
    fun `B-STAR-027 answer and use are stored once in the same key and an old value reads unchanged`() = runTest {
        val legacy = "p=4;r=3;s=0;f=42"
        val parsedLegacy = StarPromptRules.parse(legacy)
        assertNull(parsedLegacy.answer)
        assertNull(parsedLegacy.use)
        assertEquals(legacy, StarPromptRules.encode(parsedLegacy), "old string encodes back byte for byte")

        val unknown = StarPromptRules.parse("p=1;r=1;s=1;f=10;a=z;u=q")
        assertNull(unknown.answer, "unknown answer reads as no answer")
        assertNull(unknown.use, "unknown use reads as no answer")

        val kv = kv()
        val ledger = ledger(kv)
        ledger.promptStarted()
        assertTrue(ledger.replyEnded(success = true))
        ledger.answered(StarPromptAnswer.Yes)
        ledger.answered(StarPromptAnswer.No)
        ledger.usedFor(StarPromptUse.Code)
        ledger.usedFor(StarPromptUse.Both)

        val stored = StarPromptRules.parse(kv.string(StarPromptRules.KEY))
        assertEquals(StarPromptAnswer.Yes, stored.answer, "first answer wins")
        assertEquals(StarPromptUse.Code, stored.use, "first use wins")
        assertEquals(setOf(StarPromptRules.KEY), kv.keys("oc."), "stored in the same app-wide key")
        assertTrue(kv.string(StarPromptRules.KEY)!!.contains(";a=y;u=c"))
    }
}
