package io.github.eslamasabry.oc.feature.integrations

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorServer
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorState
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsPhase
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsPresenter
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectorsPresenterTest {
    private val home = ProfileId("home")
    private val work = ProfileId("work")

    private class Source(servers: List<ConnectorServer>) : ConnectorsSource {
        override val servers = MutableStateFlow(servers.toImmutableList())
        override val active: StateFlow<ProfileId?> = MutableStateFlow(null)
        val lists = mutableMapOf<ProfileId, MutableList<McpServerInfo>>()
        var listFailure: Failure? = null
        var gate: CompletableDeferred<Unit>? = null
        var toggleFailure: Failure? = null
        val calls = mutableListOf<String>()

        override suspend fun list(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>> {
            calls += "list ${profile.raw}"
            listFailure?.let { return it.failed() }
            return lists[profile].orEmpty().toImmutableList().ok()
        }

        override suspend fun connect(profile: ProfileId, name: String): Outcome<Unit> = change(profile, name, "connected")

        override suspend fun disconnect(profile: ProfileId, name: String): Outcome<Unit> = change(profile, name, "disabled")

        private suspend fun change(profile: ProfileId, name: String, to: String): Outcome<Unit> {
            calls += "$to $name"
            gate?.await()
            toggleFailure?.let { return it.failed() }
            val list = lists.getValue(profile)
            val at = list.indexOfFirst { it.name == name }
            list[at] = list[at].copy(status = to)
            return Unit.ok()
        }
    }

    private fun TestScope.presenter(source: Source, initial: ProfileId? = null) =
        ConnectorsPresenter(initial, source, backgroundScope).also { runCurrent() }

    @Test
    fun `B-AGENT-212 connectors that run come first, then the rest by name, states in fixed words`() = runTest {
        val source = Source(listOf(ConnectorServer(home, "Home")))
        source.lists[home] =
            mutableListOf(
                McpServerInfo("zeta", "connected", null),
                McpServerInfo("alpha", "disabled", null),
                McpServerInfo("beta", "failed", "boom: secret path"),
                McpServerInfo("gamma", "needs_auth", null),
                McpServerInfo("delta", "pending", null),
                McpServerInfo("eps", "something-new", null),
            )
        val p = presenter(source)
        val ui = p.state.value
        assertEquals(ConnectorsPhase.Ready, ui.phase)
        assertEquals(listOf("delta", "zeta", "alpha", "beta", "eps", "gamma"), ui.rows.map { it.name })
        assertEquals(
            listOf(
                ConnectorState.Starting, ConnectorState.Connected, ConnectorState.Off, ConnectorState.Failed,
                ConnectorState.Unknown, ConnectorState.NeedsSignIn,
            ),
            ui.rows.map { it.state },
        )
    }

    @Test
    fun `B-AGENT-135 a switch is confirmed by reading the list again and double taps coalesce`() = runTest {
        val source = Source(listOf(ConnectorServer(home, "Home")))
        source.lists[home] = mutableListOf(McpServerInfo("files", "disabled", null))
        val p = presenter(source)
        source.gate = CompletableDeferred()
        p.toggle("files", true)
        p.toggle("files", true)
        runCurrent()
        assertTrue(p.state.value.rows.single().busy)
        assertEquals(1, source.calls.count { it == "connected files" })
        source.gate!!.complete(Unit)
        runCurrent()
        val row = p.state.value.rows.single()
        assertEquals(ConnectorState.Connected, row.state)
        assertEquals(false, row.busy)
        assertEquals(listOf("list home", "connected files", "list home"), source.calls)
    }

    @Test
    fun `B-AGENT-135 a refused switch says so plainly and shows the state the server reports`() = runTest {
        val source = Source(listOf(ConnectorServer(home, "Home")))
        source.lists[home] = mutableListOf(McpServerInfo("files", "disabled", null))
        val p = presenter(source)
        source.toggleFailure = Failure.Server(500)
        p.toggle("files", true)
        runCurrent()
        val ui = p.state.value
        assertEquals("files", ui.problem?.name)
        assertEquals(true, ui.problem?.turningOn)
        assertEquals(ConnectorState.Off, ui.rows.single().state)
        p.dismissProblem()
        assertNull(p.state.value.problem)
    }

    @Test
    fun `B-AGENT-134 a change of server drops the old server's late answer`() = runTest {
        val source = Source(listOf(ConnectorServer(home, "Home"), ConnectorServer(work, "Work")))
        source.lists[home] = mutableListOf(McpServerInfo("files", "disabled", null))
        source.lists[work] = mutableListOf(McpServerInfo("db", "connected", null))
        val p = presenter(source, home)
        source.gate = CompletableDeferred()
        p.toggle("files", true)
        runCurrent()
        p.chooseServer(work)
        runCurrent()
        source.gate!!.complete(Unit)
        runCurrent()
        val ui = p.state.value
        assertEquals(work, ui.server)
        assertEquals(listOf("db"), ui.rows.map { it.name })
        assertNull(ui.problem)
    }

    @Test
    fun `B-AGENT-130 a server without connector support is explained, not an error, and no server asks to connect one`() =
        runTest {
            val source = Source(listOf(ConnectorServer(home, "Home")))
            source.listFailure = Failure.Unavailable(FeatureId("serverCatalog"))
            val p = presenter(source)
            assertEquals(ConnectorsPhase.Unsupported, p.state.value.phase)
            source.listFailure = Failure.Network(NetworkWhy.Offline)
            p.refresh()
            runCurrent()
            assertEquals(ConnectorsPhase.Failed, p.state.value.phase)
            source.servers.value = persistentListOf()
            runCurrent()
            assertEquals(ConnectorsPhase.NoServer, p.state.value.phase)
        }
}
