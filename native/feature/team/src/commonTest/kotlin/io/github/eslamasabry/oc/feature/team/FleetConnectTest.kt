package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.FleetFrame
import io.github.eslamasabry.oc.engine.team.fleet.FleetReply
import io.github.eslamasabry.oc.engine.team.fleet.FleetStreamEnd
import io.github.eslamasabry.oc.engine.team.fleet.FleetTransport
import io.github.eslamasabry.oc.engine.team.fleet.FleetTransports
import io.github.eslamasabry.oc.feature.team.fleet.FleetConnectProblem
import io.github.eslamasabry.oc.feature.team.fleet.FleetConnectRules
import io.github.eslamasabry.oc.feature.team.fleet.FleetConnectViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsPresent
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Connect a team on a computer / Change address (Flutter team_host_form.dart) and the wording of a team on this phone. */
@OptIn(ExperimentalCoroutinesApi::class)
class FleetConnectTest {
    private val dispatcher = StandardTestDispatcher()
    private val pid = ProfileId("p1")
    private val tailnet = "http://100.101.102.103:8373"

    @BeforeTest fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `the address rule is decided before any request`() {
        assertEquals(FleetConnectProblem.AddressRequired, FleetConnectRules.check("   "))
        assertEquals(FleetConnectProblem.TailnetRequired, FleetConnectRules.check("http://192.168.1.20:8373"))
        assertEquals(FleetConnectProblem.TailnetRequired, FleetConnectRules.check("http://example.com"))
        assertEquals(FleetConnectProblem.BadAddress, FleetConnectRules.check("100.101.102.103:8373"))
        assertEquals(FleetConnectProblem.BadAddress, FleetConnectRules.check("ftp://100.101.102.103"))
        assertEquals(FleetConnectProblem.BadAddress, FleetConnectRules.check("http://name@100.101.102.103"))
        assertNull(FleetConnectRules.check(tailnet))
        assertNull(FleetConnectRules.check("http://127.0.0.1:8372"))
        assertNull(FleetConnectRules.check("http://studio.tail1234.ts.net:8373"))
        assertNull(FleetConnectRules.check("https://team.example.com"))
    }

    @Test
    fun `each verdict has its own words and only a silent host may be kept anyway`() {
        assertEquals(FleetConnectProblem.NotGasCity, FleetConnectRules.problem(FleetFailure.NotGasCity))
        assertEquals(FleetConnectProblem.CityNotRunning, FleetConnectRules.problem(FleetFailure.CityNotRunning))
        assertEquals(FleetConnectProblem.TailnetRequired, FleetConnectRules.problem(FleetFailure.PlainHttpRefused))
        assertTrue(FleetConnectRules.mayKeepAnyway(FleetFailure.Unreachable))
        assertFalse(FleetConnectRules.mayKeepAnyway(FleetFailure.NotGasCity))
        assertFalse(FleetConnectRules.mayKeepAnyway(FleetFailure.CityNotRunning))
        assertTrue(FleetConnectRules.showsGuide(FleetConnectProblem.NotGasCity))
        assertFalse(FleetConnectRules.showsGuide(FleetConnectProblem.TailnetRequired))
    }

    /** A host that answers like a Gas City in memory, or does not answer at all. */
    private class Answering(var down: Boolean = false) : FleetTransports {
        val opened = mutableListOf<String>()

        override fun open(baseUrl: String): FleetTransport {
            opened += baseUrl
            return object : FleetTransport {
                override suspend fun get(path: String, query: List<Pair<String, String>>, timeoutMs: Long): FleetReply {
                    if (down) return FleetReply.Unreachable()
                    val body =
                        when (path) {
                            "/health", "/v0/city/main/health" -> """{"status":"ok","version":"1.4.1","city":"main"}"""
                            "/v0/cities" -> """{"items":[{"name":"main","running":true}]}"""
                            "/v0/city/main/status" -> """{"name":"main"}"""
                            else -> return FleetReply.Answered(404, """{"status":404}""")
                        }
                    return FleetReply.Answered(200, body)
                }

                override suspend fun send(method: String, path: String, body: Map<String, Any?>?, requestId: String, idempotencyKey: String?) =
                    FleetReply.Unreachable()

                override suspend fun stream(path: String, lastEventId: String?, stallMs: Long, onFrame: suspend (FleetFrame) -> Unit) = FleetStreamEnd.Ended

                override fun close() = Unit
            }
        }
    }

