package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner report 2026-11-14: after an app update installed over the running one, New conversation's "Who works on it"
 * listed OpenCode and Claude Code only; Codex and Antigravity, installed and signed in, were missing until the Agents
 * page (which starts the agent helper) had been opened and a conversation started there. The phone's agent registry
 * (`oc.phoneAgentFacts.<owner>`, [PhoneAgentFacts]) is the reference every list is built from, with the real rules.
 */
class AgentRegistryTest {
    private val arm = AgentArchitecture.Arm64
    private val claude = agent("claude")
    private val codex = agent("codex")
    private val antigravity = agent("antigravity")

    private fun agent(id: String): AgentDescriptor = AgentCatalog.builtIn.byId(AgentId(id))!!

    /** The copy the previous build pinned: any version but this build's pin. */
    private fun older(agent: AgentDescriptor): String = "0.0.1-${agent.id.raw}"

    /**
     * One row as AppPhoneAgents.snapshot builds it from the registry while no check has answered yet: the kept version
     * and sign-in stand in, the payload folder is there, no phone check proof was ever stored (the agents were
     * installed by the app at their pin, which needs none), and [helperRunning] is the helper's status record.
     */
    private fun row(agent: AgentDescriptor, fact: PhoneAgentFacts.Fact?, helperRunning: Boolean?): PhoneAgentRow {
        val version = PhoneAgentFacts.version(fact)
        val present = PhoneAgentRules.present(true, version)
        val signIn =
            when {
                agent.signInMethod == AgentSignInMethod.None -> AgentSignInPhase.SignedIn
                !AgentAuthScripts.checksSignIn(agent.id) -> AgentSignInPhase.Failed
                else -> PhoneAgentFacts.signIn(fact)?.let(PhoneAgentRules::phaseOf)
            }
        val found = PhoneAgentRules.foundVersion(version)
        val status =
            PhoneAgentRules.status(
                agent,
                arm,
                installed = present == true,
                payloadPresent = true,
                qualified = PhoneAgentRules.qualified(agent, arm, null, found, record = fact),
                signIn = signIn,
                helperRunning = helperRunning,
            )
        return PhoneAgentRow(agent.id.raw, agent.name, status, installedVersion = found)
    }

    private fun offer(state: PhoneAgentsState) =
        PhoneChatAgents.offer(NewConversationPhoneRules.facts(state), AgentId.OPENCODE)

    /** The registry the build before the update wrote: each agent installed at that build's pin and signed in. */
    private fun recordBeforeUpdate(): String =
        listOf(claude, codex, antigravity).joinToString(",", "{", "}") {
            "\"${it.id.raw}\":{\"installed\":true,\"found\":\"${older(it)}\",\"signedIn\":true}"
        }

    @Test
    fun `B-AGENT-REG-010 an app update that moves the pins keeps every installed signed-in agent a choice`() {
        // The updated app's first read of the versions (readForUpdate): each copy runs, none is this build's pin.
        val kept = PhoneAgentFacts.decode(recordBeforeUpdate())
        val afterRead =
            PhoneAgentFacts.merge(
                kept,
                kept.mapValues { (_, fact) ->
                    PhoneAgentFacts.of(
                            mapOf("installed" to false, "foundVersion" to fact.found),
                            null,
                            AgentPins.builtIn.paseo.version,
                            2L,
                        )
                        .copy(signedIn = fact.signedIn)
                },
            )
        val reread = PhoneAgentFacts.decode(PhoneAgentFacts.encode(afterRead))

        // The first frame reads the record as the old build left it; later frames read it after the version read.
        // Restarted by the update, the helper does not run until something starts it.
        for ((moment, record) in listOf("first frame" to kept, "after the version read" to reread)) {
            for (helperRunning in listOf(false, null, true)) {
                val rows = listOf(claude, codex, antigravity).map { row(it, record[it.id.raw], helperRunning) }
                rows.forEach {
                    assertTrue(
                        PhoneAgentRules.pastCheck(it.status),
                        "$moment: ${it.id} ${it.status}, helper $helperRunning",
                    )
                }
                val state = PhoneAgentsState(available = true, agents = rows.toImmutableList())
                assertEquals(
                    listOf("claude", "codex", "antigravity"),
                    offer(state).choices.map { it.id.raw },
                    "$moment, helper running: $helperRunning",
                )
            }
        }
    }

    @Test
    fun `B-AGENT-REG-011 an agent that needs a phone check is a dimmed set-up row even without a matrix row`() {
        // Installed at a version nothing proved (no record, no gate): it asks for the phone check.
        val fact = PhoneAgentFacts.Fact(installed = false, found = older(antigravity), signedIn = true)
        val rows = listOf(row(antigravity, fact, helperRunning = true))
        assertEquals(PhoneAgentStatus.PhoneCheck, rows.single().status)
        val offer = offer(PhoneAgentsState(available = true, agents = rows.toImmutableList()))
        assertEquals(emptyList(), offer.choices.map { it.id.raw })
        assertEquals(listOf("antigravity"), offer.needsSetup.map { it.id.raw })
        assertFalse(offer.needsSetup.single().checking)
    }

