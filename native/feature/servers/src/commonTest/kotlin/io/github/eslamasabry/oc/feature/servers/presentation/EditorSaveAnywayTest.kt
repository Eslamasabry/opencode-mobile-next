package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.servers.discovery.NoLanDiscovery
import io.github.eslamasabry.oc.feature.servers.pairing.PairingAnswer
import io.github.eslamasabry.oc.feature.servers.pairing.PairingInbox
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProbe
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSaveAnywayTest {
    private class Port : ServerEditorPort {
        var saves = 0
        override fun tailscaleState() = EditorTailscaleState.Unavailable
        override fun openTailscale(): Outcome<Unit> = Outcome.Ok(Unit)
        override fun installTailscale(): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun read(id: ProfileId): Outcome<ServerEditorRecord> =
            Outcome.Ok(
                ServerEditorRecord(
                    id, "Home", "http://192.0.2.10:4096", "opencode", BackendKind.OpenCode2, "",
                    hasSecret = false, needsSecret = false, tailscale = false,
                    cleartextOrigin = "http://192.0.2.10:4096", certificatePin = null, certificateOrigin = null,
                )
            )
        override suspend fun check(input: ServerEditorInput): Outcome<ServerEditorProbe> =
            Outcome.Failed(Failure.TimedOut())
        override suspend fun save(input: ServerEditorInput, version: String?): Outcome<ProfileId> {
            saves++
            return Outcome.Ok(ProfileId("saved"))
        }
        override suspend fun inspect(address: String): Outcome<ServerCertificate> = error("unused")
    }

    private object Connections : ServerConnections {
        override val servers = MutableStateFlow<ImmutableList<SavedServer>>(persistentListOf())
        override val busy = MutableStateFlow(false)
        override suspend fun save(name: String, address: String, username: String, password: String, allowCleartext: Boolean) =
            Outcome.Ok(ProfileId("saved"))
        override suspend fun connect(id: ProfileId): Outcome<Unit> = Outcome.Failed(Failure.TimedOut())
        override suspend fun createConversation(profile: ProfileId?) = error("unused")
        override suspend fun remove(id: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private object Nav : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        override fun open(route: AppRoute) = Unit
        override fun openChat(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef, focusComposer: Boolean) = Unit
        override fun land() = Unit
        override fun replaceRoot(root: AppRoute) = Unit
        override fun back() = Unit
    }

    private fun model(port: Port) =
        ServerEditorViewModel(
            ServersRoute.Edit(),
            port,
            Connections,
            Nav,
            PairingInbox(),
            PairingProbe { PairingAnswer.NoAnswer },
            NoLanDiscovery(),
            PlatformCapabilities.android(),
        )

    private fun input() =
        ServerEditorInput(
            null, "Home", "http://192.0.2.10:4096", "opencode", BackendKind.OpenCode2, "", null, false, false,
            "http://192.0.2.10:4096", null, null,
        )

    @BeforeTest fun before() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun `a plain check that fails does not offer Save anyway, as in Flutter`() = runTest {
        val vm = model(Port())
        vm.check(input())
        assertTrue(vm.state.value.failedCheck)
        assertFalse(vm.state.value.failedFromSave)
    }

    @Test
    fun `Save and connect that fails its check offers Save anyway and saves nothing`() = runTest {
        val port = Port()
        val vm = model(port)
        vm.save(input())
        assertTrue(vm.state.value.failedFromSave)
        assertEquals(0, port.saves)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `Save anyway saves, and the failed connect leaves one failure and no Save anyway`() = runTest {
        val port = Port()
        val vm = model(port)
        vm.save(input())
        vm.save(input(), anyway = true)
        assertEquals(1, port.saves)
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.failedFromSave)
        assertTrue(vm.state.value.failure is Failure.TimedOut)
    }
}
