package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.delivery.queue.QueueLimits
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPromptCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DlvTrimTest {
    @Test
    fun `B-STATE-035 DLV-2 deferred trim preserves an acknowledged addition`() = runTest {
        val expired = prompt("expired", createdAt = NOW_MS - QueueLimits.MAX_AGE_MS - 1)
        val disk = Disk.withQueue(QueuedPromptCodec.encodeQueue(listOf(expired)))
        val trimDispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        val w = World(disk, this, engineScope = CoroutineScope(backgroundScope.coroutineContext + trimDispatcher))
        assertEquals(Outcome.Ok(null), w.queue.queuePrompt(prompt("new")))
        trimDispatcher.release()
        testScheduler.runCurrent()
        assertEquals(listOf("new"), w.restart().queue.entries().map { it.id })
    }

    @Test
    fun `B-STATE-042 DLV-2 deferred trim preserves a newer dispatch marker across death`() = runTest {
        val expired = prompt("expired", createdAt = NOW_MS - QueueLimits.MAX_AGE_MS - 1)
        val disk = Disk.withQueue(QueuedPromptCodec.encodeQueue(listOf(expired, prompt("a"))))
        val trimDispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        val w = World(disk, this, engineScope = CoroutineScope(backgroundScope.coroutineContext + trimDispatcher))
        val onWire = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        w.link.answer = { onWire.complete(Unit); answer.await(); FakeLink.Answer.LostResponse }
        val flush = launch { w.queue.flush() }
        onWire.await()
        trimDispatcher.release()
        testScheduler.runCurrent()
        assertNotNull(w.restart().queue.entries().single().dispatchedAt)
        answer.complete(Unit)
        flush.join()
    }
}

/** Hold only the deferred cleanup launch; store commits continue on the same virtual-time scheduler. */
private class HeldDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private val held = mutableListOf<() -> Unit>()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        held += { delegate.dispatch(context, block) }
    }

    fun release() {
        val ready = held.toList()
        held.clear()
        ready.forEach { it() }
    }
}
