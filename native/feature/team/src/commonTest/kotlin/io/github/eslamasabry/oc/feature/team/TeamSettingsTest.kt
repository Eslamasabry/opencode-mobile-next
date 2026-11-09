package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsViewModel
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Team settings (Dart `TeamSettingsScreen`) for the phone's team: the agents, how it runs here, and Turn off, which asks
 * first, stops the team and leaves for the page that says it is off; a team that could not be stopped stays on and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamSettingsTest {
    private val dispatcher = StandardTestDispatcher()
    private val pid = ProfileId("p1")

    @BeforeTest fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun TestScope.watch(flow: StateFlow<*>) = backgroundScope.launch { flow.collect {} }

    private class OnHost(var off: Outcome<Unit> = Outcome.Ok(Unit)) : TeamHost {
        var config: TeamConfig? = TeamConfig(TeamProvider.PhoneEngine, "http://127.0.0.1:4098", hostMode = TeamHostMode.Phone)
        var offs = 0

        override suspend fun config(profileId: String): TeamConfig? = config

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> =
            Outcome.Ok(TeamRead(READY_HEALTH, team(shop())))

        override suspend fun command(
            profileId: String,
            config: TeamConfig,
            command: TeamCommand,
            body: Map<String, Any?>,
        ): Outcome<TeamCommandResult> = Outcome.Ok(TeamCommandResult(accepted = true))

        override suspend fun turnOffPhoneTeam(profileId: String): Outcome<Unit> {
            offs++
            if (off is Outcome.Ok) config = null
            return off
        }
    }

    private fun viewModel(host: OnHost, nav: FakeNav = FakeNav()) =
        TeamSettingsViewModel(TeamRoute.Settings(pid), TeamEngine(host), nav, Redactor())

    @Test
    fun `B-AGENT-294 the settings page shows the team's roles, its check and where it runs`() =
        runTest(dispatcher) {
            val vm = viewModel(OnHost())
            watch(vm.state)
            runCurrent()
            val ui = vm.state.value
            assertEquals(TeamPhase.Ready, ui.phase)
            assertTrue(ui.canCheck)
            val host = assertNotNull(ui.host)
            assertEquals("http://127.0.0.1:4098", host.address.text)
            assertEquals("phoneEngine", host.provider.text)
            assertEquals("landlock", host.protection)
        }

    @Test
    fun `B-AGENT-295 Turn off asks first, and Keep changes nothing`() =
        runTest(dispatcher) {
            val host = OnHost()
            val vm = viewModel(host)
            watch(vm.state)
            runCurrent()
            vm.askTurnOff()
            runCurrent()
            assertTrue(vm.state.value.confirmingOff)
            vm.cancelTurnOff()
            runCurrent()
            assertFalse(vm.state.value.confirmingOff)
            assertEquals(0, host.offs)
        }

    @Test
    fun `B-AGENT-295 a confirmed Turn off turns the team off and leaves for the page that says so`() =
        runTest(dispatcher) {
            val host = OnHost()
            val nav = FakeNav()
            val vm = viewModel(host, nav)
            watch(vm.state)
            runCurrent()
            vm.askTurnOff()
            vm.confirmTurnOff()
            vm.confirmTurnOff() // a second tap while it runs is not a second turn-off
            runCurrent()
            assertEquals(1, host.offs)
            assertEquals(1, nav.backs)
            assertEquals(TeamPhase.Off, vm.state.value.phase)
        }

    @Test
    fun `B-AGENT-043 a team that could not be stopped stays on and says so`() =
        runTest(dispatcher) {
            val host = OnHost(off = Outcome.Failed(Failure.Unexpected()))
            val nav = FakeNav()
            val vm = viewModel(host, nav)
            watch(vm.state)
            runCurrent()
            vm.askTurnOff()
            vm.confirmTurnOff()
            runCurrent()
            assertTrue(vm.state.value.offFailed)
            assertFalse(vm.state.value.working)
            assertEquals(0, nav.backs)
            assertEquals(TeamPhase.Ready, vm.state.value.phase)
            vm.dismissOffFailed()
            runCurrent()
            assertFalse(vm.state.value.offFailed)
        }

    @Test
    fun `B-AGENT-294 the Agents row opens the roles and the check row opens the four steps`() =
        runTest(dispatcher) {
            val nav = FakeNav()
            val vm = viewModel(OnHost(), nav)
            vm.openAgents()
            vm.openSetup()
            assertEquals(listOf<Any>(TeamRoute.Editor(pid, "roles"), TeamRoute.TurnOn(pid)), nav.opened)
        }
}
