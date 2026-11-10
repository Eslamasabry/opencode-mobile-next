package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A streamed text delta moves only the feed's progress time: it must not recompute the turn facts, nor hide a stall. */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnFactsStatesTest {
    private val sid = Ref.sessionId.raw

    private class VirtualClock(private val scope: TestScope) : Clock {
        var reads = 0

        override fun now(): Instant {
            reads++
            return Instant.fromEpochMilliseconds(scope.testScheduler.currentTime)
        }
    }

    private fun delta() =
        GatewayEvent.PartDelta(SessionId(sid), MessageId("m2"), PartId("x"), DeltaField.Text, "word ")

    private fun TestScope.running(clock: VirtualClock): Pair<TurnFeedKeeper, kotlinx.coroutines.flow.StateFlow<io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi>> {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(listOf(stateMessage()))
        keeper.link(ChatLink.Live)
        keeper.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        val facts =
            TurnFactsPresenter(clock).states(keeper.feed, flowOf(false), backgroundScope, SharingStarted.Eagerly)
        runCurrent()
        return keeper to facts
    }

    private fun stateMessage() =
        io.github.eslamasabry.oc.core.model.transcript.MessageWithParts(
            io.github.eslamasabry.oc.core.model.transcript.MessageInfo(
                "m1",
                sid,
                io.github.eslamasabry.oc.core.model.transcript.Role.User,
                time = io.github.eslamasabry.oc.core.model.transcript.MsgTime(1_000, null),
            ),
            kotlinx.collections.immutable.persistentListOf(
                io.github.eslamasabry.oc.core.model.transcript.Part(
                    "p1",
                    "m1",
                    sid,
                    io.github.eslamasabry.oc.core.model.transcript.PartType.Text,
                    "text",
                    text = "fix the build",
                )
            ),
        )

    /** present() asks the feed's windows whether they are empty once per recompute; this counts the asks. */
    private class CountingWindows : AbstractMap<String, Long>() {
        var asks = 0

        override val entries: Set<Map.Entry<String, Long>> = emptySet()

        override fun isEmpty(): Boolean {
            asks++
            return true
        }
    }

    @Test
    fun `fifty streamed deltas recompute nothing`() = runTest {
        val windows = CountingWindows()
        val feed = MutableStateFlow(TurnFeed(busy = true, runStartedAtMs = 0, lastProgressAtMs = 0, link = ChatLink.Live, windows = windows))
        TurnFactsPresenter(VirtualClock(this)).states(feed, flowOf(false), backgroundScope, SharingStarted.Eagerly)
        runCurrent()
        val before = windows.asks
        assertTrue(before > 0, "the first facts were worked out")
        repeat(50) {
            // What a text delta does to the feed: only the progress time moves.
            feed.value = feed.value.copy(lastProgressAtMs = 100L + it)
            runCurrent()
        }
        assertEquals(0, windows.asks - before, "each delta recomputed the turn facts")
    }

    @Test
    fun `a reply that keeps streaming is never stalled, and one that goes silent still is`() = runTest {
        val clock = VirtualClock(this)
        val (keeper, facts) = running(clock)
        for (step in 1..20) {
            advanceTimeBy(10_000)
            keeper.event(delta(), testScheduler.currentTime)
            runCurrent()
            assertNull(facts.value.live, "a reply still streaming at ${testScheduler.currentTime} ms looks stalled")
        }
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(LiveNoticeUi.Stall(TurnStallNotice.ModelSlow), facts.value.live)
        assertTrue(testScheduler.currentTime > 200_000)
    }
}
