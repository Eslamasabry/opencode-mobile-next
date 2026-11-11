package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DockFeedTest {
    private val p = ProfileId("p1")
    private val s = SessionId("s1")
    private val ref = ConversationRef(p, s)

    private fun feed(vararg events: GatewayEvent): DockUi =
        events.flatMap { DockFeed.events(p, it) }.fold(DockModel.empty()) { m, e -> m.reduce(e) }.ui

    private fun todo(vararg statuses: String, name: String = "todowrite"): Part =
        Part(
            id = "p", messageId = "m", sessionId = s.raw, type = PartType.Tool, rawType = "tool", toolName = name,
            tool = ToolState(
                status = ToolStatus.Completed,
                input = JsonObject(mapOf("todos" to JsonArray(statuses.map { JsonObject(mapOf("content" to JsonPrimitive("t"), "status" to JsonPrimitive(it))) }))),
            ),
        )

    private fun part(type: PartType): Part = Part(id = "p", messageId = "m", sessionId = s.raw, type = type, rawType = type.name)

    private fun permission(session: SessionId = s, id: String = "r1") =
        PermissionRequest(RequestId(id), session, "edit", persistentListOf(), JsonObject(emptyMap()), persistentListOf(), null, false, null)

    @Test
    fun `task progress counts completed of everything not cancelled`() {
        assertEquals(1 to 3, DockFeed.taskProgress(todo("completed", "pending", "in_progress", "cancelled")))
        assertEquals(2 to 2, DockFeed.taskProgress(todo("completed", "completed")))
        assertEquals(0 to 0, DockFeed.taskProgress(todo("cancelled")))
        assertEquals(1 to 1, DockFeed.taskProgress(todo("completed", name = "todo")))
        assertNull(DockFeed.taskProgress(todo("completed", name = "bash")))
        assertNull(DockFeed.taskProgress(part(PartType.Text)))
    }

    @Test
    fun `gateway events drive the rings`() {
        assertEquals(DockState.Working, feed(GatewayEvent.RunStarted(s)).entries.single().state)
        assertEquals(
            DockState.WorkingWithTasks(1, 2),
            feed(GatewayEvent.RunStarted(s), GatewayEvent.PartUpserted(todo("completed", "pending"))).entries.single().state,
        )
        assertEquals(
            DockState.Thinking,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.PartUpserted(part(PartType.Reasoning))).entries.single().state,
        )
        assertEquals(
            DockState.Working,
            feed(
                GatewayEvent.RunStarted(s),
                GatewayEvent.PartUpserted(part(PartType.Reasoning)),
                GatewayEvent.PartDelta(s, io.github.eslamasabry.oc.core.common.ids.MessageId("m"), io.github.eslamasabry.oc.core.common.ids.PartId("p"), DeltaField.Text, "x"),
            ).entries.single().state,
            "a text delta after reasoning ends the thinking",
        )
        assertEquals(
            DockState.NeedsYou,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.PermissionAsked(permission())).entries.single().state,
        )
        assertEquals(
            DockState.Working,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.PermissionAsked(permission()), GatewayEvent.PermissionSettled(s, RequestId("r1"))).entries.single().state,
        )
        val q = PendingQuestion(RequestId("r1"), s, persistentListOf())
        assertEquals(
            DockState.Working,
            feed(
                GatewayEvent.RunStarted(s), GatewayEvent.PermissionAsked(permission()), GatewayEvent.QuestionAsked(q),
                GatewayEvent.PermissionSettled(s, RequestId("r1")), GatewayEvent.QuestionSettled(s, RequestId("r1")),
            ).entries.single().state,
            "a permission and a question with one id are two requests",
        )
        assertEquals(
            DockState.Failed,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.RunEnded(s, RunOutcome.Failed(null, WireTag("x"), null))).entries.single().state,
        )
        assertEquals(
            DockState.Finished,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.RunEnded(s, RunOutcome.Idle)).entries.single().state,
        )
        assertEquals(
            DockState.Idle,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.RunEnded(s, RunOutcome.Interrupted(InterruptReason.User))).entries.single().state,
            "a stop is a quiet recent ring",
        )
        assertEquals(0, feed(GatewayEvent.RunStarted(s), GatewayEvent.SessionDeleted(s)).entries.size)
    }

    @Test
    fun `the session record names the ring and a rename follows`() {
        val titled = feed(
            GatewayEvent.SessionUpserted(Session(s, title = "Bank API", agent = "build")),
            GatewayEvent.RunStarted(s),
            GatewayEvent.SessionChanged(s, "Bank API v2", null, null, false),
        ).entries.single()
        assertEquals("Bank API v2", titled.title)
        assertEquals("build", titled.agent)
    }

    @Test
    fun `a global form belongs to no conversation`() {
        val global = FormSpec(FormId("f"), SessionId("global"), null, null, persistentListOf())
        assertEquals(emptyList(), DockFeed.events(p, GatewayEvent.FormCreated(global)))
        val mine = FormSpec(FormId("f"), s, null, null, persistentListOf())
        assertEquals(DockState.NeedsYou, feed(GatewayEvent.RunStarted(s), GatewayEvent.FormCreated(mine)).entries.single().state)
        assertEquals(
            DockState.Working,
            feed(GatewayEvent.RunStarted(s), GatewayEvent.FormCreated(mine), GatewayEvent.FormSettled(s, FormId("f"), true)).entries.single().state,
        )
    }

    @Test
    fun `a failed request read says nothing and a good one lists every kind`() {
        assertNull(DockFeed.requestsRead(p, null, emptyList()))
        assertNull(DockFeed.requestsRead(p, emptyList(), null))
        val read = assertNotNull(
            DockFeed.requestsRead(
                p, listOf(permission()), listOf(PendingQuestion(RequestId("q"), s, persistentListOf())),
                listOf(FormSpec(FormId("f"), s, null, null, persistentListOf()), FormSpec(FormId("g"), SessionId("global"), null, null, persistentListOf())),
            ),
        )
        assertEquals(setOf("permission:r1", "question:q", "form:f"), read.bySession.getValue(s))
        assertEquals(1, read.bySession.size)
    }

    @Test
    fun `only busy and retrying sessions count as busy in a status read`() {
        val read = DockFeed.statusesRead(
            p,
            mapOf(SessionId("a") to SessionStatus.Busy, SessionId("b") to SessionStatus.Retry, SessionId("c") to SessionStatus.Idle, SessionId("d") to SessionStatus("later")),
        )
        assertEquals(setOf(SessionId("a"), SessionId("b")), read.busy)
    }
}
