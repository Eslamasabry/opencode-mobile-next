package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** B-LINUX-617: behavioral guarantees for OsFileDescriptors and NativeComponentUpdateRecovery.AndroidFs. */
class OsFileDescriptorsTest {
    @Test
    fun `B-LINUX-617 useFd closes FileDescriptor on normal completion and on exception`() {
        val fd = FileDescriptor()
        val closed = mutableListOf<FileDescriptor>()
        val result = OsFileDescriptors.useFd(fd, close = { closed += it }) { "ok" }
        assertEquals("ok", result)
        assertEquals(1, closed.size)
        assertSame(fd, closed.single())

        val closedOnError = mutableListOf<FileDescriptor>()
        val failure =
            assertThrows(IllegalStateException::class.java) {
                OsFileDescriptors.useFd(fd, close = { closedOnError += it }) {
                    throw IllegalStateException("boom")
                }
            }
        assertEquals("boom", failure.message)
        assertEquals(1, closedOnError.size)
        assertSame(fd, closedOnError.single())
    }

    @Test
    fun `B-LINUX-617 useInputStream and useOutputStream close FileDescriptor on normal and error paths`() {
        val dir = Files.createTempDirectory("os-fd-test-").toFile()
        try {
            val file = File(dir, "sample.txt")
            val writeClosed = mutableListOf<FileDescriptor>()
            FileOutputStream(file).use { raw ->
                val rawFd = raw.fd
                OsFileDescriptors.useOutputStream(rawFd, close = { writeClosed += it }) { out ->
                    out.write("hello".toByteArray(Charsets.UTF_8))
                }
            }
            assertEquals(1, writeClosed.size)

            val readClosed = mutableListOf<FileDescriptor>()
            val text =
                FileInputStream(file).use { raw ->
                    OsFileDescriptors.useInputStream(raw.fd, close = { readClosed += it }) { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }
            assertEquals("hello", text)
            assertEquals(1, readClosed.size)

            val errorClosed = mutableListOf<FileDescriptor>()
            assertThrows(IOException::class.java) {
                FileInputStream(file).use { raw ->
                    OsFileDescriptors.useInputStream(raw.fd, close = { errorClosed += it }) {
                        throw IOException("read failed")
                    }
                }
            }
            assertEquals(1, errorClosed.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `B-LINUX-617 receiptHandle closes FileDescriptor once and closes on stream creation failure`() {
        val fd = FileDescriptor()
        var fdCloses = 0
        var streamCloses = 0
        val sampleStat = NativeComponentUpdateRecovery.Stat(NativeComponentUpdateRecovery.Kind.REGULAR, 1L, 2L, 3L)
        val handle =
            NativeComponentUpdateRecovery.AndroidFs.receiptHandle(
                descriptor = fd,
                statOf = { sampleStat },
                streamOf = {
                    object : ByteArrayInputStream("new".toByteArray()) {
                        override fun close() {
                            streamCloses++
                            super.close()
                        }
                    }
                },
                closeFd = { fdCloses++ },
            )
        assertArrayEquals("new".toByteArray(), handle.read(4))
        assertEquals(sampleStat, handle.stat())
        handle.close()
        handle.close()
        assertEquals("stream must close once", 1, streamCloses)
        assertEquals("FileDescriptor must close exactly once (no double close)", 1, fdCloses)

        var failedCreationCloses = 0
        assertThrows(IOException::class.java) {
            NativeComponentUpdateRecovery.AndroidFs.receiptHandle(
                descriptor = fd,
                statOf = { sampleStat },
                streamOf = { throw IOException("stream init failed") },
                closeFd = { failedCreationCloses++ },
            )
        }
        assertEquals("FileDescriptor must close if stream creation throws", 1, failedCreationCloses)
    }
}
