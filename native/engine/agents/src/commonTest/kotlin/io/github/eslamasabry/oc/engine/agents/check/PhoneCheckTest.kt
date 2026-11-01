package io.github.eslamasabry.oc.engine.agents.check

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.gate.CheckRequiredReason
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.host.Abi
import io.github.eslamasabry.oc.engine.agents.host.AgentHelper
import io.github.eslamasabry.oc.engine.agents.host.AgentHostSecret
import io.github.eslamasabry.oc.engine.agents.host.AgentHostStatus
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.HelperRunning
import io.github.eslamasabry.oc.engine.agents.host.InstalledAgent
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentSecrets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeCheckPorts : PhoneCheckPorts {
    var installResult: Outcome<Unit> = Unit.ok()
    var busy: Boolean? = false
    var live = false
    var hello: Outcome<String?> = "0.11.2".ok()
    val calls = mutableListOf<String>()
    override suspend fun installAndWait(owner: OwnerId, agent: AgentId, helperOnly: Boolean): Outcome<Unit> { calls += "install:${agent.raw}"; return installResult }
    override suspend fun localWorkBusy(owner: OwnerId): Boolean? = busy
    override suspend fun liveHelperAtPin(owner: OwnerId): Boolean = live
    override suspend fun helloVersion(owner: OwnerId): Outcome<String?> { calls += "hello"; return hello }
}

class PhoneCheckTest {
    private val owner = ProfileId("owner")
    private val pins = AgentPins.builtIn
    private val catalog = AgentCatalog.builtIn
    private val codex = AgentId("codex")

    private class Rig(abi: Abi?, running: Boolean = false) {
        val prefs = FakeAgentPrefs()
        val ports = FakeCheckPorts()
        val host = FakeAgentHost().apply {
            statusFlow.value = AgentHostStatus(running, abi, null, null, null)
            ensureResult = { _, s ->
                statusFlow.value = statusFlow.value.copy(running = true)
                HelperRunning(4099, s.expectedHelperVersion, abi ?: Abi.Arm64V8a, true).ok()
            }
        }
        val check = PhoneCheck(prefs, host, AgentHelper(host, AgentHostSecret(FakeAgentSecrets(), { ByteArray(it) }, {})), ports)
    }

    private fun Rig.installed(agent: AgentId, version: String) { host.installed[agent] = InstalledAgent(version) }

