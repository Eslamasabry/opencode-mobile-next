package io.github.eslamasabry.oc.engine.delivery

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DlvClearTest {
    @Test
    fun `B-STATE-045 DLV-7 clear refuses an offline dispatch and preserves its review after response loss`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        val onWire = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        w.link.answer = { onWire.complete(Unit); answer.await(); FakeLink.Answer.LostResponse }
        val flushing = launch { w.queue.flush() }
        onWire.await()
        assertFalse(w.queue.clearAll())
        answer.complete(Unit)
        flushing.join()
        val reborn = w.restart()
        assertNotNull(reborn.queue.entries().single().dispatchedAt)
        reborn.queue.flush()
        assertEquals(1, w.link.attempts.size)
    }
}
