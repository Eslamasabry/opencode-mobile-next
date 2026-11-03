package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiSetupReviewTest {
    private val transport = Any()
    private val connection = MutableStateFlow(
        SetupConnection(SetupScope("server", null, null, transport), online = true, setupConfigRead = true),
    )

    private fun TestScope.open(
        gateway: SetupConfigGateway? = ReviewGateway(),
        gatewayFor: ((SetupScope) -> SetupConfigGateway?)? = null,
    ): SetupSession {
        val session = SetupSession(
            connection = connection,
            gatewayFor = gatewayFor ?: { gateway },
            controllerFor = { scope, g, isCurrent ->
                SetupController(
                    g, FakeAuditLog(), FakeRedaction(), scope.profileId,
                    "${scope.directory.orEmpty()}\u0000${scope.workspace.orEmpty()}", isCurrent,
                )
            },
            scope = backgroundScope,
        )
        runCurrent()
        return session
    }

    private fun other(bound: SetupScope) = SetupScope(bound.profileId, "/projects/other", null, transport)

    private val SetupSession.body: AiSetupReviewBody get() = state.value.body

    // ---- entry (slice test :227)

    @Test
    fun `B-AGENT-211 the server settings page shows the AI setup entry only on a server that shares its setup`() {
        assertTrue(aiSetupEntryVisible(ServerCapabilities(setupConfigRead = true)))
        assertFalse(aiSetupEntryVisible(ServerCapabilities()))
    }

    @Test
    fun `B-AGENT-211 opening AI setup on OpenCode 2 lists its sources and tool servers in plain words`() = runTest {
        val gateway = ReviewGateway().apply {
            config = json("""{"sources":[{"type":"document","info":{"model":"anthropic/claude-sonnet-4"}}]}""")
            servers = listOf(SetupMcpStatus("docs", "connected"))
        }
        val session = open(gateway)
        val content = assertIs<AiSetupReviewBody.Content>(session.body)
        val sources = assertIs<AiSetupConfigView.Sources>(content.configuration)
        assertEquals(listOf(AiSetupSource(1, null, "document", listOf("model"))), sources.sources)
        assertTrue(sources.allSources!!.contains("anthropic/claude-sonnet-4"))
        assertEquals(listOf(SetupTool("docs", SetupToolStatus.Connected)), content.tools)
        assertEquals(AiSetupNotice.ReviewOnly, content.notice)
        session.dispose()
    }

    // ---- states (slice test :464)

    @Test
    fun `B-AGENT-211 loading shows until the first read answers, then empty`() = runTest {
        val gateway = ReviewGateway().apply { hold = CompletableDeferred() }
        val session = open(gateway)
        assertEquals(AiSetupReviewBody.Loading, session.body)
        gateway.hold!!.complete(Unit)
        runCurrent()
        assertEquals(AiSetupReviewBody.Empty, session.body)
        session.dispose()
    }

    @Test
    fun `B-AGENT-211 a server with nothing set up shows empty`() = runTest {
        val session = open()
        assertEquals(AiSetupReviewBody.Empty, session.body)
        assertTrue(session.state.value.canRefresh)
        session.dispose()
    }

    @Test
    fun `B-AGENT-211 unsupported reads nothing and offers no refresh`() = runTest {
        val gateway = ReviewGateway()
        val session = open(gatewayFor = { null })
        assertEquals(AiSetupReviewBody.Unsupported, session.body)
        assertFalse(session.isSupported)
        assertEquals(0, gateway.reads)
        assertFalse(session.state.value.canRefresh)
        session.dispose()
    }

    @Test
    fun `B-AGENT-211 needs sign-in offers this server's sign-in`() = runTest {
        val gateway = ReviewGateway().apply { failure = Failure.SignIn(SignInWhy.PasswordRejected) }
        val session = open(gateway)
        assertEquals(AiSetupReviewBody.NeedsSignIn, session.body)
        session.dispose()
    }

    @Test
    fun `B-AGENT-211 error keeps the reason under Details and Try again reads again`() = runTest {
        val gateway = ReviewGateway().apply { failure = Failure.Server(500) }
        val session = open(gateway)
        val error = assertIs<AiSetupReviewBody.Error>(session.body)
        assertEquals(SetupReason.Transport, error.details)
        assertTrue(error.canRetry)
        assertFalse(session.state.value.canRefresh)
        gateway.failure = null
        gateway.config = json("""{"model":"example/model"}""")
        session.refresh()
        runCurrent()
        val content = assertIs<AiSetupReviewBody.Content>(session.body)
        assertEquals("example/model", (content.configuration as AiSetupConfigView.Effective).model)
        assertEquals(2, gateway.reads)
        session.dispose()
    }

    @Test
    fun `B-AGENT-211 offline before any read shows offline and reads nothing`() = runTest {
        connection.value = connection.value.copy(online = false)
        val gateway = ReviewGateway()
        val session = open(gateway)
        assertEquals(AiSetupReviewBody.Offline, session.body)
        assertEquals(0, gateway.reads)
        session.dispose()
    }

    @Test
    fun `B-AGENT-211 offline keeps the last read and reconnecting reads again`() = runTest {
        val gateway = ReviewGateway().apply { config = json("""{"model":"example/model"}""") }
        val session = open(gateway)
        fun model() = ((session.body as AiSetupReviewBody.Content).configuration as AiSetupConfigView.Effective).model
        assertEquals("example/model", model())

        connection.value = connection.value.copy(online = false)
        runCurrent()
        assertEquals(AiSetupNotice.OfflineStale, (session.body as AiSetupReviewBody.Content).notice)
        assertEquals("example/model", model())
        assertEquals(1, gateway.reads)
        assertFalse(session.state.value.canRefresh)

        gateway.config = json("""{"model":"example/newer"}""")
        connection.value = connection.value.copy(online = true)
        runCurrent()
        assertEquals("example/newer", model())
        assertEquals(AiSetupNotice.ReviewOnly, (session.body as AiSetupReviewBody.Content).notice)
        assertEquals(2, gateway.reads)
        session.dispose()
    }

    // ---- location change (slice test :572)

    @Test
    fun `B-AGENT-211 a location change recreates the setup owner and reads again`() = runTest {
        val gateways = mutableListOf<ReviewGateway>()
        val session = open(
            gatewayFor = {
                ReviewGateway().apply { config = json("""{"model":"example/${gateways.size}"}""") }.also(gateways::add)
            },
        )
        session.settled()
        assertEquals(str("example/0"), session.currentSnapshot.config["model"])

        val bound = connection.value.scope!!
        connection.value = connection.value.copy(scope = other(bound))
        runCurrent()
        session.settled()
        assertEquals(2, gateways.size)
        assertEquals(str("example/1"), session.currentSnapshot.config["model"])

        // The same location again (an equal scope) keeps the owner: no third read.
        connection.value = connection.value.copy(scope = other(bound))
        runCurrent()
        assertEquals(2, gateways.size)
        session.dispose()
    }
}
