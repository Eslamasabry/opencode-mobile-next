package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter `mcp_chat_card_authority_test.dart` (B-AGENT-131) and `paseo_genui_recovery_snapshot_test.dart` (B-AGENT-166). */
class CardAuthorityTest {
    private var live = true
    private val authority = CardAuthority { live }

    private fun connectorInput(reason: String = "Find references"): Map<String, Any?> =
        mapOf(
            "v" to 1L,
            "id" to "suggest",
            "title" to "Suggested connector",
            "body" to emptyList<Any?>(),
            "connector" to mapOf("catalogId" to "com.example/design", "reason" to reason),
        )

    private fun original() = CardFixtures.card(connectorInput())

    private fun observe(reason: String = "Find references") =
        authority.observe(CardFixtures.scope, "session", listOf(CardFixtures.assistant(input = connectorInput(reason))), tailComplete = true)

    @Test
    fun `B-AGENT-131 a retained payload stays readable during suspension but cannot authorize new requests`() {
        observe()
        val card = original()
        assertEquals(CardState.Report, authority.state(card))
        live = false
        assertEquals(CardState.Unknown, authority.state(card))
        assertEquals("com.example/design", authority.observedCard(card)?.connector?.catalogId)
        assertEquals(CardState.Report, authority.observedState(card))
    }

    @Test
    fun `B-AGENT-131 a replaced or deleted payload cannot reuse prior consent`() {
        observe()
        val card = original()
        observe(reason = "Changed recommendation")
        assertNull(authority.observedCard(card))
        assertEquals(CardState.Unknown, authority.observedState(card))
        assertEquals(CardState.Unknown, authority.state(card))
        authority.removeSession(CardFixtures.scope, "session")
        assertNull(authority.observedCard(card))
        assertEquals(CardState.Unknown, authority.observedState(card))
    }

    @Test
    fun `B-AGENT-131 an incomplete recovery differs from a complete read where the card is gone`() {
        observe()
        val card = original()
        authority.observe(CardFixtures.scope, "session", emptyList(), tailComplete = false)
        assertNull(authority.observedState(card))
        authority.observe(CardFixtures.scope, "session", emptyList(), tailComplete = true)
        assertEquals(CardState.Unknown, authority.observedState(card))
    }

    private val created = "2026-10-25T08:00:00Z"
    private val prompt = "2026-10-26T08:00:00Z"

    private fun agent(
        status: String = "idle",
        lastUser: Any? = prompt,
        createdAt: String = created,
        updatedAt: String = prompt,
        activeTurn: Boolean = false,
    ): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive("conversation"))
            put("provider", JsonPrimitive("claude"))
            put("status", JsonPrimitive(status))
            put("createdAt", JsonPrimitive(createdAt))
            put("updatedAt", JsonPrimitive(updatedAt))
            put(
                "lastUserMessageAt",
                when (lastUser) {
                    null -> JsonNull
                    is String -> JsonPrimitive(lastUser)
                    is Number -> JsonPrimitive(lastUser)
                    else -> JsonNull
                },
            )
            put("activeTurn", if (activeTurn) buildJsonObject { put("turnId", JsonPrimitive("active")) } else JsonNull)
        }

    @Test
    fun `B-AGENT-166 the recovery snapshot reads the cached prompt and never a conversation it has not listed`() {
        val snapshots = CardRecoverySnapshots()
        assertNull(snapshots.snapshot("conversation"))
        snapshots.listed(agent())
        assertEquals(CardRecoverySnapshot(prompt, idle = true), snapshots.snapshot("conversation"))
        assertNull(snapshots.snapshot("unknown"))
    }

    @Test
    fun `B-AGENT-166 a never-prompted card falls back to the creation stamp`() {
        val snapshots = CardRecoverySnapshots()
        for (invalid in listOf<Any?>(null, "", "   ", 12)) {
            snapshots.listed(agent(lastUser = invalid))
            assertEquals(created, snapshots.snapshot("conversation")?.promptStamp)
        }
        snapshots.listed(agent(lastUser = null, createdAt = ""))
        assertNull(snapshots.snapshot("conversation")?.promptStamp)
    }

    @Test
    fun `B-AGENT-166 a changed prompt is seen but updatedAt is ignored`() {
        val snapshots = CardRecoverySnapshots()
        snapshots.listed(agent())
        snapshots.listed(agent(updatedAt = "2026-10-26T09:00:00Z"))
        assertEquals(prompt, snapshots.snapshot("conversation")?.promptStamp)
        snapshots.listed(agent(lastUser = "2026-10-26T10:00:00Z"))
        assertEquals("2026-10-26T10:00:00Z", snapshots.snapshot("conversation")?.promptStamp)
    }

    @Test
    fun `B-AGENT-166 running, initializing and active turns are not idle, closed and failed agents are`() {
        val snapshots = CardRecoverySnapshots()
        for (status in listOf("running", "initializing")) {
            snapshots.listed(agent(status = status))
            assertFalse(snapshots.snapshot("conversation")!!.idle)
        }
        snapshots.listed(agent(activeTurn = true))
        assertFalse(snapshots.snapshot("conversation")!!.idle)
        for (status in listOf("closed", "error")) {
            snapshots.listed(agent(status = status))
            assertTrue(snapshots.snapshot("conversation")!!.idle)
        }
    }

    @Test
    fun `B-AGENT-166 a live turn overrides an idle listing and daemon and draft aliases share metadata`() {
        val snapshots = CardRecoverySnapshots()
        snapshots.listed(agent())
        snapshots.streamed("conversation", "turn_started")
        assertFalse(snapshots.snapshot("conversation")!!.idle)
        snapshots.streamed("conversation", "turn_completed")
        assertTrue(snapshots.snapshot("conversation")!!.idle)

        snapshots.alias("draft-1", "conversation")
        assertEquals("conversation", snapshots.daemonId("draft-1"))
        assertEquals(CardRecoverySnapshot(prompt, idle = false), snapshots.snapshot("draft-1"))
        assertEquals(snapshots.snapshot("conversation"), snapshots.snapshot("draft-1"))
        snapshots.streamed("conversation", "turn_completed")
        assertEquals(CardRecoverySnapshot(prompt, idle = true), snapshots.snapshot("conversation"))
    }
}
