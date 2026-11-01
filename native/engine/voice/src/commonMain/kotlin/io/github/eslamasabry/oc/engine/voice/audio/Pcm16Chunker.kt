package io.github.eslamasabry.oc.engine.voice.audio

import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

/** 16 kHz mono PCM16 (plan 10 §6.1). */
public const val VOICE_SAMPLE_RATE: Int = 16_000

/** The recognizer hears 30 s at a time; there is no cap on the recording itself (P10.3). */
public val VOICE_CHUNK_DURATION: Duration = 30.seconds
public const val VOICE_CHUNK_SAMPLES: Int = VOICE_SAMPLE_RATE * 30

/** 16 kHz × 2 bytes: one millisecond of capture is 32 bytes, so a 4096-byte buffer is 128 ms. */
public const val PCM16_BYTES_PER_MS: Int = VOICE_SAMPLE_RATE * 2 / 1000

/**
 * Port of Dart `Pcm16Accumulator` (`lib/voice/audio.dart`): little-endian PCM16 into float samples (`signed / 32768`),
 * an odd trailing byte carried into the next buffer, [level] = RMS of the last buffer clamped 0..1, and [add] returning
 * how many bytes it took so a buffer crossing the chunk edge starts the next chunk (B-MEDIA-014).
 */
public class Pcm16Chunker(public val maximumSamples: Int = VOICE_CHUNK_SAMPLES) {
    private val samples = FloatArray(maximumSamples)
    private var pendingLowByte: Int = -1

    public var length: Int = 0
        private set

    public var level: Float = 0f
        private set

    public val isFull: Boolean get() = length >= maximumSamples

    public val duration: Duration get() = ((length.toLong() * 1_000_000L) / VOICE_SAMPLE_RATE).microseconds

    /** Adds bytes from [start] until the chunk is full; returns how many bytes it took (the rest is the next chunk's). */
    public fun add(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): Int {
        if (start >= end || isFull) return 0
        var index = start
        var sumSquares = 0.0
        var levelSamples = 0
        if (pendingLowByte >= 0) {
            val value = decode(pendingLowByte, bytes[index++].toInt() and 0xff)
            pendingLowByte = -1
            samples[length++] = value
            sumSquares += value * value
            levelSamples++
        }
        while (index + 1 < end && !isFull) {
            val value = decode(bytes[index].toInt() and 0xff, bytes[index + 1].toInt() and 0xff)
            index += 2
            samples[length++] = value
            sumSquares += value * value
            levelSamples++
        }
        if (!isFull && index < end) pendingLowByte = bytes[index++].toInt() and 0xff
        if (levelSamples > 0) level = sqrt(sumSquares / levelSamples).toFloat().coerceIn(0f, 1f)
        return index - start
    }

    /** The samples so far; the chunker is empty afterwards. */
    public fun takeSamples(): FloatArray {
        val result = samples.copyOf(length)
        clear()
        return result
    }

    public fun clear() {
        samples.fill(0f, 0, length)
        length = 0
        pendingLowByte = -1
        level = 0f
    }

    private fun decode(low: Int, high: Int): Float {
        var signed = low or (high shl 8)
        if (signed >= 0x8000) signed -= 0x10000
        return signed / 32768f
    }
}
