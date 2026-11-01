package io.github.eslamasabry.oc.engine.voice.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Pcm16ChunkerTest {
    @Test
    fun `B-MEDIA-014 PCM16 little-endian samples normalize correctly across split buffers`() {
        val pcm = Pcm16Chunker(maximumSamples = 4)
        pcm.add(bytes(0x00, 0x80, 0xff))
        pcm.add(bytes(0x7f, 0x00, 0x00, 0x00, 0xc0))
        val samples = pcm.takeSamples()
        assertEquals(4, samples.size)
        assertEquals(-1.0f, samples[0])
        assertEquals(32767f / 32768f, samples[1], 1e-7f)
        assertEquals(0.0f, samples[2])
        assertEquals(-0.5f, samples[3])
    }

    @Test
    fun `B-MEDIA-014 one chunk holds 30 seconds and says where the next one starts`() {
        val pcm = Pcm16Chunker()
        val bytes = ByteArray((VOICE_CHUNK_SAMPLES + 100) * 2)
        val taken = pcm.add(bytes)
        assertEquals(VOICE_CHUNK_SAMPLES * 2, taken)
        assertEquals(VOICE_CHUNK_SAMPLES, pcm.length)
        assertEquals(VOICE_CHUNK_DURATION, pcm.duration)
        assertTrue(pcm.isFull)
        assertEquals(0, pcm.add(bytes, taken))
        assertEquals(VOICE_CHUNK_SAMPLES, pcm.takeSamples().size)
        assertEquals(200, pcm.add(bytes, taken))
        assertEquals(100, pcm.length)
    }

    @Test
    fun `B-MEDIA-014 an odd byte at a chunk edge carries into the next chunk`() {
        val pcm = Pcm16Chunker(maximumSamples = 2)
        val bytes = bytes(0, 0, 0, 0x40, 0x00)
        val taken = pcm.add(bytes)
        assertEquals(4, taken)
        assertContentEquals(floatArrayOf(0.0f, 0.5f), pcm.takeSamples())
        assertEquals(1, pcm.add(bytes, taken))
        pcm.add(bytes(0xc0))
        assertContentEquals(floatArrayOf(-0.5f), pcm.takeSamples())
    }

    @Test
    fun `B-MEDIA-014 the level is the RMS of the newest buffer, clamped`() {
        val pcm = Pcm16Chunker()
        pcm.add(bytes(0x00, 0x40, 0x00, 0xc0)) // 0.5 and -0.5
        assertEquals(0.5f, pcm.level, 1e-6f)
        pcm.add(bytes(0x00, 0x00))
        assertEquals(0f, pcm.level)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
