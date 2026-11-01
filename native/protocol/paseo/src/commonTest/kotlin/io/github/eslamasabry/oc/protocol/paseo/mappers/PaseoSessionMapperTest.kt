package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.SessionContextUse
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.plus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Ported from test/paseo_stale_working_test.dart (group paseoSessionStatus), paseo_title_clip_test.dart and the session shapes of paseo_gateway_test.dart. */
class PaseoSessionMapperTest {
    private val now = kotlin.time.Instant.parse("2026-10-20T12:00:00Z").toEpochMilliseconds()

    private fun iso(ms: Long): String = kotlin.time.Instant.fromEpochMilliseconds(ms).toString()

    private fun record(status: String, ageMs: Long): JsonObject = mapperAgent("a", status = status, loaded = false, updatedAt = iso(now - ageMs))

    @Test
    fun `B-AGENT-176 a record saying running is Working only while its last update is recent`() {
        assertEquals(SessionStatus.Idle, paseoSessionStatus(record("running", 2L * 24 * 3600 * 1000), now))
        assertEquals(SessionStatus.Idle, paseoSessionStatus(record("initializing", 3600_000L), now))
        assertEquals(SessionStatus.Busy, paseoSessionStatus(record("running", 60_000L), now))
        // The bound is the 3-minute grace, measured from the record's own update time.
        val fresh = record("running", PASEO_RECORD_BUSY_FRESH_FOR_MS - 300)
        assertEquals(SessionStatus.Busy, paseoSessionStatus(fresh, now))
        assertEquals(SessionStatus.Idle, paseoSessionStatus(fresh, now + 300))
        assertEquals(now + 300, paseoRecordBusyUntilMs(fresh))
        assertNull(paseoRecordBusyUntilMs(record("idle", 0)))
    }

    @Test
    fun `B-AGENT-177 a loaded agent is Working however old its record`() {
        val live = mapperAgent("a", status = "running", updatedAt = iso(now - 2L * 24 * 3600 * 1000))
        assertEquals(SessionStatus.Busy, paseoSessionStatus(live, now))
        assertNull(paseoRecordBusyUntilMs(live))
        assertEquals(SessionStatus.Busy, paseoSessionStatus(live + mapOf("activeTurn" to JsonNull), now))
    }

    @Test
    fun `B-AGENT-178 the status view of a loaded snapshot never expires, so a watched start stays Working over a stale record`() {
        // The mapper half of the row: a loaded snapshot (activeTurn key present) has no record bound.
        val loaded = mapperAgent("a", status = "initializing", updatedAt = iso(now - 3600_000L)) +
            mapOf("activeTurn" to jsonOf("turnId" to "t"))
        assertEquals(SessionStatus.Busy, paseoSessionStatus(loaded, now))
        // A loaded agent initializing with no turn is the daemon loading it, not a run.
        val loading = mapperAgent("a", status = "initializing") + mapOf("activeTurn" to JsonNull)
        assertEquals(SessionStatus.Idle, paseoSessionStatus(loading, now))
    }

    @Test
    fun `B-AGENT-179 a record with no readable time is not believed to be running`() {
        val unknown = JsonObject(record("running", 0) - "updatedAt")
        assertEquals(SessionStatus.Idle, paseoSessionStatus(unknown, now))
        assertEquals(0L, paseoRecordBusyUntilMs(unknown))
        val garbled = record("running", 0) + mapOf("updatedAt" to jsonOf("x" to 1)["x"]!!)
        assertEquals(SessionStatus.Idle, paseoSessionStatus(garbled, now))
    }

    private fun title(title: String): String =
        paseoSession(jsonOf("id" to "a1", "cwd" to "/p", "provider" to "claude", "title" to title)).title!!

    @Test
    fun `B-AGENT-183 a first prompt cut at 60 characters ends with an ellipsis`() {
        val prompt = "Build a tip calculator in one index.html: bill amount, 10, 15 and 20"
        val cut = prompt.substring(0, 60).trim()
        kotlin.test.assertTrue(cut.length >= 58)
        assertEquals("$cut…", title(cut))
    }

    @Test
    fun `B-AGENT-183 a finished sentence and a short title stay as they are`() {
        val sentence = "Build a tip calculator in index.html with a bill split."
        assertEquals(sentence, title(sentence))
        assertEquals("Tip calculator", title("Tip calculator"))
        assertEquals("Tip calculator", paseoClippedTitle("Tip calculator"))
    }

    @Test
    fun `B-AGENT-162 an agent maps to a session with its model, mode, cost and context use`() {
        val agent = mapperAgent("a1") + mapOf(
            "lastUsage" to jsonOf("totalCostUsd" to 0.04, "contextWindowUsedTokens" to 1200, "contextWindowMaxTokens" to 200000),
            "thinkingOptionId" to jsonOf("v" to "high")["v"]!!,
        )
        val session = paseoSession(agent)
        assertEquals("a1", session.id.raw)
        assertEquals("Agent a1", session.title)
        assertEquals("/work/app", session.directory)
        assertEquals("claude/claude-haiku-4-5", session.model)
        assertEquals(0.04, session.cost)
        assertEquals("default", session.agent)
        assertEquals(SessionContextUse(1200, 200000), session.contextUse)
        assertEquals(ModelRef("claude", "claude-haiku-4-5"), session.selection?.model)
        assertEquals("high", session.selection?.variant)
        assertEquals("default", session.selection?.agent)
        assertEquals(true, session.selection?.modelKnown)
        assertEquals(kotlin.time.Instant.parse("2026-09-19T03:35:27.840Z").toEpochMilliseconds(), session.time?.createdMs)
        // A record without a title of its own reads as the runtime's conversation; an unknown runtime keeps its id.
        assertEquals("Claude Code conversation", paseoSession(mapperAgent("b", title = null)).title)
        assertEquals("kilo conversation", paseoSession(mapperAgent("c", title = "  ", provider = "kilo")).title)
        assertEquals("Pi", paseoProviderName("pi"))
        assertEquals("GitHub Copilot", paseoProviderName("copilot"))
        assertNull(paseoContextUse(jsonOf("contextWindowUsedTokens" to 0, "contextWindowMaxTokens" to 10)))
    }

    @Test
    fun `B-AGENT-162 an agent without id, folder or runtime is an unsupported response`() {
        for (missing in listOf("id", "cwd", "provider")) {
            val failure = assertFailsWith<PaseoException>(missing) { paseoSession(JsonObject(mapperAgent("a") - missing)) }
            assertEquals(PaseoFailureKind.InvalidResponse, failure.kind)
        }
    }
}
