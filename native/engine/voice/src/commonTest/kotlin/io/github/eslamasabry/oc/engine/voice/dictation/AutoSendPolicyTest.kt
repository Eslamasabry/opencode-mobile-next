package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSendPolicyTest {
    @Test
    fun `B-MEDIA-016 nothing, a stray sound and the model's notes are not sent`() {
        for (words in listOf("", "   ", "a", "ا", "[BLANK_AUDIO]", "(music)", "♪ ♪", "[ Silence ] .", "...", "*sigh*")) {
            assertFalse(mayAutoSend(words), "\"$words\"")
        }
    }

    @Test
    fun `B-MEDIA-016 real words are sent, in any script`() {
        for (words in listOf("ok", "Run the tests", "شغّل الاختبارات", "[BLANK_AUDIO] fix the build", "42", "𝐀𝐁")) {
            assertTrue(mayAutoSend(words), "\"$words\"")
        }
    }

    @Test
    fun `B-MEDIA-016 a spoken slash or bang is never run as a command`() {
        assertFalse(mayAutoSend("/new session"))
        assertFalse(mayAutoSend("  !rm -rf build"))
        assertTrue(isSendableTranscript("/new session"))
    }

    @Test
    fun `B-MEDIA-016 two letters are counted as code points, not UTF-16 units`() {
        assertFalse(isSendableTranscript("𝐀")) // one code point, two UTF-16 units
        assertEquals(1, codePointCount("𝐀"))
    }

    @Test
    fun `B-MEDIA-017 only a tap dictation with the setting on stops by silence`() {
        assertFalse(stopsOnSilence(VoiceMode.Tap, autoSend = false)) // off by default: a long pause never stops it
        assertTrue(stopsOnSilence(VoiceMode.Tap, autoSend = true))
        assertFalse(stopsOnSilence(VoiceMode.Hold, autoSend = true))
        assertFalse(stopsOnSilence(VoiceMode.Conversation, autoSend = true))
    }
}
