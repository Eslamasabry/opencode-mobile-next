package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class FooterModelIdTest {
    @Test
    fun `B-523-007 a footer carries the modelId of the turn's newest assistant message`() {
        val model = TranscriptModel(S, TranscriptCounters())
        Ev.prompt("u1", "Hello").forEach(model::apply)
        model.apply(GatewayEvent.RunStarted(S))
        model.apply(
            GatewayEvent.MessageUpserted(
                MessageInfo("a1", S, Role.Assistant, createdMs = 1L, providerId = "prov", modelId = "m1")
            )
        )
        model.apply(
            GatewayEvent.MessageUpserted(
                MessageInfo(
                    "a2",
                    S,
                    Role.Assistant,
                    createdMs = 2L,
                    completedMs = 5L,
                    providerId = "prov",
                    modelId = "m2",
                )
            )
        )
        model.apply(GatewayEvent.RunEnded(S, RunOutcome.Succeeded))

        val footer = model.derive(1L).rows.filterIsInstance<FooterRow>().single()
        assertEquals("prov", footer.providerId)
        assertEquals("m2", footer.modelId)
    }

    @Test
    fun `old wire MessageInfo without modelId still decodes with null modelId`() {
        val raw = """{"id":"a1","sessionId":"s","role":"Assistant","createdMs":1,"providerId":"prov"}"""
        val decoded = Json.decodeFromString(MessageInfo.serializer(), raw)
        assertEquals("a1", decoded.id)
        assertEquals("prov", decoded.providerId)
        assertNull(decoded.modelId)

        val encoded = Json.encodeToString(MessageInfo.serializer(), decoded.copy(modelId = "m2"))
        assertEquals("m2", Json.decodeFromString(MessageInfo.serializer(), encoded).modelId)
    }
}
