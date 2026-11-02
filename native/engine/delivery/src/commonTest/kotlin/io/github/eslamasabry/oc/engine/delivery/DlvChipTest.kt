package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DlvChipTest {
    @Test
    fun `B-AGENT-243 DLV-9 Stop never restores a waiting chip to the draft`() = runTest {
        val w = World(Disk(), this)
        val ready = CompletableDeferred<Unit>()
        w.link.transport = { ready.await(); Unit.ok() }
        val d = w.delivery()
        d.sendInsteadOfDraft(req("continue"))
        w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND)
        d.stop(REF)
        ready.complete(Unit)
        testScheduler.runCurrent()
        assertTrue(d.outcomes.value.isEmpty())
        assertTrue(w.link.attempts.isEmpty() && w.queue.entries().isEmpty())
    }
}
