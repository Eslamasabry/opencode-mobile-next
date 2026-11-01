package io.github.eslamasabry.oc.engine.agents.claudeagent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.IntegrationConnectionInfo
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.engine.agents.OwnerId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val EXTERNAL = IntegrationMethodInfo(
    type = "external",
    id = CLAUDE_AGENT_LOGIN_METHOD_ID,
    label = "Claude Code login (claude auth login)",
    prompts = persistentListOf(),
    environmentNames = persistentListOf(),
    presetAnswers = persistentMapOf(),
)

private fun claude(connections: List<IntegrationConnectionInfo> = emptyList()) =
    IntegrationInfo(CLAUDE_AGENT_PROVIDER_ID, "Claude Agent", persistentListOf(EXTERNAL), connections.toImmutableList(), connections.size)

private fun connection(id: String, status: String? = null) = IntegrationConnectionInfo("credential", id, "Claude Agent", status)

private class Operations : ClaudeAgentIntegrations {
    var connections: List<IntegrationConnectionInfo> = emptyList()
    var offered = true
    var statusAfterConnect: String? = null
    var listFails = false
    val connected = mutableListOf<Pair<String, String>>()
    val removed = mutableListOf<String>()
    private var next = 0
    override val connectsExternal: Boolean = true
    override val removesCredentials: Boolean = true

    override suspend fun listIntegrations(): Outcome<List<IntegrationInfo>> {
        if (listFails) return Failure.Unknown().failed()
        val anthropic = IntegrationInfo(
            "anthropic", "Anthropic",
            persistentListOf(IntegrationMethodInfo("key", null, "API key", persistentListOf(), persistentListOf(), persistentMapOf())),
            persistentListOf(), 0,
        )
        return (listOf(anthropic) + if (offered) listOf(claude(connections)) else emptyList()).ok()
    }

    override suspend fun connectExternal(integrationId: String, methodId: String): Outcome<Unit> {
        connected += integrationId to methodId
        // OpenCode lists the newest connection first.
        connections = listOf(connection("cred-${next++}", statusAfterConnect)) + connections
        return Unit.ok()
    }

    override suspend fun removeCredential(id: String): Outcome<Unit> {
        removed += id
        connections = connections.filter { it.id != id }
        return Unit.ok()
    }
}

private class Host(val operations: Operations) : ClaudeAgentTrialHost {
    override var onPhoneServer = true
    override var serverVersion: String? = "2.2.5"
    override var agentOwner: OwnerId? = ProfileId("owner-1")
    override var scope: String? = "phone@2.2.5"
    override val integrations: ClaudeAgentIntegrations get() = operations
    override var hasClaudeAgentModels: Boolean? = false
    var modelsAfterRefresh = false
    var refreshes = 0

    override suspend fun refreshCatalog() {
        refreshes++
        hasClaudeAgentModels = modelsAfterRefresh
    }
}

private class Link(var result: ClaudeAgentLinkResult = ClaudeAgentLinkResult.Linked) : ClaudeAgentLinkRunner {
    val owners = mutableListOf<OwnerId>()

    override suspend fun link(owner: OwnerId): ClaudeAgentLinkResult {
        owners += owner
        return result
    }
}

private fun TestScope.trial(host: Host, link: Link, lateLoad: Duration = 3_000.milliseconds) =
    ClaudeAgentTrial(host, link, backgroundScope, pollInterval = Duration.ZERO, pollAttempts = 3, lateLoadDelay = lateLoad)

/**
 * Port of test/claude_agent_trial_test.dart (14). No catalog rows exist for the trial: B-AGENT-401…409 are proposed
 * (catalog request in the S3-AGENTSE report).
 */
class ClaudeAgentTrialTest {
    @Test
    fun `B-AGENT-401 server versions compare by release number`() {
        assertTrue(serverVersionAtLeast("2.2.3", "2.2.3"))
        assertTrue(serverVersionAtLeast("2.2.5", "2.2.3"))
        assertTrue(serverVersionAtLeast("v2.10.0-beta", "2.2.3"))
        assertTrue(serverVersionAtLeast("3.0.0", "2.2.3"))
        assertFalse(serverVersionAtLeast("2.2.2", "2.2.3"))
        assertFalse(serverVersionAtLeast("2.1.9", "2.2.3"))
        assertFalse(serverVersionAtLeast(null, "2.2.3"))
        assertFalse(serverVersionAtLeast("local", "2.2.3"))
    }

