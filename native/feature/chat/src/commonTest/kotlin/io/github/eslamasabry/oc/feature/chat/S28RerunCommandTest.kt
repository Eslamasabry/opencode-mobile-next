package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.feature.chat.ui.rerunCommand
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * S28 story 6 (Flutter `tool_card_body.dart` `_shellRerun`, `chat_commands.dart` `_rerunShellCommand`): a finished
 * shell step offers "Run this command again", which opens the Run shell command dialog with that command in it.
 */
class S28RerunCommandTest {
    private val input = JsonObject(mapOf("command" to JsonPrimitive("npm test -- --watch=false")))

    @Test
    fun `B-CHAT-S28-10 a finished or failed shell step offers its whole command again`() {
        assertEquals("npm test -- --watch=false", rerunCommand(ToolKind.Shell, StepState.Done, input, "npm test"))
        assertEquals("npm test -- --watch=false", rerunCommand(ToolKind.Shell, StepState.Failed, input, null))
        assertEquals("ls", rerunCommand(ToolKind.Shell, StepState.Done, JsonObject(emptyMap()), "ls"))
    }

    @Test
    fun `B-CHAT-S28-11 a running, stopped or not-shell step offers nothing`() {
        assertNull(rerunCommand(ToolKind.Shell, StepState.Running, input, null))
        assertNull(rerunCommand(ToolKind.Shell, StepState.Pending, input, null))
        assertNull(rerunCommand(ToolKind.Shell, StepState.NotRun, input, null))
        assertNull(rerunCommand(ToolKind.Read, StepState.Done, input, null))
        assertNull(rerunCommand(ToolKind.Shell, StepState.Done, JsonObject(emptyMap()), " "))
    }

    @Test
    fun `B-CHAT-S28-12 Run this command again opens the dialog with the command, only where the shell is on`() {
        val world = FakeChatWorld()
        val work = ChatWorkModel(world) {}
        world.capabilities.value = ServerCapabilities(conversationShell = false)
        work.openShellDialog("npm test")
        assertNull(work.state.value.dialog)
        world.capabilities.value = ServerCapabilities(terminal = true, conversationShell = true)
        work.openShellDialog("npm test")
        assertEquals("npm test", work.state.value.dialog?.initial)
    }
}
