package io.github.eslamasabry.oc.engine.agents.host

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentSecrets
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentHelperTest {
    private val owner = ProfileId("owner")
    private var counter = 0
    private val random = SecureBytes { n -> ByteArray(n) { (it + 16 * counter).toByte() }.also { counter++ } }

    @Test
    fun `B-AGENT-125 the password is 64 hex, committed before the helper starts, and only ever in the stdin field`() = runTest {
        val secrets = FakeAgentSecrets()
        val registered = mutableListOf<String>()
        var savedAtStart: String? = null
        val host = FakeAgentHost()
        host.ensureResult = { _, s ->
            savedAtStart = secrets.values["oc.agentHostSecret.owner"]
            Outcome.Ok(HelperRunning(4099, s.expectedHelperVersion, Abi.Arm64V8a, true))
        }
        val helper = AgentHelper(host, AgentHostSecret(secrets, random, registered::add))
        assertIs<Outcome.Ok<HelperRunning>>(helper.ensureRunning(owner))
        val start = host.lastStart!!
        val password = start.password.value
        assertTrue(AgentHostSecret.FORMAT.matches(password))
        assertEquals(password, savedAtStart)
        assertEquals(listOf(password), registered)
        assertFalse(password in start.config.json)
        assertFalse(password in start.toString())
        assertFalse(password in host.calls.joinToString())
        assertEquals("0.11.2", start.expectedHelperVersion)
        // The same password is reused on the next start.
        helper.ensureRunning(owner)
        assertEquals(password, host.lastStart!!.password.value)
    }

    @Test
    fun `B-AGENT-125 a refused secret write starts nothing and an unreadable stored value is replaced`() = runTest {
        val secrets = FakeAgentSecrets().apply { refuseWrites = true }
        val host = FakeAgentHost()
        assertIs<Outcome.Failed>(AgentHelper(host, AgentHostSecret(secrets, random, {})).ensureRunning(owner))
        assertEquals(emptyList(), host.calls)
        val bad = FakeAgentSecrets().apply { values["oc.agentHostSecret.owner"] = "NOT-HEX" }
        AgentHelper(host, AgentHostSecret(bad, random, {})).ensureRunning(owner)
        assertTrue(AgentHostSecret.FORMAT.matches(bad.values["oc.agentHostSecret.owner"]!!))
    }

    @Test
    fun `B-AGENT-125 rotation stops guarded, commits a new value, starts with it and reopens the agents graph`() = runTest {
        val secrets = FakeAgentSecrets()
        val host = FakeAgentHost()
        val reopened = mutableListOf<String>()
        val helper = AgentHelper(host, AgentHostSecret(secrets, random, {}), reopenAgentsGraph = { reopened += it.raw })
        helper.ensureRunning(owner)
        val first = secrets.values["oc.agentHostSecret.owner"]
        assertIs<Outcome.Ok<HelperRunning>>(helper.rotate(owner))
        val second = secrets.values["oc.agentHostSecret.owner"]
        assertTrue(first != second)
        assertEquals(second, host.lastStart!!.password.value)
        assertEquals(listOf("ensureRunning:owner", "stop:owner:true", "ensureRunning:owner"), host.calls)
        assertEquals(listOf("owner"), reopened)
        // A busy helper refuses the guarded stop: nothing rotates.
        host.stopResult = Outcome.Failed(AgentHostProblem.AgentHostBusy.asFailure())
        assertIs<Outcome.Failed>(helper.rotate(owner))
        assertEquals(second, secrets.values["oc.agentHostSecret.owner"])
    }

    @Test
    fun `B-AGENT-124 host problems keep their channel code through the interim failure encoding`() {
        for (p in AgentHostProblem.entries) assertEquals(p, p.asFailure().agentHostProblem())
        assertNull(Failure.Device().agentHostProblem())
        assertEquals(AgentHostProblem.HelperUpdateRequired, AgentHostProblem.fromCode("helper_update_required"))
    }

    @Test
    fun `B-AGENT-125 off the phone every helper call is unavailable and nothing is stored`() = runTest {
        val secrets = FakeAgentSecrets()
        val r = AgentHelper(UnavailableAgentHost(), AgentHostSecret(secrets, random, {})).ensureRunning(owner)
        assertEquals(AgentHostProblem.AgentUnavailable, (r as Outcome.Failed).failure.agentHostProblem())
        assertTrue(secrets.values.isEmpty())
        assertFalse(UnavailableAgentHost().available)
        assertEquals("•••", Secret("x").toString())
        assertEquals("•••", TransientLabel("me@example").toString())
    }

    @Test
    fun `B-LINUX-137 an automatic start follows the native idle fields and any missing field denies`() {
        assertTrue(PhoneAgentIdleState(null, null, null, null, null, null).automaticStartAllowed)
        assertTrue(PhoneAgentIdleState(false, true, true, 3, false, false).automaticStartAllowed)
        assertTrue(PhoneAgentIdleState(true, false, false, 0, true, true).automaticStartAllowed)
        assertFalse(PhoneAgentIdleState(true, true, false, 0, true, true).automaticStartAllowed)
        assertFalse(PhoneAgentIdleState(true, false, false, 1, true, true).automaticStartAllowed)
        assertFalse(PhoneAgentIdleState(true, false, false, 0, null, true).automaticStartAllowed)
    }
}
