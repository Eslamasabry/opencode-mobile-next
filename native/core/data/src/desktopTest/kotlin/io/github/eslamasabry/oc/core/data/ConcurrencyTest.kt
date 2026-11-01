package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real threads (audit codex-04+05 MAJOR 8; codex-01+03 BLOCKER 1). */
class ConcurrencyTest {
    private val pool = Executors.newFixedThreadPool(4)
    private val threads = pool.asCoroutineDispatcher()

    @AfterTest
    fun stop() {
        pool.shutdownNow()
    }

    /** Flags two commits overlapping in time, i.e. a broken file-wide lock. */
    private class OverlapBackend(initial: Map<String, RawPref>) : InMemoryPrefsBackend(initial) {
        val inside = AtomicInteger()
        val overlapped = AtomicBoolean()
        val refuseEvery = AtomicInteger(0)
        private val count = AtomicInteger()

        override fun commit(changes: Map<String, RawPref?>): Boolean {
            if (inside.incrementAndGet() > 1) overlapped.set(true)
            try {
                Thread.sleep(0, 200_000)
                val n = refuseEvery.get()
                if (n > 0 && count.incrementAndGet() % n == 0) return false
                return super.commit(changes)
            } finally {
                inside.decrementAndGet()
            }
        }
    }

    @Test
    fun `B-STATE-004 compare-and-set and ordinary commits on many threads never lose an update or overlap`() = runBlocking {
        val backend = OverlapBackend(physical("oc.counter" to RawPref.Str("0")))
        backend.refuseEvery.set(7) // refusals and their compensations share the same lock
        val store = storeOver(backend, threads)
        val lanes = List(4) { WriterLane("l$it") }
        val successes = AtomicInteger()
        coroutineScope {
            (0 until 200).map { i ->
                async(threads) {
                    val lane = lanes[i % 4]
                    if (i % 3 == 0) {
                        store.commit(lane) { putString("oc.noise.$i", "n") }
                    } else {
                        while (true) {
                            val cur = store.string("oc.counter")!!
                            val r = store.compareAndSet(lane, "oc.counter", cur, (cur.toInt() + 1).toString())
                            if (r == Outcome.Ok(true)) { successes.incrementAndGet(); break }
                        }
                    }
                }
            }.awaitAll()
        }
        assertFalse(backend.overlapped.get(), "two platform commits ran at once")
        assertEquals(successes.get().toString(), store.string("oc.counter"))
        assertEquals(RawPref.Str(successes.get().toString()), backend.persisted()["flutter.oc.counter"])
    }

    @Test
    fun `B-STATE-013 under real threads no write admitted before stopAdmission finishes after drain`() = runBlocking {
        repeat(50) {
            val lane = WriterLane("p")
            val finished = AtomicInteger()
            val drainedAt = AtomicInteger(-1)
            val writers = (0 until 20).map {
                async(threads) {
                    lane.write { Thread.sleep(0, 100_000); finished.incrementAndGet(); Outcome.Ok(Unit) }
                }
            }
            withContext(threads) {
                lane.stopAdmission()
                lane.drain()
                drainedAt.set(finished.get())
            }
            writers.awaitAll()
            // Every write that was admitted (not refused) had finished when drain returned.
            val admitted = writers.count { it.await() is Outcome.Ok }
            assertEquals(admitted, drainedAt.get())
            assertTrue(admitted <= 20)
        }
    }
}
