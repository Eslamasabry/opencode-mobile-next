package io.github.eslamasabry.oc.engine.agents.ownership

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/phone_agent_owner_test.dart (B-LINUX-118). The caller passes only this phone's server profiles. */
class AgentOwnershipTest {
    private fun ids(vararg raw: String) = raw.map(::ProfileId)
    private val one = ProfileId("one")
    private val two = ProfileId("two")

    @Test
    fun `B-LINUX-118 a new protocol profile reuses the checked home through restart`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.agentPhoneGate.one" to "{\"claude\":{\"fingerprint\":\"pin\"}}",
                "oc.agentFeed.one" to "[{\"sessionID\":\"chat\"}]",
                "oc.phoneAgentsUsed.one" to true,
            ),
        )
        val o = AgentOwnership(prefs)
        assertEquals(one, (o.migrate(ids("one")) as Outcome.Ok).value)
        assertEquals(one, (o.migrate(ids("one", "two")) as Outcome.Ok).value)
        assertEquals(one, o.ownerOf(two))
        // Restart: a fresh instance reads the saved mapping.
        assertEquals(one, AgentOwnership(prefs).ownerOf(two))
        assertTrue(prefs.string("oc.agentPhoneGate.one")!!.contains("claude"))
    }

    @Test
    fun `B-LINUX-118 legacy profiles prefer a checked owner without copying secrets`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.agentPhoneGate.empty" to "{}",
                "oc.agentPhoneGate.checked" to "{\"claude\":{\"fingerprint\":\"proof\"}}",
            ),
        )
        val o = AgentOwnership(prefs)
        o.migrate(ids("empty", "checked", "two"))
        assertEquals(ProfileId("checked"), o.ownerOf(ProfileId("empty")))
        assertEquals(ProfileId("checked"), o.ownerOf(two))
        // Remote and Termux servers are never passed in, so they stay their own.
        assertEquals(ProfileId("termux"), o.ownerOf(ProfileId("termux")))
        assertEquals(ProfileId("remote"), o.ownerOf(ProfileId("remote")))
    }

    @Test
    fun `B-LINUX-118 score prefers checked (4) over chats (2) over used (1) with ties to creation order`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.phoneAgentsUsed.a" to true,
                "oc.agentFeed.b" to "[{}]",
                "oc.phoneAgentsUsed.c" to true,
                "oc.agentFeed.c" to "[]",
            ),
        )
        val o = AgentOwnership(prefs)
        o.migrate(ids("a", "b", "c"))
        assertEquals(ProfileId("b"), o.ownerOf(ProfileId("a")))
        val tie = FakeAgentPrefs()
        AgentOwnership(tie).migrate(ids("x", "y"))
        assertEquals(ProfileId("x"), AgentOwnership(tie).ownerOf(ProfileId("y")))
    }

    @Test
    fun `B-LINUX-118 already mapped homes adopt every legacy check once without reviving retries`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.phoneAgentOwner.one" to "one",
                "oc.phoneAgentOwner.two" to "one",
                "oc.agentPhoneGate.one" to "{\"claude\":{\"fingerprint\":\"current\"},\"pi\":{\"fingerprint\":\"owner\"}}",
                "oc.agentPhoneGate.two" to "{\"fx\":{\"fingerprint\":\"fx-pin\",\"architecture\":\"x64\"}," +
                    "\"omp\":{\"fingerprint\":\"omp-pin\"},\"pi\":{\"fingerprint\":\"old\"},\"invalid\":{},\"empty\":{\"fingerprint\":\"\"}}",
            ),
        )
        val o = AgentOwnership(prefs)
        o.migrate(ids("one", "two"))
        assertEquals(
            "{\"claude\":{\"fingerprint\":\"current\"},\"pi\":{\"fingerprint\":\"owner\"}," +
                "\"fx\":{\"fingerprint\":\"fx-pin\",\"architecture\":\"x64\"},\"omp\":{\"fingerprint\":\"omp-pin\"}}",
            prefs.string("oc.agentPhoneGate.one"),
        )
        assertEquals(listOf("two"), prefs.stringList("oc.phoneAgentGateMigration.one"))
        // A failed phone-check retry removes old proof; the next load must not bring it back.
        prefs.values["oc.agentPhoneGate.one"] = "{\"claude\":{\"fingerprint\":\"current\"},\"pi\":{\"fingerprint\":\"owner\"},\"omp\":{\"fingerprint\":\"omp-pin\"}}"
        o.migrate(ids("one", "two"))
        assertFalse(prefs.string("oc.agentPhoneGate.one")!!.contains("fx"))
        // Retaining the separate legacy home never repeats its migration.
        o.migrate(ids("two"))
        assertFalse(prefs.string("oc.agentPhoneGate.one")!!.contains("fx"))
        assertEquals(listOf("two"), prefs.stringList("oc.phoneAgentGateMigration.one"))
        assertTrue(prefs.string("oc.agentPhoneGate.two")!!.contains("fx-pin"))
    }

    @Test
    fun `B-LINUX-118 a malformed migration receipt refuses to revive old proof`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.phoneAgentOwner.one" to "one",
                "oc.phoneAgentGateMigration.one" to "broken",
                "oc.agentPhoneGate.one" to "{}",
                "oc.agentPhoneGate.two" to "{\"fx\":{\"fingerprint\":\"old\"}}",
            ),
        )
        val r = AgentOwnership(prefs).migrate(ids("one", "two"))
        assertIs<Failure.Storage>((r as Outcome.Failed).failure)
        assertEquals("{}", prefs.string("oc.agentPhoneGate.one"))
        val badId = FakeAgentPrefs(mapOf("oc.phoneAgentOwner.one" to "one", "oc.phoneAgentGateMigration.one" to listOf("../x")))
        assertIs<Outcome.Failed>(AgentOwnership(badId).migrate(ids("one", "two")))
    }

    @Test
    fun `B-LINUX-118 a malformed donor does not suppress another checked home`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.phoneAgentOwner.one" to "one",
                "oc.agentPhoneGate.bad" to "broken",
                "oc.agentPhoneGate.two" to "{\"fx\":{\"fingerprint\":\"fx-pin\"}}",
            ),
        )
        AgentOwnership(prefs).migrate(ids("one", "bad", "two"))
        assertTrue(prefs.string("oc.agentPhoneGate.one")!!.contains("fx-pin"))
    }

    @Test
    fun `B-LINUX-118 a refused write keeps the migration for a persisted retry`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.phoneAgentOwner.one" to "one",
                "oc.agentPhoneGate.one" to "{}",
                "oc.agentPhoneGate.two" to "{\"fx\":{\"fingerprint\":\"fx-pin\"}}",
            ),
        )
        prefs.refuseWrites = true
        assertIs<Outcome.Failed>(AgentOwnership(prefs).migrate(ids("one", "two")))
        assertNull(prefs.stringList("oc.phoneAgentGateMigration.one"))
        assertEquals("{}", prefs.string("oc.agentPhoneGate.one"))
        prefs.refuseWrites = false
        AgentOwnership(prefs).migrate(ids("one", "two"))
        assertTrue(prefs.string("oc.agentPhoneGate.one")!!.contains("fx-pin"))
        assertEquals(listOf("two"), prefs.stringList("oc.phoneAgentGateMigration.one"))
    }

    @Test
    fun `B-LINUX-118 deleting an owner alias keeps the agents until the last protocol is removed`() = runTest {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.agentPhoneGate.one" to "{\"claude\":{\"fingerprint\":\"proof\"}}",
                "oc.agentFeed.one" to "[]",
                "oc.sessionAutoApproval.one.agents" to "{\"chat\":true}",
                "oc.autoApprove.one.agents" to "{}",
                "oc.agentFeed.one2" to "[]",
            ),
        )
        val o = AgentOwnership(prefs)
        o.migrate(ids("one", "two"))
        val retained = o.retainedPreferenceKeys(one, ids("one", "two"))
        assertTrue("oc.agentPhoneGate.one" in retained)
        assertTrue("oc.sessionAutoApproval.one.agents" in retained)
        assertTrue("oc.autoApprove.one.agents" in retained)
        assertFalse("oc.agentFeed.one2" in retained)
        assertTrue(o.retainedAfterRemoving(one, ids("one", "two")))
        assertFalse(o.retainedAfterRemoving(two, ids("two")))
        assertEquals(emptySet(), o.retainedPreferenceKeys(two, ids("two")))
    }
}
