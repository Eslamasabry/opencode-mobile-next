package io.github.eslamasabry.oc.engine.requests.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Always allow" invitation (plan 09 §5.3, map CHAT-GENUI-46; Dart `repeated_permission_consent.dart`). */
class AlwaysAllowInvitationTest {
    private class MemoryStorage(var value: String? = null) : ConsentStorage {
        var failWrites = false
        var writes = 0

        override suspend fun read(): Outcome<String?> = value.ok()

        override suspend fun write(json: String): Outcome<Unit> {
            if (failWrites) return Failure.StorageRefused().failed()
            writes++
            value = json
            return Unit.ok()
        }
    }

    private val npm = ConsentScope("ses_1", "bash", listOf("npm install", "a b"), listOf("npm install *"))

    private suspend fun AlwaysAllowInvitation.ask(scope: ConsentScope, request: String, grants: Boolean = true, auto: Boolean = false) =
        observe(scope, request, supportsPersistentGrants = grants, autoApproving = auto)

    @Test
    fun `B-SEC-147 the third distinct identical ask offers Always allow once, across conversations`() = runTest {
        val invitation = AlwaysAllowInvitation(MemoryStorage())
        assertFalse(invitation.ask(npm, "per_1").offerAlwaysAllow)
        assertEquals(1, invitation.ask(npm, "per_1").distinctAsks, "the same request seen twice counts once")
        assertFalse(invitation.ask(npm.copy(sessionId = "ses_2", patterns = listOf("a b", "npm install")), "per_2").offerAlwaysAllow)
        val third = invitation.ask(npm, "per_3")
        assertTrue(third.offerAlwaysAllow)
        assertEquals(OfferDecision.Offered, third.decision)
        assertFalse(invitation.ask(npm, "per_4").offerAlwaysAllow, "never offered twice")
    }

    @Test
    fun `B-SEC-147 the claim survives a restart and a decline is explained, an acceptance is recorded once`() = runTest {
        val storage = MemoryStorage()
        val first = AlwaysAllowInvitation(storage)
        repeat(3) { first.ask(npm, "per_$it") }
        val restarted = AlwaysAllowInvitation(storage)
        assertFalse(restarted.ask(npm, "per_9").offerAlwaysAllow, "a replay after restart cannot offer it again")
        val declined = restarted.recordDecision(npm, accepted = false)
        assertTrue(declined.explainDeclinedInSettings)
        assertEquals(OfferDecision.Declined, restarted.recordDecision(npm, accepted = true).decision, "only an open offer changes")
        assertEquals(1, restarted.declinedCount())
        assertTrue(restarted.forgetDeclined())
        assertEquals(0, restarted.declinedCount())
    }

    @Test
    fun `B-SEC-147 capability and auto-approval gate the offer and nothing is counted`() = runTest {
        val storage = MemoryStorage()
        val invitation = AlwaysAllowInvitation(storage)
        repeat(3) { assertEquals(ConsentStatus(), invitation.ask(npm, "per_$it", grants = false)) }
        repeat(3) { assertEquals(ConsentStatus(), invitation.ask(npm, "per_$it", auto = true)) }
        assertEquals(0, storage.writes)
    }

    @Test
    fun `B-SEC-147 only digests are stored, in the Flutter format, never a command or pattern`() = runTest {
        val storage = MemoryStorage()
        val invitation = AlwaysAllowInvitation(storage)
        invitation.ask(npm, "per_1")
        val stored = storage.value!!
        assertFalse("npm" in stored || "ses_1" in stored || "per_1" in stored, stored)
        // Digests of Dart jsonEncode([...]) (sha256), computed independently.
        assertEquals(
            """{"version":1,"entries":{"71842d1f8e2df6ad0e4dfbc8a0f928e8a5aa93a09f4002f5548b0e220ef8e287":""" +
                """{"requests":["ad88d1ab8c9924dd5f4036b6f4a41cfd10b62b56a2df06a62d2f71c82b066163"],"decision":"notOffered"}}}""",
            stored,
        )
        assertEquals("oc.permissionConsent.prof_1", AlwaysAllowInvitation.storageKey("prof_1"))
        assertFailsWith<IllegalArgumentException> { AlwaysAllowInvitation.storageKey("a.b") }
    }

    @Test
    fun `B-SEC-147 corrupt storage and refused writes suppress offers`() = runTest {
        val corrupt = AlwaysAllowInvitation(MemoryStorage("""{"version":1,"entries":{"nothex":{"requests":[],"decision":"offered"}}}"""))
        assertFalse(corrupt.ask(npm, "per_1").storageAvailable)
        assertNull(corrupt.declinedCount())

        val refusing = MemoryStorage().apply { failWrites = true }
        val invitation = AlwaysAllowInvitation(refusing)
        assertFalse(invitation.ask(npm, "per_1").storageAvailable)
        refusing.failWrites = false
        assertFalse(invitation.ask(npm, "per_2").storageAvailable, "suppressed until restart, never guessed")
    }
}