    @Test
    fun `B-AGENT-402 the gate needs this phone, OpenCode 2_2_3+ and the provider offered`() = runTest {
        val host = Host(Operations())
        val trial = trial(host, Link())
        assertFalse(trial.capable, "nothing read yet")
        trial.refresh()
        assertTrue(trial.capable)
        assertEquals(ClaudeAgentTrialState.Available, trial.stateNow())
        assertEquals(ClaudeAgentTrialState.Available, trial.state.value)
        host.serverVersion = "2.2.2"
        assertFalse(trial.capable)
        host.serverVersion = "2.2.5"
        host.onPhoneServer = false
        assertFalse(trial.capable)
        assertEquals(ClaudeAgentTrialState.Unavailable, trial.stateNow())
        host.onPhoneServer = true
        host.operations.offered = false
        trial.refresh()
        assertFalse(trial.capable)
    }

    @Test
    fun `B-AGENT-402 the capability flag is derived from the trial, never an input`() = runTest {
        val host = Host(Operations())
        val trial = trial(host, Link())
        assertFalse(trial.capabilities(ServerCapabilities()).claudeAgent)
        trial.refresh()
        assertTrue(trial.capabilities(ServerCapabilities()).claudeAgent)
    }

    @Test
    fun `B-AGENT-403 a first list that misses it is read once more, once per scope`() = runTest {
        val ops = Operations().apply { offered = false }
        val host = Host(ops)
        val trial = trial(host, Link(), lateLoad = 10.milliseconds)
        trial.refresh()
        assertFalse(trial.capable)
        ops.offered = true
        advanceTimeBy(50)
        runCurrent()
        assertTrue(trial.capable)
        // The same scope never schedules a second late read.
        ops.offered = false
        trial.refresh()
        ops.offered = true
        advanceTimeBy(50)
        runCurrent()
        assertFalse(trial.capable)
    }

    @Test
    fun `B-AGENT-403 another server or profile needs a fresh read`() = runTest {
        val host = Host(Operations())
        val trial = trial(host, Link())
        trial.refresh()
        host.scope = "other@2.2.5"
        assertFalse(trial.capable)
    }

    @Test
    fun `B-AGENT-404 turning it on links the agent home, connects once and waits for the models`() = runTest {
        val ops = Operations()
        val host = Host(ops).apply { modelsAfterRefresh = true }
        val link = Link()
        val trial = trial(host, link)
        trial.refresh()
        val states = mutableListOf<ClaudeAgentTrialState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { trial.state.toList(states) }
        trial.start()
        assertEquals(listOf(ProfileId("owner-1")), link.owners)
        assertEquals(listOf(CLAUDE_AGENT_PROVIDER_ID to CLAUDE_AGENT_LOGIN_METHOD_ID), ops.connected)
        assertTrue(ClaudeAgentTrialState.Preparing in states)
        assertEquals(ClaudeAgentTrialState.Ready, trial.stateNow())
        assertEquals(ClaudeAgentTrialState.Ready, trial.state.value)
        assertTrue(host.refreshes > 0)
    }

    @Test
    fun `B-AGENT-404 an existing connection is reused, never doubled`() = runTest {
        val ops = Operations().apply { connections = listOf(connection("cred-old")) }
        val host = Host(ops).apply { modelsAfterRefresh = true }
        val trial = trial(host, Link())
        trial.refresh()
        trial.start()
        assertTrue(ops.connected.isEmpty())
        assertEquals(ClaudeAgentTrialState.Ready, trial.stateNow())
    }

    @Test
    fun `B-AGENT-405 no sign-in reads as needs sign-in`() = runTest {
        val ops = Operations().apply { statusAfterConnect = "needs_auth" }
        val trial = trial(Host(ops), Link())
        trial.refresh()
        trial.start()
        assertEquals(ClaudeAgentTrialState.NeedsSignIn, trial.stateNow())
    }

    @Test
    fun `B-AGENT-405 Claude Code missing or the launcher taken stop before connecting`() = runTest {
        for ((result, state) in listOf(
            ClaudeAgentLinkResult.NotInstalled to ClaudeAgentTrialState.NotInstalled,
            ClaudeAgentLinkResult.Blocked to ClaudeAgentTrialState.Blocked,
            ClaudeAgentLinkResult.Failed to ClaudeAgentTrialState.Failed,
        )) {
            val ops = Operations()
            val trial = trial(Host(ops), Link(result))
            trial.refresh()
            trial.start()
            assertEquals(state, trial.stateNow(), result.name)
            assertTrue(ops.connected.isEmpty(), result.name)
        }
    }

