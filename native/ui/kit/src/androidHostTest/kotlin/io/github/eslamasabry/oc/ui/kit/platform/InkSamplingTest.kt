package io.github.eslamasabry.oc.ui.kit.platform

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the ink sampler (AndroidBackdropSampler): target buckets, the corner read and the GPU guard. */
class InkSamplingTest {
    @Test
    fun `a target side is the next multiple of 16, at least 16`() {
        assertEquals(16, bucket(0))
        assertEquals(16, bucket(1))
        assertEquals(16, bucket(16))
        assertEquals(32, bucket(17))
        assertEquals(144, bucket(135))
        assertTrue(fits(144, 32, 144, 32))
        assertFalse(fits(144, 32, 145, 32))
        assertFalse(fits(144, 32, 10, 33))
    }

    @Test
    fun `a search bar opening and closing makes one target, not one per size`() {
        val size = InkTargetSize()
        assertTrue("the first sample makes the target", size.request(135, 20))
        assertEquals(144 to 32, size.width to size.height)
        var made = 0
        repeat(200) { i ->
            // A pane that changes by a few pixels each cycle, shrinks and grows back.
            val (w, h) = if (i % 2 == 0) 135 to 26 else 131 to 18
            if (size.request(w, h)) made++
        }
        assertEquals(0, made)
        assertEquals(144 to 32, size.width to size.height)
    }

    @Test
    fun `a sample that no longer fits grows the target and never shrinks a side`() {
        val size = InkTargetSize()
        size.request(135, 20)
        assertTrue(size.request(150, 10))
        assertEquals(160 to 32, size.width to size.height)
        assertTrue(size.request(20, 40))
        assertEquals(160 to 48, size.width to size.height)
        assertFalse(size.request(160, 48))
    }

    @Test
    fun `a target far too big shrinks once after 50 samples in a row`() {
        val size = InkTargetSize()
        size.request(160, 64)
        // 16x16 needed against 160x64: smaller both ways and more than four times the area.
        repeat(InkTargetSize.SHRINK_AFTER - 1) { assertFalse(size.request(10, 10)) }
        assertTrue(size.request(10, 10))
        assertEquals(16 to 16, size.width to size.height)
        repeat(100) { assertFalse(size.request(10, 10)) }
    }

    @Test
    fun `the shrink count starts again when a sample needs the target`() {
        val size = InkTargetSize()
        size.request(160, 64)
        repeat(InkTargetSize.SHRINK_AFTER - 1) { size.request(10, 10) }
        assertFalse(size.request(150, 60))
        repeat(InkTargetSize.SHRINK_AFTER - 1) { assertFalse(size.request(10, 10)) }
        assertTrue(size.request(10, 10))
    }

    @Test
    fun `a target too big in one direction only, or by four times or less, is kept`() {
        val wide = InkTargetSize()
        wide.request(160, 16)
        repeat(200) { assertFalse(wide.request(10, 16)) }
        val near = InkTargetSize()
        near.request(32, 32)
        repeat(200) { assertFalse(near.request(16, 16)) }
    }

    @Test
    fun `release forgets the target`() {
        val size = InkTargetSize()
        size.request(100, 20)
        size.reset()
        assertTrue(size.request(10, 10))
        assertEquals(16 to 16, size.width to size.height)
    }

    @Test
    fun `the corner read takes w by h pixels and skips the rest of each row`() {
        // A 4x3 frame, 4 bytes a pixel, rows padded to 20 bytes; every byte holds its own index.
        val stride = 20
        val bytes = ByteArray(stride * 3) { it.toByte() }
        val out = argbRegion(ByteBuffer.wrap(bytes), stride, 4, 2, 2)
        fun px(at: Int) = (bytes[at + 3].toInt() shl 24) or (bytes[at].toInt() shl 16) or (bytes[at + 1].toInt() shl 8) or bytes[at + 2].toInt()
        assertArrayEquals(intArrayOf(px(0), px(4), px(stride), px(stride + 4)), out)
    }

    @Test
    fun `the corner read turns RGBA bytes into ARGB ints`() {
        val rgba = byteArrayOf(0x11, 0x22, 0x33, 0xFF.toByte(), 0x80.toByte(), 0x00, 0x7F, 0x40)
        val out = argbRegion(ByteBuffer.wrap(rgba), 8, 4, 2, 1)
        assertArrayEquals(intArrayOf(0xFF112233.toInt(), 0x4080007F), out)
    }

    @Test
    fun `one sync at a time, and a quick one clears the late count`() {
        val guard = InkGpuGuard()
        assertTrue(guard.tryBegin(0))
        assertFalse("a second sample while a sync is out is skipped", guard.tryBegin(10))
        guard.end(50)
        assertTrue(guard.tryBegin(60))
        guard.end(100)
    }

    @Test
    fun `three late syncs in a row rest every sampler for ten seconds, then sampling starts again`() {
        val guard = InkGpuGuard()
        var now = 0L
        repeat(3) {
            assertTrue(guard.tryBegin(now))
            // The UI side sees the guard still held after 2 s.
            assertFalse(guard.tryBegin(now + 2_100))
            now += 2_500
            guard.end(now)
            now += 100
        }
        assertTrue(guard.resting(now))
        assertFalse("resting: nothing is posted", guard.tryBegin(now))
        assertFalse(guard.tryBegin(now + 9_000))
        assertTrue("rested: sampling starts again", guard.tryBegin(now + 10_000))
    }

    @Test
    fun `a late sync is counted once, seen from the UI side or at its end`() {
        val guard = InkGpuGuard()
        assertTrue(guard.tryBegin(0))
        repeat(10) { assertFalse(guard.tryBegin(2_000L + it * 100)) }
        guard.end(5_000)
        assertTrue(guard.tryBegin(5_100))
        guard.end(7_200) // late, seen only at its end
        assertFalse(guard.resting(7_200))
        assertTrue(guard.tryBegin(7_300))
        guard.end(9_400)
        assertTrue("the third late sync in a row", guard.resting(9_400))
    }

    @Test
    fun `a quick sync between late ones starts the count again`() {
        val guard = InkGpuGuard()
        var now = 0L
        fun sync(ms: Long) {
            assertTrue(guard.tryBegin(now))
            now += ms
            guard.end(now)
            now += 10
        }
        sync(3_000)
        sync(3_000)
        sync(20)
        sync(3_000)
        sync(3_000)
        assertFalse(guard.resting(now))
        sync(3_000)
        assertTrue(guard.resting(now))
    }

    @Test
    fun `a sync that never returns skips every later sample and never throws`() {
        val guard = InkGpuGuard()
        assertTrue(guard.tryBegin(0))
        for (t in 100L..600_000L step 100L) assertFalse(guard.tryBegin(t))
    }

    @Test
    fun `a guard taken but never posted is given back without a judgement`() {
        val guard = InkGpuGuard(stuckMs = 10, strikes = 1)
        assertTrue(guard.tryBegin(0))
        guard.abandon()
        assertTrue(guard.tryBegin(1_000))
        guard.end(1_001)
        assertFalse(guard.resting(1_001))
    }
}
