package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.delivery.queue.QueueMoveBlock
import io.github.eslamasabry.oc.engine.delivery.queue.QueueMoveResult
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Moving a whole server's waiting prompts to the connected server, with Undo (Flutter `QueuedPromptMove`). */
class QueueServerMoveTest {
    private val redactor = Redactor()
    private val target = ConversationRef(P1, S2)

    private suspend fun World.move(
        ids: Set<String>,
        models: Set<ModelRef>? = null,
        agents: Set<String>? = null,
    ): Outcome<QueueMoveResult?> = queue.moveMany(P2, ids, target, false, models, agents, redactor)

    private fun World.queueOf(): List<String> = queue.entries().map { "${it.id}@${it.profileId.raw.takeLast(1)}" }

    @Test
    fun `the picked prompts move in one write, keep their place and order, and nothing is sent`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a", profile = P2, createdAt = NOW_MS - 30))
        w.queue.queuePrompt(prompt("mine", profile = P1, createdAt = NOW_MS - 25))
        w.queue.queuePrompt(prompt("b", profile = P2, createdAt = NOW_MS - 20))
        w.queue.queuePrompt(prompt("c", profile = P2, createdAt = NOW_MS - 10))
        val before = w.disk.commits
        val result = (w.move(setOf("a", "c")) as Outcome.Ok).value
        assertNotNull(result)
        assertEquals(1, w.disk.commits - before, "one queue write")
        assertEquals(listOf("a@1", "mine@1", "b@2", "c@1"), w.queueOf())
        assertEquals(listOf("a", "c"), result.originals.map { it.id })
        assertEquals(2, result.moved)
        assertEquals(0, result.skipped)
        assertTrue(w.queue.entries().filter { it.id in setOf("a", "c") }.all { it.sessionId == S2 })
        assertTrue(w.link.delivered.isEmpty(), "moving sends nothing")
    }

    @Test
    fun `a prompt in doubt, on the wire, with a server file or masked secrets is blocked and counted as skipped`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("doubt", profile = P2, dispatchedAt = NOW_MS - 5))
        w.queue.queuePrompt(prompt("file", profile = P2, attachmentUrls = listOf("file:///data/x.txt")))
        w.queue.queuePrompt(prompt("web", profile = P2, attachmentUrls = listOf("https://example.com/x.txt")))
        assertEquals(QueueMoveBlock.Uncertain, w.queue.moveBlock(w.queue.entries().first { it.id == "doubt" }, redactor))
        assertEquals(QueueMoveBlock.ServerFile, w.queue.moveBlock(w.queue.entries().first { it.id == "file" }, redactor))
        assertNull(w.queue.moveBlock(w.queue.entries().first { it.id == "web" }, redactor))
        val result = (w.move(setOf("doubt", "file", "web")) as Outcome.Ok).value
        assertNotNull(result)
        assertEquals(listOf("web"), result.originals.map { it.id })
        assertEquals(2, result.skipped)
        assertEquals(listOf("doubt@2", "file@2", "web@1"), w.queueOf())
    }

    @Test
    fun `nothing movable answers null and writes nothing`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("doubt", profile = P2, dispatchedAt = NOW_MS - 5))
        val before = w.disk.commits
        assertEquals(Outcome.Ok(null), w.move(setOf("doubt", "gone")))
        assertEquals(before, w.disk.commits)
    }

    @Test
    fun `a model or agent the destination does not offer is dropped together, an unknown list drops nothing`() = runTest {
        val w = World(Disk(), this)
        val picked = QueuedPrompt(
            "m", P2, S1, "hi", modelProviderId = "p", modelId = "gone", agent = "build", variant = "high", createdAt = NOW_MS - 9,
        )
        w.queue.queuePrompt(picked)
        w.queue.queuePrompt(
            QueuedPrompt("k", P2, S1, "hi", modelProviderId = "p", modelId = "ok", agent = "build", createdAt = NOW_MS - 8),
        )
        val result = (w.move(setOf("m", "k"), setOf(ModelRef("p", "ok")), setOf("build")) as Outcome.Ok).value
        assertNotNull(result)
        assertEquals(1, result.selectionsReset)
        val m = w.queue.entries().first { it.id == "m" }
        assertNull(m.model); assertNull(m.agent); assertNull(m.variant)
        val k = w.queue.entries().first { it.id == "k" }
        assertEquals(ModelRef("p", "ok"), k.model)
        assertEquals("build", k.agent)
    }

    @Test
    fun `undo puts the prompts back exactly as queued for the source, in place`() = runTest {
        val w = World(Disk(), this)
        val a = QueuedPrompt("a", P2, S1, "first", modelProviderId = "p", modelId = "gone", agent = "plan", createdAt = NOW_MS - 30, error = "network")
        w.queue.queuePrompt(a)
        w.queue.queuePrompt(prompt("mine", profile = P1, createdAt = NOW_MS - 20))
        w.queue.queuePrompt(prompt("b", profile = P2, createdAt = NOW_MS - 10))
        val originalBlob = w.queue.entries()
        val result = (w.move(setOf("a", "b"), setOf(ModelRef("p", "ok")), setOf("build")) as Outcome.Ok).value
        assertNotNull(result)
        assertEquals(2, (w.queue.undoMove(result) as Outcome.Ok).value)
        assertEquals(originalBlob, w.queue.entries(), "same entries, same order, same fields")
        assertEquals(0, (w.queue.undoMove(result) as Outcome.Ok).value, "a second undo changes nothing")
    }

    @Test
    fun `undo leaves a moved prompt that is already sent or removed where it is`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a", profile = P2, createdAt = NOW_MS - 20))
        w.queue.queuePrompt(prompt("b", profile = P2, createdAt = NOW_MS - 10))
        val result = (w.move(setOf("a", "b")) as Outcome.Ok).value
        assertNotNull(result)
        w.queue.remove("a")
        assertEquals(1, (w.queue.undoMove(result) as Outcome.Ok).value)
        assertEquals(listOf("b@2"), w.queueOf())
    }

    @Test
    fun `a refused write moves nothing and undo of a refused write changes nothing`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a", profile = P2))
        w.disk.refuseQueueWrites()
        assertIs<Failure.StorageRefused>((w.move(setOf("a")) as Outcome.Failed).failure)
        assertEquals(listOf("a@2"), w.queueOf())
    }

    @Test
    fun `moving into the same server moves nothing`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a", profile = P1))
        assertEquals(Outcome.Ok(null), w.queue.moveMany(P1, setOf("a"), target, false, null, null, redactor))
    }

    @Test
    fun `masked secrets under agent mentions block the move, plain text moves`() {
        val secretive = Redactor().apply { registerSecret("hunter2-hunter2-hunter2") }
        val mention = io.github.eslamasabry.oc.core.model.PromptAgentMention("build", "build", 0, 6)
        val withSecret = QueuedPrompt("s", P2, S1, "@build use hunter2-hunter2-hunter2", mentions = listOf(mention), createdAt = NOW_MS)
        val plain = QueuedPrompt("p", P2, S1, "@build hello", mentions = listOf(mention), createdAt = NOW_MS)
        assertEquals(
            QueueMoveBlock.SecretMentions,
            io.github.eslamasabry.oc.engine.delivery.queue.QueueServerMove.blockFor(withSecret, secretive),
        )
        assertNull(io.github.eslamasabry.oc.engine.delivery.queue.QueueServerMove.blockFor(plain, secretive))
        assertEquals(
            QueueMoveBlock.Sending,
            io.github.eslamasabry.oc.engine.delivery.queue.QueueServerMove.blockFor(plain, secretive, sending = true),
        )
    }
}
