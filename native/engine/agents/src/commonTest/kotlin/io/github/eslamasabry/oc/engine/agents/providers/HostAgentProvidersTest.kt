package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.readiness.AgentFixAction
import io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness
import io.github.eslamasabry.oc.engine.agents.readiness.AgentRow
import io.github.eslamasabry.oc.engine.agents.readiness.AgentStatusKey
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentStatus
import io.github.eslamasabry.oc.engine.agents.readiness.toSignInPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Port of test/host_agent_providers_test.dart (B-AGENT-111, 112, 113). The four fixture tests that run captured Paseo
 * snapshots through `paseoHostAgentCatalog` belong to the Paseo projection (:protocol:paseo, S3-PASEO); their readiness
 * half (CLI account states stay final whatever the helper's model list says) is kept here.
 */
class HostAgentProvidersTest {
    private fun phoneRow(agent: AgentDescriptor, probe: AuthState): AgentRow =
        AgentReadiness.row(
            agent,
            AgentArchitecture.Arm64,
            ServerCapabilities(),
            PhoneAgentRuntime(
                agentId = agent.id,
                installed = true,
                architectureQualified = true,
                hostAvailable = true,
                signInPhase = probe.toSignInPhase(),
            ),
        )

    @Test
    fun `B-AGENT-111 all catalog phone agents reach final rows for explicit probe outcomes`() {
        val expected = mapOf(
            AuthState.SignedIn to PhoneAgentStatus.Ready,
            AuthState.SignedOut to PhoneAgentStatus.SignedOut,
            AuthState.Error to PhoneAgentStatus.Unavailable,
        )
        for (agent in AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }) {
            for ((probe, status) in expected) {
                val row = phoneRow(agent, probe)
                assertEquals(status, row.status, agent.id.raw)
                assertNotEquals(AgentStatusKey.AgentCheckingSignIn, row.statusKey, agent.id.raw)
            }
        }
    }

    @Test
    fun `B-AGENT-111 a signed-out fx reaches a final signed-out row with Sign in`() {
        val row = phoneRow(AgentCatalog.builtIn.byId(AgentId("fx"))!!, AuthState.SignedOut)
        assertEquals(PhoneAgentStatus.SignedOut, row.status)
        assertEquals(AgentFixAction.SignIn, row.fixAction)
        assertFalse(row.chatSelectable)
        assertNotEquals(AgentStatusKey.AgentCheckingSignIn, row.statusKey)
    }

    private fun provider(
        availability: HostAgentProviderAvailability = HostAgentProviderAvailability.Ready,
        hiddenReason: HostAgentProviderHiddenReason? = null,
        loginState: HostAgentLoginState = HostAgentLoginState.Ready,
        resumeSupport: HostAgentResumeSupport = HostAgentResumeSupport.LoadSession,
        id: String = "gemini",
        models: List<HostAgentModel> = emptyList(),
    ) = HostAgentProvider(id, "Host agent", availability, hiddenReason, loginState, resumeSupport, models)

    @Test
    fun `B-AGENT-112 missing restoration does not block a host-ready provider`() {
        val unknownLogin = provider(loginState = HostAgentLoginState.Unknown)
        assertTrue(unknownLogin.selectable)
        assertFailsWith<IllegalArgumentException> { provider(loginState = HostAgentLoginState.NeedsHostSignIn) }
        for (support in listOf(HostAgentResumeSupport.Unknown, HostAgentResumeSupport.Unsupported)) {
            val candidate = provider(resumeSupport = support)
            assertTrue(candidate.selectable)
            assertFalse(candidate.resumeVerified)
            assertTrue(candidate.resumeUnverified)
        }
        assertFalse(provider().resumeUnverified)
        assertTrue(provider(resumeSupport = HostAgentResumeSupport.ListAndLoad).selectable)
    }

    @Test
    fun `B-AGENT-112 missing sign-in still blocks without restoration proof`() {
        val needsSignIn = provider(
            availability = HostAgentProviderAvailability.NeedsHostSignIn,
            loginState = HostAgentLoginState.NeedsHostSignIn,
            resumeSupport = HostAgentResumeSupport.Unknown,
        )
        assertFalse(needsSignIn.selectable)
        assertEquals(HostAgentReasonKey.HostAgentSignInFirst, needsSignIn.reason)
        assertTrue(needsSignIn.resumeUnverified)
        assertFailsWith<IllegalArgumentException> {
            provider(availability = HostAgentProviderAvailability.NeedsHostSignIn, loginState = HostAgentLoginState.Unknown)
        }
    }

    @Test
    fun `B-AGENT-112 hidden and checking agents stay unavailable even when logged in`() {
        for (reason in listOf(HostAgentProviderHiddenReason.ResumeUnverified, HostAgentProviderHiddenReason.ResumeUnsupported)) {
            assertFailsWith<IllegalArgumentException> {
                provider(availability = HostAgentProviderAvailability.Hidden, hiddenReason = reason)
            }
        }
        val hidden = provider(
            availability = HostAgentProviderAvailability.Hidden,
            hiddenReason = HostAgentProviderHiddenReason.Disabled,
        )
        val checking = provider(availability = HostAgentProviderAvailability.Checking)
        assertFalse(hidden.selectable)
        assertFalse(checking.selectable)
        assertEquals(HostAgentReasonKey.HostAgentDisabled, hidden.reason)
        assertEquals(HostAgentReasonKey.HostAgentChecking, checking.reason)
        assertFailsWith<IllegalArgumentException> { provider(availability = HostAgentProviderAvailability.Hidden) }
        assertFailsWith<IllegalArgumentException> { provider(hiddenReason = HostAgentProviderHiddenReason.Disabled) }
    }

    @Test
    fun `B-AGENT-113 catalog and model lists cannot be changed by caller mutation`() {
        val models = mutableListOf(HostAgentModel("model-1", "Model one"))
        val ready = provider(models = models)
        models.clear()
        assertEquals("model-1", ready.models.single().id)
        val source = mutableListOf(ready, provider(availability = HostAgentProviderAvailability.Checking))
        val catalog = HostAgentProviderCatalog(source)
        source.clear()
        assertEquals(2, catalog.providers.size)
        assertSame(ready, catalog.selectable.single())
    }

    @Test
    fun `B-AGENT-113 sign-in uses only fixed app commands`() {
        assertEquals("gemini", provider().hostSignInCommand)
        assertEquals("omp", provider(id = "omp").hostSignInCommand)
        assertEquals("omp", provider(id = "omp-acp").hostSignInCommand)
        assertEquals("fx login", provider(id = "fx").hostSignInCommand)
        assertNull(provider(id = "unknown-agent").hostSignInCommand)
        assertNull(provider(id = "fx; arbitrary command").hostSignInCommand)
        assertEquals(HostAgentSignInMessageKey.HostAgentSignInGemini, provider().signInMessage)
        assertEquals(HostAgentSignInMessageKey.HostAgentSignInOmp, provider(id = "omp").signInMessage)
        assertEquals(HostAgentSignInMessageKey.HostAgentSignInGeneric, provider(id = "fx").signInMessage)
    }

    @Test
    fun `B-AGENT-113 permission choices expose fixed once-only labels and copied lists`() {
        val allow = HostAgentPermissionChoice("host-action-allow", HostAgentPermissionBehavior.AllowOnce)
        val reject = HostAgentPermissionChoice("host-action-reject", HostAgentPermissionBehavior.RejectOnce)
        val choices = mutableListOf(allow, reject)
        val request = HostAgentPermissionRequest("request-1", "session-1", choices)
        choices.clear()
        assertEquals(
            listOf(HostAgentPermissionBehavior.AllowOnce, HostAgentPermissionBehavior.RejectOnce),
            request.choices.map { it.behavior },
        )
        assertEquals(setOf("AllowOnce", "RejectOnce"), HostAgentPermissionBehavior.entries.map { it.name }.toSet())
        assertFailsWith<IllegalArgumentException> { HostAgentPermissionRequest("request-2", "session-1", listOf(allow, allow)) }
        assertFailsWith<IllegalArgumentException> {
            HostAgentPermissionRequest(
                "request-3",
                "session-1",
                listOf(HostAgentPermissionChoice(" ", HostAgentPermissionBehavior.AllowOnce)),
            )
        }
    }

    @Test
    fun `B-AGENT-112 old chats with unknown restoration need an explicit new chat`() {
        for (state in HostAgentContinuationState.entries) {
            val continuation = HostAgentContinuation("s", "gemini", state)
            val needsNew = state == HostAgentContinuationState.ResumeUnverified ||
                state == HostAgentContinuationState.MissingHandle
            assertEquals(needsNew, continuation.requiresNewChat, state.name)
            assertEquals(needsNew, continuation.blocked, state.name)
        }
    }
}
