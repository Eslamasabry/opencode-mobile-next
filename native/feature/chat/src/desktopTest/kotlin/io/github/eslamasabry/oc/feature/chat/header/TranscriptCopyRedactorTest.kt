package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptCopy
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E15-S05: Copy transcript through the app's real Redactor (the screen's `rememberKitMask`), with the fakes the
 * go/no-go on build 3062b719a found in clear. Fakes are built from adjacent literals; messages never print a value.
 */
class TranscriptCopyRedactorTest {
    private val words = TranscriptWords("Untitled", "You", "Assistant", "Reasoning", "Only loaded history")
    private val project = "sk-proj-" + "abcdefghijklmnopqrstuvwxyz" + "0123456789ABCD"
    private val github = "ghp_" + "aBcDeFgHiJkLmNoPqRsTuVwXyZ" + "0123456789"
    private val redactor = Redactor()
    private val mask: (String) -> String = redactor::redact

    private val reply =
        "Here are the fake keys:\n\n- `$project`\n- `$github`\n- `OPENAI_API_KEY=$project`\n\n" +
            "```bash\nexport GITHUB_TOKEN=$github\n```"

    private fun assertNoFakes(copy: String) {
        assertFalse("abcdefghijklmnopqrstuvwxyz" in copy, "the sk-proj key is in clear")
        assertFalse("aBcDeFgHiJkLmNoPqRsTuVwXyZ" in copy, "the ghp_ key is in clear")
        assertEquals(0, copy.count { it == '`' } % 2, "backticks stay paired")
        assertTrue("- `sk-proj-•••`" in copy && "- `OPENAI_API_KEY=•••`" in copy, "inline code keeps its backticks")
    }

    @Test
    fun `B-SEC-145 a reply, its reasoning and a tool's output are masked with Markdown intact`() {
        fun part(id: String, type: PartType, text: String = "", tool: ToolState? = null) =
            Part(id, "a1", "s", type, type.name.lowercase(), text, toolName = "bash".takeIf { tool != null }, tool = tool)
        val messages =
            listOf(
                MessageWithParts(MessageInfo("u1", "s", Role.User), persistentListOf(part("u-t", PartType.Text, "show me"))),
                MessageWithParts(
                    MessageInfo("a1", "s", Role.Assistant, errorText = "failed with $github"),
                    persistentListOf(
                        part("a-r", PartType.Reasoning, "I will print $project"),
                        part("a-t", PartType.Tool, tool = ToolState(ToolStatus.Completed, title = "env", output = "GH_TOKEN=$github")),
                        part("a-x", PartType.Text, reply),
                    ),
                ),
            )
        val copy = TranscriptCopy.markdownOf("Keys $project", messages, words, partial = false, mask = mask)
        assertNoFakes(copy)
        assertTrue("```bash\nexport GITHUB_TOKEN=•••\n```" in copy, "the fenced block keeps its fences")
    }

    @Test
    fun `B-SEC-145 the display-rows copy masks the same reply`() {
        val rows =
            listOf(BlockRow("b1", "t", RowKind.Code, "p", 0, reply, live = false, open = false, origin = Origin.Loaded))
        assertNoFakes(TranscriptCopy.markdown(null, rows, words, partial = false, mask = mask))
    }
}