    @Test
    fun `B-LINUX-134 an ARM64 pass needs install, version, daemon and the exact Paseo hello, then writes the gate`() = runTest {
        val rig = Rig(Abi.Arm64V8a)
        rig.installed(codex, "0.166.0")
        val r = rig.check.run(owner, codex)
        assertTrue(r.passed, r.toString())
        assertTrue(r.arm64Qualified)
        assertEquals(PhoneCheckStep.entries.toList(), r.completed.toList())
        assertEquals("0.11.2", r.observedHelperVersion)
        val gate = PhoneGate.read(rig.prefs, owner)["codex"]!!
        assertEquals(PhoneGate.fingerprint(catalog.byId(codex)!!, AgentArchitecture.Arm64, pins), (gate["fingerprint"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("\"arm64\"", gate["architecture"].toString())
        assertTrue("ensureRunning:owner" in rig.host.calls)
    }

    @Test
    fun `B-LINUX-134 an x64 pass can never qualify ARM64`() = runTest {
        val rig = Rig(Abi.X86_64)
        rig.installed(codex, "0.166.0")
        val r = rig.check.run(owner, codex)
        assertTrue(r.passed)
        assertFalse(r.arm64Qualified)
        assertEquals(AgentArchitecture.X64, r.architecture)
    }

    @Test
    fun `B-LINUX-134 a failed retry clears old proof and never starts the helper after a wrong version`() = runTest {
        val rig = Rig(Abi.Arm64V8a)
        rig.prefs.values["oc.agentPhoneGate.owner"] = "{\"codex\":{\"fingerprint\":\"old\"},\"fx\":{\"fingerprint\":\"keep\"}}"
        rig.installed(codex, "0.160.0")
        val r = rig.check.run(owner, codex)
        assertEquals(PhoneCheckFailure.Version, r.failure)
        assertEquals(listOf(PhoneCheckStep.Install), r.completed)
        assertEquals("{\"fx\":{\"fingerprint\":\"keep\"}}", rig.prefs.string("oc.agentPhoneGate.owner"))
        assertFalse(rig.host.calls.any { it.startsWith("ensureRunning") })
        assertFalse("hello" in rig.ports.calls)
    }

    @Test
    fun `B-LINUX-131 an old Paseo hello is a helper update, not agent silence, and writes no proof`() = runTest {
        val rig = Rig(Abi.Arm64V8a)
        rig.installed(codex, "0.166.0")
        rig.ports.hello = "0.9.2".ok()
        val r = rig.check.run(owner, codex)
        assertEquals(PhoneCheckFailure.HelperVersion, r.failure)
        assertEquals("0.9.2", r.observedHelperVersion)
        assertTrue(PhoneGate.read(rig.prefs, owner).isEmpty())
        rig.ports.hello = Failure.Net.Unreachable(Failure.Net.Unreachable.Why.Timeout).failed()
        assertEquals(PhoneCheckFailure.Timeout, rig.check.run(owner, codex).failure)
        rig.ports.hello = "not a version".ok()
        assertEquals(PhoneCheckFailure.HelperVersion, rig.check.run(owner, codex).failure)
    }

    @Test
    fun `B-LINUX-132 a busy helper or unknown work blocks the check, and a live helper at the pin is not restarted`() = runTest {
        val rig = Rig(Abi.Arm64V8a, running = true)
        rig.installed(codex, "0.166.0")
        rig.ports.busy = null
        assertEquals(PhoneCheckFailure.Busy, rig.check.run(owner, codex).failure)
        assertTrue(rig.ports.calls.isEmpty())
        rig.ports.busy = false
        rig.ports.live = true
        val r = rig.check.run(owner, codex)
        assertTrue(r.passed, r.toString())
        assertFalse(rig.host.calls.any { it.startsWith("ensureRunning") })
    }

    @Test
    fun `B-LINUX-130 Claude's check ends with a sign-in read and signed-out becomes needs sign-in`() = runTest {
        val rig = Rig(Abi.Arm64V8a)
        rig.installed(AgentId.CLAUDE, "2.1.316")
        rig.host.authResult = { _, _, _ -> AuthProjection(AuthState.SignedOut).ok() }
        val r = rig.check.run(owner, AgentId.CLAUDE)
        assertEquals(PhoneCheckFailure.NeedsSignIn, r.failure)
        assertTrue(rig.host.calls.any { it == "auth:claude:Probe:${PhoneCheck.CLAUDE_PROBE_TIMEOUT}" })
        // Installation proof survives: signing in later needs no new check.
        assertTrue(PhoneGate.read(rig.prefs, owner).containsKey("claude"))
        rig.host.authResult = { _, _, _ -> AuthProjection(AuthState.Error, error = AuthProbeError.TimedOut).ok() }
        assertEquals(PhoneCheckFailure.Timeout, rig.check.run(owner, AgentId.CLAUDE).failure)
        rig.host.authResult = { _, _, _ -> AuthProjection(AuthState.SignedIn).ok() }
        assertTrue(rig.check.run(owner, AgentId.CLAUDE).passed)
    }

    @Test
    fun `B-LINUX-134 no architecture fails before anything runs`() = runTest {
        val rig = Rig(abi = null)
        val r = rig.check.run(owner, codex)
        assertEquals(PhoneCheckFailure.WrongArchitecture, r.failure)
        assertTrue(rig.host.calls.isEmpty())
        assertFalse(rig.check.checkInFlight)
    }

    @Test
    fun `B-LINUX-129 an older Claude that runs reads as installed and asks for a check`() = runTest {
        val rig = Rig(Abi.Arm64V8a, running = true)
        rig.installed(AgentId.CLAUDE, "2.1.200")
        val i = (GateInspector(rig.prefs, rig.host).inspect(owner, AgentId.CLAUDE) as Outcome.Ok).value
        assertTrue(i.installed)
        assertFalse(i.architectureQualified)
        assertEquals(CheckRequiredReason.FirstCheck, i.checkRequiredReason)
    }

    @Test
    fun `B-LINUX-133 a qualified older version keeps working behind the pin, and a changed payload invalidates proof`() = runTest {
        val rig = Rig(Abi.Arm64V8a, running = false)
        val claude = catalog.byId(AgentId.CLAUDE)!!
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        val oldFp = io.github.eslamasabry.oc.engine.agents.json.AgentJson.encodeStrings(
            listOf("claude", "2.1.294", sha, "0.11.2", pins.paseo.packageLockSha256, "v24.21.0"),
        )
        rig.prefs.values["oc.agentPhoneGate.owner"] =
            "{\"claude\":{\"fingerprint\":${io.github.eslamasabry.oc.engine.agents.json.AgentJson.encodeStrings(listOf(oldFp)).removeSurrounding("[", "]")},\"architecture\":\"arm64\",\"activeVersion\":\"2.1.294\"}}"
        rig.installed(AgentId.CLAUDE, "2.1.294")
        val behind = (GateInspector(rig.prefs, rig.host).inspect(owner, AgentId.CLAUDE) as Outcome.Ok).value
        assertTrue(behind.architectureQualified)
        assertTrue(behind.behindPin)
        assertNull(behind.checkRequiredReason)
        assertTrue(behind.stoppedInBackground)
        // A different installed version than the old proof names: no proof.
        rig.installed(AgentId.CLAUDE, "2.1.283")
        val moved = (GateInspector(rig.prefs, rig.host).inspect(owner, AgentId.CLAUDE) as Outcome.Ok).value
        assertFalse(moved.architectureQualified)
        assertEquals(CheckRequiredReason.AgentVersion, moved.checkRequiredReason)
    }

    @Test
    fun `B-LINUX-140 a passed check plus a signed-in read makes a ready row, and a stopped helper offers Resume`() = runTest {
        val rig = Rig(Abi.Arm64V8a)
        rig.installed(codex, "0.166.0")
        assertTrue(rig.check.run(owner, codex).passed)
        val server = io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities()
        val descriptor = catalog.byId(codex)!!
        val signedIn = io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase.SignedIn
        val running = (GateInspector(rig.prefs, rig.host).inspect(owner, codex) as Outcome.Ok).value
        val ready = io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness.row(descriptor, AgentArchitecture.Arm64, server, running.toRuntime(signedIn))
        assertEquals(io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentStatus.Ready, ready.status)
        assertTrue(ready.chatSelectable)
        rig.host.statusFlow.value = rig.host.statusFlow.value.copy(running = false)
        val stopped = (GateInspector(rig.prefs, rig.host).inspect(owner, codex) as Outcome.Ok).value
        val row = io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness.row(descriptor, AgentArchitecture.Arm64, server, stopped.toRuntime(signedIn))
        assertEquals(io.github.eslamasabry.oc.engine.agents.readiness.AgentFixAction.Resume, row.fixAction)
        assertFalse(row.chatSelectable)
    }
}
