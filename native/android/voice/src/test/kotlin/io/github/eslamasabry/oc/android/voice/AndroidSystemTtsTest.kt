package io.github.eslamasabry.oc.android.voice

import android.speech.tts.TextToSpeech
import io.github.eslamasabry.oc.android.voice.tts.AndroidSystemTts
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSystemTtsTest {
    @Test
    fun `B-MEDIA-009 leaving the app while the voice starts does not break later readings`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val listeners = mutableListOf<TextToSpeech.OnInitListener>()
        val made = mutableListOf<TextToSpeech>()
        val tts =
            AndroidSystemTts(context, UnconfinedTestDispatcher(testScheduler)) { c, listener ->
                listeners += listener
                TextToSpeech(c) {}.also { made += it }
            }
        // The first reading is cancelled (the app went to the background) before the engine says it is ready.
        val first = launch { tts.voices() }
        runCurrent()
        first.cancel()
        runCurrent()
        assertTrue(shadowOf(made[0]).isShutdown)
        // The next reading starts a new engine instead of answering "busy" until the process restarts.
        val second = async { tts.voices() }
        runCurrent()
        listeners[1].onInit(TextToSpeech.SUCCESS)
        val result = second.await()
        if (result is Outcome.Failed) assertNotEquals(MediaProblem.ReadAloudBusy, (result.failure as? Failure.Media)?.problem)
    }
}
