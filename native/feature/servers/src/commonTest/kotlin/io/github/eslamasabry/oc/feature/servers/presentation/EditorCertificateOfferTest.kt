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
import kotlin.test.assertNull
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

/** B-SEC-155: a refused certificate is never a dead end, and never re-trusted without a tap. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorCertificateOfferTest {
    private val seen = ServerCertificate("https://home.example:8443", "ab".repeat(32), "2026-01-01", "2027-01-01")

    private class Port(var check: Outcome<ServerEditorProbe>) : ServerEditorPort {
        var inspections = 0
        var saves = 0
        override fun tailscaleState() = EditorTailscaleState.Unavailable
        override fun openTailscale(): Outcome<Unit> = Outcome.Ok(Unit)
        override fun installTailscale(): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun read(id: ProfileId): Outcome<ServerEditorRecord> = error("new server")
        override suspend fun check(input: ServerEditorInput) = check
        override suspend fun save(input: ServerEditorInput, version: String?): Outcome<ProfileId> {
            saves++
            return Outcome.Ok(ProfileId("saved"))
        }
        lateinit var certificate: Outcome<ServerCertificate>
        override suspend fun inspect(address: String): Outcome<ServerCertificate> {
            inspections++
            return certificate
        }
    }

    private object Connections : ServerConnections {
        override val servers = MutableStateFlow<ImmutableList<SavedServer>>(persistentListOf())
        override val busy = MutableStateFlow(false)
        override suspend fun save(name: String, address: String, username: String, password: String, allowCleartext: Boolean) =
            Outcome.Ok(ProfileId("saved"))
        override suspend fun connect(id: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)
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

    private fun input(pin: String? = null) =
        ServerEditorInput(
            null, "Home", "https://home.example:8443", "opencode", BackendKind.OpenCode2, "", null, false, false,
            null, pin, pin?.let { "https://home.example:8443" },
        )

    @BeforeTest fun before() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun `B-SEC-155 an untrusted certificate opens the trust sheet by itself and pins nothing`() = runTest {
        val port = Port(Outcome.Failed(Failure.Certificate(CertificateWhy.Untrusted)))
        port.certificate = Outcome.Ok(seen)
        val vm = model(port)
        vm.check(input())
        assertEquals(seen, vm.state.value.certificate)
        assertTrue(vm.state.value.failure is Failure.Certificate)
        assertEquals(0, port.saves)
    }

    @Test
    fun `B-SEC-155 a changed certificate shows the new fingerprint and the refusal stays`() = runTest {
        val port = Port(Outcome.Failed(Failure.Certificate(CertificateWhy.Changed)))
        port.certificate = Outcome.Ok(seen)
        val vm = model(port)
        vm.check(input(pin = "cd".repeat(32)))
        assertEquals(seen, vm.state.value.certificate)
        assertEquals(CertificateWhy.Changed, (vm.state.value.failure as Failure.Certificate).why)
    }

    @Test
    fun `B-SEC-155 an expired certificate has nothing to trust so no sheet opens`() = runTest {
        val port = Port(Outcome.Failed(Failure.Certificate(CertificateWhy.Expired)))
        port.certificate = Outcome.Ok(seen)
        val vm = model(port)
        vm.check(input())
        assertNull(vm.state.value.certificate)
        assertEquals(0, port.inspections)
    }

    @Test
    fun `B-SEC-155 a failed automatic look keeps the original refusal`() = runTest {
        val port = Port(Outcome.Failed(Failure.Certificate(CertificateWhy.Untrusted)))
        port.certificate = Outcome.Failed(Failure.TimedOut())
        val vm = model(port)
        vm.check(input())
        assertNull(vm.state.value.certificate)
        assertTrue(vm.state.value.failure is Failure.Certificate)
    }
}
