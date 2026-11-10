package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneAgentRulesTest {
    private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!
    private val codex = AgentCatalog.builtIn.byId(AgentId("codex"))!!
    private val arm = AgentArchitecture.Arm64

    // Owner report 2026-11-11: Claude Code was installed but its card said "Not installed · 340 MB".
    @Test
    fun `a Claude Code that runs is installed even though the inventory has no folder for it`() {
        assertEquals(true, PhoneAgentRules.present(false, mapOf("installed" to false, "foundVersion" to "2.1.316")))
        assertEquals(true, PhoneAgentRules.present(false, mapOf("installed" to true)))
    }

    @Test
    fun `a version other than the pin is still installed, and nothing found leaves the inventory's answer`() {
        assertTrue(PhoneAgentRules.runs(mapOf("installed" to false, "foundVersion" to "2.1.200")))
        assertEquals(false, PhoneAgentRules.present(false, mapOf("installed" to false, "foundVersion" to null)))
        assertEquals(true, PhoneAgentRules.present(true, mapOf("installed" to false)))
        assertNull(PhoneAgentRules.present(null, null))
    }

    @Test
    fun `only Claude Code and agents with a payload need a version probe`() {
        assertTrue(PhoneAgentRules.needsVersionProbe(AgentId.CLAUDE, false))
        assertTrue(PhoneAgentRules.needsVersionProbe(AgentId("codex"), true))
        assertFalse(PhoneAgentRules.needsVersionProbe(AgentId("codex"), false))
    }

    // Owner report: every installed, signed-in agent said "Phone check needed".
    @Test
    fun `an installed signed-in agent at the pinned version is ready`() {
        val q = PhoneAgentRules.qualified(codex, arm, null, codex.recipe!!.version)
        assertEquals(PhoneAgentStatus.Ready, PhoneAgentRules.status(codex, arm, true, true, q, AgentSignInPhase.SignedIn))
    }

    @Test
    fun `a signed-out agent says sign in, an unread one says checking, an unproved one asks for the phone check`() {
        assertEquals(PhoneAgentStatus.SignInNeeded, PhoneAgentRules.status(codex, arm, true, true, true, AgentSignInPhase.SignedOut))
        assertEquals(PhoneAgentStatus.Checking, PhoneAgentRules.status(codex, arm, true, true, true, null))
        assertEquals(PhoneAgentStatus.PhoneCheck, PhoneAgentRules.status(claude, arm, true, true, false, AgentSignInPhase.SignedIn))
        assertEquals(PhoneAgentStatus.NotInstalled, PhoneAgentRules.status(codex, arm, false, false, false, null))
        assertEquals(PhoneAgentStatus.Unavailable, PhoneAgentRules.status(codex, arm, true, true, true, AgentSignInPhase.Failed))
    }

    @Test
    fun `an older tested Claude Code with proof from the Flutter app stays ready, one without proof asks for the check`() {
        val older = "2.1.316"
        assertFalse(PhoneAgentRules.qualified(claude, arm, null, older))
        assertFalse(PhoneAgentRules.qualified(claude, arm, null, null))
    }

    // Owner report: the page asked a sign-in process every 2 s during an install and Android killed the install.
    @Test
    fun `no sign-in probe starts while an install runs`() {
        assertFalse(PhoneAgentRules.mayProbeSignIn(installRunning = true))
        assertTrue(PhoneAgentRules.mayProbeSignIn(installRunning = false))
    }

    @Test
    fun `a step whose own check passes is not run again`() {
        val exits = mapOf("node" to 0, "paseo" to 1, "agent" to null)
        val skipped = PhoneAgentRules.satisfiedSteps(mapOf("agent-node" to "node", "agent-paseo" to "paseo", "agent-claude" to "agent")) { exits[it] }
        assertEquals(setOf("agent-node"), skipped)
    }

    @Test
    fun `the primary ABI decides, so an x86 emulator that also lists arm64 is x64`() {
        assertEquals(AgentArchitecture.X64, PhoneAgentRules.architectureOf(listOf("x86_64", "arm64-v8a")))
        assertEquals(AgentArchitecture.Arm64, PhoneAgentRules.architectureOf(listOf("arm64-v8a", "armeabi-v7a")))
        assertNull(PhoneAgentRules.architectureOf(emptyList()))
        assertNull(PhoneAgentRules.architectureOf(listOf("armeabi-v7a")))
    }
}
