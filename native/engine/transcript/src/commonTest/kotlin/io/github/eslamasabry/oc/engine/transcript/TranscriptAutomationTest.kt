package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class TranscriptAutomationTest {
    @Test
    fun `B-AUTO-008 prompt row carries automation name and run while ordinary prompt stays null`() {
        val model = TranscriptModel(S, TranscriptCounters())
        model.apply(GatewayEvent.MessageUpserted(MessageInfo("u1", S, Role.User, createdMs = 1L)))
        model.apply(
            GatewayEvent.PartUpserted(Part("text-0", "u1", S, PartType.Text, text = "Human prompt"))
        )
        model.apply(
            GatewayEvent.MessageUpserted(
                MessageInfo(
                    "u2",
                    S,
                    Role.User,
                    createdMs = 2L,
                    automationName = "CI check",
                    automationRun = 4,
                )
            )
        )
        model.apply(
            GatewayEvent.PartUpserted(
                Part("text-0", "u2", S, PartType.Text, text = "Check CI and summarize any failures")
            )
        )

        val prompts = model.derive(1L).rows.filterIsInstance<PromptRow>()
        val human = prompts.single { it.turnId == "u1" }
        val automated = prompts.single { it.turnId == "u2" }

        assertNull(human.automationName)
        assertNull(human.automationRun)
        assertEquals("CI check", automated.automationName)
        assertEquals(4, automated.automationRun)
    }

    @Test
    fun `B-AUTO-008 old wire MessageInfo without automation fields decodes cleanly`() {
        val raw = """{"id":"u1","sessionId":"s","role":"User","createdMs":1}"""
        val decoded = Json.decodeFromString(MessageInfo.serializer(), raw)
        assertNull(decoded.automationName)
        assertNull(decoded.automationRun)

        val encoded =
            Json.encodeToString(
                MessageInfo.serializer(),
                decoded.copy(automationName = "Nightly digest", automationRun = 2),
            )
        val roundTrip = Json.decodeFromString(MessageInfo.serializer(), encoded)
        assertEquals("Nightly digest", roundTrip.automationName)
        assertEquals(2, roundTrip.automationRun)
    }
}
