package io.github.eslamasabry.oc.feature.plans

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorControls
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorUi
import io.github.eslamasabry.oc.feature.plans.presentation.KeptPlans
import io.github.eslamasabry.oc.feature.plans.presentation.PlansLastKnown
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansSource
import io.github.eslamasabry.oc.feature.plans.presentation.PlansViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstPlansTest {
    private class Source(val live: UsageSnapshot) : PlansSource {
        override val snapshot = MutableStateFlow(UsageSnapshot())
        val gate = CompletableDeferred<Unit>()

        override suspend fun refresh(force: Boolean) {
            gate.await()
            snapshot.value = live
        }

        override suspend fun setThreshold(key: String?, percent: Double?) = false

        override suspend fun setResetNotice(key: String?, value: Boolean) = false

        override val defaultThreshold = 80.0
        override val resetNotice = false
    }

    private object NoCollector : CollectorControls {
        override val state: StateFlow<CollectorUi> = MutableStateFlow(CollectorUi())

        override suspend fun setUp() {}

        override suspend fun turnOff() {}
    }

    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        override fun open(route: AppRoute) {}
        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}
        override fun land() {}
        override fun replaceRoot(root: AppRoute) {}
        override fun back() {}
    }

    private class Shelf(var kept: KeptPlans? = null) : PlansLastKnown {
        override fun peek() = kept
        override suspend fun read() = kept
        override suspend fun keep(kept: KeptPlans) {
            this.kept = Json.decodeFromString(KeptPlans.serializer(), Json.encodeToString(KeptPlans.serializer(), kept))
        }
    }

    private object Now : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_793_606_400_000L)
    }

    private val card = UsageSubscription("claude@phone", "claude", "Claude", SubscriptionState.Ok, planLabel = "Max")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `CF-85 the first frame of Plans with kept cards is the list, not a loading page`() = runTest {
        val shelf = Shelf(KeptPlans.of(io.github.eslamasabry.oc.feature.plans.presentation.plansUi(UsageSnapshot(listOf(card), loaded = true), false, 0)))
        val vm = PlansViewModel(Source(UsageSnapshot()), Nav, Now, NoCollector, setOf(shelf))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val first = vm.state.value
        assertEquals(PlansPhase.List, first.phase)
        assertEquals(listOf("Claude"), first.cards.map { it.name })
        assertTrue(first.updating)
    }

    @Test
    fun `CF-86 the live cards replace the kept ones in place and are kept for next time`() = runTest {
        val shelf = Shelf(KeptPlans(cards = listOf(KeptPlans.Card("old", "claude", "Old", state = SubscriptionState.Ok))))
        val source = Source(UsageSnapshot(listOf(card), loaded = true))
        val vm = PlansViewModel(source, Nav, Now, NoCollector, setOf(shelf))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        source.gate.complete(Unit)
        assertEquals(listOf("Claude"), vm.state.value.cards.map { it.name })
        assertEquals(false, vm.state.value.updating)
        assertEquals(listOf("Claude"), shelf.kept!!.cards.map { it.name })
    }

    @Test
    fun `CF-87 with nothing kept the first-ever load is loading`() = runTest {
        val vm = PlansViewModel(Source(UsageSnapshot()), Nav, Now, NoCollector, setOf(Shelf()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(PlansPhase.Loading, vm.state.value.phase)
    }
}
