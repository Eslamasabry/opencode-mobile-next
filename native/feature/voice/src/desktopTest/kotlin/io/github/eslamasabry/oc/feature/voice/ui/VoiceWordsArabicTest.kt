package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ports test/voice_model_localization_test.dart's translation check (B-L10N-024). */
@OptIn(ExperimentalTestApi::class)
class VoiceWordsArabicTest {
    private var saved: Locale = Locale.getDefault()

    @BeforeTest
    fun arabic() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(saved)
    }

    @Test
    fun `B-L10N-024 voice model presentation and categorized failures are translated without changing data`() = runComposeUiTest {
        val pack = PackId("base")
        setContent {
            Column {
                BasicText(packName(pack))
                BasicText(languageName(VoiceLanguage.Detect))
                BasicText(problemWords(MediaProblem.MicDeniedForever))
                BasicText(problemWords(MediaProblem.DownloadHttp))
                BasicText(problemWords(MediaProblem.NoAudioCaptured))
            }
        }
        onNodeWithText("متوازن").assertExists()
        onNodeWithText("تعرّف على اللغة من الكلام").assertExists()
        onNodeWithText("إعدادات", substring = true).assertExists()
        onNodeWithText("رفض", substring = true).assertExists()
        onNodeWithText("لم يُلتقط أي صوت.").assertExists()
        // The data under the words is untouched.
        assertEquals("base", pack.raw)
    }
}
