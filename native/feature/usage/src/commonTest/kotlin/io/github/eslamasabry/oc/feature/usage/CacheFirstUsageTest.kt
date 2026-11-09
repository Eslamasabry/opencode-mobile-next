package io.github.eslamasabry.oc.feature.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.feature.usage.presentation.KeptUsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsPhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageDay
import io.github.eslamasabry.oc.feature.usage.presentation.UsageLastKnown
import io.github.eslamasabry.oc.feature.usage.presentation.UsageModel
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageProject
import io.github.eslamasabry.oc.feature.usage.presentation.UsageQuery
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageSource
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTimeZone
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTools
import io.github.eslamasabry.oc.feature.usage.presentation.UsageViewModel
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

/** Cache-first (owner rule, 2026-11-09): Usage and Limits draw their real layout from the last data seen. */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstUsageTest {
    private val home = ProfileId("home")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val full =
        UsageReport(
            0, 1, 4, 1, 9, 30, 3, 2, 1.5, UsageTokens(input = 5, output = 7),
            UsageTools(10, 8, 1, 1),
            persistentListOf(UsageModel("anthropic", "sonnet", "high", 12, UsageTokens(input = 3), 0.5)),
            persistentListOf(UsageDay("2026-11-01", 7)),
        )

    private class Source(var next: Outcome<UsageReport>) : UsageSource {
        override val servers = MutableStateFlow<ImmutableList<UsageServer>>(persistentListOf(UsageServer(ProfileId("home"), "Home")))
        override val active = MutableStateFlow<ProfileId?>(ProfileId("home"))
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun projects(profile: ProfileId): Outcome<ImmutableList<UsageProject>> = Outcome.Ok(persistentListOf())

        override suspend fun statistics(profile: ProfileId, query: UsageQuery): Outcome<UsageReport> {
            val answer = next
            gate?.await()
            return answer
        }
    }

    private class Shelf : UsageLastKnown {
        val kept = HashMap<String, KeptUsageReport>()

        override fun peek(profile: ProfileId, key: String) = kept[key]

        override suspend fun read(profile: ProfileId, key: String) = kept[key]

        override suspend fun keep(profile: ProfileId, key: String, report: KeptUsageReport) {
            kept[key] = report
        }
    }

    private object Nav : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)

        override fun open(route: AppRoute) = Unit

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-02T12:00:00Z")
        }

    private fun vm(source: Source, shelf: Shelf) =
        UsageViewModel(UsageRoute.Hub(home), source, UsageTimeZone { "Africa/Cairo" }, clock, Nav, emptySet(), setOf(shelf))

    @Test
    fun `CF-35 the kept report survives a round trip with every field the page draws`() {
        val back = KeptUsageReport.of(full).report()
        assertEquals(full, back)
    }

    @Test
    fun `CF-36 Usage opens on the kept report while the live read is pending, and the live one replaces it in place`() = runTest {
        val shelf = Shelf()
        val first = vm(Source(Outcome.Ok(full)), shelf)
        assertEquals(UsagePhase.Ready, first.state.value.phase)
        assertEquals(1, shelf.kept.size)
        val gate = CompletableDeferred<Unit>()
        val slow = Source(Outcome.Ok(full.copy(cost = 2.0))).also { it.gate = gate }
        val second = vm(slow, shelf)
        val frame = second.state.value
        assertEquals(UsagePhase.Ready, frame.phase)
        assertEquals(full.copy(coversRequest = false), frame.report)
        assertTrue(frame.refreshing)
        gate.complete(Unit)
        assertEquals(2.0, second.state.value.report?.cost)
        assertFalse(second.state.value.refreshing)
    }

    @Test
    fun `CF-37 a failed read with a kept report keeps the report and labels it as the previous result`() = runTest {
        val shelf = Shelf()
        vm(Source(Outcome.Ok(full)), shelf)
        val failing = vm(Source(Outcome.Failed(Failure.Network(NetworkWhy.Refused))), shelf)
        val s = failing.state.value
        assertEquals(UsagePhase.Failed, s.phase)
        assertNotNull(s.report)
        assertTrue(s.previous)
    }

    @Test
    fun `CF-38 a first-ever failed read still has no report to show`() = runTest {
        val s = vm(Source(Outcome.Failed(Failure.Network(NetworkWhy.Refused))), Shelf()).state.value
        assertEquals(null, s.report)
        assertFalse(s.previous)
    }

    @Test
    fun `CF-39 Limits drawn from kept readings before the first refresh has the loaded frame's structure`() {
        val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
        val sub =
            UsageSubscription(
                key = "claude",
                providerId = "claude",
                displayName = "Claude",
                state = SubscriptionState.OfflineStale,
                windows = listOf(UsageWindow("five_hour", 40.0, resetsAt = Instant.fromEpochMilliseconds(now + 3_600_000))),
                fetchedAt = Instant.fromEpochMilliseconds(now - 60_000),
            )
        val kept = limitsUi(UsageSnapshot(subscriptions = listOf(sub), loaded = false), true, now, "UTC")
        val live = limitsUi(UsageSnapshot(subscriptions = listOf(sub.copy(state = SubscriptionState.Ok)), loaded = true), false, now, "UTC")
        assertEquals(LimitsPhase.List, kept.phase)
        assertEquals(live.accounts.map { it.key to it.groups.map { g -> g.windows.map { w -> w.id } } },
            kept.accounts.map { it.key to it.groups.map { g -> g.windows.map { w -> w.id } } })
        assertTrue(kept.accounts.single().stale, "the status line, not another layout, says the numbers are old")
    }
}
