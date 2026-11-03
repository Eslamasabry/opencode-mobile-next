package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptCopy
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Codex chat-header #3, #6, #7: Copy transcript (B-CHAT-111, SEC-13). */
class TranscriptCopyTest {
    private val words = TranscriptWords("Untitled", "You", "Assistant", "Reasoning", "Only loaded history")
    private val pem = "-----BEGIN PRIVATE KEY-----"
    private val pemEnd = "-----END PRIVATE KEY-----"

    /** Stands for the app's redactor: a key needs BEGIN and END in the same text. */
    private val mask: (String) -> String = { text ->
        Regex("$pem[\\s\\S]*?$pemEnd").replace(text, "[private key]")
    }

    private fun block(i: Int, text: String, continued: Boolean, continues: Boolean) =
        BlockRow("b$i", "t", RowKind.Code, "p", i, text, live = false, open = false, origin = Origin.Loaded,
            continued = continued, continues = continues)

    @Test
    fun `B-CHAT-111 Codex header 3 a key split across display chunks is masked`() {
        val rows = listOf(
            // newest first
            block(2, "line17\n$pemEnd", continued = true, continues = false),
            block(1, "$pem\nsecretmaterial", continued = false, continues = true),
            PromptRow("u", "t", "show the key"),
        )
        val copy = TranscriptCopy.markdown(null, rows, words, partial = false, mask = mask)
        assertFalse("secretmaterial" in copy, copy)
        assertTrue("[private key]" in copy, copy)
    }

    @Test
    fun `B-CHAT-111 Codex header 7 a long code block keeps its line breaks at chunk seams`() {
        val rows = listOf(
            block(2, "val b = 2", continued = true, continues = false),
            block(1, "val a = 1", continued = false, continues = true),
        )
        val copy = TranscriptCopy.markdown(null, rows, words, partial = false, mask = { it })
        assertTrue("val a = 1\nval b = 2" in copy, copy)
    }

    @Test
    fun `B-CHAT-111 Codex header 6 the copy has tools, attachments, full reasoning and errors`() {
        fun part(id: String, type: PartType, text: String = "", tool: ToolState? = null, file: String? = null, name: String? = null) =
            Part(id, "a1", "s", type, type.name.lowercase(), text, toolName = name, tool = tool, filename = file)
        val messages = listOf(
            MessageWithParts(
                MessageInfo("u1", "s", Role.User),
                persistentListOf(part("u-t", PartType.Text, "read it"), part("u-f", PartType.File, file = "notes.txt")),
            ),
            MessageWithParts(
                MessageInfo("a1", "s", Role.Assistant, errorText = "Model timed out"),
                persistentListOf(
                    part("r", PartType.Reasoning, "first thought\nsecond thought"),
                    part("tl", PartType.Tool, name = "read", tool = ToolState(ToolStatus.Completed, title = "Read notes.txt", output = "hello file")),
                    part("t", PartType.Text, "Done."),
                ),
            ),
        )
        val copy = TranscriptCopy.markdownOf("Chat", messages, words, partial = false, mask = { it })
        assertTrue("- Attachment: notes.txt" in copy, copy)
        assertTrue("second thought" in copy, copy)
        assertTrue("### Tool: Read notes.txt" in copy && "hello file" in copy, copy)
        assertTrue("> Error: Model timed out" in copy, copy)
        assertTrue("read it" in copy && "Done." in copy, copy)
    }
}
