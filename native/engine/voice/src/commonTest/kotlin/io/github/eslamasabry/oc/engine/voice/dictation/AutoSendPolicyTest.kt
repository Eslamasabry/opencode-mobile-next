package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    @Test
    fun `B-MEDIA-017 the countdown shows 3, 2, 1 in the pill and sends at zero`() {
        val t0 = Instant.fromEpochMilliseconds(1_000_000)
        val countdown = AutoSendCountdown.startIfAllowed("Run the tests", stoppedBySilence = true, now = t0)!!
        assertEquals(3, countdown.secondsLeft(t0))
        assertEquals(3, countdown.secondsLeft(t0 + 1.milliseconds))
        assertEquals(2, countdown.secondsLeft(t0 + 1.seconds))
        assertEquals(1, countdown.secondsLeft(t0 + 2.5.seconds))
        assertEquals(0, countdown.secondsLeft(t0 + 3.seconds))
        assertEquals(AutoSendCountdown.Decision.Wait, countdown.decide(t0 + 2.seconds, "Run the tests", true, false))
        assertEquals(
            AutoSendCountdown.Decision.Send("Run the tests"),
            countdown.decide(t0 + 3.seconds, "Run the tests", foreground = true, sendInFlight = false),
        )
        assertEquals(
            AutoSendCountdown.Decision.Send("Run the tests"),
            countdown.decide(t0, "Run the tests", foreground = true, sendInFlight = false, sendNow = true),
        )
    }

    @Test
    fun `B-MEDIA-017 an edited field, the background or a send in flight cancels and keeps the words`() {
        val t0 = Instant.fromEpochMilliseconds(0)
        val countdown = AutoSendCountdown.startIfAllowed("Run the tests", true, t0)!!
        val end = t0 + 3.seconds
        assertEquals(AutoSendCountdown.Decision.Cancel, countdown.decide(end, "Run the tests!", true, false))
        assertEquals(AutoSendCountdown.Decision.Cancel, countdown.decide(end, "Run the tests", false, false))
        assertEquals(AutoSendCountdown.Decision.Cancel, countdown.decide(end, "Run the tests", true, true))
    }

    @Test
    fun `B-MEDIA-017 no countdown without a silence stop or for words that may not go out by themselves`() {
        val t0 = Instant.fromEpochMilliseconds(0)
        assertNull(AutoSendCountdown.startIfAllowed("Run the tests", stoppedBySilence = false, now = t0))
        assertNull(AutoSendCountdown.startIfAllowed("[BLANK_AUDIO]", stoppedBySilence = true, now = t0))
        assertNull(AutoSendCountdown.startIfAllowed("/undo", stoppedBySilence = true, now = t0))
    }
}
