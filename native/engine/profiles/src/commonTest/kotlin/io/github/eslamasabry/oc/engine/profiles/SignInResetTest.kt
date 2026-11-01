package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter `test/profile_sign_in_reset_test.dart`, ported (B-STATE-064). */
class SignInResetTest {
    private val opaque = "synthetic-reset-server-value"

    private val preserved: List<Pair<String, String>> = listOf(
        "oc.profiles" to json(
            listOf(
                StoredProfileJson.encode(StoredProfile(id("server"), "Saved server", "https://example.invalid")),
                StoredProfileJson.encode(StoredProfile(id("agent"), "Saved agent", "ws://localhost:1234", BackendKind.Codex)),
            ),
        ),
        "oc.offlineQueue" to "retained queue source",
        "oc.sessionDrafts" to "retained draft source",
        "oc.keptQueuedPrompts" to "retained saved prompts",
        "oc.automation.server" to "retained server preference",
        "oc.appearance" to "light",
    )

    private class Setup(val h: Harness, val reset: SignInReset, val participantCalls: MutableList<Set<String>>)

    private suspend fun TestScope.setup(participantFails: () -> Boolean = { false }): Setup {
        val secrets = FakeSecrets(
            mapOf(
                "pw.server" to opaque,
                "oc.codexToken.agent" to "synthetic-reset-agent-value",
                "pw.orphan" to "synthetic-reset-orphan-value",
                "oc.codexToken.orphan" to "synthetic-reset-orphan-agent-value",
                "other.signIn" to "synthetic-unrelated-value",
                "oc.orchestration.token.server" to "synthetic-team-value",
                "pw" to "synthetic-prefix-neighbor",
                "oc.codexToken" to "synthetic-prefix-neighbor",
            ),
        )
        val h = Harness(StandardTestDispatcher(testScheduler), secrets)
        h.seed(*(preserved + ("oc.activeProfile" to "server")).toTypedArray())
        val calls = mutableListOf<Set<String>>()
        val byo = object : SignInResetParticipant {
            override val owner = ":engine:integrations"
            override suspend fun reset(found: Set<String>): Outcome<Unit> {
                calls += found
                return if (participantFails()) Failure.Device().failed() else Unit.ok()
            }
        }
        return Setup(h, SignInReset(h.store, h.kv, h.secrets, h.lanes, h.redactor, setOf(byo)), calls)
    }

    private fun Setup.expectPreserved() {
        for ((key, value) in preserved) assertEquals(value, h.string(key), key)
    }

    private fun Outcome<Unit>.refusedSafely(vararg secretValues: String) {
        val failure = assertIs<Outcome.Failed>(this).failure
        assertIs<Failure.Storage>(failure)
        assertNull(failure.details)
        for (v in secretValues) assertFalse(v in failure.toString())
    }

    @Test
    fun `B-STATE-064 reset removes only owned sign-ins and is idempotent`() = runTest {
        val s = setup()
        assertTrue(s.h.store.profiles.value.isEmpty(), "load is not a prerequisite")

        assertIs<Outcome.Ok<Unit>>(s.reset.reset())

        assertNull(s.h.kv.get("oc.activeProfile"))
        assertNull(s.h.store.activeId.value)
        assertEquals(setOf("other.signIn", "oc.orchestration.token.server", "pw", "oc.codexToken"), s.h.secrets.values.keys)
        s.expectPreserved()
        assertTrue(s.h.redactor.containsSecret("leak $opaque here"))
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertEquals(4, s.h.secrets.deleted.size)
        s.expectPreserved()
    }

    @Test
    fun `B-STATE-064 reset never parses or erases unreadable profile metadata`() = runTest {
        val s = setup()
        s.h.kv.commit(s.h.lanes.app) { putLong("oc.profiles", 7) }

        assertIs<Outcome.Ok<Unit>>(s.reset.reset())

        assertEquals(PrefValue.Int64(7), s.h.kv.get("oc.profiles"))
        assertNull(s.h.kv.get("oc.activeProfile"))
        assertFalse(s.h.secrets.values.keys.any { it.startsWith("pw.") || it.startsWith("oc.codexToken.") })
    }