    @Test
    fun `B-AGENT-406 models that never arrive, or a read that fails, read as failed`() = runTest {
        val trial = trial(Host(Operations()), Link())
        trial.refresh()
        trial.start()
        assertEquals(ClaudeAgentTrialState.Failed, trial.stateNow())

        val ops = Operations()
        val other = trial(Host(ops), Link())
        other.refresh()
        ops.listFails = true
        other.start()
        assertEquals(ClaudeAgentTrialState.Failed, other.stateNow())
    }

    @Test
    fun `B-AGENT-407 check again asks OpenCode afresh and keeps one connection`() = runTest {
        val ops = Operations().apply { statusAfterConnect = "needs_auth" }
        val host = Host(ops)
        val trial = trial(host, Link())
        trial.refresh()
        trial.start()
        assertEquals(ClaudeAgentTrialState.NeedsSignIn, trial.stateNow())
        // The person signed in to Claude Code meanwhile.
        ops.statusAfterConnect = null
        host.modelsAfterRefresh = true
        trial.checkAgain()
        assertEquals(2, ops.connected.size)
        assertEquals(listOf("cred-0"), ops.removed)
        assertEquals(listOf("cred-1"), ops.connections.map { it.id })
        assertEquals(ClaudeAgentTrialState.Ready, trial.stateNow())
    }

    @Test
    fun `B-AGENT-408 nothing happens off this phone`() = runTest {
        val ops = Operations()
        val host = Host(ops).apply { onPhoneServer = false }
        val link = Link()
        val trial = trial(host, link)
        trial.refresh()
        trial.start()
        assertTrue(link.owners.isEmpty())
        assertTrue(ops.connected.isEmpty())
    }

    @Test
    fun `B-AGENT-409 the root script refuses an agent home id that is not one`() {
        assertFailsWith<IllegalArgumentException> { ClaudeAgentLink.script(ProfileId("../x")) }
        assertFailsWith<IllegalArgumentException> { ClaudeAgentLink.script(ProfileId("")) }
        assertFailsWith<IllegalArgumentException> { ClaudeAgentLink.script(ProfileId("a b")) }
    }

    @Test
    fun `B-AGENT-409 the root script points at the shared agent home and never touches the sign-in`() {
        val script = ClaudeAgentLink.script(ProfileId("owner-1"))
        assertTrue("home='/home/oc/.oc-profiles/owner-1/claude'" in script)
        assertTrue("export CLAUDE_CONFIG_DIR='/home/oc/.oc-profiles/owner-1/claude'" in script)
        assertTrue("shim=/usr/local/sbin/claude" in script)
        assertTrue("if [ -t 0 ] && [ -x /usr/local/bin/claude ]; then exec /usr/local/bin/claude \"\$@\"; fi" in script)
        assertTrue(ClaudeAgentLink.SHIM_MARKER in script)
        assertTrue("! grep -qx '${ClaudeAgentLink.SHIM_MARKER}'" in script)
        for (secret in listOf(".credentials.json", "cp ", "cat \"\$home", "rm -rf")) assertFalse(secret in script, secret)
        assertFalse('§' in script || '⟨' in script, "no template marks remain")
        assertTrue(script.startsWith("set -u\n"))
    }

    @Test
    fun `B-AGENT-409 its result line is read, anything else is a failure`() {
        assertEquals(ClaudeAgentLinkResult.Linked, ClaudeAgentLink.parse(0, "noise\noc-claude-agent:linked\n"))
        assertEquals(ClaudeAgentLinkResult.LinkedWithoutResume, ClaudeAgentLink.parse(0, "oc-claude-agent:linked-without-resume"))
        assertEquals(ClaudeAgentLinkResult.NotInstalled, ClaudeAgentLink.parse(0, "oc-claude-agent:not-installed"))
        assertEquals(ClaudeAgentLinkResult.Blocked, ClaudeAgentLink.parse(0, "oc-claude-agent:blocked"))
        assertEquals(ClaudeAgentLinkResult.Failed, ClaudeAgentLink.parse(0, ""))
        assertEquals(ClaudeAgentLinkResult.Failed, ClaudeAgentLink.parse(1, "oc-claude-agent:linked"))
    }
}
