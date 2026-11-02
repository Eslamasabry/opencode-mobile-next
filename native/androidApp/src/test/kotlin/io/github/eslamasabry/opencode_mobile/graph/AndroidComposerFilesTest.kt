package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Bounded-input and single-flight regressions for the Android file picker (full-composer slice
 * 2026-11-02). Catalog rows B-CHAT-010 and B-STATE-101 are the referenced rows; the launcher, metadata query
 * and real provider reads are covered by the coordinator's emulator pass.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidComposerFilesTest {
    // ---- BoundedRead (B-CHAT-010)

    /** B-CHAT-010 a stream bigger than the limit is refused as too large, closed, and not slurped. */
    @Test
    fun boundedReadRefusesBiggerStreamWithoutSlurping() {
        val limit = 10L
        val stream = CountingStream(ByteArray(1024 * 1024) { (it % 251).toByte() })
        val tooLarge = kotlin.test.assertFailsWith<DocumentRead.TooLarge> { DocumentRead.atMost(stream, limit) }
        assertTrue(stream.closed, "the provider stream must be closed on overflow")
        assertTrue(stream.consumed <= limit + 1, "read only the overflow byte, got ${stream.consumed}")
        val failure = tooLarge.asReadFailure()
        val media = assertIs<Failure.Media>(failure)
        assertEquals(MediaProblem.AttachmentTooLarge, media.problem)
        assertNull(media.details, "no exception text rides along")
    }

    /** B-CHAT-010 a file that fits is returned byte for byte, closed, and an empty file stays empty. */
    @Test
    fun boundedReadReturnsExactlyWhatFits() {
        val fits = ByteArray(64) { (it * 7).toByte() }
        val exact = CountingStream(fits.copyOf(10))
        assertContentEquals(fits.copyOf(10), DocumentRead.atMost(exact, 10L))
        assertTrue(exact.closed)
        val shorterBytes = ByteArray(9) { (it * 7).toByte() }
        val shorter = CountingStream(shorterBytes)
        assertContentEquals(shorterBytes, DocumentRead.atMost(shorter, 10L))
        assertTrue(shorter.closed)
        val empty = CountingStream(ByteArray(0))
        assertTrue(DocumentRead.atMost(empty, 10L).isEmpty())
        assertTrue(empty.closed)
    }

    /** B-CHAT-010 one byte past the limit already reads as too large. */
    @Test
    fun boundedReadDetectsOneBytePast() {
        val limit = 5L
        val stream = CountingStream(ByteArray(limit.toInt() + 1) { 1 })
        kotlin.test.assertFailsWith<DocumentRead.TooLarge> { DocumentRead.atMost(stream, limit) }
        assertTrue(stream.closed)
    }

    /** B-CHAT-010 read failures are typed and carry no message, path or exception text. */
    @Test
    fun readFailuresAreTypedAndSilent() {
        assertEquals(MediaProblem.AttachmentContentMissing, IOException("secret path").asReadFailure().mediaProblem)
        assertEquals(
            MediaProblem.AttachmentContentMissing,
            SecurityException("provider detail").asReadFailure().mediaProblem,
        )
        assertIs<Failure.Device>(RuntimeException("anything").asReadFailure())
        val all = listOf(IOException("p"), SecurityException("q"), RuntimeException("r")).map { it.asReadFailure() }
        assertTrue(all.all { it.details == null }, "no failure carries technical text")
    }

    private val Failure.mediaProblem: MediaProblem
        get() = assertIs<Failure.Media>(this).problem

    // ---- PickRequests (B-STATE-101..009)

    /** B-STATE-101 only one pick is pending at a time; a resolved slot frees the next one. */
    @Test
    fun onlyOnePickIsPendingAtATime() {
        val requests = PickRequests<String>()
        val first = requests.begin()
        assertTrue(first != null)
        assertNull(requests.begin(), "a second pick while one is open is refused, not queued")
        requests.resolve(first!!, "picked")
        assertEquals("picked", first.getCompleted())
        assertTrue(requests.begin() != null, "the slot is free after a result")
    }

    /** B-STATE-101 a null result is the cancel path: completed with null, slot freed. */
    @Test
    fun nullResultMeansCancelled() {
        val requests = PickRequests<String>()
        val request = requests.begin()!!
        requests.resolve(request, null)
        assertNull(request.getCompleted())
        assertTrue(requests.begin() != null)
    }

    /** B-STATE-101 the host going away resolves an open pick as cancelled, never hung. */
    @Test
    fun detachingCancelsTheOpenPick() {
        val requests = PickRequests<String>()
        val request = requests.begin()!!
        requests.cancelActive()
        assertNull(request.getCompleted(), "cancellation is preserved as a null completion")
        assertTrue(requests.begin() != null)
    }

    /** B-STATE-101 a late result for a retired request is dropped, never replayed onto a newer one. */
    @Test
    fun lateResultForRetiredRequestIsDropped() {
        val requests = PickRequests<String>()
        val old = requests.begin()!!
        assertTrue(requests.retire(old), "the cancelled await retires its own request")
        assertNull(requests.begin(), "an abandoned system request still owns the slot")
        requests.resolve(old, "stale document")
        val next = requests.begin()!!
        assertFalse(next.isCompleted, "a stale result must not resolve the newer pick")
        requests.resolve(next, "fresh document")
        assertEquals("fresh document", next.getCompleted())
    }

    /** B-STATE-101 a result delivered after detach cannot complete the retired pick either. */
    @Test
    fun resultAfterDetachChangesNothing() {
        val requests = PickRequests<String>()
        val request = requests.begin()!!
        requests.cancelActive()
        requests.resolve(request, "late document")
        assertNull(request.getCompleted(), "detach's cancellation stands")
    }
}

/** An in-memory stream that counts what was consumed and whether the reader closed it. */
private class CountingStream(data: ByteArray) : InputStream() {
    private val source = ByteArrayInputStream(data)
    var closed = false
        private set

    var consumed = 0
        private set

    override fun read(): Int {
        val byte = source.read()
        if (byte >= 0) consumed++
        return byte
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        if (count > 0) consumed += count
        return count
    }

    override fun close() {
        closed = true
        source.close()
    }
}
