package io.github.eslamasabry.oc.android.camera.scan

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What one analysed frame came to. Carries no decoded text (SEC-2). */
public enum class FrameVerdict {
    /** A stale start, or the session already latched: the frame was not decoded. */
    Ignored,
    NoCode,

    /** Not a pairing code; the host shows "That QR is not a pairing code" once per sighting. */
    Rejected,

    /** The same wrong code, still in view (or back within 2 s): nothing new to say. */
    RejectedQuietly,
    Accepted,
}

/** One camera start. A frame from an older start (the camera was paused or stopped since) is ignored. */
@JvmInline
public value class ScanAttempt(public val id: Int)

/**
 * The scanner's rules, free of CameraX (plan 10 §14.2):
 * - **Latch:** the first text [parse] accepts stops analysis, [releaseCamera] runs, and only then [onLatched] hears of
 *   it; queued frames are ignored, so the host pops once.
 * - A rejected text is reported once per sighting: not again until it has been out of view for [quiet].
 * - Start attempts are counted, so a late start or frame after a pause is ignored.
 * - **Never keep the value:** the text goes to [parse] and nowhere else; only SHA-256 digests of rejected texts are
 *   remembered, and nothing is logged.
 *
 * [analyze] runs on the one analysis thread; [begin] and [end] on the main thread.
 */
@OptIn(ExperimentalTime::class)
public class ScanSession<F, T : Any>(
    private val decoder: QrFrameDecoder<F>,
    private val parse: (String) -> T?,
    private val clock: Clock,
    private val releaseCamera: () -> Unit,
    private val onLatched: (T) -> Unit,
    private val onRejected: () -> Unit = {},
    private val quiet: Duration = 2.seconds,
) {
    private val attempts = AtomicInteger(0)
    private val running = AtomicBoolean(false)
    private val latch = AtomicBoolean(false)
    private val rejected = RejectMemory()

    public val latched: Boolean get() = latch.get()

    public fun begin(): ScanAttempt {
        val attempt = ScanAttempt(attempts.incrementAndGet())
        running.set(true)
        return attempt
    }

    /** ON_PAUSE, ON_STOP, dispose: every frame of the current start is ignored from now on. */
    public fun end() {
        running.set(false)
        attempts.incrementAndGet()
    }

    public fun isCurrent(attempt: ScanAttempt): Boolean = running.get() && attempts.get() == attempt.id

    public fun analyze(attempt: ScanAttempt, frame: F): FrameVerdict {
        if (latch.get() || !isCurrent(attempt)) return FrameVerdict.Ignored
        val text = decoder.decode(frame) ?: return FrameVerdict.NoCode
        val value = parseQuietly(text)
        if (value == null) {
            val first = rejected.firstSighting(digest(text), clock.now(), quiet)
            if (first) onRejected()
            return if (first) FrameVerdict.Rejected else FrameVerdict.RejectedQuietly
        }
        if (!latch.compareAndSet(false, true) || !isCurrent(attempt)) return FrameVerdict.Ignored
        running.set(false)
        try {
            releaseCamera()
        } catch (_: RuntimeException) {
            // The camera is the host's to close on pop as well; the pairing must still reach it.
        }
        onLatched(value)
        return FrameVerdict.Accepted
    }

    private fun parseQuietly(text: String): T? =
        try {
            parse(text)
        } catch (_: RuntimeException) {
            null // a parser bug is a rejected code, never a crash on the camera thread
        }

    override fun toString(): String = "ScanSession(latched=${latch.get()})"

    private companion object {
        fun digest(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

/** Digest → last time seen. Bounded: a crowd of wrong codes cannot grow it. Touched only from the analysis thread. */
@OptIn(ExperimentalTime::class)
private class RejectMemory(private val capacity: Int = 16) {
    private val lastSeen = LinkedHashMap<String, Instant>()

    fun firstSighting(digest: String, now: Instant, quiet: Duration): Boolean {
        val previous = lastSeen.remove(digest)
        lastSeen[digest] = now
        while (lastSeen.size > capacity) lastSeen.remove(lastSeen.keys.first())
        return previous == null || now - previous >= quiet
    }
}
