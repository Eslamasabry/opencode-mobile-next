package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.engine.delivery.port.SendLink
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DlvParallelFlushTest {
    @Test
    fun `B-STATE-042 DLV-1 parallel flush admission dispatches a prompt at most once`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        val readers = AtomicInteger()
        val rendezvous = CyclicBarrier(2)
        // The old check-set reads origin between checking and setting `flushing`. Hold both threads there.
        // A serial lane permits only one reader and the bounded rendezvous then releases it.
        w.connection.link.value = object : SendLink by w.link {
            override val scope: DispatchScope
                get() {
                    if (readers.incrementAndGet() <= 2) {
                        try {
                            rendezvous.await(1, TimeUnit.SECONDS)
                        } catch (_: TimeoutException) {
                            // Only one caller admitted.
                        } catch (_: BrokenBarrierException) {
                            // The other reader timed out after serial admission.
                        }
                    }
                    return w.link.scope
                }
        }
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { workers ->
            listOf(async(workers) { w.queue.flush() }, async(workers) { w.queue.flush() }).awaitAll()
        }
        assertEquals(listOf("prompt a"), w.link.deliveredTexts())
        assertEquals(1, w.link.attempts.size)
    }
}
