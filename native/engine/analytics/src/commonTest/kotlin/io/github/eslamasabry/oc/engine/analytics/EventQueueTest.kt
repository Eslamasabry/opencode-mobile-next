package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventQueueTest {
    private fun event(seq: Long, atMs: Long = 0, tier: AnalyticsTier = AnalyticsTier.Basic) =
        QueuedEvent(
            seq = seq,
            tier = tier,
            atMs = atMs,
            name = "app_start",
            hourUtc = 0,
            install = "0123456789abcdef0123456789abcdef",
            system = TestSystem,
            props = mapOf("kind" to "Cold", "duration" to "Under1S"),
        )

    @Test
    fun `B-ANLY-006 the queue keeps at most 500 events and drops the oldest`() {
        val queue = EventQueue()
        (0L until 520L).forEach { queue.add(event(it)) }
        assertEquals(500, queue.size)
        assertEquals(20L, queue.snapshot().first().seq)
        assertEquals(519L, queue.snapshot().last().seq)
    }

    @Test
    fun `B-ANLY-006 the queue keeps at most 256 KiB and drops the oldest`() {
        val one = event(0).sizeBytes
        val queue = EventQueue(maxEvents = 100_000)
        val count = (EventQueue.MAX_BYTES / one) + 40
        (0L until count.toLong()).forEach { queue.add(event(it)) }
        assertTrue(queue.bytes <= EventQueue.MAX_BYTES)
        assertEquals(EventQueue.MAX_BYTES / one, queue.size)
        assertEquals(count - 1L, queue.snapshot().last().seq)
    }

    @Test
    fun `B-ANLY-006 events older than seven days expire`() {
        val queue = EventQueue()
        queue.add(event(0, atMs = 0))
        queue.add(event(1, atMs = 2 * DAY_MS))
        assertEquals(0, queue.expire(7 * DAY_MS))
        assertEquals(1, queue.expire(7 * DAY_MS + 1))
        assertEquals(listOf(1L), queue.snapshot().map { it.seq })
    }

    @Test
    fun `B-ANLY-007 batches hold at most 25 events, oldest first, and removal takes only what was sent`() {
        val queue = EventQueue()
        (0L until 60L).forEach { queue.add(event(it)) }
        val batch = queue.batch()
        assertEquals(25, batch.size)
        assertEquals((0L until 25L).toList(), batch.map { it.seq })
        queue.add(event(60))
        queue.remove(batch.map { it.seq }.toSet())
        assertEquals(36, queue.size)
        assertEquals(25L, queue.batch().first().seq)
    }

    @Test
    fun `B-ANLY-002 dropping a tier removes only that tier and frees its bytes`() {
        val queue = EventQueue()
        queue.add(event(0, tier = AnalyticsTier.Basic))
        queue.add(event(1, tier = AnalyticsTier.Detailed))
        queue.add(event(2, tier = AnalyticsTier.Detailed))
        assertEquals(2, queue.dropTier(AnalyticsTier.Detailed))
        assertEquals(listOf(0L), queue.snapshot().map { it.seq })
        assertEquals(event(0).sizeBytes, queue.bytes)
    }
}
