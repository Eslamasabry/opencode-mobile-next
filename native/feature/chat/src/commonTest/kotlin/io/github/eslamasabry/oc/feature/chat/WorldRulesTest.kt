package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.SendGate
import io.github.eslamasabry.oc.feature.chat.world.P3Bridge
import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.github.eslamasabry.oc.core.domain.events.DeltaField as CoreDelta
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent as Core
import io.github.eslamasabry.oc.core.domain.events.NoticeKind as CoreNotice
import io.github.eslamasabry.oc.core.domain.events.RunOutcome as CoreOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo as CoreInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts as CoreMessage
import io.github.eslamasabry.oc.core.model.transcript.Part as CorePart
import io.github.eslamasabry.oc.core.model.transcript.PartType as CorePartType
import io.github.eslamasabry.oc.core.model.transcript.Role as CoreRole
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus as CoreTool

/** The slice-2 world's pure rules: the temporary core → P3 bridge and the composer's send gate. */
class WorldRulesTest {
    private val s1 = SessionId("s1")

    @Test
    fun `B-PROTO-015 core events reach the P3 transcript field for field, and other sessions never do`() {
        val bridge = P3Bridge("s1")
        assertEquals(GatewayEvent.RunStarted("s1"), bridge.event(Core.RunStarted(s1)))
        assertNull(bridge.event(Core.RunStarted(SessionId("other"))))
        assertEquals(
            GatewayEvent.PartDelta("s1", "m1", "p1", DeltaField.Text, "Hel"),
            bridge.event(Core.PartDelta(s1, MessageId("m1"), PartId("p1"), CoreDelta.Text, "Hel")),
        )
        assertEquals(GatewayEvent.TranscriptStale("s1"), bridge.event(Core.TranscriptStale(s1)))
        assertEquals(GatewayEvent.TranscriptStale("s1"), bridge.event(Core.SessionCompacted(s1)), "a compaction re-reads")
        val tool = CorePart("p2", "m1", "s1", CorePartType.Tool, rawType = "tool", toolName = "bash", tool = ToolState(CoreTool.Error))
        val mapped = (bridge.event(Core.PartUpserted(tool)) as GatewayEvent.PartUpserted).part
        assertEquals(PartType.Tool, mapped.type)
        assertEquals(ToolStatus.Error, mapped.toolStatus)
        assertEquals("bash", mapped.toolName)
        val snapshot = CorePart("p3", "m1", "s1", CorePartType.Snapshot, rawType = "snapshot")
        assertEquals(PartType.Unknown, (bridge.event(Core.PartUpserted(snapshot)) as GatewayEvent.PartUpserted).part.type)
    }

    @Test
    fun `B-CHAT-097 notices carry only their kind, and a session-level notice is never invented a message`() {
        val bridge = P3Bridge("s1")
        val notice = bridge.event(Core.Notice(s1, MessageId("m1"), CoreNotice.InstructionsUpdated)) as GatewayEvent.Notice
        assertEquals(NoticeKind.InstructionsUpdated, notice.kind)
        assertEquals("m1", notice.messageId)
        assertNull(bridge.event(Core.Notice(s1, null, CoreNotice.SkillChanged)))
        assertEquals(NoticeKind.Other, (bridge.event(Core.Notice(s1, MessageId("m1"), CoreNotice("note.changed"))) as GatewayEvent.Notice).kind)
    }

    @Test
    fun `INTEGRATE-W1 an idle never replaces the run's failure, and ends a clean run`() {
        val bridge = P3Bridge("s1")
        bridge.event(Core.RunStarted(s1))
        assertEquals(
            GatewayEvent.RunEnded("s1", RunOutcome.Failed("ProviderAuth")),
            bridge.event(Core.RunEnded(s1, CoreOutcome.Failed(io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind.ProviderAuth, null, null))),
        )
        assertNull(bridge.event(Core.RunEnded(s1, CoreOutcome.Idle)), "the failure stays the run's outcome")
        bridge.event(Core.RunStarted(s1))
        assertEquals(GatewayEvent.RunEnded("s1", RunOutcome.Succeeded), bridge.event(Core.RunEnded(s1, CoreOutcome.Idle)))
        assertEquals(
            GatewayEvent.RunEnded("s1", RunOutcome.Failed("rate_limited")),
            bridge.event(Core.RunEnded(s1, CoreOutcome.Failed(null, WireTag("rate_limited"), null))),
        )
        assertEquals(
            GatewayEvent.RunEnded("s1", RunOutcome.Interrupted("User")),
            bridge.event(Core.RunEnded(s1, CoreOutcome.Interrupted(InterruptReason.User))),
        )
    }

    @Test
    fun `B-PROTO-015 a history page keeps roles, times, finish and error text`() {
        val bridge = P3Bridge("s1")
        val page =
            CoreMessage(
                CoreInfo("m1", "s1", CoreRole.Assistant, time = MsgTime(10, 20), finish = "stop", errorText = "quota"),
                persistentListOf(CorePart("p1", "m1", "s1", CorePartType.Text, rawType = "text", text = "Hi", ended = true)),
            )
        val m = bridge.message(page)
        assertEquals(Role.Assistant, m.info.role)
        assertEquals(10L, m.info.createdMs)
        assertEquals(20L, m.info.completedMs)
        assertEquals("stop", m.info.finish)
        assertEquals("quota", m.info.errorText)
        assertEquals("Hi", m.parts.single().text)
        assertTrue(m.parts.single().ended)
    }

    @Test
    fun `09-4_2 one tap is one send, never blank, never while a send is in flight, never without a conversation`() {
        val ready = BasicComposerUi(available = true, sending = false, steers = false)
        assertTrue(SendGate.accepts(ready, "hello"))
        assertFalse(SendGate.accepts(ready, "  \n "))
        assertFalse(SendGate.accepts(ready.copy(sending = true), "hello"))
        assertFalse(SendGate.accepts(ready.copy(available = false), "hello"))
        assertTrue(SendGate.accepts(ready.copy(steers = true), "and also this"), "a send during a run steers it")
    }
}
