package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Owner 2026-11-13 ("How many stopped we will show"): a turn the person stopped says "Stopped" once, in its footer.
 * The live chat built a second closing line from the run's own end (Interrupted by the person) while the reply's
 * aborted state already marks the footer stopped; reopened, only the footer said it. The turn's end state has one
 * source: the reply's own stopped state; the run's end adds "Stopped" only where no reply says it.
 */
class StoppedOnceTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun prompt() =
        MessageWithParts(
            MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)),
            listOf(Part("u1", "m1", sid, PartType.Text, "text", text = "write a long story about rivers", ended = true))
                .toImmutableList(),
        )

    private fun step(output: String) =
        Part(
            "t-read", "m2", sid, PartType.Tool, "tool", callId = "c-read", toolName = "read",
            tool = ToolState(
                ToolStatus.Error,
                input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/rivers.txt"))),
                output = output,
                startedAtMs = 2_000,
                completedAtMs = 3_000,
            ),
        )

    /** The reply the person stopped: the server marks it aborted and ends its running step. */
    private fun stoppedReply(vararg parts: Part) =
        MessageWithParts(
            MessageInfo(
                "m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle",
                time = MsgTime(1_100, 3_100), errorText = "The operation was aborted.", errorKind = MessageErrorKind.Aborted,
            ),
            parts.toList().toImmutableList(),
        )

    /** Live: the chat saw the run end because the person pressed Stop. */
    private fun live(page: List<MessageWithParts>): TurnFactsUi {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page, newest = true)
        keeper.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Interrupted(InterruptReason.User)), 4_000)
        return TurnFactsPresenter(FixedClock).present(keeper.feed.value, 5_000)
    }

    /** Reopened: the same messages read back, the run's end no longer known. */
    private fun reloaded(page: List<MessageWithParts>): TurnFactsUi {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page, newest = true)
        keeper.idle()
        return TurnFactsPresenter(FixedClock).present(keeper.feed.value, 5_000)
    }

    @Test
    fun `a stopped turn with one step adds no second closing line, live or reopened`() {
        val page = listOf(prompt(), stoppedReply(step("Tool execution aborted")))
        val live = live(page)
        val reloaded = reloaded(page)
        // The footer says "Stopped" (the reply is aborted); no closing line repeats it.
        assertNull(live.reading.endNotes["m1"], "live")
        assertEquals(reloaded.reading.endNotes["m1"], live.reading.endNotes["m1"], "live and reopened read the same")
        assertEquals(WorkLineState.Stopped, live.work["m1"]?.state)
        assertEquals(reloaded.work["m1"]?.state, live.work["m1"]?.state)
    }

    @Test
    fun `a stopped turn whose words had streamed keeps them and adds no closing line`() {
        val words = Part("x2", "m2", sid, PartType.Text, "text", text = "The Nile flows north.")
        val page = listOf(prompt(), stoppedReply(words))
        assertNull(live(page).reading.endNotes["m1"])
        assertNull(reloaded(page).reading.endNotes["m1"])
    }

    @Test
    fun `work that ended on a failure still opens and names it`() {
        val failed =
            MessageWithParts(
                MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(1_100, 3_100), finish = "stop"),
                listOf(step("ENOENT: no such file")).toImmutableList(),
            )
        val facts = reloaded(listOf(prompt(), failed))
        assertEquals(WorkLineState.EndedFailed, facts.work["m1"]?.state)
    }
}
