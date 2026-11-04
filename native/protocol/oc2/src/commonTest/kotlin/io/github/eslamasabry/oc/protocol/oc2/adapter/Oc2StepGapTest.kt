package io.github.eslamasabry.oc.protocol.oc2.adapter

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ports test/chat_oc2_step_gap_test.dart:228 (a finished step is not a finished OpenCode 2 run). */
class Oc2StepGapTest {
    private val adapter = Oc2EventAdapter(nowMs = { 1_000L })

    private fun v2(type: String, data: String): List<Oc2Adapted> {
        val body = Oc2Wire.json.parseToJsonElement(data).jsonObject
        val merged = JsonObject(mapOf("sessionID" to JsonPrimitive("ses_x")) + body)
        return adapter.adapt(Oc2Envelope.decode(Oc2Wire.json.parseToJsonElement("""{"type":"$type","data":$merged}""")))
    }

    @Test
    fun `B-PROTO-040 a finished step is not a finished run, the execution end is`() {
        assertTrue(v2("session.execution.started", "{}").any { it is Oc2Adapted.RunStarted })
        assertTrue(v2("session.step.started", """{"assistantMessageID":"msg_a1","agent":"build"}""").any { it is Oc2Adapted.RunStarted })
        val stepEnd = v2("session.step.ended", """{"assistantMessageID":"msg_a1","finish":"tool-calls"}""")
        assertTrue(stepEnd.none { it is Oc2Adapted.RunEnded }, "between two steps the run is still running")
        // The next step keeps it running.
        val next = v2("session.step.started", """{"assistantMessageID":"msg_a2","agent":"build"}""")
        assertTrue(next.none { it is Oc2Adapted.RunEnded })
        val end = v2("session.execution.succeeded", "{}").filterIsInstance<Oc2Adapted.RunEnded>().single()
        assertEquals(Oc2RunOutcome.Succeeded, end.outcome)
    }
}