    @Test
    fun `B-AGENT-REG-012 an agent being updated stays listed and reads checking`() {
        val rows =
            listOf(PhoneAgentRow("codex", "Codex", PhoneAgentStatus.Ready, installedVersion = older(codex)))
        val state =
            PhoneAgentsState(
                available = true,
                agents = rows.toImmutableList(),
                installation = PhoneAgentInstallation("codex", PhoneAgentInstallPhase.Installing, 0.4),
            )
        val offer = offer(state)
        assertEquals(emptyList(), offer.choices.map { it.id.raw }, "its files are being replaced: not a choice now")
        assertEquals(listOf("codex"), offer.needsSetup.map { it.id.raw })
        assertTrue(offer.needsSetup.single().checking)
    }

    @Test
    fun `B-AGENT-REG-013 a proof holds for the version that ran under a helper this app trusts, and only then`() {
        val pins = AgentPins.builtIn
        val proof =
            PhoneAgentFacts.proven(PhoneAgentFacts.Fact(true, "1.0.0", true), "1.0.0", pins.paseo.version, 1L)
        assertTrue(PhoneAgentFacts.provenRuns(proof, "1.0.0", pins))
        assertFalse(PhoneAgentFacts.provenRuns(proof, "1.0.1", pins), "another version runs now")
        assertFalse(PhoneAgentFacts.provenRuns(proof, null, pins))
        val testedHelper = pins.paseoTestedVersions.first { it != pins.paseo.version }
        // A tested helper's proof carries only while the pinned helper is tested too (a pin not yet proved is not).
        assertEquals(
            pins.paseo.version in pins.paseoTestedVersions,
            PhoneAgentFacts.provenRuns(proof.copy(provenHelper = testedHelper), "1.0.0", pins),
        )
        assertFalse(PhoneAgentFacts.provenRuns(proof.copy(provenHelper = "0.0.1"), "1.0.0", pins), "untested helper")
        assertFalse(PhoneAgentFacts.provenRuns(PhoneAgentFacts.Fact(false, "1.0.0", true), "1.0.0", pins), "no proof")
        // A record from before the proof was written: its pinned copy that ran is the proof.
        assertTrue(PhoneAgentFacts.provenRuns(PhoneAgentFacts.Fact(true, "1.0.0", true), "1.0.0", pins))
    }

    @Test
    fun `B-AGENT-REG-014 the registry keeps its proof and answer times across reads, and a removal drops the proof`() {
        val first = PhoneAgentFacts.of(mapOf("installed" to true, "foundVersion" to "1.0.0"), null, "0.17.3", 7L, 99L)
        assertEquals("1.0.0", first.proven, "a pinned copy that runs is proven by its install")
        assertEquals("0.17.3", first.provenHelper)
        assertEquals(7L, first.provenBuild)
        assertNull(first.signedInAt, "no sign-in answer, no time for it")
        val text = PhoneAgentFacts.encode(mapOf("codex" to first.copy(signedIn = true, signedInAt = 99L)))
        val kept = PhoneAgentFacts.decode(text)
        assertEquals(first.copy(signedIn = true, signedInAt = 99L), kept["codex"])

        // A later read finds the copy behind a moved pin and does not read the sign-in: proof and answer time stay.
        val behind = PhoneAgentFacts.of(mapOf("installed" to false, "foundVersion" to "1.0.0"), null)
        val merged = PhoneAgentFacts.merge(kept, mapOf("codex" to behind.copy(signedIn = true)))["codex"]!!
        assertEquals("1.0.0", merged.proven)
        assertEquals(99L, merged.signedInAt)
        assertEquals(false, merged.installed)

        // An install's step keeps the proof (the copy that ran was proven); a removal or a fresh check does not.
        assertEquals("1.0.0", PhoneAgentFacts.forget(kept, "codex", version = true, signIn = false)["codex"]?.proven)
        val removed = PhoneAgentFacts.forget(kept, "codex", version = true, signIn = true, proof = true)["codex"]!!
        assertNull(removed.proven)
        assertFalse(removed.known)
        assertFalse(text.contains("account") || text.contains("token"), "answers only")
    }

    @Test
    fun `B-AGENT-REG-015 deleting the owner's profile sweeps its agent registry`() {
        val keys = listOf(PhoneAgentFacts.key("one"), PhoneAgentFacts.key("two"), "oc.profiles")
        assertEquals("oc.phoneAgentFacts.one", PhoneAgentFacts.key("one"))
        assertEquals(setOf("oc.phoneAgentFacts.one"), ProfileKeyRule.scopedKeys("one", keys, setOf("one", "two")))
    }
}
