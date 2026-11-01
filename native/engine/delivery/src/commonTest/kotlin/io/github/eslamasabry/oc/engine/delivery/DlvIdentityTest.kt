package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRefusal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DlvIdentityTest {
    @Test
    fun `B-STATE-009 DLV-4 a completed command identity cannot consume a replacement after restart`() = runTest {
        val w = World(Disk(), this, link = FakeLink(capabilities = ServerCapabilities(commandReceipts = true), correlated = true))
        w.queue.queuePrompt(prompt("a", text = "original"))
        w.queue.flush()
        val reborn = w.restart()
        assertEquals(Outcome.Ok(QueueRefusal.DuplicateId), reborn.queue.queuePrompt(prompt("a", text = "replacement")))
        assertEquals(Outcome.Ok(null), reborn.queue.queuePrompt(prompt("replacement", text = "replacement")))
        reborn.queue.flush()
        assertEquals(listOf("original", "replacement"), w.link.deliveredTexts())
        assertTrue(reborn.restart().queue.entries().isEmpty())
    }
}
