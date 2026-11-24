package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RootfsSizeTest {
    @get:Rule val temp = TemporaryFolder()

    private class SettableClock(var time: Long = 1_000_000L) {
        fun now(): Long = time
    }

    @Test
    fun `B-LINUX-284 no second walk starts while one runs`() {
        val every = 60_000L
        val clock = SettableClock()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val meter =
            RootfsSizeMeter(
                walk = {
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    3220L
                },
                now = clock::now,
                everyMs = every,
            )
        meter.want(true)
        val answer = AtomicBoolean()
        val walker = Thread { answer.set(meter.measureIfDue(true)) }
        try {
            walker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            clock.time += 2 * every
            assertFalse(meter.measureIfDue(true))
            assertEquals(1L, calls.get().toLong())
        } finally {
            release.countDown()
            walker.join(5_000)
        }
        assertFalse(walker.isAlive)
        assertTrue(answer.get())
        assertEquals(3220L, meter.last())
    }

    @Test
    fun `B-LINUX-285 no walk runs when nothing asks for the size`() {
        val every = 60_000L
        val clock = SettableClock()
        val calls = AtomicInteger()
        val meter =
            RootfsSizeMeter(
                walk = {
                    calls.incrementAndGet()
                    3220L
                },
                now = clock::now,
                everyMs = every,
            )
        repeat(100) { read ->
            assertNull(meter.last())
            clock.time += 2 * every
            if (read % 10 == 0) assertFalse(meter.measureIfDue(true))
        }
        assertEquals(0L, calls.get().toLong())
        assertNull(meter.last())
        meter.want(true)
        meter.want(false)
        assertEquals(0L, calls.get().toLong())
        assertNull(meter.last())
    }

    private fun oldSizeOf(file: File): Long {
        val attrs =
            try {
                Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: IOException) {
                return 0
            }
        return when {
            attrs.isSymbolicLink -> 0L
            !file.isDirectory -> attrs.size()
            else -> file.listFiles()?.sumOf { oldSizeOf(it) } ?: 0L
        }
    }

    @Test
    fun `B-LINUX-286 the size matches the old function on a fixture tree with links`() {
        val root = temp.newFolder("root")
        val sub = File(root, "sub").apply { mkdirs() }
        val deep = File(sub, "deep").apply { mkdirs() }
        File(root, "empty").apply { mkdirs() }
        val a = File(root, "a.txt").apply { writeText("0123456789") }
        File(sub, "b.bin").apply { writeBytes(ByteArray(200)) }
        File(deep, "c.bin").apply { writeBytes(ByteArray(3000)) }
        val outside = temp.newFolder("outside")
        File(outside, "big.bin").apply { writeBytes(ByteArray(50_000)) }
        Files.createSymbolicLink(File(root, "rel-link").toPath(), Paths.get("a.txt"))
        Files.createSymbolicLink(File(root, "abs-link").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "dangling").toPath(), File(root, "gone").toPath())
        Files.createLink(File(sub, "hard-a").toPath(), a.toPath())

        val expected = oldSizeOf(root)
        assertEquals(3220L, expected)
        assertEquals(expected, RootfsWalk.size(root.toPath()))
        assertEquals(0L, oldSizeOf(File(root, "gone").toPath()))
        assertEquals(0L, RootfsWalk.size(File(root, "gone").toPath()))
        assertEquals(0L, RootfsWalk.size(File(root, "rel-link").toPath()))
    }
}
