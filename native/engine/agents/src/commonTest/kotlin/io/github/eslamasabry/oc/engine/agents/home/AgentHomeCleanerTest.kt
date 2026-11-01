package io.github.eslamasabry.oc.engine.agents.home

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.host.AgentHostSecret
import io.github.eslamasabry.oc.engine.agents.ownership.AgentOwnership
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentSecrets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentHomeCleanerTest {
    private val one = ProfileId("one")
    private val two = ProfileId("two")

    private fun prefs() = FakeAgentPrefs(
        mapOf(
            "oc.phoneAgentOwner.one" to "one",
            "oc.phoneAgentOwner.two" to "one",
            "oc.agentPhoneGate.one" to "{}",
            "oc.phoneAgentGateMigration.one" to listOf("two"),
            "oc.agentInstall.one" to "codex",
            "oc.agentFeed.one" to "[]",
            "oc.phoneAgentsUsed.one" to true,
            "oc.autoApprove.one.agents" to "{}",
            "oc.agentFeed.other" to "[]",
        ),
    )

    private fun secrets() = FakeAgentSecrets().apply {
        values["oc.agentHostSecret.one"] = "a".repeat(64)
        values["pw.one"] = "unrelated"
    }

    @Test
    fun `B-STATE-056 removing a non-last alias keeps the home, the keys and the password`() = runTest {
        val prefs = prefs()
        val host = FakeAgentHost()
        val secrets = secrets()
        val cleaner = AgentHomeCleaner(prefs, host, AgentHostSecret(secrets, { ByteArray(it) }, {}))
        val r = (cleaner.clean(one, listOf(one, two)) as Outcome.Ok).value
        assertIs<AgentHomeCleanup.Retained>(r)
        assertTrue("oc.agentPhoneGate.one" in r.retainedKeys)
        assertEquals(emptyList(), host.calls)
        assertTrue("oc.agentHostSecret.one" in secrets.values)
    }

    @Test
    fun `B-STATE-056 the last alias drains the home before the password goes, then sweeps the owner's keys`() = runTest {
        val prefs = prefs()
        val secrets = secrets()
        val order = mutableListOf<String>()
        val host = object : FakeAgentHost() {
            override suspend fun deleteHome(owner: io.github.eslamasabry.oc.engine.agents.OwnerId): Outcome<Unit> {
                order += "drain:${secrets.values.containsKey("oc.agentHostSecret.one")}"
                return super.deleteHome(owner)
            }
        }
        val cleaner = AgentHomeCleaner(prefs, host, AgentHostSecret(secrets, { ByteArray(it) }, {}))
        val r = (cleaner.clean(two, listOf(two)) as Outcome.Ok).value
        assertIs<AgentHomeCleanup.Erased>(r)
        assertEquals(listOf("drain:true"), order)
        assertEquals(listOf("deleteHome:one"), host.calls)
        assertNull(secrets.values["oc.agentHostSecret.one"])
        assertEquals("unrelated", secrets.values["pw.one"])
        for (k in listOf("oc.agentPhoneGate.one", "oc.phoneAgentGateMigration.one", "oc.agentInstall.one", "oc.agentFeed.one",
            "oc.phoneAgentsUsed.one", "oc.autoApprove.one.agents", "oc.phoneAgentOwner.one", "oc.phoneAgentOwner.two")) {
            assertNull(prefs.values[k], k)
        }
        assertEquals("[]", prefs.values["oc.agentFeed.other"])
    }

    @Test
    fun `B-STATE-056 a failed drain keeps the profile data and the secure slot for retry`() = runTest {
        val prefs = prefs()
        val secrets = secrets()
        val host = FakeAgentHost().apply { deleteHomeResult = Outcome.Failed(Failure.Device()) }
        val cleaner = AgentHomeCleaner(prefs, host, AgentHostSecret(secrets, { ByteArray(it) }, {}))
        assertIs<Outcome.Failed>(cleaner.clean(two, listOf(two)))
        assertEquals("a".repeat(64), secrets.values["oc.agentHostSecret.one"])
        assertEquals("{}", prefs.values["oc.agentPhoneGate.one"])
        assertEquals(ProfileId("one"), AgentOwnership(prefs).ownerOf(two))
    }

    @Test
    fun `B-STATE-056 saved-sign-in reset includes orphan agent slots and keeps unrelated secrets`() = runTest {
        val secrets = secrets().apply { values["oc.agentHostSecret.orphan"] = "b".repeat(64) }
        val host = FakeAgentHost()
        val names = (AgentsSignInReset(host, AgentHostSecret(secrets, { ByteArray(it) }, {})).reset(listOf(one)) as Outcome.Ok).value
        assertEquals(setOf("oc.agentHostSecret.one", "oc.agentHostSecret.orphan"), names)
        assertEquals(listOf("stop:one:false"), host.calls)
        assertEquals(mapOf("pw.one" to "unrelated"), secrets.values)
    }
}
