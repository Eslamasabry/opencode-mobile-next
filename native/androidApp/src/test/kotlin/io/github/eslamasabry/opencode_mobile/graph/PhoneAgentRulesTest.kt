package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
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

    private val claudeThere = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded)
    private val claudeMissing = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.NotInstalled)

    private fun ended(phase: PhoneAgentInstallPhase) = PhoneAgentInstallation("claude", phase, null)

    @Test
    fun `an interrupted or failed install result is dropped once the agent is installed`() {
        assertTrue(PhoneAgentRules.staleResult(ended(PhoneAgentInstallPhase.Interrupted), listOf(claudeThere)))
        assertTrue(PhoneAgentRules.staleResult(ended(PhoneAgentInstallPhase.Failed), listOf(claudeThere)))
    }

    @Test
    fun `an install result for an agent that is still missing, or a running install, stays`() {
        assertFalse(PhoneAgentRules.staleResult(ended(PhoneAgentInstallPhase.Interrupted), listOf(claudeMissing)))
        assertFalse(PhoneAgentRules.staleResult(ended(PhoneAgentInstallPhase.Installing), listOf(claudeThere)))
        assertFalse(PhoneAgentRules.staleResult(null, listOf(claudeThere)))
    }

    @Test
    fun `only the saved Phone agents server of its owner is the helper's`() {
        assertTrue(PhoneAgentRules.isHelperProfile("abc-agents", "abc"))
        assertFalse(PhoneAgentRules.isHelperProfile("abc-agents", null))
        assertFalse(PhoneAgentRules.isHelperProfile("abc-agents", "other"))
        assertFalse(PhoneAgentRules.isHelperProfile("abc", "abc"))
    }

    // ONE-PHONE (owner 2026-11-11, "This phone" and "Phone agents" side by side): the helper is part of the phone.
    @Test
    fun `ONE-PHONE a saved choice of the helper means the phone it belongs to`() {
        val saved = setOf("phone", "phone-agents", "laptop")
        // The app's own OpenCode first, else the owner the helper was saved for.
        assertEquals("phone", PhoneAgentRules.phoneOf("phone-agents", "phone", "phone", saved))
        assertEquals("phone", PhoneAgentRules.phoneOf("phone-agents", "phone", null, saved))
        assertEquals("laptop", PhoneAgentRules.phoneOf("old-agents", "old", "laptop", saved + "old-agents"))
        // Not the helper, or no phone left to belong to: nothing to map.
        assertEquals(null, PhoneAgentRules.phoneOf("laptop", "laptop", "phone", saved))
        assertEquals(null, PhoneAgentRules.phoneOf("phone-agents", null, "phone", saved))
        assertEquals(null, PhoneAgentRules.phoneOf("gone-agents", "gone", null, setOf("gone-agents")))
        // The helper never maps to itself.
        assertEquals(null, PhoneAgentRules.phoneOf("x-agents", "x", "x-agents", setOf("x-agents")))
    }

    // The owner's phone today: "phone" (This phone), its helper "phone-agents" saved as "Phone agents", and a laptop.
    private val saved = listOf("phone", "phone-agents", "laptop")
    private val helpers = mapOf("phone-agents" to "phone", "old-agents" to null)

    private fun isHelper(id: String) = id in helpers

    private fun phoneOf(id: String) = helpers[id]

    @Test
    fun `ONE-PHONE at start a saved choice of the helper opens on its phone, any other choice stays`() {
        assertEquals("phone", PhoneAgentRules.homeServer("phone-agents", saved, ::isHelper, ::phoneOf))
        assertEquals("laptop", PhoneAgentRules.homeServer("laptop", saved, ::isHelper, ::phoneOf))
        assertEquals("phone", PhoneAgentRules.homeServer("phone", saved, ::isHelper, ::phoneOf))
        // Nothing chosen: the first saved server, never the helper even when it was saved first.
        assertEquals("phone", PhoneAgentRules.homeServer(null, listOf("phone-agents", "phone"), ::isHelper, ::phoneOf))
        // A helper whose phone is gone: the first real server; with none, nothing to open (never the helper).
        assertEquals("laptop", PhoneAgentRules.homeServer("old-agents", listOf("old-agents", "laptop"), ::isHelper, ::phoneOf))
        assertNull(PhoneAgentRules.homeServer("old-agents", listOf("old-agents"), ::isHelper, ::phoneOf))
        assertNull(PhoneAgentRules.homeServer(null, emptyList(), ::isHelper, ::phoneOf))
    }

    @Test
    fun `ONE-PHONE removing This phone removes its helper, removing anything else leaves it`() {
        assertEquals(listOf("phone-agents"), PhoneAgentRules.removedWith("phone", saved, ::phoneOf))
        assertEquals(emptyList<Any>(), PhoneAgentRules.removedWith("laptop", saved, ::phoneOf))
        // The helper's own id never lists itself (a direct removal of it is the one removal).
        assertEquals(emptyList<Any>(), PhoneAgentRules.removedWith("phone-agents", saved, ::phoneOf))
        // A helper with no phone is not swept by some other removal.
        assertEquals(emptyList<Any>(), PhoneAgentRules.removedWith("laptop", saved + "old-agents", ::phoneOf))
    }

    @Test
    fun `ONE-PHONE the helper carries its phone's name, so nothing names it Phone agents`() {
        val names = listOf("phone" to "This phone", "phone-agents" to "Phone agents", "laptop" to "Laptop", "old-agents" to "Phone agents")
        assertEquals(listOf("phone-agents" to "This phone"), PhoneAgentRules.helperNames(names, ::phoneOf))
        // The person renamed the phone: the helper follows; already named: nothing to write.
        assertEquals(
            listOf("phone-agents" to "Pixel"),
            PhoneAgentRules.helperNames(listOf("phone" to "Pixel", "phone-agents" to "This phone"), ::phoneOf),
        )
        assertEquals(emptyList<Any>(), PhoneAgentRules.helperNames(listOf("phone" to "Pixel", "phone-agents" to "Pixel"), ::phoneOf))
    }

    @Test
    fun `ONE-PHONE the phone agents' folder list never makes the whole list single-project`() {
        assertTrue(PhoneAgentRules.listsAcrossProjects(globalSessionSearch = false, helper = true))
        assertTrue(PhoneAgentRules.listsAcrossProjects(globalSessionSearch = true, helper = false))
        // A real server that cannot list across projects still says so.
        assertFalse(PhoneAgentRules.listsAcrossProjects(globalSessionSearch = false, helper = false))
    }

    // G3: a Ready tile never changes when the helper is dead.
    @Test
    fun `a proved signed-in agent whose helper is not running is stopped, not ready`() {
        val q = PhoneAgentRules.qualified(codex, arm, null, codex.recipe!!.version)
        assertEquals(PhoneAgentStatus.Stopped, PhoneAgentRules.status(codex, arm, true, true, q, AgentSignInPhase.SignedIn, helperRunning = false))
        assertEquals(PhoneAgentStatus.Ready, PhoneAgentRules.status(codex, arm, true, true, q, AgentSignInPhase.SignedIn, helperRunning = true))
        assertEquals(PhoneAgentStatus.Ready, PhoneAgentRules.status(codex, arm, true, true, q, AgentSignInPhase.SignedIn, helperRunning = null))
    }

    @Test
    fun `an agent without proof asks for the phone check before it can be called stopped`() {
        assertEquals(
            PhoneAgentStatus.PhoneCheck,
            PhoneAgentRules.status(claude, arm, true, true, false, AgentSignInPhase.SignedIn, helperRunning = false),
        )
    }
}
