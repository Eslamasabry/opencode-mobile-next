package io.github.eslamasabry.oc.engine.voice.dictation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnDeviceSpeechTest {
    @Test
    fun `S1 dictation runs only where an on-device recognizer exists`() {
        assertTrue(OnDeviceSpeech.usable(31) { true })
        assertTrue(OnDeviceSpeech.usable(36) { true })
        assertFalse(OnDeviceSpeech.usable(36) { false })
        var asked = false
        assertFalse(OnDeviceSpeech.usable(30) { asked = true; true })
        assertFalse(asked) // below API 31 the on-device check is not even made
    }

}
