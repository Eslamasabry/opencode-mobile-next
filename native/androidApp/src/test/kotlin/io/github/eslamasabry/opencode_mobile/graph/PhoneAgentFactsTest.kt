package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Agents page's tiles show what is known at once (w4-probes): the last answers, kept across app starts, stand in
 * while the slow checks run again; a recent "signed in" is reused instead of starting the agent's program again; a
 * person's step on an agent forgets that agent's answers only.
 */
class PhoneAgentFactsTest {
    private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!
    private val pinned = claude.recipe!!.version

    @Test
    fun `the last answers survive an app start and draw a ready Claude Code tile before any check runs`() {
        val read =
            PhoneAgentFacts.of(mapOf("installed" to true, "foundVersion" to pinned), AuthProjection(AuthState.SignedIn))
        val kept = PhoneAgentFacts.decode(PhoneAgentFacts.encode(mapOf("claude" to read)))["claude"]

        // What the tile is built from, with no version or sign-in check started.
        val version = PhoneAgentFacts.version(kept)
        val present = PhoneAgentRules.present(null, version)
        val signIn = PhoneAgentFacts.signIn(kept)
        assertEquals(true, present)
        assertEquals(AuthState.SignedIn, signIn?.state)
        assertEquals(
            PhoneAgentStatus.Ready,
            PhoneAgentRules.status(
                claude,
                AgentArchitecture.Arm64,
                installed = true,
                payloadPresent = false,
                qualified =
                    PhoneAgentRules.qualified(
                        claude,
                        AgentArchitecture.Arm64,
                        null,
                        PhoneAgentRules.foundVersion(version),
                    ),
                signIn = AgentSignInPhase.SignedIn,
            ),
        )
    }

    @Test
    fun `an error is never kept, nor an account name`() {
        val fact =
            PhoneAgentFacts.of(
                mapOf("installed" to true, "foundVersion" to pinned),
                AuthProjection(AuthState.Error, error = AuthProbeError.TimedOut),
            )
        assertNull(fact.signedIn)
        val text = PhoneAgentFacts.encode(mapOf("codex" to fact, "kilo" to PhoneAgentFacts.of(null, null)))
        assertFalse("kilo" in PhoneAgentFacts.decode(text), "nothing known, nothing kept")
        assertFalse(text.contains("account"))
    }

    @Test
    fun `unreadable kept text is no facts`() {
        assertTrue(PhoneAgentFacts.decode("not json").isEmpty())
        assertTrue(PhoneAgentFacts.decode(null).isEmpty())
        assertNull(PhoneAgentFacts.decode("{\"claude\":{\"installed\":true,\"found\":\"$(id)\"}}")["claude"]?.found)
    }

    @Test
    fun `a step on one agent forgets only that agent, and an install keeps its sign-in`() {
        val kept =
            mapOf(
                "claude" to PhoneAgentFacts.Fact(installed = true, found = pinned, signedIn = true),
                "codex" to PhoneAgentFacts.Fact(installed = true, found = "0.1.0", signedIn = false),
            )

        val removed = PhoneAgentFacts.forget(kept, "codex", version = true, signIn = true)
        assertEquals(kept["claude"], removed["claude"])
        assertEquals(PhoneAgentFacts.Fact(null, null, null), removed["codex"])

        val installing = PhoneAgentFacts.forget(kept, "claude", version = true, signIn = false)
        assertEquals(PhoneAgentFacts.Fact(null, null, signedIn = true), installing["claude"])
        assertEquals(kept["codex"], installing["codex"])

        // A read that could not answer for an agent leaves its kept fact; one that could replaces it.
        val merged =
            PhoneAgentFacts.merge(
                kept,
                mapOf(
                    "claude" to PhoneAgentFacts.Fact(null, null, null),
                    "codex" to PhoneAgentFacts.Fact(installed = true, found = "0.1.0", signedIn = true),
                ),
            )
        assertEquals(kept["claude"], merged["claude"])
        assertEquals(true, merged["codex"]?.signedIn)
    }

    @Test
    fun `a recent signed-in answer is reused, an old one or a signed-out one is checked again`() {
        val signedIn = AuthProjection(AuthState.SignedIn)
        assertTrue(PhoneAgentRules.reuseSignIn(signedIn, 0))
        assertTrue(PhoneAgentRules.reuseSignIn(signedIn, PhoneAgentRules.SIGN_IN_REUSE_MS - 1))
        assertFalse(PhoneAgentRules.reuseSignIn(signedIn, PhoneAgentRules.SIGN_IN_REUSE_MS))
        assertFalse(PhoneAgentRules.reuseSignIn(signedIn, null), "a forgotten answer (a step on the agent)")
        assertFalse(PhoneAgentRules.reuseSignIn(signedIn, -1))
        // A person who just signed in comes back to a tile that said "Sign in": that answer is always checked.
        assertFalse(PhoneAgentRules.reuseSignIn(AuthProjection(AuthState.SignedOut), 0))
        assertFalse(PhoneAgentRules.reuseSignIn(AuthProjection(AuthState.Error), 0))
        assertFalse(PhoneAgentRules.reuseSignIn(null, 0))
    }
}
