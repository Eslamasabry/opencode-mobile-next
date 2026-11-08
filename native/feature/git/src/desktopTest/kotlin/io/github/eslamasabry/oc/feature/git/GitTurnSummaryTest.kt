package io.github.eslamasabry.oc.feature.git

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.feature.git.presentation.turnSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.toImmutableList

/** Flutter chat_top_bar.dart `_turnSummary`: what "Write it for me" puts in the commit message. */
class GitTurnSummaryTest {
    private var n = 0

    private fun message(role: Role, vararg parts: Pair<PartType, String>): MessageWithParts {
        val id = "m${n++}"
        return MessageWithParts(
            MessageInfo(id, "s", role),
            parts.map { (type, text) -> Part("p${n++}", id, "s", type, type.name.lowercase(), text) }.toImmutableList(),
        )
    }

    @Test
    fun `WALK-1108-G1 the last reply's first sentence, without Markdown marks`() {
        val messages =
            listOf(
                message(Role.Assistant, PartType.Text to "An older turn."),
                message(Role.User, PartType.Text to "add juice"),
                message(
                    Role.Assistant,
                    PartType.Reasoning to "thinking about it",
                    PartType.Text to "\n## **Added** a combo counter and `screen shake`. Then I ran the tests.",
                ),
            )
        assertEquals("Added a combo counter and screen shake.", turnSummary(messages))
    }

    @Test
    fun `WALK-1108-G1 a reply with no words is skipped, and none at all gives nothing`() {
        assertEquals(
            "Fixed it.",
            turnSummary(listOf(message(Role.Assistant, PartType.Text to "Fixed it."), message(Role.Assistant, PartType.Tool to ""))),
        )
        assertNull(turnSummary(listOf(message(Role.User, PartType.Text to "hello"))))
    }

    @Test
    fun `WALK-1108-G1 a long line is cut on a word at a commit subject's length`() {
        val long = "Moved the settings screen onto the new kit so every row lines up with the rest of the app now"
        val said = turnSummary(listOf(message(Role.Assistant, PartType.Text to long)))!!
        assertEquals("Moved the settings screen onto the new kit so every row lines up with…", said)
    }
}
