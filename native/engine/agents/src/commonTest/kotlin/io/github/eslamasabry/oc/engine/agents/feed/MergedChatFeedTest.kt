package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ports of test/merged_chat_feed_test.dart (plus saved-rows paint-first from phone_agents_cache.dart). */
@OptIn(ExperimentalCoroutinesApi::class)
class MergedChatFeedTest {
    private val first = FakeFeedSource()
    private val second = FakeFeedSource()

    private fun TestScope.merged(default: String? = null) = MergedChatFeed(
        listOf(NamedChatFeedSource("first", "Computer", first), NamedChatFeedSource("second", "Phone", second)),
        backgroundScope,
        defaultSourceId = default,
    )

    @Test
    fun `B-NAV-030 attention then running then recent rows sort across named sources`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("idle", time = 99), row("running", ChatStatus.Running, time = 2))
        second.rows(row("permission", ChatStatus.NeedsYou), row("failed", ChatStatus.Failed, time = 100))
        assertEquals(listOf("permission", "running", "failed", "idle"), merged.snapshot().items.map { it.sessionId })
        assertEquals("Phone", merged.snapshot().items.first().sourceLabel)
        assertEquals(merged.snapshot().items, merged.items.value)
    }

    @Test
    fun `B-NAV-030 equal session ids remain distinct and route only to their original source`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("same"))
        second.rows(row("same"))
        val rows = merged.snapshot().items
        assertEquals(2, rows.map { it.identity }.toSet().size)
        assertSame(first, merged.sourceFor(merged.routeFor(rows.first()).getOrNull()!!).getOrNull())
        assertSame(second, merged.sourceFor(merged.routeFor(rows.last()).getOrNull()!!).getOrNull())
        first.rows()
        assertEquals(Failure.Refused(RefusalReason.StaleScope), (merged.routeFor(rows.first()) as Outcome.Failed).failure)
        assertEquals(1, second.state.value.items.size)
    }

    @Test
    fun `B-NAV-030 agent identity changes invalidate stale navigation routes`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("one"))
        val route = merged.routeFor(merged.snapshot().items.single()).getOrNull()!!
        first.rows(row("one", agent = "claude"))
        assertIs<Outcome.Failed>(merged.sourceFor(route))
    }

    @Test
    fun `B-NAV-031 agent and project filters preserve additive attention chips`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("one", ChatStatus.NeedsYou), row("two", ChatStatus.Running, agent = "claude"))
        second.rows(row("three", ChatStatus.Running), row("four"))
        assertEquals(
            listOf("one", "three"),
            merged.snapshot(ChatFeedFilter(agentId = AgentId("codex"), needsYou = true, running = true)).items.map { it.sessionId },
        )
        assertTrue(merged.snapshot(ChatFeedFilter(projectDirectory = "/missing")).items.isEmpty())
    }

    @Test
    fun `B-NAV-031 partial and project scope flags survive per source and aggregate`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("one"), across = false)
        second.rows(complete = false, loading = true)
        val snapshot = merged.snapshot()
        assertFalse(snapshot.complete)
        assertFalse(snapshot.acrossProjects)
        assertFalse(snapshot.loading) // held rows remain usable
        assertEquals(listOf("Phone"), snapshot.incompleteSources)
        assertTrue(merged.sourceChecks().getValue("first").complete)
        assertTrue(merged.sourceChecks().getValue("second").loading)
    }

    @Test
    fun `B-NAV-031 a refresh never turns a filter with no matches into loading`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("idle"))
        val pending = CompletableDeferred<Unit>()
        second.onRefresh = { pending.await(); Unit.ok() }
        val running = ChatFeedFilter(running = true)
        val read = launch { merged.refresh() }
        assertTrue(merged.snapshot(running).items.isEmpty())
        assertFalse(merged.snapshot(running).loading)
        second.rows(loading = true)
        assertFalse(merged.snapshot(running).loading)
        pending.complete(Unit)
        read.join()
        first.rows(loading = true) // nothing known anywhere yet: that is still a first load
        assertTrue(merged.snapshot(running).loading)
    }

    @Test
    fun `B-AGENT-153 a coalesced refresh isolates a failed child and recovers on the next read`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        val pending = CompletableDeferred<Unit>()
        first.onRefresh = { pending.await(); Unit.ok() }
        second.onRefresh = { throw IllegalStateException("synthetic raw provider credential") }
        val one = launch { merged.refresh() }
        val two = launch { merged.refresh() }
        pending.complete(Unit)
        one.join()
        two.join()
        assertEquals(1, first.reads)
        assertEquals(1, second.reads)
        assertTrue(merged.sourceChecks().getValue("first").complete)
        assertFalse(merged.sourceChecks().getValue("second").complete)
        second.onRefresh = { Failure.Unknown().failed() }
        merged.refresh()
        assertFalse(merged.sourceChecks().getValue("second").complete) // a Failed outcome is a failure too
        second.onRefresh = { Unit.ok() }
        merged.refresh()
        assertTrue(merged.sourceChecks().getValue("second").complete)
    }

    @Test
    fun `B-AGENT-153 rows are kept after a failed refresh`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        first.rows(row("kept"))
        first.onRefresh = { Failure.Unknown().failed() }
        merged.refresh()
        assertEquals(listOf("kept"), merged.snapshot().items.map { it.sessionId })
        assertFalse(merged.snapshot().complete)
    }

    @Test
    fun `B-NAV-032 start needs an explicit source with several backends, one source keeps the old behaviour`() =
        runTest(UnconfinedTestDispatcher()) {
            val several = merged()
            assertIs<Outcome.Failed>(several.startSource())
            assertEquals("second", several.startSource("second").getOrNull()!!.id)
            assertEquals("first", merged(default = "first").startSource().getOrNull()!!.id)
            val one = MergedChatFeed(listOf(NamedChatFeedSource("first", "Computer", first)), backgroundScope)
            assertEquals("first", one.startSource().getOrNull()!!.id)
        }

    @Test
    fun `B-NAV-032 duplicate names and missing defaults are refused`() = runTest(UnconfinedTestDispatcher()) {
        assertFailsWith<IllegalArgumentException> {
            MergedChatFeed(listOf(NamedChatFeedSource("same", "A", first), NamedChatFeedSource("same", "B", second)), backgroundScope)
        }
        assertFailsWith<IllegalArgumentException> { MergedChatFeed(emptyList(), backgroundScope, defaultSourceId = "unknown") }
    }

    @Test
    fun `B-STATE-029 a disposed projection rejects late reads and never disposes a shared child`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        val pending = CompletableDeferred<Unit>()
        first.onRefresh = { pending.await(); Unit.ok() }
        val refreshing = launch { merged.refresh() }
        merged.dispose()
        first.rows(row("late"))
        pending.complete(Unit)
        refreshing.join()
        assertTrue(merged.snapshot().items.isEmpty())
        assertFalse(merged.snapshot().complete)
        assertIs<Outcome.Failed>(merged.routeFor(row("late").copy(sourceId = "first")))
        assertTrue(merged.projectSummaries().isEmpty())
        // The child is untouched and still usable by its other owners.
        assertEquals(listOf("late"), first.feed.value.items.map { it.sessionId })
    }

    @Test
    fun `B-STATE-029 child changes notify the owner until disposal`() = runTest(UnconfinedTestDispatcher()) {
        val merged = merged()
        val start = merged.revision.value
        first.rows(row("one"))
        assertEquals(start + 1, merged.revision.value)
        merged.dispose()
        second.rows(row("two"))
        assertEquals(start + 1, merged.revision.value)
        assertEquals(listOf("one"), merged.items.value.map { it.sessionId })
    }

    @Test
    fun `B-AGENT-P01 saved rows paint first, never claim running, and live rows replace them source by source`() =
        runTest(UnconfinedTestDispatcher()) {
            val saved = listOf(
                row("old-a", ChatStatus.Running).copy(sourceId = "paseo:/root/projects/a", finishedUnseen = true),
                row("old-b").copy(sourceId = "paseo:/root/projects/b"),
            )
            val a = FakeFeedSource(SourceFeed(loaded = false))
            val b = FakeFeedSource(SourceFeed(loaded = false))
            val merged = MergedChatFeed(
                listOf(
                    NamedChatFeedSource("paseo:/root/projects/a", "Claude Code", a, SavedChatFeed.rowsFor(saved, "paseo:/root/projects/a")),
                    NamedChatFeedSource("paseo:/root/projects/b", "Claude Code", b, SavedChatFeed.rowsFor(saved, "paseo:/root/projects/b")),
                ),
                backgroundScope,
            )
            val first = merged.items.value
            assertEquals(setOf("old-a", "old-b"), first.map { it.sessionId }.toSet())
            assertTrue(first.all { it.status == ChatStatus.Idle && !it.finishedUnseen })
            assertFalse(merged.snapshot().complete)
            assertTrue(merged.sourceChecks().getValue("paseo:/root/projects/a").showingSaved)

            a.state.value = SourceFeed(persistentListOf(row("live-a", ChatStatus.Running)), loaded = true)
            assertEquals(listOf("live-a", "old-b"), merged.items.value.map { it.sessionId })
            assertEquals(ChatStatus.Running, merged.items.value.first().status)
        }

    @Test
    fun `B-AGENT-P01 at most four Paseo folders are read`() {
        val wanted = PaseoFeedSources.wanted(listOf("/a", "/b", "/a", "/c", "/d", "/e"))
        assertEquals(listOf("/a", "/b", "/c", "/d"), wanted)
        assertEquals("paseo:/a", PaseoFeedSources.sourceId("/a"))
    }
}
