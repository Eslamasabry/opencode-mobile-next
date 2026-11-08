package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackgroundWorkResult
import io.github.eslamasabry.oc.core.model.BackgroundWorkSupport
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.BackgroundWork
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S30 story 3: Prompt tools › Background, as Flutter's `foregroundBackgroundableParts` and `_backgroundRunningWork`. */
class BackgroundWorkTest {
    private fun tool(id: String, name: String, status: ToolStatus = ToolStatus.Running, input: JsonObject = JsonObject(emptyMap())) =
        Part(
            id = id, messageId = "a1", sessionId = "s", type = PartType.Tool, rawType = "tool", toolName = name,
            tool = ToolState(status = status, input = input),
        )

    private fun reply(vararg parts: Part, done: Boolean = false) =
        MessageWithParts(
            MessageInfo("a1", "s", Role.Assistant, time = MsgTime(1L, if (done) 2L else null)),
            persistentListOf(*parts),
        )

    @Test
    fun `S30-CHAT-003 a running subagent task can go to the background, shells only where the server backgrounds them`() {
        val messages = listOf(reply(tool("t", "task"), tool("sh", "shell"), tool("r", "read")))
        assertEquals(listOf("t"), BackgroundWork.parts(messages, BackgroundWorkSupport.Subagents).map { it.id })
        assertEquals(listOf("t", "sh"), BackgroundWork.parts(messages, BackgroundWorkSupport.SubagentsAndShells).map { it.id })
        assertEquals(emptyList(), BackgroundWork.parts(messages, BackgroundWorkSupport.Unavailable))
    }

    @Test
    fun `S30-CHAT-003 finished replies, finished steps and work already in the background are left out`() {
        val bg = JsonObject(mapOf("background" to JsonPrimitive(true)))
        assertEquals(emptyList(), BackgroundWork.parts(listOf(reply(tool("t", "task"), done = true)), BackgroundWorkSupport.Subagents))
        assertEquals(emptyList(), BackgroundWork.parts(listOf(reply(tool("t", "task", ToolStatus.Completed))), BackgroundWorkSupport.Subagents))
        assertEquals(emptyList(), BackgroundWork.parts(listOf(reply(tool("t", "task", input = bg))), BackgroundWorkSupport.Subagents))
    }

    private class World(support: BackgroundWorkSupport) : FakeChatWorld(), TurnFeedSource {
        override val turnFeed = MutableStateFlow(TurnFeed())
        var support: Outcome<BackgroundWorkSupport> = Outcome.Ok(support)
        var result: Outcome<BackgroundWorkResult> = Outcome.Ok(BackgroundWorkResult.Requested)

        override suspend fun backgroundWorkSupport(): Outcome<BackgroundWorkSupport> = support

        override suspend fun backgroundWork(): Outcome<BackgroundWorkResult> = result.also { calls += "background" }
    }

    private fun running(world: World) {
        world.transcript.value = stateOf(run = RunFacts(busy = true))
        world.turnFeed.value = TurnFeed(ledger = LedgerSnapshot(1, listOf(reply(tool("t", "task"))), emptyList()), busy = true)
    }

    @Test
    fun `S30-CHAT-003 Background is offered while a reply runs a subagent the server can background, and sends once`() {
        val world = World(BackgroundWorkSupport.Subagents)
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        Immediate.launch { p.loadBackgroundSupport() }
        assertFalse(p.current().canBackground, "nothing runs yet")
        running(world)
        assertTrue(p.current().canBackground)
        p.backgroundWork()
        assertEquals(1, world.calls.count { it == "background" })
        assertFalse(p.current().canBackground, "the same work is not offered twice")
        p.backgroundWork()
        assertEquals(1, world.calls.count { it == "background" })
    }

    @Test
    fun `S30-CHAT-003 a server that backgrounds nothing never offers Background`() {
        val world = World(BackgroundWorkSupport.Unavailable)
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        Immediate.launch { p.loadBackgroundSupport() }
        running(world)
        assertFalse(p.current().canBackground)
        p.backgroundWork()
        assertTrue("background" !in world.calls)
    }
}
