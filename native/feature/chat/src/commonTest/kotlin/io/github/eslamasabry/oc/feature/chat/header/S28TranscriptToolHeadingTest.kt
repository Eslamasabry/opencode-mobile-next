package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolPhrase
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptCopy
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * S28 story 13 (Flutter `transcriptToolHeading`): Copy transcript names a tool call in words, as its step row does,
 * never by its raw id; without the words it keeps the call's own title.
 */
class S28TranscriptToolHeadingTest {
    private val messages =
        listOf(
            MessageWithParts(
                MessageInfo("a1", "s", Role.Assistant),
                persistentListOf(
                    Part(
                        "tl", "a1", "s", PartType.Tool, "tool",
                        toolName = "bash",
                        tool = ToolState(ToolStatus.Completed, output = "ok"),
                    )
                ),
            )
        )

    @Test
    fun `B-CHAT-S28-30 a tool heading uses the step row's words for the call`() {
        val words =
            TranscriptWords(
                "Chat", "User", "Assistant", "Reasoning", "Only loaded",
                toolTitle = { f -> if (f.phrase == ToolPhrase.Shell) "Shell" else "Other" },
            )
        val copy = TranscriptCopy.markdownOf("Chat", messages, words, partial = false, mask = { it })
        assertTrue("### Tool: Shell" in copy, copy)
        assertFalse("### Tool: bash" in copy, copy)
    }

    @Test
    fun `B-CHAT-S28-31 without words the heading keeps the call's name`() {
        val words = TranscriptWords("Chat", "User", "Assistant", "Reasoning", "Only loaded")
        val copy = TranscriptCopy.markdownOf("Chat", messages, words, partial = false, mask = { it })
        assertTrue("### Tool: bash" in copy, copy)
    }
}
