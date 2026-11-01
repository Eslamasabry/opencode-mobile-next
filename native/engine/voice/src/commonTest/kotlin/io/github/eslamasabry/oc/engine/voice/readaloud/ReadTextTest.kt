package io.github.eslamasabry.oc.engine.voice.readaloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadTextTest {
    @Test
    fun `B-MEDIA-005 a reply's language follows its script, then the app`() {
        assertEquals("en", ReadLanguage.detect("Hello there, how are you?", "en"))
        assertEquals("ar", ReadLanguage.detect("مرحبا بك في التطبيق", "en"))
        assertEquals("en", ReadLanguage.detect("Hello there", "ar"))
        assertEquals("es", ReadLanguage.detect("Hola, ¿cómo estás?", "es"))
        assertEquals("other", ReadLanguage.detect("你好,世界", "en"))
        assertEquals("ar", ReadLanguage.detect("12345", "ar"))
        assertEquals("pt", ReadLanguage.detect("Olá", "pt-BR"))
        assertEquals("en", ReadLanguage.detect("Hello", "ja_JP"))
        assertEquals("en", ReadLanguage.detect("Hello", null))
    }

    @Test
    fun `B-MEDIA-005 text is cut into sentences a model reads in one go`() {
        assertEquals(listOf("One.", "Two!", "Three?"), SentenceSplitter.split("One. Two! Three?"))
        val long = List(200) { "word" }.joinToString(" ")
        val pieces = SentenceSplitter.split(long, limit = 100)
        assertTrue(pieces.all { it.length <= 100 })
        assertEquals(long, pieces.joinToString(" "))
        assertEquals(listOf("a".repeat(280), "a".repeat(20)), SentenceSplitter.split("a".repeat(300)))
        assertEquals(listOf("Line one", "Line two"), SentenceSplitter.split("Line one\n\nLine two"))
    }

    @Test
    fun `B-MEDIA-018 the reading voice is language and region first, then language, else none`() {
        val voices = listOf(TtsVoice("ar", "ar", "ar-EG"), TtsVoice("gb", "gb", "en-GB"), TtsVoice("us", "us", "en_us"))
        assertEquals("us", VoicePick.forLocale(voices, "en-US")?.id)
        assertEquals("gb", VoicePick.forLocale(voices, "en-AU")?.id)
        assertEquals("gb", VoicePick.forLocale(voices, "en")?.id)
        assertEquals("ar", VoicePick.forLocale(voices, "ar_SA")?.id)
        assertNull(VoicePick.forLocale(voices, "fr-FR"))
        assertNull(VoicePick.forLocale(emptyList(), "en"))
    }

    @Test
    fun `B-MEDIA-002 the voice matches the full locale before the language and keeps an explicit choice`() {
        val voices = listOf(TtsVoice("en", "English", "en-US"), TtsVoice("ar-eg", "Arabic Egypt", "ar-EG"), TtsVoice("ar-ae", "Arabic UAE", "ar-AE"))
        assertEquals("ar-ae", VoicePick.choose(voices, "AR_ae")?.id)
        assertEquals("ar-eg", VoicePick.choose(voices, "ar-SA")?.id)
        assertNull(VoicePick.choose(voices, "fr-FR"))
        assertEquals("en", VoicePick.choose(voices, "ar-AE", explicitId = "en")?.id)
        assertNull(VoicePick.choose(emptyList(), "ar"))
    }
}
