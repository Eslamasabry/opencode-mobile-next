package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.phoneAgentStartsConversation
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-11-15 (their phone): "when oh my pi needs a setup after installation why show new conversation? instead
 * of setup cta". An agent whose sign-in no check can read (Qwen Code, Goose, Oh My Pi) and that the app never saw set
 * up leads with Set up: no choice in New conversation, a dimmed "Set up" row there instead. The registry
 * (`oc.phoneAgentFacts.<owner>`, [PhoneAgentFacts.Fact.setUp]) learns it once the set-up terminal ended or a
 * conversation got an answer; an update keeps it, a removal, a sign-out and a reset of the saved sign-ins drop it.
 */
class SetUpFirstTest {
    private val x64 = AgentArchitecture.X64
    private val omp = agent("omp-acp")

    private fun agent(id: String): AgentDescriptor = AgentCatalog.builtIn.byId(AgentId(id))!!

    /** The row AppPhoneAgents.snapshot builds for a pinned copy just installed, with the registry's [fact]. */
    private fun row(agent: AgentDescriptor, fact: PhoneAgentFacts.Fact?, helperRunning: Boolean?): PhoneAgentRow {
        val found = agent.recipe!!.version
        val setUpFirst = PhoneAgentRules.setUpFirst(agent, seen = fact?.setUp == true)
        val status =
            PhoneAgentRules.status(
                agent,
                x64,
                installed = true,
                payloadPresent = true,
                qualified = PhoneAgentRules.qualified(agent, x64, null, found, record = fact),
                // No check can read its sign-in: the same answer every time (AppPhoneAgents.knownSignIn).
                signIn = AgentSignInPhase.Failed,
                helperRunning = helperRunning,
                setUpFirst = setUpFirst,
            )
        return PhoneAgentRow(
            agent.id.raw,
            agent.name,
            status,
            removable = true,
            signInAvailable = true,
            installedVersion = found,
            needsSetUp = setUpFirst && status == PhoneAgentStatus.SetUpAtFirstUse,
        )
    }

    private fun offer(vararg rows: PhoneAgentRow) =
        PhoneChatAgents.offer(
            NewConversationPhoneRules.facts(PhoneAgentsState(available = true, agents = persistentListOf(*rows))),
            AgentId.OPENCODE,
        )

    @Test
    fun `B-AGENT-SETUP-010 a fresh Oh My Pi is a dimmed Set up row in New conversation, also with the helper down`() {
        for (helperRunning in listOf(true, false, null)) {
            val fresh = row(omp, fact = null, helperRunning = helperRunning)
            assertEquals(PhoneAgentStatus.SetUpAtFirstUse, fresh.status, "helper running: $helperRunning")
            assertTrue(fresh.needsSetUp, "helper running: $helperRunning")
            assertFalse(phoneAgentStartsConversation(fresh))
            assertFalse(NewConversationPhoneRules.startsHere(fresh))
            val offer = offer(fresh)
            assertEquals(emptyList(), offer.choices.map { it.id.raw }, "not a choice whose first answer fails")
            assertEquals(listOf("omp-acp"), offer.needsSetup.map { it.id.raw }, "a dimmed Set up row")
            assertFalse(offer.needsSetup.single().checking)
        }
    }

    @Test
    fun `B-AGENT-SETUP-011 once seen set up it is a normal choice, and Resume again while the helper is down`() {
        val seen = PhoneAgentFacts.seenSetUp(emptyMap(), "omp-acp")["omp-acp"]
        val running = row(omp, seen, helperRunning = true)
        assertEquals(PhoneAgentStatus.SetUpAtFirstUse, running.status)
        assertFalse(running.needsSetUp)
        assertTrue(phoneAgentStartsConversation(running))
        assertEquals(listOf("omp-acp"), offer(running).choices.map { it.id.raw })
        assertEquals(emptyList(), offer(running).needsSetup.map { it.id.raw })
        // As before this change: a stopped helper reads Stopped, and New conversation's start starts it.
        val stopped = row(omp, seen, helperRunning = false)
        assertEquals(PhoneAgentStatus.Stopped, stopped.status)
        assertEquals(listOf("omp-acp"), offer(stopped).choices.map { it.id.raw })
    }

