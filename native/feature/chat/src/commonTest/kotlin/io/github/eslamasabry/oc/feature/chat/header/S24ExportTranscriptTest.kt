package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderPresenter
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.world.TranscriptExport
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S24-CHAT story 8 (Flutter `_exportTranscript`): the ⋯ menu's Export transcript saves the same
 * masked Markdown as Copy transcript as `opencode-<id>.md`, and is offered only with a saver.
 */
class S24ExportTranscriptTest {
    private class Saver(var answer: Outcome<Boolean> = Outcome.Ok(true)) : TranscriptExport {
        val saved = mutableListOf<Pair<String, String>>()

        override suspend fun saveMarkdown(fileName: String, markdown: String): Outcome<Boolean> {
            saved += fileName to markdown
            return answer
        }
    }

    private val world = FakeChatWorld()
    private val words = TranscriptWords("OpenCode conversation", "User", "Assistant", "Reasoning", "Only loaded messages")
    private val mask: (String) -> String = { it.replace("sk-live-123456", "••••") }

    private fun presenter(saver: TranscriptExport?) =
        ChatHeaderPresenter(world, FakeHeaderPort(), RecordingNavigator(), Immediate, export = saver)

    @Test
    fun `B-CHAT-S24-80 Export is in the menu only when the app can save a file`() {
        assertFalse(HeaderAction.ExportTranscript in presenter(null).current().menu)
        val menu = presenter(Saver()).current().menu
        assertTrue(HeaderAction.ExportTranscript in menu)
        assertEquals(menu.indexOf(HeaderAction.CopyTranscript) + 1, menu.indexOf(HeaderAction.ExportTranscript))
    }

    @Test
    fun `B-CHAT-S24-81 the file is the masked transcript named after the conversation`() {
        world.transcript.value =
            stateOf(prompt("u1", "my key is sk-live-123456"), block("u1", "a1", "p1", 0, "printed sk-live-123456"))
        val saver = Saver()
        val p = presenter(saver)
        p.exportTranscript(words, mask)
        val (name, text) = saver.saved.single()
        assertEquals("opencode-s1.md", name)
        assertTrue(text.startsWith("# Fix the build"), text)
        assertTrue("## Assistant\n\nprinted ••••" in text, text)
        assertFalse("sk-live-123456" in text)
        assertNotNull(p.current().saved)
    }

    @Test
    fun `B-CHAT-S24-82 a cancelled save says nothing and a failed one says so`() {
        world.transcript.value = stateOf(block("u1", "a1", "p1", 0, "done"))
        val saver = Saver(Outcome.Ok(false))
        val p = presenter(saver)
        p.exportTranscript(words, mask)
        assertNull(p.current().saved)
        assertNull(p.current().failure)
        saver.answer = Outcome.Failed(Failure.Device())
        p.exportTranscript(words, mask)
        assertEquals(HeaderAction.ExportTranscript, p.current().failure?.action)
    }
}