    @Test
    fun `B-STATE-064 cached profiles lose their secrets and keep their metadata`() = runTest {
        val s = setup()
        s.h.store.load()
        val server = s.h.store.profiles.value.first()
        assertTrue(server.hasSecret)

        assertIs<Outcome.Ok<Unit>>(s.reset.reset())

        val (afterServer, afterAgent) = s.h.store.profiles.value
        assertEquals(server.profile, afterServer.profile)
        assertFalse(afterServer.hasSecret || afterAgent.hasSecret)
        assertTrue(afterServer.reentry.password)
        assertTrue(afterAgent.reentry.agentToken)
        assertEquals("Saved server", afterServer.profile.name)
        s.expectPreserved()
    }

    @Test
    fun `B-STATE-064 a failed enumeration never broadens the reset or reports success`() = runTest {
        val s = setup()
        s.h.store.load()
        s.h.secrets.failEnumerationAt = 1

        s.reset.reset().refusedSafely(opaque)

        assertTrue(s.h.secrets.deleted.isEmpty())
        assertEquals(8, s.h.secrets.values.size)
        assertEquals("server", s.h.string("oc.activeProfile"))
        assertTrue(s.h.store.profiles.value.none { it.hasSecret })
        s.expectPreserved()
        s.h.secrets.failEnumerationAt = null
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertNull(s.h.kv.get("oc.activeProfile"))
    }

    @Test
    fun `B-STATE-064 a refused active-selection removal keeps every durable sign-in`() = runTest {
        val s = setup()
        s.h.backend.refuse += "oc.activeProfile"

        s.reset.reset().refusedSafely(opaque)

        assertTrue(s.h.secrets.deleted.isEmpty())
        assertEquals("server", s.h.string("oc.activeProfile"))
        s.expectPreserved()
        s.h.backend.refuse.clear()
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertNull(s.h.kv.get("oc.activeProfile"))
    }

    @Test
    fun `B-STATE-064 a partial secure deletion fails and a retry is safe`() = runTest {
        val s = setup()
        s.h.store.load()
        s.h.secrets.failDeletes += "oc.codexToken.agent"

        s.reset.reset().refusedSafely(opaque)

        assertNull(s.h.kv.get("oc.activeProfile"))
        assertFalse("pw.server" in s.h.secrets.values)
        assertTrue("oc.codexToken.agent" in s.h.secrets.values)
        assertTrue(s.h.store.profiles.value.none { it.hasSecret })
        s.expectPreserved()
        s.h.secrets.failDeletes.clear()
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertFalse(s.h.secrets.values.keys.any { it.startsWith("pw.") || it.startsWith("oc.codexToken.") })
    }

    @Test
    fun `B-STATE-064 an unconfirmed secure deletion is a failure until verification succeeds`() = runTest {
        val s = setup()
        s.h.secrets.retainDeletes += "pw.orphan"

        s.reset.reset().refusedSafely()

        assertTrue("pw.orphan" in s.h.secrets.values)
        s.expectPreserved()
        s.h.secrets.retainDeletes.clear()
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertFalse("pw.orphan" in s.h.secrets.values)
    }

    @Test
    fun `B-STATE-064 a failed post-deletion enumeration is not a successful reset`() = runTest {
        val s = setup()
        // The first pass enumerates once per prefix; fail the first call of the verification pass.
        s.h.secrets.failEnumerationAt = ProfileKeys.SIGN_IN_PREFIXES.size + 1

        s.reset.reset().refusedSafely()

        s.expectPreserved()
        s.h.secrets.failEnumerationAt = null
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertNull(s.h.kv.get("oc.activeProfile"))
    }

    @Test
    fun `B-STATE-064 reset owns the BYO envelope while keeping its journal, and a participant refusal is a failure`() = runTest {
        var fail = true
        val s = setup { fail }
        s.h.secrets.values["oc.byoHostSecrets.host-a"] = "synthetic-byo-envelope"
        s.h.seed("oc.byoHost.host-a" to "nonsecret-journal")

        s.reset.reset().refusedSafely("synthetic-byo-envelope")
        assertEquals("synthetic-byo-envelope", s.h.secrets.values["oc.byoHostSecrets.host-a"])

        fail = false
        assertIs<Outcome.Ok<Unit>>(s.reset.reset())
        assertNull(s.h.secrets.values["oc.byoHostSecrets.host-a"])
        assertEquals("nonsecret-journal", s.h.string("oc.byoHost.host-a"))
        assertTrue(s.h.secrets.values["other.signIn"] != null)
        assertTrue("oc.byoHostSecrets.host-a" in s.participantCalls.last())
    }
}
