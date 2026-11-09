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
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter `running_work_sheet.dart` "Keep chatting while it runs" (`running-work-background`). */
class RunningWorkBackgroundTest {
    private class World(var support: BackgroundWorkSupport) : FakeChatWorld(), TurnFeedSource {
        override val turnFeed = MutableStateFlow(TurnFeed())
        var result: Outcome<BackgroundWorkResult> = Outcome.Ok(BackgroundWorkResult.Requested)

        override suspend fun backgroundWorkSupport(): Outcome<BackgroundWorkSupport> = Outcome.Ok(support)

        override suspend fun backgroundWork(): Outcome<BackgroundWorkResult> = result.also { calls += "background" }
    }

    private fun running(world: World) {
        val part =
            Part(
                id = "t", messageId = "a1", sessionId = "s", type = PartType.Tool, rawType = "tool", toolName = "task",
                tool = ToolState(status = ToolStatus.Running, input = JsonObject(emptyMap())),
            )
        val reply = MessageWithParts(MessageInfo("a1", "s", Role.Assistant, time = MsgTime(1L, null)), persistentListOf(part))
        world.transcript.value = stateOf(run = RunFacts(busy = true))
        world.turnFeed.value = TurnFeed(ledger = LedgerSnapshot(1, listOf(reply), emptyList()), busy = true)
    }

    @Test
    fun `B-CHAT-TASKS-001 the sheet offers Keep chatting only while a reply runs work the server can move`() {
        val world = World(BackgroundWorkSupport.Subagents)
        val work = ChatWorkModel(world, Immediate::launch)
        work.refresh()
        assertFalse(work.state.value.backgroundNotice, "nothing runs")
        running(world)
        work.refresh()
        assertTrue(work.state.value.canBackground)
        assertTrue(work.state.value.backgroundNotice)
    }

    @Test
    fun `B-CHAT-TASKS-002 asking sends once and the sheet then shows what the server said`() {
        val world = World(BackgroundWorkSupport.Subagents)
        val work = ChatWorkModel(world, Immediate::launch)
        running(world)
        work.refresh()
        work.runInBackground()
        assertEquals(1, world.calls.count { it == "background" })
        assertEquals(BackgroundWorkResult.Requested, work.state.value.backgroundResult)
        assertFalse(work.state.value.canBackground)
        work.runInBackground()
        assertEquals(1, world.calls.count { it == "background" })
        work.refresh()
        assertNull(work.state.value.backgroundResult, "a reopened sheet starts clean")
        assertFalse(work.state.value.canBackground, "the same work is not offered twice")
    }

    @Test
    fun `B-CHAT-TASKS-003 a server that backgrounds nothing never offers it`() {
        val world = World(BackgroundWorkSupport.Unavailable)
        val work = ChatWorkModel(world, Immediate::launch)
        running(world)
        work.refresh()
        assertFalse(work.state.value.backgroundNotice)
        work.runInBackground()
        assertTrue("background" !in world.calls)
    }

    @Test
    fun `B-CHAT-TASKS-004 a failed request keeps the offer and says it did not go through`() {
        val world = World(BackgroundWorkSupport.Subagents)
        world.result = Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("x")))
        val work = ChatWorkModel(world, Immediate::launch)
        running(world)
        work.refresh()
        work.runInBackground()
        assertTrue(work.state.value.actionFailed)
        assertTrue(work.state.value.canBackground)
        assertNull(work.state.value.backgroundResult)
    }
}
