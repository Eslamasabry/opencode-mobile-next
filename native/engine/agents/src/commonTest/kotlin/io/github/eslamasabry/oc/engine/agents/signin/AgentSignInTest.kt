package io.github.eslamasabry.oc.engine.agents.signin

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.auth.AgentAuth
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.GuestCommand
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private val OWNER: OwnerId = ProfileId("phone")

private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
}

private class SignInHost : FakeAgentHost() {
    val commands = mutableListOf<Pair<AgentId, List<String>>>()

    override fun signInCommand(owner: OwnerId, agent: AgentId, args: List<String>): Outcome<GuestCommand> {
        commands += agent to args
        return GuestCommand(agent.raw, args.toImmutableList(), persistentMapOf(), "/home/oc/.oc-profiles/${owner.raw}").ok()
    }
}

/** Plan 17 §1.5 "Sign in" (AGENTS-BACKENDS-13, 14); B-AGENT-113 (fixed app commands), B-LINUX-125, B-LINUX-126. */
class AgentSignInTest {
    @Test
    fun `B-AGENT-113 sign-in uses only fixed app commands from the catalog`() {
        val host = SignInHost()
        val signIn = AgentSignIn(host, AgentAuth(host, OWNER, FixedClock))
        val claude = assertIs<SignInPlan.Terminal>(signIn.plan(AgentId.CLAUDE))
        assertEquals("claude", claude.program)
        assertEquals(listOf("auth", "login", "--claudeai"), claude.args)
        assertEquals(listOf("login", "--device-auth"), assertIs<SignInPlan.Terminal>(signIn.plan(AgentId("codex"))).args)
        assertEquals(listOf("login"), assertIs<SignInPlan.Terminal>(signIn.plan(AgentId("fx"))).args)
        assertEquals(listOf("auth", "login"), assertIs<SignInPlan.Terminal>(signIn.plan(AgentId("kilo"))).args)
        assertEquals(listOf("auth"), assertIs<SignInPlan.Terminal>(signIn.plan(AgentId("cline"))).args)
        val omp = assertIs<SignInPlan.Terminal>(signIn.plan(AgentId("omp-acp")))
        assertEquals("omp", omp.program, "Oh My Pi signs in inside its own CLI")
        assertTrue(omp.args.isEmpty())
        assertTrue(assertIs<SignInPlan.Terminal>(signIn.plan(AgentId("gemini"))).args.isEmpty())
    }

    @Test
    fun `B-LINUX-125 every phone agent can open its sign-in, API-key agents are sent to the host instead`() {
        val host = SignInHost()
        val signIn = AgentSignIn(host, AgentAuth(host, OWNER, FixedClock))
        assertEquals(SignInPlan.ProviderKeyOnHost(AgentId("qwen")), signIn.plan(AgentId("qwen")))
        assertEquals(SignInPlan.ProviderKeyOnHost(AgentId("goose")), signIn.plan(AgentId("goose")))
        assertEquals(SignInPlan.NotAvailable(AgentId("opencode2")), signIn.plan(AgentId("opencode2")), "a server is not a phone agent")
        assertEquals(SignInPlan.NotAvailable(AgentId("missing")), signIn.plan(AgentId("missing")))
        for (agent in listOf("claude", "codex", "gemini", "kilo", "cline", "omp-acp", "fx")) {
            assertIs<Outcome.Ok<GuestCommand>>(signIn.command(OWNER, AgentId(agent)), agent)
        }
        assertIs<Outcome.Failed>(signIn.command(OWNER, AgentId("qwen")))
        assertFalse(host.commands.any { it.first.raw == "qwen" })
        assertEquals(listOf("auth", "login", "--claudeai"), host.commands.first().second)
    }

    @Test
    fun `B-AGENT-113 the command allowlist refuses other programs and arguments`() {
        assertTrue(SignInCommandPolicy.allows("claude", listOf("auth", "login", "--claudeai")))
        assertFalse(SignInCommandPolicy.allows("bash", emptyList()))
        assertFalse(SignInCommandPolicy.allows("claude", listOf("a", "b", "c", "d", "e", "f")))
        for (bad in listOf("", "x;rm", "a b", "\$(id)", "a".repeat(33), "--x=y", "ü")) {
            assertFalse(SignInCommandPolicy.allows("codex", listOf(bad)), bad)
        }
        assertNull(SignInCommandPolicy.checked("codex", listOf("x;rm")))
    }

    @Test
    fun `B-LINUX-126 when the terminal ends, signed in only on a SignedIn projection`() = runTest {
        val host = SignInHost()
        var answer = AuthProjection(AuthState.SignedOut)
        host.authResult = { _, _, _ -> answer.ok() }
        val signIn = AgentSignIn(host, AgentAuth(host, OWNER, FixedClock))
        assertFalse(signIn.finish(AgentId.CLAUDE))
        answer = AuthProjection(AuthState.Error)
        assertFalse(signIn.finish(AgentId.CLAUDE))
        answer = AuthProjection(AuthState.SignedIn)
        assertTrue(signIn.finish(AgentId.CLAUDE))
    }

    @Test
    fun `B-AGENT-026 the sign-in foreground failures have their fixed codes and unknown text is never echoed`() {
        val codes = listOf(
            SignInForegroundFailure.Admission("busy"),
            SignInForegroundFailure.ServiceRefused,
            SignInForegroundFailure.ServiceReceiptUnsaved,
        ) + SignInForegroundFailure.LostReason.entries.map { SignInForegroundFailure.Lost(it) }
        assertEquals(
            listOf(
                "sign-in-admission/busy", "service-refused", "service-receipt-unsaved",
                "sign-in-lost/android-time-limit", "sign-in-lost/paused-by-you", "sign-in-lost/service-gone",
                "sign-in-lost/live-turned-off", "sign-in-lost/app-closed", "sign-in-lost/owner-closed",
            ),
            codes.map { it.code },
        )
        for (failure in codes) assertEquals(failure, SignInForegroundFailure.fromCode(failure.code))
        assertNull(SignInForegroundFailure.fromCode("sign-in-lost/secret text"))
        assertNull(SignInForegroundFailure.fromCode("sign-in-admission/Bad Code"))
        assertEquals(5, SignInForegroundFailure.START_RECEIPT_WAIT.inWholeSeconds)
    }
}
