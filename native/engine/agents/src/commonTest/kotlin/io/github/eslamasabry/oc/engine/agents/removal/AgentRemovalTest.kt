package io.github.eslamasabry.oc.engine.agents.removal

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRemovalTest {
    private val owner = ProfileId("owner")
    private val random = SecureBytes { n -> ByteArray(n) { 0xab.toByte() } }

    private class Runner(var run: RemovalRun) : AgentRemovalRunner {
        val calls = mutableListOf<String>()
        override suspend fun remove(owner: OwnerId, agent: AgentId, receiptId: String): Outcome<RemovalRun> {
            calls += "${agent.raw}:$receiptId"
            return run.ok()
        }
    }

    private suspend fun AgentRemoval.result(agent: String, busy: Boolean = false) =
        (remove(owner, AgentId(agent), busy) as Outcome.Ok).value

    @Test
    fun `B-LINUX-139 removal never dispatches for Claude or non-catalog ids`() = runTest {
        val runner = Runner(RemovalRun(0, RemovalReceipt(1, false)))
        val removal = AgentRemoval(runner, random)
        for (id in listOf("claude", "opencode2", "missing")) {
            assertEquals(RemovalResult.Refused(RemovalRefusal.NotRemovable), removal.result(id))
        }
        assertTrue(runner.calls.isEmpty())
        for (id in listOf("codex", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx")) assertTrue(removal.removable(AgentId(id)), id)
        assertFalse(removal.removable(AgentId.CLAUDE))
    }

    @Test
    fun `B-LINUX-139 measured freed bytes, zero when absent, and never success when unconfirmed`() = runTest {
        val runner = Runner(RemovalRun(0, RemovalReceipt(98_000_000, false)))
        val removal = AgentRemoval(runner, random)
        assertEquals(RemovalResult.Removed(98_000_000), removal.result("codex"))
        assertEquals(listOf("codex:" + "ab".repeat(16)), runner.calls)
        runner.run = RemovalRun(0, RemovalReceipt(5, wasAbsent = true))
        assertEquals(RemovalResult.Removed(0), removal.result("codex"))
        runner.run = RemovalRun(0, null)
        assertEquals(RemovalResult.Refused(RemovalRefusal.Unconfirmed), removal.result("codex"))
    }

    @Test
    fun `B-LINUX-139 exit codes map to busy, unsafe and failed, and a busy phone never dispatches`() = runTest {
        val runner = Runner(RemovalRun(16, null))
        val removal = AgentRemoval(runner, random)
        assertEquals(RemovalResult.Refused(RemovalRefusal.Busy), removal.result("fx"))
        runner.run = RemovalRun(17, null)
        assertEquals(RemovalResult.Refused(RemovalRefusal.Unsafe), removal.result("fx"))
        for (code in listOf(18, 1, 255)) {
            runner.run = RemovalRun(code, RemovalReceipt(1, false))
            assertEquals(RemovalResult.Refused(RemovalRefusal.Failed), removal.result("fx"))
        }
        val before = runner.calls.size
        assertEquals(RemovalResult.Refused(RemovalRefusal.Busy), removal.result("fx", busy = true))
        assertEquals(before, runner.calls.size)
    }
}
