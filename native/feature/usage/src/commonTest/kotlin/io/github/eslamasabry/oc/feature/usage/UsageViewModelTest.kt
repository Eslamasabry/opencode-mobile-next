package io.github.eslamasabry.oc.feature.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageProject
import io.github.eslamasabry.oc.feature.usage.presentation.UsageQuery
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageScope
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageSource
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTimeZone
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModelTest {
    private val home = ProfileId("home")
    private val work = ProfileId("work")

    private fun report(cost: Double, sessions: Long = 1) =
        UsageReport(0, 1, sessions, 0, 1, 1, 1, 1, cost, UsageTokens(input = 5), null, persistentListOf(), persistentListOf())

    private inner class Source : UsageSource {
        override val servers = MutableStateFlow<ImmutableList<UsageServer>>(persistentListOf(UsageServer(home, "Home")))
        override val active = MutableStateFlow<ProfileId?>(home)
        val queries = mutableListOf<Pair<ProfileId, UsageQuery>>()
        var next: Outcome<UsageReport> = Outcome.Ok(report(1.0))
        var gate: CompletableDeferred<Unit>? = null
        var projectsResult: Outcome<ImmutableList<UsageProject>> =
            Outcome.Ok(persistentListOf(UsageProject("prj_app", "app")))

        override suspend fun projects(profile: ProfileId) = projectsResult

        override suspend fun statistics(profile: ProfileId, query: UsageQuery): Outcome<UsageReport> {
            queries += profile to query
            val answer = next
            gate?.await()
            return answer
        }
    }

    private class Navigator : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        val opened = mutableListOf<AppRoute>()

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
            override fun now(): Instant = Instant.parse("2026-11-02T12:00:00Z")
        }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun vm(source: Source, zone: String? = "Africa/Cairo", nav: Navigator = Navigator(), profile: ProfileId? = null) =
        UsageViewModel(UsageRoute.Hub(profile), source, UsageTimeZone { zone }, clock, nav)

    @Test
    fun `B-NAV-078 a failed refresh after the time zone changed never relabels the old totals with the new zone`() = runTest {
        val source = Source()
        var zone = "Africa/Cairo"
        val vm = UsageViewModel(UsageRoute.Hub(null), source, UsageTimeZone { zone }, clock, Navigator())
        assertEquals(1.0, vm.state.value.report?.cost)
        zone = "Asia/Tokyo"
        val slow = CompletableDeferred<Unit>()
        source.gate = slow
        source.next = Outcome.Failed(Failure.Network(NetworkWhy.Refused))
        vm.refresh()
        assertEquals(UsagePhase.Loading, vm.state.value.phase)
        assertNull(vm.state.value.report)
        slow.complete(Unit)
        val s = vm.state.value
        assertEquals(UsagePhase.Failed, s.phase)
        assertEquals("Asia/Tokyo", s.timezone)
        assertNull(s.report)
        assertFalse(s.previous)
    }

    @Test
    fun `B-PROTO-122 opening reads the active server for 30 days across all projects in the device time zone`() = runTest {
        val source = Source()
        val vm = vm(source)
        val s = vm.state.value
        assertEquals(UsagePhase.Ready, s.phase)
        assertEquals(1.0, s.report?.cost)
        assertEquals(UsageRange.ThirtyDays, s.range)
        val (profile, query) = source.queries.single()
        assertEquals(home, profile)
        assertNull(query.projectId)
        assertEquals("Africa/Cairo", query.timezone)
        assertEquals(listOf(UsageProject("prj_app", "app")), s.projects)
    }

    @Test
    fun `B-PROTO-122 choosing a project and a range sends exactly that project and range`() = runTest {
        val source = Source()
        val vm = vm(source)
        vm.chooseScope(UsageScope.Project("prj_app"))
        assertEquals("prj_app", source.queries.last().second.projectId)
        vm.chooseRange(UsageRange.AllTime)
        assertNull(source.queries.last().second.fromMs)
        assertEquals("prj_app", source.queries.last().second.projectId)
    }

    @Test
    fun `B-NAV-078 a late answer for older filters can never label the newer totals`() = runTest {
        val source = Source()
        val vm = vm(source)
        val slow = CompletableDeferred<Unit>()
        source.gate = slow
        source.next = Outcome.Ok(report(99.0))
        vm.chooseRange(UsageRange.Today)
        source.gate = null
        source.next = Outcome.Ok(report(2.0))
        vm.chooseRange(UsageRange.Year)
        slow.complete(Unit)
        assertEquals(2.0, vm.state.value.report?.cost)
        assertEquals(UsageRange.Year, vm.state.value.range)
    }

    @Test
    fun `B-NAV-078 a failed refresh keeps the previous result only for unchanged filters and says so`() = runTest {
        val source = Source()
        val vm = vm(source)
        source.next = Outcome.Failed(Failure.Network(NetworkWhy.Refused))
        vm.refresh()
        var s = vm.state.value
        assertEquals(UsagePhase.Failed, s.phase)
        assertEquals(1.0, s.report?.cost)
        assertTrue(s.previous)
        vm.chooseRange(UsageRange.Today)
        s = vm.state.value
        assertEquals(UsagePhase.Failed, s.phase)
        assertNull(s.report)
        assertFalse(s.previous)
    }

    @Test
    fun `B-NAV-078 authorization, absent endpoint and unreadable usage stay distinct states`() = runTest {
        val source = Source()
        source.next = Outcome.Failed(Failure.SignIn(SignInWhy.PasswordRejected))
        val vm = vm(source)
        assertEquals(UsagePhase.Authorization, vm.state.value.phase)
        source.next = Outcome.Failed(Failure.Unavailable(FeatureId("usageStatistics")))
        vm.refresh()
        assertEquals(UsagePhase.Unsupported, vm.state.value.phase)
        source.next = Outcome.Failed(Failure.UnreadableReply())
        vm.refresh()
        assertEquals(UsagePhase.Invalid, vm.state.value.phase)
        source.next = Outcome.Ok(report(3.0))
        vm.refresh()
        assertEquals(UsagePhase.Ready, vm.state.value.phase)
    }

    @Test
    fun `B-PROTO-122 an unreadable device time zone sends nothing and says so`() = runTest {
        val source = Source()
        val vm = vm(source, zone = null)
        assertEquals(UsagePhase.NoTimeZone, vm.state.value.phase)
        assertTrue(source.queries.isEmpty())
    }

    @Test
    fun `SCREENS-NAV-96 switching server resets the project filter and reads that server`() = runTest {
        val source = Source()
        source.servers.value = persistentListOf(UsageServer(home, "Home"), UsageServer(work, "Work"))
        val vm = vm(source)
        vm.chooseScope(UsageScope.Project("prj_app"))
        vm.chooseServer(work)
        assertEquals(UsageScope.AllProjects, vm.state.value.scope)
        assertEquals(work, source.queries.last().first)
        assertNull(source.queries.last().second.projectId)
        assertEquals("Work", vm.state.value.serverName)
    }

    @Test
    fun `SCREENS-NAV-96 no connected server offers Servers and a failed project list keeps All projects`() = runTest {
        val empty = Source()
        empty.servers.value = persistentListOf()
        empty.active.value = null
        val nav = Navigator()
        val vm = vm(empty, nav = nav)
        assertEquals(UsagePhase.NoServer, vm.state.value.phase)
        vm.openServers()
        assertEquals(listOf<AppRoute>(ServersRoute.Home), nav.opened)

        val failing = Source()
        failing.projectsResult = Outcome.Failed(Failure.Network(NetworkWhy.Refused))
        val other = vm(failing)
        assertTrue(other.state.value.projectsFailed)
        assertEquals(UsagePhase.Ready, other.state.value.phase)
        assertEquals(UsageScope.AllProjects, other.state.value.scope)
    }
}
