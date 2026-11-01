package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.feature.servers.presentation.AddressProblem
import io.github.eslamasabry.oc.feature.servers.presentation.EditorMode
import io.github.eslamasabry.oc.feature.servers.presentation.EditorVerdict
import io.github.eslamasabry.oc.feature.servers.presentation.EditorViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.EntryKind
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.FailureCheck
import io.github.eslamasabry.oc.feature.servers.presentation.FailureKind
import io.github.eslamasabry.oc.feature.servers.presentation.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.SecretPresence
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServersEnvironment
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorViewModelTest {
    private val password = "fixture-not-a-live-serve-password-000000000"
    private val env = ServersEnvironment(supportsTermux = true)

    private fun TestScope.editor(
        port: FakeServersPort,
        nav: RecordingServersNavigator = RecordingServersNavigator(),
        mode: EditorMode = EditorMode.Add,
    ): EditorViewModel = EditorViewModel(mode, false, port, RulesAddressPolicy, env, nav, backgroundScope)

    private fun TestScope.typed(vm: EditorViewModel, address: String) {
        vm.useEntry(EntryKind.Address)
        vm.setAddress(address)
        vm.setPassword(password)
        vm.test()
        runCurrent()
    }

    @Test
    fun `B-SEC-124 the editor holds a private http address and asks before any byte is sent`() =
        runTest {
            val port = FakeServersPort()
            val vm = editor(port)
            typed(vm, "http://192.168.1.20:4096")
            assertTrue(port.probes.isEmpty())
            val ask = assertNotNull(vm.state.value.consent)
            assertEquals(ServerOrigin("http", "192.168.1.20", 4096), ask.origin)
            assertFalse(ask.public)

            vm.allowHttp()
            runCurrent()
            assertEquals(ServerOrigin("http", "192.168.1.20", 4096), port.probes.single().consentedOrigin)
            assertIs<EditorVerdict.Answered>(vm.state.value.verdict)
        }

    @Test
    fun `B-SEC-124 declining plain HTTP sends nothing and keeps the address`() =
        runTest {
            val port = FakeServersPort()
            val vm = editor(port)
            typed(vm, "http://192.0.2.20:4096")
            assertTrue(assertNotNull(vm.state.value.consent).public)
            vm.declineHttp()
            runCurrent()
            assertTrue(port.probes.isEmpty())
            assertNull(vm.state.value.consent)
            assertEquals("http://192.0.2.20:4096", vm.state.value.address)
        }

    @Test
    fun `B-SEC-120 loopback and https never ask, and a bare private address gains https`() =
        runTest {
            val port = FakeServersPort()
            val vm = editor(port)
            typed(vm, "http://127.0.0.1:4096")
            assertNull(vm.state.value.consent)
            typed(vm, "192.168.1.20:4096")
            assertNull(vm.state.value.consent)
            assertEquals(
                listOf(ServerOrigin("http", "127.0.0.1", 4096), ServerOrigin("https", "192.168.1.20", 4096)),
                port.probes.map { it.origin },
            )
        }

    @Test
    fun `B-SEC-153 consent for one port does not cover another, and credentials in the address are refused`() =
        runTest {
            val port = FakeServersPort()
            val vm = editor(port)
            typed(vm, "http://192.0.2.20:4097")
            vm.allowHttp()
            runCurrent()
            typed(vm, "http://192.0.2.20:4098")
            assertEquals(ServerOrigin("http", "192.0.2.20", 4098), assertNotNull(vm.state.value.consent).origin)
            assertEquals(1, port.probes.size)

            typed(vm, "https://user:pw@desk.example:4097")
            assertEquals(AddressProblem.CredentialsInAddress, vm.state.value.addressProblem)
            typed(vm, "https://desk.example:4097/?token=x")
            assertEquals(AddressProblem.Invalid, vm.state.value.addressProblem)
            assertEquals(1, port.probes.size)
        }

    @Test
    fun `B-NAV-008 an untrusted certificate is trusted only after its fingerprint was shown`() =
        runTest {
            val port = FakeServersPort()
            port.probeAnswer = { t ->
                if (t.pinnedSha256 == null) Outcome.Failed(Net.TrustRefused(Net.TrustRefused.Why.CertificateUntrusted)) else Outcome.Ok(io.github.eslamasabry.oc.feature.servers.presentation.ProbeAnswer("2.1.1"))
            }
            val vm = editor(port)
            typed(vm, "https://desk.example:4097")
            val failed = assertIs<EditorVerdict.Failed>(vm.state.value.verdict).failure
            assertEquals(FailureAction.ReviewCertificate, failed.primary)

            vm.reviewCertificate()
            runCurrent()
            val review = assertNotNull(vm.state.value.pinReview)
            assertTrue(review.fingerprint.startsWith("AB:12:CD:34"))
            vm.cancelCertificateReview() // closing never grants trust
            assertFalse(vm.state.value.pinned)

            vm.reviewCertificate()
            runCurrent()
            vm.trustCertificate()
            runCurrent()
            assertEquals(FakeServersPort.SHA, port.probes.last().pinnedSha256)
            assertIs<EditorVerdict.Answered>(vm.state.value.verdict)
        }

    @Test
    fun `B-SEC-154 editing the host drops the pin and the plain-HTTP choice`() =
        runTest {
            val pinned = profile("p1", "Desk", ServerOrigin("https", "desk.example", 4097), pin = FakeServersPort.SHA)
            val port = FakeServersPort(listOf(SavedServer(pinned, ServerStatus.NotConnected, false)))
            val vm = editor(port, mode = EditorMode.Edit(ProfileId("p1")))
            assertTrue(vm.state.value.pinned)
            assertEquals(SecretPresence.Saved, vm.state.value.password) // never prefilled (K11)
            vm.test()
            runCurrent()
            assertEquals(FakeServersPort.SHA, port.probes.last().pinnedSha256)

            vm.setAddress("https://laptop.example:4097")
            vm.test()
            runCurrent()
            assertNull(port.probes.last().pinnedSha256)
            assertFalse(vm.state.value.pinned)
        }

    @Test
    fun `B-NAV-009 testing three times adds the retry line`() =
        runTest {
            val port = FakeServersPort()
            port.probeAnswer = { Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Refused)) }
            val vm = editor(port)
            repeat(3) { typed(vm, "https://desk.example:4097") }
            val f = assertIs<EditorVerdict.Failed>(vm.state.value.verdict).failure
            assertEquals(FailureKind.NotReachable, f.kind)
            assertEquals(FailureCheck.TriedTimes(3), f.checks.last())
        }

    @Test
    fun `B-SEC-107 a pairing code connects, saves, lands, and its password is dropped`() =
        runTest {
            val port = FakeServersPort()
            val nav = RecordingServersNavigator()
            val vm = editor(port, nav)
            vm.useEntry(EntryKind.PairingCode)
            vm.setCode("""{"urls":["https://desk.example:4097"],"username":"opencode","password":"$password"}""")
            vm.saveAndConnect()
            runCurrent()
            val draft = port.saved.single()
            assertEquals(ServerOrigin("https", "desk.example", 4097), draft.origin)
            assertEquals(password, draft.password?.reveal())
            assertEquals("desk.example", draft.name)
            assertEquals(listOf("land"), nav.calls)
            assertFalse(password in vm.state.value.toString())
        }

    @Test
    fun `B-SEC-108 a pairing code where nothing answers lists one reason per address`() =
        runTest {
            val port = FakeServersPort()
            port.probeAnswer = { Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Timeout)) }
            val vm = editor(port)
            vm.useEntry(EntryKind.PairingCode)
            vm.setCode("""{"urls":["https://a.example:4097","https://b.example:4097"],"password":"x"}""")
            vm.test()
            runCurrent()
            val v = assertIs<EditorVerdict.NoAddressAnswered>(vm.state.value.verdict)
            assertEquals(listOf("https://a.example:4097", "https://b.example:4097"), v.outcomes.map { it.display })
            assertTrue(port.saved.isEmpty())
        }

    @Test
    fun `B-SEC-124 a pairing code with a private http address asks first, then dials only that origin`() =
        runTest {
            val port = FakeServersPort()
            val vm = editor(port)
            vm.useEntry(EntryKind.PairingCode)
            vm.setCode("""{"urls":["http://192.168.1.20:4097"],"password":"x"}""")
            vm.test()
            runCurrent()
            assertTrue(port.probes.isEmpty())
            assertEquals(ServerOrigin("http", "192.168.1.20", 4097), assertNotNull(vm.state.value.consent).origin)
            vm.allowHttp()
            runCurrent()
            assertEquals(listOf(ServerOrigin("http", "192.168.1.20", 4097)), port.probes.map { it.origin })
        }

    @Test
    fun `E2E-01 saving never happens without a successful test, and success lands`() =
        runTest {
            val port = FakeServersPort()
            port.probeAnswer = { Outcome.Failed(Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected)) }
            val nav = RecordingServersNavigator()
            val vm = editor(port, nav)
            vm.useEntry(EntryKind.Address)
            vm.setAddress("https://desk.example:4097")
            vm.setPassword("wrong")
            vm.saveAndConnect()
            runCurrent()
            assertTrue(port.saved.isEmpty())
            assertEquals(FailureKind.PasswordRejected, assertIs<EditorVerdict.Failed>(vm.state.value.verdict).failure.kind)

            port.probeAnswer = { Outcome.Ok(io.github.eslamasabry.oc.feature.servers.presentation.ProbeAnswer("2.1.1")) }
            vm.setPassword(password)
            vm.saveAndConnect()
            runCurrent()
            assertEquals(1, port.saved.size)
            assertEquals(listOf("land"), nav.calls)
        }
}
