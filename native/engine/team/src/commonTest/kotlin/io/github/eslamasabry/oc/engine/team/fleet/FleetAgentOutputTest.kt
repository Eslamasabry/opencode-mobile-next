package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private object Now : Clock {
    override fun now(): Instant = Instant.parse("2026-11-11T10:00:00Z")
}

private val CONFIG = TeamConfig(TeamProvider.GasCity, url = "http://100.101.102.103:8372", city = "main")

/** The fake host, but a session's own stream answers the frames given and then a 404. */
private class SessionHost(private val inner: FakeGasCity, private val turns: List<FleetFrame>) : FleetTransports {
    val streams = mutableListOf<Pair<String, String?>>()

    override fun open(baseUrl: String): FleetTransport {
        val base = inner.open(baseUrl)
        return object : FleetTransport by base {
            override suspend fun stream(path: String, lastEventId: String?, stallMs: Long, onFrame: suspend (FleetFrame) -> Unit): FleetStreamEnd {
                if (!path.contains("/session/")) return base.stream(path, lastEventId, stallMs, onFrame)
                streams += path to lastEventId
                turns.forEach { onFrame(it) }
                return FleetStreamEnd.Refused(404)
            }
        }
    }
}

private fun turn(id: String, text: String) = FleetFrame("turn", id, """{"id":"s-1","turns":[{"role":"output","text":${quote(text)}}]}""")

private fun quote(text: String) = "\"" + text.replace("\n", "\\n") + "\""

class FleetAgentOutputTest {
    @Test
    fun a_chunk_already_inside_the_text_changes_nothing() {
        assertEquals("abc", FleetOutput.merge("abc", "bc"))
        assertEquals("abc", FleetOutput.merge("abc", ""))
    }

    @Test
    fun a_cumulative_transcript_appends_only_its_new_tail() {
        assertEquals("one\ntwo\nthree", FleetOutput.merge("one\ntwo", "two\nthree"))
    }

    @Test
    fun an_unrelated_chunk_is_appended_whole_and_the_text_is_kept_to_its_limit() {
        assertEquals("abcxyz", FleetOutput.merge("abc", "xyz"))
        assertEquals("cdef", FleetOutput.merge("abcd", "ef", limit = 4))
    }

    @Test
    fun only_a_turn_frame_with_text_is_output() {
        assertEquals("hello\nworld", FleetOutput.turnText(FleetFrame("turn", "1", """{"turns":[{"text":"hello"},{"text":""},{"text":"world"}]}""")))
        assertNull(FleetOutput.turnText(FleetFrame("heartbeat", null, """{"timestamp":"x"}""")))
        assertNull(FleetOutput.turnText(FleetFrame("turn", "2", """{"turns":[]}""")))
    }

    @Test
    fun the_output_flow_gives_the_growing_transcript_and_completes_when_the_host_says_gone() =
        runTest {
            val host = SessionHost(FakeGasCity(), listOf(turn("1", "step one"), turn("2", "step one\nstep two"), FleetFrame("heartbeat", null, "{}")))
            val fleet = FleetEngine(host, Now, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            val seen = fleet.agentOutput("p1", "s-1").toList()
            assertEquals(listOf("step one", "step one\nstep two"), seen)
            assertEquals("/v0/city/main/session/s-1/stream", host.streams.single().first)
        }

    @Test
    fun with_no_team_connected_the_output_flow_is_empty_and_ends() =
        runTest {
            val fleet = FleetEngine(FakeGasCity(), Now, newRequestId = { "req-1" })
            assertEquals(emptyList(), fleet.agentOutput("nobody", "s-1").toList())
        }
}
