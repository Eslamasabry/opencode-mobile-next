package io.github.eslamasabry.oc.feature.servers.pairing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

/** P12-FIX-PREVIEWQR: Codex second-eye findings on pairing (previewqr #1–#3), from their catalog rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class P12FixPairingTest {
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

        override suspend fun save(name: String, address: String, username: String, password: String, allowCleartext: Boolean): Outcome<ProfileId> =
            Outcome.Ok(ProfileId("p"))

        override suspend fun connect(id: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef> = error("not used")

        override suspend fun remove(id: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)
    }

    /** Answers by address; records what was asked, in order. */
    private class Probe(private val answers: Map<String, PairingAnswer>) : PairingProbe {
        val asked = mutableListOf<String>()

        override suspend fun check(address: String): PairingAnswer {
            asked += address
            return answers[address] ?: PairingAnswer.NoAnswer
        }
    }

    private fun code(vararg urls: String) =
        """{"urls":[${urls.joinToString(",") { "\"$it\"" }}],"username":"me","password":"pw"}"""

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    // ---- #1 B-PROTO-068 ---------------------------------------------------------------------------------------

    @Test
    fun `B-PROTO-068 the form fills the first address that works, falling through ones that do not answer`() = runTest {
        val probe = Probe(mapOf("https://b.example:4096" to PairingAnswer.Works))
        val inbox = PairingInbox()
        val vm = ServersViewModel(null, Connections(), Navigator(), inbox, probe)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.pastePairing(code("https://a.example:4096", "https://b.example:4096"))
        val fill = vm.takePairing()!!
        assertEquals("https://b.example:4096", fill.address)
        assertEquals("pw", fill.takePassword(), "the password moves with the chosen address")
        assertEquals(listOf("https://a.example:4096", "https://b.example:4096"), probe.asked)
    }

    @Test
    fun `B-PROTO-068 an address that answers 401 still counts, but a working one beats it`() = runTest {
        val urls = listOf("https://a.example", "https://b.example", "https://c.example")
        assertEquals(
            "https://c.example",
            PairingChoice.choose(urls, Probe(mapOf("https://b.example" to PairingAnswer.Answers, "https://c.example" to PairingAnswer.Works))),
        )
        assertEquals("https://b.example", PairingChoice.choose(urls, Probe(mapOf("https://b.example" to PairingAnswer.Answers))))
        assertNull(PairingChoice.choose(urls, Probe(emptyMap())), "none answered: the form keeps its best guess")
    }

    @Test
    fun `B-SEC-124 plain HTTP to another device is never probed before the person agrees, loopback HTTP may be`() = runTest {
        val probe = Probe(mapOf("http://127.0.0.1:4096" to PairingAnswer.Works))
        val chosen = PairingChoice.choose(listOf("http://box.lan:4096", "http://127.0.0.1:4096"), probe)
        assertEquals("http://127.0.0.1:4096", chosen)
        assertEquals(listOf("http://127.0.0.1:4096"), probe.asked)
    }

    @Test
    fun `B-PROTO-068 when nothing answers the form still fills the best-ordered address`() = runTest {
        val vm = ServersViewModel(null, Connections(), Navigator(), PairingInbox(), Probe(emptyMap()))
        vm.pastePairing(code("https://a.example:4096", "http://127.0.0.1:4096"))
        assertEquals("https://a.example:4096", vm.takePairing()?.address)
    }

    // ---- #2 B-PROTO-069 ---------------------------------------------------------------------------------------

    @Test
    fun `B-PROTO-069 a camera result arriving after Close neither fills the form nor steps Back twice`() {
        val inbox = PairingInbox()
        val nav = Navigator()
        val scanner = PairingScannerViewModel(inbox, nav)
        scanner.close()
        scanner.scanned(code("https://box.tail.ts.net:4096"))
        assertEquals(1, nav.backs, "only Close stepped back; the server form under it stays")
        assertNull(inbox.take())
    }

    @Test
    fun `B-PROTO-069 a second code after one was delivered is ignored`() {
        val inbox = PairingInbox()
        val nav = Navigator()
        val scanner = PairingScannerViewModel(inbox, nav)
        scanner.pasted(code("https://first.example"))
        scanner.scanned(code("https://second.example"))
        assertEquals(1, nav.backs)
        assertEquals("https://first.example", inbox.take()?.address)
        assertNull(inbox.take())
    }

    // ---- #3 the unsaved password ------------------------------------------------------------------------------

    @Test
    fun `B-PROTO-069 the unsaved password waits in memory while the scanner is on top, never in screen state`() {
        val vm = ServersViewModel(null, Connections(), Navigator(), PairingInbox())
        vm.holdPassword("s3cret")
        assertEquals("s3cret", vm.heldPassword())
        assertEquals(false, "s3cret" in vm.state.value.toString())
    }
}