    @Test
    fun `I147-FIRST-RUN New conversation words an agent set up at first use as the Agents page does, never Ready`() {
        val seen = PhoneAgentFacts.seenSetUp(emptyMap(), "omp-acp")["omp-acp"]
        val state = PhoneAgentsState(available = true, agents = persistentListOf(row(omp, seen, helperRunning = true)))
        // A choice (it can start), but the Agents page words it "Installed. Set it up the first time you use it."
        assertEquals(listOf("omp-acp"), offer(*state.agents.toTypedArray()).choices.map { it.id.raw })
        assertEquals(setOf("omp-acp"), NewConversationPhoneRules.setUpAtFirstUse(state))
        val fresh = PhoneAgentsState(available = true, agents = persistentListOf(row(omp, null, helperRunning = true)))
        assertEquals(setOf("omp-acp"), NewConversationPhoneRules.setUpAtFirstUse(fresh))
        val ready = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready)
        assertEquals(emptySet(), NewConversationPhoneRules.setUpAtFirstUse(PhoneAgentsState(available = true, agents = persistentListOf(ready))))
    }

    @Test
    fun `B-AGENT-SETUP-012 the set-up comes first only for agents no check can read, until seen set up`() {
        for (id in listOf("qwen", "goose", "omp-acp")) {
            assertTrue(PhoneAgentRules.setUpFirst(agent(id), seen = false), id)
            assertFalse(PhoneAgentRules.setUpFirst(agent(id), seen = true), id)
        }
        for (id in listOf("claude", "codex", "antigravity"))
            assertFalse(PhoneAgentRules.setUpFirst(agent(id), seen = false), "$id has its own sign-in check")
    }

    @Test
    fun `B-AGENT-SETUP-013 the fact is written once, kept across app starts, and survives an update of the agent`() {
        val pin = omp.recipe!!.version
        val installed = PhoneAgentFacts.of(mapOf("installed" to true, "foundVersion" to pin), null, "0.17.3", 7L)
        val seen = PhoneAgentFacts.seenSetUp(mapOf("omp-acp" to installed), "omp-acp")
        assertTrue(seen.getValue("omp-acp").setUp)
        assertEquals(pin, seen.getValue("omp-acp").proven, "the rest of what is known stays")
        assertEquals(seen, PhoneAgentFacts.seenSetUp(seen, "omp-acp"), "said twice, kept once")
        assertEquals(setOf("omp-acp"), PhoneAgentFacts.setUp(seen))
        // Kept across app starts.
        val reread = PhoneAgentFacts.decode(PhoneAgentFacts.encode(seen))
        assertEquals(seen, reread)
        // A set-up of an agent the record knew nothing about yet is kept on its own.
        val alone = PhoneAgentFacts.encode(PhoneAgentFacts.seenSetUp(emptyMap(), "goose"))
        assertEquals(setOf("goose"), PhoneAgentFacts.setUp(PhoneAgentFacts.decode(alone)))

        // An update: its install forgets the version, then the next read finds the new copy (behind or at a new pin).
        val updating = PhoneAgentFacts.forget(reread, "omp-acp", version = true, signIn = false)
        assertTrue(updating.getValue("omp-acp").setUp, "an install or update keeps it")
        val afterUpdate =
            PhoneAgentFacts.merge(
                updating,
                mapOf(
                    "omp-acp" to
                        PhoneAgentFacts.of(mapOf("installed" to true, "foundVersion" to "99.0.0"), null, "0.17.3", 8L)
                ),
            )
        assertTrue(afterUpdate.getValue("omp-acp").setUp, "the owner is not asked to set up again after an update")
        assertFalse(PhoneAgentRules.setUpFirst(omp, seen = afterUpdate.getValue("omp-acp").setUp))
        // The phone check starting over drops the proof, not the set-up.
        val rechecked = PhoneAgentFacts.forget(afterUpdate, "omp-acp", version = false, signIn = false, proof = true)
        assertTrue(rechecked.getValue("omp-acp").setUp)
    }

    @Test
    fun `B-AGENT-SETUP-014 a removal, a sign-out and a reset of the saved sign-ins clear it`() {
        val seen =
            PhoneAgentFacts.seenSetUp(
                mapOf(
                    "omp-acp" to PhoneAgentFacts.Fact(installed = true, found = omp.recipe!!.version, signedIn = null),
                    "goose" to PhoneAgentFacts.Fact(installed = true, found = "1.0.0", signedIn = null, setUp = true),
                ),
                "omp-acp",
            )
        // AppPhoneAgents.removeLocked: a removal that removed.
        val removed = PhoneAgentFacts.forget(seen, "omp-acp", version = true, signIn = true, proof = true, setUp = true)
        assertFalse(removed.getValue("omp-acp").setUp)
        assertTrue(removed.getValue("goose").setUp, "other agents keep theirs")
        assertTrue(PhoneAgentRules.setUpFirst(omp, seen = removed.getValue("omp-acp").setUp), "installed again: Set up")
        // AppPhoneAgents.signOut.
        val signedOut = PhoneAgentFacts.forget(seen, "omp-acp", version = false, signIn = true, setUp = true)
        assertFalse(signedOut.getValue("omp-acp").setUp)
        // AppPhoneAgents.resetSavedSignIns: every agent's.
        val reset = PhoneAgentFacts.forgetSignIns(seen)
        assertEquals(emptySet(), PhoneAgentFacts.setUp(reset))
        assertEquals(omp.recipe!!.version, reset.getValue("omp-acp").found, "versions stay")
    }

    @Test
    fun `B-AGENT-SETUP-015 a set-up an earlier build kept on its own list is read in, not asked again`() {
        val kept = mapOf("qwen" to PhoneAgentFacts.Fact(installed = true, found = "0.26.4", signedIn = null))
        val folded = PhoneAgentFacts.withEarlierSetUp(kept, listOf("goose", "qwen", "../x"))
        assertEquals(setOf("goose", "qwen"), PhoneAgentFacts.setUp(folded), "agent ids only")
        assertEquals("0.26.4", folded.getValue("qwen").found)
        assertEquals(kept, PhoneAgentFacts.withEarlierSetUp(kept, null))
        // A removal after the fold clears it in the record (and the write drops the earlier list).
        val removed = PhoneAgentFacts.forget(folded, "qwen", version = true, signIn = true, proof = true, setUp = true)
        assertFalse(removed.getValue("qwen").setUp)
    }

    @Test
    fun `B-AGENT-SETUP-016 deleting the owner's profile sweeps the record that holds it, and the earlier list`() {
        val keys =
            listOf(
                PhoneAgentFacts.key("one"),
                AgentKeys.agentSetUp(ProfileId("one")),
                PhoneAgentFacts.key("two"),
                AgentKeys.agentSetUp(ProfileId("two")),
            )
        assertEquals(
            setOf("oc.phoneAgentFacts.one", "oc.agentSetUp.one"),
            ProfileKeyRule.scopedKeys("one", keys, setOf("one", "two")),
        )
        assertTrue(PhoneAgentFacts.encode(PhoneAgentFacts.seenSetUp(emptyMap(), "omp-acp")).contains("\"setUp\":true"))
    }

    @Test
    fun `I147-FIRST-RUN a signed-out agent is no Ready choice and is worded Sign in needed like the Agents page`() {
        val claude = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded, installable = true, signInAvailable = true)
        val state = PhoneAgentsState(available = true, agents = persistentListOf(claude))
        val offer = PhoneChatAgents.offer(NewConversationPhoneRules.facts(state), AgentId.OPENCODE)
        assertEquals(emptyList(), offer.choices.map { it.id.raw }, "never marked Ready")
        assertEquals(listOf("claude"), offer.needsSetup.map { it.id.raw })
        assertEquals(setOf("claude"), NewConversationPhoneRules.signInNeeded(state))
        val ready = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready)
        assertEquals(emptySet(), NewConversationPhoneRules.signInNeeded(PhoneAgentsState(available = true, agents = persistentListOf(ready))))
    }

    @Test
    fun `I147-FIRST-RUN every catalog agent has its own name in the host agent list, never the generic one`() {
        for (descriptor in AgentCatalog.builtIn.agents)
            assertEquals(
                descriptor.name,
                io.github.eslamasabry.oc.protocol.paseo.requests.paseoHostAgentName(descriptor.providerId),
                "the box placeholder and the model chip of ${descriptor.id.raw} read this name",
            )
    }
}
