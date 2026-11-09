package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamIntroPrimary
import io.github.eslamasabry.oc.feature.team.presentation.TeamServer
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerFacts
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind
import io.github.eslamasabry.oc.feature.team.presentation.primary
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Device walk 2026-11-08: the AI Team page showed only "AI Team is off". Flutter's off page (team_intro_screen.dart)
 * says how to turn it on for this kind of server, and the same page becomes the team once it is on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamOffTest {
    private val dispatcher = StandardTestDispatcher()
    private val pid = ProfileId("p1")

    @BeforeTest fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun TestScope.watch(flow: StateFlow<*>) = backgroundScope.launch { flow.collect {} }

    private class OffHost(val can: Boolean) : TeamHost {
        override suspend fun config(profileId: String): TeamConfig? = null

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> =
            Outcome.Ok(TeamRead(READY_HEALTH, team(shop())))

        override suspend fun command(
            profileId: String,
            config: TeamConfig,
            command: TeamCommand,
            body: Map<String, Any?>,
        ): Outcome<TeamCommandResult> = Outcome.Ok(TeamCommandResult(accepted = true))

        override suspend fun canTurnOnPhoneTeam(profileId: String): Boolean = can
    }

    private val phone = TeamServerFacts { TeamServer(TeamServerKind.ThisPhone, "This phone") }
    private val computer = TeamServerFacts { TeamServer(TeamServerKind.Computer, "studio-pc") }

    @Test
    fun `B-AGENT-294 this phone turns its team on from the off page, on the four-step page`() =
        runTest(dispatcher) {
            val nav = FakeNav()
            val vm = TeamHomeViewModel(TeamRoute.Home(pid), TeamEngine(OffHost(can = true)), nav, FIXED_CLOCK, phone)
            watch(vm.state)
            runCurrent()
            val off = vm.state.value
            assertEquals(TeamPhase.Off, off.phase)
            assertEquals(TeamServer(TeamServerKind.ThisPhone, "This phone"), off.intro.server)
            assertEquals(TeamIntroPrimary.TurnOnPhone, off.intro.primary())
            vm.turnOn()
            assertEquals(listOf<Any>(TeamRoute.TurnOn(pid)), nav.opened)
        }

    @Test
    fun `B-AGENT-294 where the app cannot turn it on, this phone's primary opens Add tools`() =
        runTest(dispatcher) {
            val nav = FakeNav()
            val vm = TeamHomeViewModel(TeamRoute.Home(pid), TeamEngine(OffHost(can = false)), nav, FIXED_CLOCK, phone)
            watch(vm.state)
            runCurrent()
            assertEquals(TeamIntroPrimary.SetUpPhone, vm.state.value.intro.primary())
            vm.turnOn()
            vm.setUpOnPhone()
            assertEquals(listOf<Any>(PhoneSetupRoute.Customize(adding = true)), nav.opened)
        }

    @Test
    fun `B-AGENT-294 a computer gets the host guide, and an unknown server waits`() =
        runTest(dispatcher) {
            val vm = TeamHomeViewModel(
                TeamRoute.Home(pid),
                TeamEngine(OffHost(can = false)),
                FakeNav(),
                FIXED_CLOCK,
                computer,
            )
            watch(vm.state)
            runCurrent()
            assertEquals(TeamIntroPrimary.SetUpComputer, vm.state.value.intro.primary())
            vm.openGuide()
            runCurrent()
            assertTrue(vm.state.value.intro.guide)
            vm.closeGuide()
            runCurrent()
            assertFalse(vm.state.value.intro.guide)
            val unknown = TeamHomeViewModel(
                TeamRoute.Home(pid),
                TeamEngine(OffHost(can = true)),
                FakeNav(),
                FIXED_CLOCK,
            )
            watch(unknown.state)
            runCurrent()
            assertNull(unknown.state.value.intro.primary())
        }
}
