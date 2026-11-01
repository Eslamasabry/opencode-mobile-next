package io.github.eslamasabry.oc.android.camera.capture

import io.github.eslamasabry.oc.android.camera.scan.FakeClock
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Instant

class CaptureFilesTest {
    private val filesDir: File = Files.createTempDirectory("capture").toFile()
    private val files = CaptureFiles(filesDir)

    @AfterTest
    fun cleanUp() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `B-MEDIA-008 a capture id is microseconds since the epoch, as the pending-photo record stores it`() {
        val clock = FakeClock(Instant.fromEpochSeconds(1_800_000_000, 123_456_789))
        assertEquals("1800000000123456", files.newCaptureId(clock))
        clock.advance(1.microseconds)
        assertEquals("1800000000123457", files.newCaptureId(clock))
    }

    @Test
    fun `B-MEDIA-008 the capture file lives in files camera-capture-v1 under its id`() {
        val f = files.fileFor("1800000000123456")!!
        assertEquals(File(filesDir, "camera-capture-v1/1800000000123456.jpg"), f)
        assertTrue(f.parentFile!!.isDirectory)
    }

    @Test
    fun `an id that is not plain digits never names a file`() {
        listOf("", "../x", "12/34", "1.jpg", "abc", "1".repeat(21), "١٢٣").forEach { assertNull(files.fileFor(it), it) }
    }

    @Test
    fun `B-MEDIA-008 a cancelled or failed capture leaves no file behind`() {
        val f = files.fileFor("42")!!
        f.writeBytes(byteArrayOf(1, 2, 3))
        files.discard("42")
        assertFalse(f.exists())
        files.discard("42") // idempotent
        files.discard("../../etc") // refused quietly
    }

    @Test
    fun `B-MEDIA-008 a capture that wrote nothing counts as no photo`() {
        val f = files.fileFor("7")!!
        assertFalse(files.hasPhoto("7"))
        f.writeBytes(ByteArray(0))
        assertFalse(files.hasPhoto("7"))
        f.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        assertTrue(files.hasPhoto("7"))
    }

    @Test
    fun `stale captures with no record are swept at start, the recorded one is kept`() {
        files.fileFor("1")!!.writeBytes(byteArrayOf(1))
        files.fileFor("2")!!.writeBytes(byteArrayOf(1))
        File(filesDir, "camera-capture-v1/odd.tmp").writeBytes(byteArrayOf(1))
        File(filesDir, "camera-capture-v1/sub").mkdirs()
        assertEquals(2, files.sweepStale(keep = setOf("2")))
        assertFalse(files.fileFor("1")!!.exists())
        assertTrue(files.fileFor("2")!!.exists())
        assertTrue(File(filesDir, "camera-capture-v1/sub").isDirectory)
        assertEquals(0, CaptureFiles(Files.createTempDirectory("empty").toFile()).sweepStale(emptySet()))
    }
}
