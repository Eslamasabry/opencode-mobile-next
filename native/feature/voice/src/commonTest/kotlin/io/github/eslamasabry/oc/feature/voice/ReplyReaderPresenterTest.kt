package io.github.eslamasabry.oc.feature.voice

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.feature.voice.presentation.ReadNote
import io.github.eslamasabry.oc.feature.voice.presentation.ReplyReaderPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechProse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ReplyReaderPresenterTest {
    @Test
    fun `B-MEDIA-009 the first reading asks once and nothing reaches the engine before consent`() = runTest {
        val hub = FakeHub(reader = FakeReader(consent = false))
        val presenter = ReplyReaderPresenter(hub, backgroundScope)
        runCurrent()
        presenter.read("row-1", "The tests pass.")
        runCurrent()
        assertTrue(presenter.state.value.asking)
        assertTrue(hub.reader.reads.isEmpty())
        presenter.consent()
        runCurrent()
        assertEquals("The tests pass.", hub.reader.reads.single().text)
        assertEquals("row-1", presenter.state.value.reading)
        presenter.stop()
        runCurrent()
        assertNull(presenter.state.value.reading)

        presenter.read("row-2", "Again.")
        runCurrent()
        assertFalse(presenter.state.value.asking, "asked once per phone")
        assertEquals(2, hub.reader.reads.size)
    }

    @Test
    fun `B-MEDIA-009 Not now reads nothing and remembers nothing`() = runTest {
        val hub = FakeHub(reader = FakeReader(consent = false))
        val presenter = ReplyReaderPresenter(hub, backgroundScope)
        runCurrent()
        presenter.read("row-1", "Hello")
        presenter.declineConsent()
        runCurrent()
        assertTrue(hub.reader.reads.isEmpty())
        assertFalse(hub.reader.consent)
    }

    @Test
    fun `B-MEDIA-009 a reading failure is a plain note`() = runTest {
        val hub = FakeHub(reader = FakeReader(consent = true))
        hub.reader.readResult = Failure.Media(MediaProblem.NoOfflineVoice).failed()
        val presenter = ReplyReaderPresenter(hub, backgroundScope)
        runCurrent()
        presenter.read("row-1", "Hello there")
        runCurrent()
        assertEquals(ReadNote.Failed(MediaProblem.NoOfflineVoice), presenter.state.value.note)
    }

    @Test
    fun `B-CHAT-060 a reply that is only code has no prose to read`() = runTest {
        val hub = FakeHub(reader = FakeReader(consent = true))
        val presenter = ReplyReaderPresenter(hub, backgroundScope)
        runCurrent()
        presenter.read("row-1", "```kotlin\nval x = 1\n```")
        runCurrent()
        assertEquals(ReadNote.NoProse, presenter.state.value.note)
        assertTrue(hub.reader.reads.isEmpty())
    }

    @Test
    fun `B-CHAT-060 a four-backtick example holding a three-backtick block is skipped whole`() {
        val markdown = "Here is how to quote code:\n\n````markdown\n```bash\nexport TOKEN=secret\n```\nstill inside\n````\n\nDone."
        val spoken = SpeechProse.of(markdown)
        assertEquals("Here is how to quote code:\nDone.", spoken)
        assertTrue("TOKEN" !in spoken && "still inside" !in spoken)
    }

    @Test
    fun `B-CHAT-060 speech prose drops fences, tables and link targets`() {
        val markdown =
            """
            # Done
            I fixed **the crash** in `Main.kt`. See [the docs](https://example.com/docs).

            ```bash
            ./gradlew test
            ```

            | file | lines |
            |---|---|
            | a.kt | 3 |

            - first step
            - second step
            """.trimIndent()
        val prose = SpeechProse.of(markdown)
        assertEquals("Done\nI fixed the crash in Main.kt. See the docs.\nfirst step\nsecond step", prose)
        assertFalse("https" in prose)
        assertFalse("gradlew" in prose)
        assertFalse("|" in prose)
    }
}
