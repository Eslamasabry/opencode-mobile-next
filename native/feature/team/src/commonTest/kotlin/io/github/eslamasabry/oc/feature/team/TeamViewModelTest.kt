package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.TeamWorkspace
import io.github.eslamasabry.oc.feature.team.presentation.BoardColumn
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pid = ProfileId("p1")

    private class Host(var answer: Outcome<TeamWorkspace>) : TeamHost {
        var reads = 0

        override suspend fun config(profileId: String): TeamConfig =
            TeamConfig(TeamProvider.PhoneEngine, "http://127.0.0.1:4098", hostMode = TeamHostMode.Phone)

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamWorkspace> {
            reads++
            return answer
        }
    }

    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
        }

    @BeforeTest
    fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    private fun TestScope.watch(flow: StateFlow<*>) = backgroundScope.launch { flow.collect {} }

    @Test
    fun `B-TEAM-030 an open team page reads the engine again every 3 s, and stops when it is not shown`() =
        runTest(dispatcher) {
            val host = Host(Outcome.Ok(team(shop())))
            val vm = TeamHomeViewModel(TeamRoute.Home(pid), TeamEngine(host), Nav(), clock)
            val job = watch(vm.state)
            runCurrent()
            assertEquals(TeamPhase.Ready, vm.state.value.phase)
            assertEquals(1, host.reads)
            advanceTimeBy(TeamEngine.POLL_MS + 1)
            assertEquals(2, host.reads)
            job.cancel()
            advanceTimeBy(60_000)
            // The 5 s grace after the page goes away allows one more read (at 6 s), then reading stops.
            assertEquals(3, host.reads)
        }

    @Test
    fun `B-TEAM-031 a project row and a request row open the project`() =
        runTest(dispatcher) {
            val nav = Nav()
            val vm = TeamHomeViewModel(TeamRoute.Home(pid), TeamEngine(Host(Outcome.Ok(team(shop())))), nav, clock)
            vm.openProject("shop")
            vm.openRequest("shop", "q1")
            assertEquals(listOf<AppRoute>(TeamRoute.Project(pid, "shop"), TeamRoute.Project(pid, "shop")), nav.opened)
        }

    @Test
    fun `B-TEAM-032 the phone team not answering shows the team off`() =
        runTest(dispatcher) {
            val vm = TeamHomeViewModel(TeamRoute.Home(pid), TeamEngine(Host(Outcome.Failed(Failure.Unexpected()))), Nav(), clock)
            watch(vm.state)
            runCurrent()
            assertEquals(TeamPhase.PhoneOff, vm.state.value.phase)
        }

    @Test
    fun `B-TEAM-033 the board opens on its milestone and keeps the person's filters and lane`() =
        runTest(dispatcher) {
            val vm =
                TeamBoardViewModel(TeamRoute.Board(pid, "shop", milestone = "m2"), TeamEngine(Host(Outcome.Ok(team(shop())))), Nav(), clock)
            watch(vm.state)
            runCurrent()
            assertEquals(listOf("t2", "t3", "t4", "t5"), vm.state.value.cards.map { it.id })
            vm.selectColumn(BoardColumn.Review)
            vm.filterRepo("r1")
            runCurrent()
            assertEquals(listOf("t2", "t5"), vm.state.value.cards.map { it.id })
            assertEquals(BoardColumn.Review, vm.state.value.column)
            vm.showGraph(true)
            runCurrent()
            assertEquals(true, vm.state.value.graph)
        }
}
