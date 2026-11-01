package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.feature.servers.presentation.EditorMode
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.FailureKind
import io.github.eslamasabry.oc.feature.servers.presentation.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerPageViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSwitcherViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.ServersEnvironment
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.TransportSecurity
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServersViewModelsTest {
    private val env = ServersEnvironment(supportsTermux = true)
    private val phone = profile("ph", "This phone", ServerOrigin("http", "127.0.0.1", 4097), onPhone = true)
    private val laptop = profile("lp", "Laptop", ServerOrigin("https", "desk.example", 4096))
    private val laptop2 = profile("l2", "Laptop beta", ServerOrigin("https", "desk.example", 4097), pin = FakeServersPort.SHA)
    private val lan = profile("ln", "Attic", ServerOrigin("http", "192.168.1.20", 4096), consentAtMs = 5)

    private fun saved(vararg p: io.github.eslamasabry.oc.core.model.ServerProfile, active: String? = null) =
        p.map { SavedServer(it, if (it.id.raw == active) ServerStatus.Connected else ServerStatus.NotConnected, it.id.raw == active) }

    @Test
    fun `E2E-01 the phone's rows lead, every row shows its port, and connect success lands`() =
        runTest {
            val port = FakeServersPort(saved(laptop, phone))
            val nav = RecordingServersNavigator()
            val vm = ServersViewModel(port, RulesAddressPolicy, env, nav, backgroundScope)
            runCurrent()
            assertEquals(listOf("This phone", "Laptop"), vm.state.value.rows.map { it.name })
            assertEquals("https://desk.example:4096", vm.state.value.rows[1].address)
            vm.connect(ProfileId("lp"))
            runCurrent()
            assertEquals(listOf("land"), nav.calls)
        }

    @Test
    fun `B-NAV-007 a failed connect stays inline on its row with the fix as the action`() =
        runTest {
            val port = FakeServersPort(saved(laptop))
            port.connectAnswer = { Outcome.Failed(Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected)) }
            val nav = RecordingServersNavigator()
            val vm = ServersViewModel(port, RulesAddressPolicy, env, nav, backgroundScope)
            vm.connect(ProfileId("lp"))
            runCurrent()
            val failure = assertNotNull(vm.state.value.rows.single().failure)
            assertEquals(FailureKind.PasswordRejected, failure.kind)
            assertTrue(nav.calls.isEmpty())
            vm.fix(ProfileId("lp"), failure.primary)
            assertEquals(listOf("editor ${EditorMode.Credentials(ProfileId("lp"))} focus=true"), nav.calls)
        }

    @Test
    fun `E2E-01 forget asks first and then runs the profile sweep`() =
        runTest {
            val port = FakeServersPort(saved(laptop, lan))
            val vm = ServersViewModel(port, RulesAddressPolicy, env, RecordingServersNavigator(), backgroundScope)
            vm.forget(ProfileId("ln"))
            runCurrent()
            assertEquals("Attic", vm.state.value.forgetting?.name)
            vm.cancelForget()
            runCurrent()
            assertEquals(2, vm.state.value.rows.size)
            vm.forget(ProfileId("ln"))
            vm.confirmForget()
            runCurrent()
            assertEquals(listOf("Laptop"), vm.state.value.rows.map { it.name })
        }

    @Test
    fun `B-NAV-041 the switcher tells same-host servers apart by port and puts the current one first`() =
        runTest {
            val port = FakeServersPort(saved(phone, laptop, laptop2, active = "l2"))
            val vm = ServerSwitcherViewModel(port, RecordingServersNavigator(), backgroundScope)
            runCurrent()
            val rows = vm.state.value.rows
            assertEquals("Laptop beta", rows.first().name)
            assertTrue(rows.first().current)
            assertEquals(mapOf("Laptop beta" to 4097, "Laptop" to 4096, "This phone" to null), rows.associate { it.name to it.port })
        }

    @Test
    fun `E2E-01 the switcher only chooses - it closes first, then connects and lands`() =
        runTest {
            val port = FakeServersPort(saved(phone, laptop, active = "ph"))
            val nav = RecordingServersNavigator()
            val vm = ServerSwitcherViewModel(port, nav, backgroundScope)
            vm.choose(ProfileId("ph")) // the current one: close only
            runCurrent()
            assertEquals(listOf("back"), nav.calls)
            vm.choose(ProfileId("lp"))
            runCurrent()
            assertEquals(listOf("back", "back", "land"), nav.calls)
            assertEquals(listOf(ProfileId("lp")), port.connects)
        }

    @Test
    fun `B-SEC-154 the server page shows how it is reached, and a pin is shown as its fingerprint`() =
        runTest {
            val port = FakeServersPort(saved(phone, laptop2, lan))
            fun page(id: String) = ServerPageViewModel(ProfileId(id), port, RulesAddressPolicy, env, RecordingServersNavigator(), backgroundScope)
            val pinned = page("l2")
            val http = page("ln")
            val local = page("ph")
            runCurrent()
            assertEquals(TransportSecurity.HttpsPinned, pinned.state.value?.security)
            assertTrue(pinned.state.value!!.pinFingerprint!!.startsWith("AB:12"))
            assertEquals(TransportSecurity.HttpAllowed, http.state.value?.security)
            assertEquals(TransportSecurity.ThisDevice, local.state.value?.security)
        }

    @Test
    fun `B-NAV-008 a changed certificate is reviewed against the old pin before the replacement is trusted`() =
        runTest {
            val port = FakeServersPort(saved(laptop2))
            port.fail(ProfileId("l2"), Net.TrustRefused(Net.TrustRefused.Why.CertificateChanged))
            port.certificate = Outcome.Ok(FakeServersPort.CERT.copy(sha256Hex = FakeServersPort.SHA2))
            val nav = RecordingServersNavigator()
            val vm = ServerPageViewModel(ProfileId("l2"), port, RulesAddressPolicy, env, nav, backgroundScope)
            runCurrent()
            val failure = assertNotNull(vm.state.value?.failure)
            assertEquals(FailureKind.CertificateChanged, failure.kind)
            vm.fix(FailureAction.ReviewCertificate)
            runCurrent()
            val review = assertNotNull(vm.state.value?.pinReview)
            assertTrue(review.previousFingerprint!!.startsWith("AB:12"))
            assertTrue(review.fingerprint.startsWith("01:23"))
            assertTrue(port.trusted.isEmpty())
            vm.trustCertificate()
            runCurrent()
            assertEquals(listOf(ProfileId("l2") to FakeServersPort.SHA2), port.trusted)
            assertEquals(listOf("land"), nav.calls)
        }

    @Test
    fun `E2E-01 Disconnect from the server page resets to Servers, and a forgotten profile has no page`() =
        runTest {
            val port = FakeServersPort(saved(laptop, active = "lp"))
            val nav = RecordingServersNavigator()
            val vm = ServerPageViewModel(ProfileId("lp"), port, RulesAddressPolicy, env, nav, backgroundScope)
            vm.disconnect()
            runCurrent()
            assertEquals(listOf("resetToServers"), nav.calls)
            vm.forget()
            vm.confirmForget()
            runCurrent()
            assertNull(vm.state.value)
        }
}
