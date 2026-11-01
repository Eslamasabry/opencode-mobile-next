package io.github.eslamasabry.oc.engine.voice.audio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SilenceDetectorTest {
    private val span = 128.milliseconds

    @Test
    fun `B-MEDIA-015 quiet before any speech never ends the recording`() {
        val detector = SilenceDetector()
        repeat(100) { assertFalse(detector.add(0.001f, span)) }
    }

    @Test
    fun `B-MEDIA-015 quiet after speech ends the recording after 1_5 s and not a moment sooner`() {
        val detector = SilenceDetector()
        repeat(8) { assertFalse(detector.add(0.1f, span)) }
        repeat(11) { assertFalse(detector.add(0.002f, span), "buffer ${it + 1}") } // 1.408 s
        assertTrue(detector.add(0.002f, span)) // 1.536 s
    }

    @Test
    fun `B-MEDIA-015 speaking again restarts the quiet`() {
        val detector = SilenceDetector()
        repeat(8) { detector.add(0.1f, span) }
        repeat(10) { detector.add(0.002f, span) }
        assertFalse(detector.add(0.1f, span))
        repeat(11) { assertFalse(detector.add(0.002f, span)) }
    }

    @Test
    fun `B-MEDIA-015 a click is not speech`() {
        val detector = SilenceDetector()
        detector.add(0.2f, span)
        repeat(100) { assertFalse(detector.add(0.001f, span)) }
    }
}