    private class Host(var config: TeamConfig?) : TeamHost {
        val saved = mutableListOf<TeamConfig>()

        override suspend fun config(profileId: String): TeamConfig? = config

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> = error("not read here")

        override suspend fun command(profileId: String, config: TeamConfig, command: TeamCommand, body: Map<String, Any?>) = error("no command")

        override suspend fun saveComputerTeam(profileId: String, config: TeamConfig): Outcome<Unit> {
            saved += config
            this.config = config
            return Outcome.Ok(Unit)
        }
    }

    private fun model(host: Host, net: Answering, nav: FakeNav) =
        FleetConnectViewModel(TeamRoute.Address(pid), TeamEngine(host, computerTransports = net), nav, Redactor())

    @Test
    fun `Change address starts from the saved address and a found team is saved and the page closes`() =
        runTest(dispatcher) {
            val saved = TeamConfig(TeamProvider.GasCity, "http://100.64.0.9:8372", "main", TeamHostMode.Computer)
            val host = Host(saved)
            val nav = FakeNav()
            val vm = model(host, Answering(), nav)
            runCurrent()
            assertTrue(vm.state.value.ready)
            assertEquals("http://100.64.0.9:8372", vm.state.value.initialUrl)
            assertEquals("main", vm.state.value.initialCity)
            vm.test(" $tailnet ", "")
            runCurrent()
            assertEquals(1, host.saved.size)
            assertEquals(tailnet, host.saved.single().url)
            assertEquals("main", host.saved.single().city)
            assertEquals(1, nav.backs)
        }

    @Test
    fun `an address the rule refuses makes no request and says why`() =
        runTest(dispatcher) {
            val net = Answering()
            val vm = model(Host(null), net, FakeNav())
            runCurrent()
            vm.test("http://192.168.1.20:8373", "")
            runCurrent()
            assertEquals(FleetConnectProblem.TailnetRequired, vm.state.value.problem)
            assertTrue(net.opened.isEmpty())
            assertFalse(vm.state.value.testing)
        }

    @Test
    fun `an address that does not answer can be saved anyway and an edit takes that offer back`() =
        runTest(dispatcher) {
            val host = Host(null)
            val nav = FakeNav()
            val vm = model(host, Answering(down = true), nav)
            runCurrent()
            vm.test(tailnet, "main")
            runCurrent()
            assertEquals(FleetConnectProblem.BadAddress, vm.state.value.problem)
            assertTrue(vm.state.value.canSaveAnyway)
            assertEquals(tailnet, vm.state.value.asked?.text)
            assertTrue(host.saved.isEmpty())
            vm.edited()
            assertFalse(vm.state.value.canSaveAnyway)
            assertNull(vm.state.value.problem)
            vm.saveAnyway()
            runCurrent()
            assertTrue(host.saved.isEmpty())
            vm.test(tailnet, "main")
            runCurrent()
            vm.saveAnyway()
            runCurrent()
            assertEquals(1, host.saved.size)
            assertFalse(host.saved.single().front)
            assertEquals(1, nav.backs)
        }

    @Test
    fun `Team settings offers Change address for a computer and words a team on this phone as this phone`() {
        val onComputer = TeamConfig(TeamProvider.GasCity, "http://100.64.0.9:8372", "main")
        val computer = TeamSettingsPresent.present(TeamState(TeamPhase.Computer, onComputer), Redactor()::details)
        assertTrue(computer.computer)
        assertFalse(computer.onPhone)
        assertTrue(computer.canChangeAddress)
        val here = TeamConfig(TeamProvider.GasCity, "http://127.0.0.1:8372", "main", TeamHostMode.Phone)
        val onThisPhone = TeamSettingsPresent.present(TeamState(TeamPhase.Computer, here), Redactor()::details)
        assertTrue(onThisPhone.computer)
        assertTrue(onThisPhone.onPhone)
        val engine = TeamSettingsPresent.present(TeamState(TeamPhase.Ready, TeamConfig(TeamProvider.PhoneEngine, "http://127.0.0.1:4098")), Redactor()::details)
        assertTrue(engine.onPhone)
        assertFalse(engine.canChangeAddress)
    }
}
