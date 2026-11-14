package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.feature.chat.presentation.StepRequestReading
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

private val denySid get() = Ref.sessionId.raw

internal fun runningEditFeed(): io.github.eslamasabry.oc.feature.chat.world.TurnFeed {
    val k = TurnFeedKeeper(denySid)
    val user =
        MessageWithParts(
            MessageInfo("m1", denySid, Role.User, time = MsgTime(500, null)),
            listOf(Part("p1", "m1", denySid, PartType.Text, "text", text = "edit it")).toImmutableList(),
        )
    val edit =
        Part(
            "t1", "m2", denySid, PartType.Tool, "tool",
            callId = "call-1", toolName = "edit",
            tool = ToolState(ToolStatus.Running, startedAtMs = 1_000),
        )
    val reply =
        MessageWithParts(
            MessageInfo("m2", denySid, Role.Assistant, providerId = "a", modelId = "m", time = MsgTime(1_000, null)),
            listOf(edit).toImmutableList(),
        )
    k.history(listOf(user, reply))
    k.running(1_000)
    return k.feed.value
}

internal fun permissionCard(state: CardState): RequestCardUi.Permission {
    val request =
        PermissionRequest(
            RequestId("r1"), SessionId(denySid), "edit", persistentListOf("a.txt"), JsonObject(emptyMap()),
            persistentListOf("*"), PermissionTool(MessageId("m2"), "call-1"), true, null,
        )
    return RequestCardUi.Permission(
        id = request.id, waitingSinceMs = 0, state = state, ask = PermissionAsk.of(request),
        canAlwaysAllow = false, captured = PendingRequest.Permission(request),
    )
}


/** After Deny or Allow the step that asked answers at once, not when the server finishes the tool call. */
class DenyReadsAtOnceTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    @Test
    fun `A2-01 inside the Undo hold a Deny already reads declined, with no running step`() {
        val reading = StepRequestReading.of(listOf(permissionCard(CardState.Held(AnswerWords.Rejected))))
        assertEquals(setOf("call-1"), reading.declined)
        assertEquals(false, reading.waiting)
        val facts = TurnFactsPresenter(FixedClock).present(runningEditFeed(), 58_000, reading.waiting, reading.declined)
        val work = assertNotNull(facts.work["m1"])
        assertEquals(StepState.Declined, work.steps.single().state)
        assertNull(work.steps.single().durationMs)
        assertNull(work.now, "the live line must not name an editing step any more")
        assertEquals(0, work.counts.edited)
    }

    @Test
    fun `A2-01 Undo puts the step back to waiting`() {
        val held = StepRequestReading.of(listOf(permissionCard(CardState.Held(AnswerWords.RejectedWith("use b.txt")))))
        assertEquals(setOf("call-1"), held.declined)
        val back = StepRequestReading.of(listOf(permissionCard(CardState.Waiting)))
        assertEquals(emptySet(), back.declined)
        assertEquals(true, back.waiting)
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(runningEditFeed(), 5_000, back.waiting, back.declined).work["m1"])
        assertEquals(WorkLineState.WaitingForYou, work.state)
        assertEquals(StepState.Running, work.steps.single().state)
    }

    @Test
    fun `A2-01 sending and sent Deny stay declined, and a refused one waits again`() {
        for (s in listOf(CardState.Sending(AnswerWords.Rejected), CardState.Sent(AnswerWords.Rejected))) {
            assertEquals(setOf("call-1"), StepRequestReading.of(listOf(permissionCard(s))).declined, "$s")
        }
        val refused = StepRequestReading.of(listOf(permissionCard(CardState.NotAccepted(AnswerWords.Rejected))))
        assertEquals(emptySet(), refused.declined)
        assertEquals(true, refused.waiting)
    }

    @Test
    fun `A2-02 Allow stops saying waiting for you at once and the step runs`() {
        val reading = StepRequestReading.of(listOf(permissionCard(CardState.Held(AnswerWords.Allowed))))
        assertEquals(false, reading.waiting)
        assertEquals(emptySet(), reading.declined)
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(runningEditFeed(), 5_000, reading.waiting, reading.declined).work["m1"])
        assertEquals(WorkLineState.Running, work.state)
        assertEquals(StepState.Running, work.steps.single().state)
    }

    @Test
    fun `A2-01 the server's own refusal replaces the local reading with the same state`() {
        val k = TurnFeedKeeper(sid)
        k.history(
            listOf(
                MessageWithParts(
                    MessageInfo("m1", sid, Role.User, time = MsgTime(500, null)),
                    listOf(Part("p1", "m1", sid, PartType.Text, "text", text = "edit it")).toImmutableList(),
                ),
                MessageWithParts(
                    MessageInfo("m2", sid, Role.Assistant, providerId = "a", modelId = "m", time = MsgTime(1_000, null)),
                    listOf(
                        Part(
                            "t1", "m2", sid, PartType.Tool, "tool", callId = "call-1", toolName = "edit",
                            tool = ToolState(ToolStatus.Error, output = "The user declined this tool call", startedAtMs = 1_000, completedAtMs = 59_000),
                        ),
                    ).toImmutableList(),
                ),
            ),
        )
        k.running(1_000)
        val local = TurnFactsPresenter(FixedClock).present(runningEditFeed(), 58_000, false, setOf("call-1")).work["m1"]!!
        val server = TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000, false, emptySet()).work["m1"]!!
        assertEquals(local.steps.single().state, server.steps.single().state)
        assertEquals(local.state, server.state)
        assertEquals(local.counts, server.counts)
    }
}
