package io.github.eslamasabry.oc.engine.voice.language

import io.github.eslamasabry.oc.engine.voice.state.PackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceLanguageTest {
    /** The seven languages the app is translated to (plan 10 §19.2). */
    private val appLanguages = listOf("en", "ar", "es", "ja", "pt", "ru", "zh")

    @Test
    fun `B-MEDIA-028 Same as the app follows every language the app is translated to`() {
        for (code in appLanguages) {
            val resolved = VoiceLanguage.resolve(VoiceLanguage.Auto, code)
            assertTrue(resolved.isNamed, code)
            assertEquals(code, resolved.whisperCode)
        }
    }

    @Test
    fun `B-MEDIA-028 a language the model lacks falls back to listening for it`() {
        assertEquals(VoiceLanguage.Detect, VoiceLanguage.resolve(VoiceLanguage.Auto, "tlh"))
        assertEquals(VoiceLanguage.Detect, VoiceLanguage.resolve(VoiceLanguage.Auto, null))
        assertEquals(VoiceLanguage.Detect, VoiceLanguage.resolve(VoiceLanguage.Auto, ""))
    }

    @Test
    fun `B-MEDIA-028 older and regional codes are folded in`() {
        assertEquals(VoiceLanguage.Hebrew, VoiceLanguage.forLanguageCode("iw"))
        assertEquals(VoiceLanguage.Norwegian, VoiceLanguage.forLanguageCode("nb"))
        assertEquals(VoiceLanguage.Norwegian, VoiceLanguage.forLanguageCode("nn"))
        assertEquals(VoiceLanguage.Indonesian, VoiceLanguage.forLanguageCode("in"))
        assertEquals(VoiceLanguage.Chinese, VoiceLanguage.forLanguageCode("ZH"))
    }

    @Test
    fun `B-MEDIA-028 a named language wins whatever the app speaks`() {
        assertEquals(VoiceLanguage.French, VoiceLanguage.resolve(VoiceLanguage.French, "ar"))
        assertEquals(VoiceLanguage.Detect, VoiceLanguage.resolve(VoiceLanguage.Detect, "ar"))
    }

    @Test
    fun `B-MEDIA-028 36 choices, more than English and Arabic, with unique codes`() {
        assertEquals(36, VoiceLanguage.entries.size)
        val named = VoiceLanguage.entries.filter { it.isNamed }
        assertEquals(34, named.size)
        assertEquals(named.size, named.map { it.whisperCode }.toSet().size)
    }

    @Test
    fun `B-MEDIA-028 the old stored choices keep their meaning`() {
        assertEquals("ar", VoiceLanguage.fromStoredId("arabic").whisperCode)
        assertEquals("en", VoiceLanguage.fromStoredId("english").whisperCode)
        assertEquals(VoiceLanguage.Auto, VoiceLanguage.fromStoredId("auto"))
        assertEquals(VoiceLanguage.Detect, VoiceLanguage.fromStoredId("detect"))
        assertEquals(VoiceLanguage.Auto, VoiceLanguage.fromStoredId("nonsense"))
        assertEquals(VoiceLanguage.Auto, VoiceLanguage.fromStoredId(null))
    }

    @Test
    fun `B-MEDIA-028 the recognizer is told the right language`() {
        assertEquals("ar", VoiceLanguage.Auto.recognizerCode("ar"))
        assertEquals("ja", VoiceLanguage.Auto.recognizerCode("ja"))
        assertEquals("", VoiceLanguage.Detect.recognizerCode("ar"))
        assertEquals("es", VoiceLanguage.Spanish.recognizerCode("ar"))
    }

    @Test
    fun `B-MEDIA-029 a note says plainly when the app language is unknown offline`() {
        assertEquals(
            VoiceLanguageNote.UnavailableOffline("tlh"),
            VoiceLanguageNote.of(VoiceLanguage.Auto, PackId("base"), "tlh"),
        )
    }

    @Test
    fun `B-MEDIA-029 the note says nothing when it works`() {
        assertNull(VoiceLanguageNote.of(VoiceLanguage.Auto, PackId("base"), "ar"))
        assertNull(VoiceLanguageNote.of(VoiceLanguage.English, PackId("tiny"), "ar"))
        assertNull(VoiceLanguageNote.of(VoiceLanguage.Detect, PackId("tiny"), "ar"))
        assertNull(VoiceLanguageNote.of(VoiceLanguage.Auto, PackId("base"), ""))
    }

    @Test
    fun `B-MEDIA-029 the Compact model warns it is rough outside English`() {
        assertEquals(
            VoiceLanguageNote.CompactRough(VoiceLanguage.Arabic),
            VoiceLanguageNote.of(VoiceLanguage.Auto, PackId("tiny"), "ar"),
        )
        assertEquals(
            VoiceLanguageNote.CompactRough(VoiceLanguage.French),
            VoiceLanguageNote.of(VoiceLanguage.French, PackId("tiny"), "en"),
        )
    }
}
