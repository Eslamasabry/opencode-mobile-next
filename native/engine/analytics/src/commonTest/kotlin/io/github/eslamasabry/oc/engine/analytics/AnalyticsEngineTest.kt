package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnalyticsEngineTest {
    private class Rig(val engine: AnalyticsEngine, val clock: FakeClock, val store: InMemoryAnalyticsStore, val scope: TestScope) {
        fun settle() = scope.testScheduler.runCurrent()

        fun names(sink: RecordingRemoteSink) = sink.batches.flatMap { b -> b.events.map { it.name } }
    }

    private fun TestScope.rig(
        sink: AnalyticsSink = RecordingRemoteSink(),
        store: InMemoryAnalyticsStore = InMemoryAnalyticsStore(),
        clock: FakeClock = FakeClock(),
        ids: IdSource = CountingIds(),
        config: AnalyticsConfig = AnalyticsConfig(),
    ): Rig {
        val engine =
            AnalyticsEngine(
                backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), clock, store, sink, TestSystem, ids, config,
            )
        engine.start()
        return Rig(engine, clock, store, this)
    }

    private val basic = AppStart(StartKind.Cold, DurationBucket.Under1S)
    private val detailed = ScreenOpened(ScreenId.Chat)

    @Test
    fun `B-ANLY-003 nothing is tracked before the migration gate opens`() =
        runTest {
            val r = rig()
            r.engine.track(basic)
            r.engine.count(UsageCounter.AppOpen)
            r.settle()
            assertEquals(0, r.engine.queued.value)
            assertEquals(2, r.engine.dropped.value.beforeGate)
            r.engine.openGate()
            r.engine.track(basic)
            r.settle()
            assertEquals(1, r.engine.queued.value)
        }

    @Test
    fun `B-ANLY-001 BASIC is recorded without any consent and DETAILED only after opting in`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            r.engine.track(basic)
            r.engine.track(detailed)
            r.settle()
            assertEquals(1, r.engine.queued.value)
            assertEquals(1, r.engine.dropped.value.notConsented)
            assertEquals(DetailedConsent.NotAsked, r.engine.consent.value.detailed)

            r.engine.setDetailedConsent(true)
            r.engine.track(detailed)
            r.engine.uploadNow()
            assertEquals(listOf("app_start", "screen_open"), r.names(sink))
        }

    @Test
    fun `B-ANLY-002 declining DETAILED drops queued DETAILED events at once and sends no more of them`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.setDetailedConsent(true)
            r.engine.track(basic)
            r.engine.track(detailed)
            r.engine.track(FeatureUsed(UsedFeature.Voice))
            r.settle()
            assertEquals(3, r.engine.queued.value)
            r.engine.setDetailedConsent(false)
            assertEquals(1, r.engine.queued.value)
            assertEquals(DetailedConsent.Declined, r.engine.consent.value.detailed)
            r.engine.track(detailed)
            r.engine.updateConditions(Online)
            r.engine.uploadNow()
            assertEquals(listOf("app_start"), r.names(sink))
            assertFalse("screen_open" in r.store.text.orEmpty())
        }

    @Test
    fun `B-ANLY-007 uploads go in batches of 25, failures keep the batch and back off`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            repeat(60) { r.engine.track(ErrorSeen(ErrorCategory.Network)) }
            r.settle()
            sink.fail = true
            val failed = r.engine.uploadNow()
            assertIs<Outcome.Ok<UploadReport>>(failed)
            assertEquals(HoldReason.BackingOff, failed.value.held)
            assertEquals(60, r.engine.queued.value)
            assertEquals(1, sink.calls)

            // Still backing off 59 s later: no call.
            r.clock.advance(59_000)
            r.engine.uploadNow()
            assertEquals(1, sink.calls)

            r.clock.advance(1_000)
            sink.fail = false
            val ok = r.engine.uploadNow()
            assertIs<Outcome.Ok<UploadReport>>(ok)
            assertEquals(listOf(25, 25, 10), sink.batches.map { it.events.size })
            assertEquals(0, r.engine.queued.value)
            assertEquals(3, r.engine.sent.value.size)
        }

    @Test
    fun `B-ANLY-008 moving to the background uploads, but never over mobile data`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.updateConditions(Online.copy(network = NetworkKind.Metered))
            r.engine.track(basic)
            r.settle()
            r.engine.updateConditions(Online.copy(network = NetworkKind.Metered, foreground = false))
            r.settle()
            assertEquals(0, sink.calls)
            assertEquals(HoldReason.MobileDataInBackground, r.engine.lastHold.value)

            r.engine.updateConditions(Online)
            r.engine.updateConditions(Online.copy(foreground = false))
            r.settle()
            assertEquals(1, sink.calls)
        }

    @Test
    fun `B-ANLY-008 the foreground timer uploads every thirty minutes`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            r.engine.track(basic)
            r.settle()
            testScheduler.advanceTimeBy(30 * 60_000L - 1)
            r.settle()
            assertEquals(0, sink.calls)
            testScheduler.advanceTimeBy(2)
            r.settle()
            assertEquals(1, sink.calls)
        }

    @Test
    fun `B-ANLY-010 the no-op sink is never called, keeps nothing on disk and records nothing as sent`() =
        runTest {
            val r = rig(NoOpSink)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            r.engine.track(basic)
            r.settle()
            val report = r.engine.uploadNow()
            assertIs<Outcome.Ok<UploadReport>>(report)
            assertEquals(HoldReason.NoDestination, report.value.held)
            assertTrue(r.engine.sent.value.isEmpty())
            assertFalse("app_start" in r.store.text.orEmpty(), "the none build wrote the queue to disk")
            assertEquals(SendingState.NotSentInThisVersion, r.engine.whatWeCollect.value.sending)
        }

    @Test
    fun `B-ANLY-010 the JSON debug sink writes exactly the wire body and nothing counts as sent`() =
        runTest {
            val written = mutableListOf<String>()
            val sink = JsonDebugSink(write = { written += it })
            val r = rig(sink)
            r.engine.openGate()
            r.engine.track(basic)
            r.engine.uploadNow()
            assertEquals(1, written.size)
            val body = written.single()
            val decoded = WireCodec.json.decodeFromString(WireBatch.serializer(), body)
            assertEquals("app_start", decoded.events.single().name)
            assertEquals(mapOf("kind" to "Cold", "duration" to "Under1S"), decoded.events.single().props)
            assertEquals(TestSystem, decoded.events.single().system)
            assertEquals(0L, decoded.events.single().hourUtc % HOUR_MS)
            assertEquals(listOf(body), sink.bodies.value)
            assertTrue(r.engine.sent.value.isEmpty())
        }

    @Test
    fun `B-ANLY-010 the sent log keeps the last fifty bodies newest first, exactly as sent`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink, config = AnalyticsConfig(maxBatchesPerRun = 1))
            r.engine.openGate()
            r.engine.updateConditions(Online)
            repeat(55) {
                r.engine.track(ErrorSeen(ErrorCategory.entries[it % ErrorCategory.entries.size]))
                r.engine.uploadNow()
            }
            assertEquals(50, r.engine.sent.value.size)
            assertEquals(WireCodec.encode(sink.batches.last()), r.engine.sent.value.first().json)
            assertEquals(SinkId.Custom, r.engine.sent.value.first().sinkId)
        }

    @Test
    fun `B-ANLY-012 daily counts roll into one BASIC summary per UTC day, as buckets`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            repeat(3) { r.engine.count(UsageCounter.AppOpen) }
            r.engine.count(UsageCounter.ChatStarted)
            r.engine.noteInventory(mapOf(ServerKind.OpenCode2 to 2, ServerKind.Codex to 30), mapOf(AgentKind.ClaudeCode to 1))
            r.settle()
            assertEquals(0, r.engine.queued.value)

            r.clock.advance(DAY_MS)
            r.engine.count(UsageCounter.AppOpen)
            r.engine.uploadNow()
            val events = sink.batches.flatMap { it.events }
            assertEquals(listOf("daily_use", "server_kinds", "agent_kinds"), events.map { it.name })
            assertEquals(mapOf("opens" to "TwoToFive", "chats_started" to "One", "messages_sent" to "Zero"), events[0].props)
            assertEquals(mapOf("opencode2" to "TwoToFive", "paseo" to "Zero", "codex" to "MoreThanTwenty"), events[1].props)
            assertEquals("One", events[2].props["claude_code"])
        }

    @Test
    fun `B-ANLY-012 backend_connected is sent once per server kind per UTC day`() =
        runTest {
            val sink = RecordingRemoteSink()
            val r = rig(sink)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            repeat(3) { r.engine.track(BackendConnected(ServerKind.OpenCode2, AddressClass.Tailnet)) }
            r.engine.track(BackendConnected(ServerKind.Paseo, AddressClass.LocalNetwork))
            r.settle()
            assertEquals(2, r.engine.queued.value)
            r.clock.advance(DAY_MS)
            r.engine.track(BackendConnected(ServerKind.OpenCode2, AddressClass.Tailnet))
            r.engine.uploadNow()
            assertEquals(3, sink.batches.flatMap { it.events }.count { it.name == "backend_connected" })
        }

    @Test
    fun `B-ANLY-013 track never throws or blocks, and an overfull inbox drops instead of growing`() =
        runTest {
            val r = rig(config = AnalyticsConfig(inboxLimit = 10))
            r.engine.openGate()
            repeat(25) { r.engine.track(basic) }
            assertEquals(15, r.engine.dropped.value.overflow)
            r.settle()
            assertEquals(10, r.engine.queued.value)
        }

    @Test
    fun `B-ANLY-014 the queue, consent and install id survive a restart, the session does not`() =
        runTest {
            val store = InMemoryAnalyticsStore()
            val ids = CountingIds()
            val first = rig(RecordingRemoteSink(), store, ids = ids)
            first.engine.openGate()
            first.engine.setDetailedConsent(true)
            first.engine.track(basic)
            first.engine.track(detailed)
            first.settle()
            val install = ids.issued[0]
            val firstSession = ids.issued[1]

            val sink = RecordingRemoteSink()
            val second = rig(sink, store, ids = ids)
            second.engine.openGate()
            second.engine.updateConditions(Online)
            second.engine.uploadNow()
            assertEquals(DetailedConsent.Granted, second.engine.consent.value.detailed)
            val sent = sink.batches.single().events
            assertEquals(listOf("app_start", "screen_open"), sent.map { it.name })
            assertTrue(sent.all { it.install == install })
            assertTrue(sent.none { it.session == firstSession })
        }

    @Test
    fun `B-ANLY-014 an unreadable or tampered store starts empty and never sends what it holds`() =
        runTest {
            val corrupt = InMemoryAnalyticsStore("{ not json")
            val r = rig(store = corrupt)
            r.engine.openGate()
            r.settle()
            assertEquals(0, r.engine.queued.value)

            val good = InMemoryAnalyticsStore()
            val writer = rig(RecordingRemoteSink(), good)
            writer.engine.openGate()
            writer.engine.track(ErrorSeen(ErrorCategory.Network))
            writer.settle()
            val tampered = InMemoryAnalyticsStore(good.text!!.replace("\"Network\"", "\"/home/me/notes.md\""))
            val sink = RecordingRemoteSink()
            val reader = rig(sink, tampered)
            reader.engine.openGate()
            reader.engine.updateConditions(Online)
            reader.engine.uploadNow()
            assertEquals(0, sink.calls)
            assertEquals(0, reader.engine.queued.value)
        }

    @Test
    fun `B-ANLY-014 delete everything clears the queue, the sent log, the consent and the install id`() =
        runTest {
            val ids = CountingIds()
            val sink = RecordingRemoteSink()
            val r = rig(sink, ids = ids)
            r.engine.openGate()
            r.engine.updateConditions(Online)
            r.engine.setDetailedConsent(true)
            r.engine.track(basic)
            r.engine.uploadNow()
            r.engine.track(basic)
            r.settle()
            val oldInstall = ids.issued[0]
            r.engine.deleteEverything()
            assertEquals(0, r.engine.queued.value)
            assertTrue(r.engine.sent.value.isEmpty())
            assertEquals(DetailedConsent.NotAsked, r.engine.consent.value.detailed)
            r.engine.track(basic)
            r.engine.uploadNow()
            assertTrue(sink.batches.last().events.single().install != oldInstall)
        }

    @Test
    fun `B-ANLY-015 errors are sent as categories, never text`() {
        val failures =
            listOf(
                Failure.Refused(RefusalReason.ConsentMissing),
                Failure.Folder(FolderProblem.Denied, null),
                Failure.Server(503, details = Redacted.shape(IllegalStateException("db password hunter2"))),
                Failure.Network(),
            )
        val categories = failures.map(ErrorCategory::of)
        assertEquals(listOf(ErrorCategory.Refused, ErrorCategory.Folder, ErrorCategory.Server, ErrorCategory.Network), categories)
        categories.forEach { assertEquals(ScrubVerdict.Clean, PrivacyScrubber.checkTyped(ErrorSeen(it))) }
    }
}
