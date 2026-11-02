package io.github.eslamasabry.oc.feature.servers.pairing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.PairingRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The scanner → inbox → server form hand-off (plan 07 E03; Flutter `test/pairing_scanner_test.dart`). */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingFlowTest {
    private val code = """{"urls":["https://box.tail.ts.net:4096"],"username":"me","password":"pw"}"""

    private class Navigator : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        var backs = 0

        override fun open(route: AppRoute) {
            top.value = route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() {
            backs++
        }
    }

    private class Connections : ServerConnections {
        override val servers = MutableStateFlow<ImmutableList<SavedServer>>(persistentListOf())
        override val busy = MutableStateFlow(false)
        var saves = 0

        override suspend fun save(name: String, address: String, username: String, password: String, allowCleartext: Boolean): Outcome<ProfileId> {
            saves++
            return Outcome.Ok(ProfileId("p"))
        }

        override suspend fun connect(id: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef> = error("not used")

        override suspend fun remove(id: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-PROTO-069 a scanned pairing QR is handled exactly like a pasted code`() {
        val scannedInbox = PairingInbox()
        val pastedInbox = PairingInbox()
        val scanNav = Navigator()
        val scanner = PairingScannerViewModel(scannedInbox, scanNav)
        assertTrue(scanner.accepts(code))
        scanner.scanned(code)
        PairingScannerViewModel(pastedInbox, Navigator()).pasted(code)
        val scanned = scannedInbox.take()!!
        val pasted = pastedInbox.take()!!
        assertEquals(pasted.address, scanned.address)
        assertEquals(pasted.username, scanned.username)
        assertEquals(pasted.takePassword(), scanned.takePassword())
        assertEquals(1, scanNav.backs, "the scanner closes back to the form")
        assertNull(scannedInbox.take(), "taken once")
    }

    @Test
    fun `B-PROTO-069 a QR that is not a pairing code is rejected plainly and nothing is handed over`() {
        val inbox = PairingInbox()
        val nav = Navigator()
        val scanner = PairingScannerViewModel(inbox, nav)
        assertFalse(scanner.accepts("https://example.com/menu"))
        assertFalse(scanner.accepts("WIFI:S:home;T:WPA;P:secret;;"))
        scanner.rejected()
        assertTrue(scanner.state.value.rejected)
        assertNull(inbox.take())
        assertEquals(0, nav.backs)
    }

    @Test
    fun `B-ANDR-049 paste instead works with no camera, and a bad paste says why without leaving`() {
        val inbox = PairingInbox()
        val nav = Navigator()
        val scanner = PairingScannerViewModel(inbox, nav)
        scanner.pasted(null)
        assertEquals(PairingProblem.Empty, scanner.state.value.problem)
        scanner.pasted("""{"urls":["https://a.example"]}""")
        assertEquals(PairingProblem.PasswordMissing, scanner.state.value.problem)
        assertEquals(0, nav.backs)
        assertNull(inbox.take())
    }

    @Test
    fun `B-PROTO-064 a pasted code reaches the server form through the inbox, password never in screen state`() = runTest {
        val inbox = PairingInbox()
        val nav = Navigator()
        val vm = ServersViewModel(null, Connections(), nav, inbox)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val before = vm.state.value.pairingArrival
        vm.pastePairing(code)
        assertEquals(before + 1, vm.state.value.pairingArrival)
        assertFalse("pw" in vm.state.value.toString(), "the password never enters observable state")
        val fill = vm.takePairing()!!
        assertEquals("https://box.tail.ts.net:4096", fill.address)
        assertEquals("me", fill.username)
        assertEquals("pw", fill.takePassword())
        assertNull(vm.takePairing())
    }

    @Test
    fun `B-PROTO-065 a broken pasted code shows its plain problem and fills nothing`() = runTest {
        val inbox = PairingInbox()
        val vm = ServersViewModel(null, Connections(), Navigator(), inbox)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.pastePairing("""{"urls":"nope","password":"x"}""")
        assertEquals(PairingProblem.UrlsType, vm.state.value.pairingProblem)
        assertNull(vm.takePairing())
        vm.pastePairing(code)
        assertNull(vm.state.value.pairingProblem)
    }

    @Test
    fun `B-SEC-124 pairing fills the form but never saves, contacts or consents to plain HTTP by itself`() = runTest {
        val connections = Connections()
        val vm = ServersViewModel(null, connections, Navigator(), PairingInbox())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.pastePairing("""{"urls":["http://box.lan:4096"],"password":"pw"}""")
        assertEquals(0, connections.saves)
        assertFalse(vm.state.value.cleartextAllowed)
        assertEquals("http://box.lan:4096", vm.takePairing()?.address)
    }

    @Test
    fun `B-PROTO-069 Scan code on the server form opens the pairing scanner`() {
        val nav = Navigator()
        val vm = ServersViewModel(null, Connections(), nav, PairingInbox())
        vm.scanPairing()
        assertEquals(PairingRoute.Scanner, nav.top.value)
    }
}
