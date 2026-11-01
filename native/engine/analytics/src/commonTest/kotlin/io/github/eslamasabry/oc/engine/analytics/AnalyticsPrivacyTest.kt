package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactingLogSink
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Planted-secret rejection (plan 16 §5.10 `AnalyticsPrivacyTest`, B-SEC-191). */
class AnalyticsPrivacyTest {
    /** Canaries that must be recognised as content by the pattern layer, with the kind each should be. */
    private val contentCanaries: Map<String, LeakKind> =
        mapOf(
            // paths and file names
            "/data/data/io.github.app/files/notes.md" to LeakKind.Path,
            "C:\\Users\\me\\plan.txt" to LeakKind.Path,
            "~/projects/opencode" to LeakKind.Path,
            "quarterly-report.pdf" to LeakKind.Path,
            "src/main/App.kt" to LeakKind.Path,
            // links
            "https://my-box.example.net:4096/api/session" to LeakKind.Url,
            "http://opencode:hunter2@198.51.100.5:4096" to LeakKind.Url,
            "wss://relay.example.org/socket" to LeakKind.Url,
            "www.example.com" to LeakKind.Url,
            "content://media/external/images/1" to LeakKind.Url,
            // addresses and host names
            "192.0.2.20" to LeakKind.Address,
            "203.0.113.103" to LeakKind.Address,
            "2001:db8::1ff:fe23:4567:890a" to LeakKind.Address,
            "[2001:db8::7]:4096" to LeakKind.Address,
            "localhost:4096" to LeakKind.Address,
            "my-server.local" to LeakKind.Address,
            "build.example.com" to LeakKind.Address,
            // emails (built from parts so the repository scan for addresses stays clean)
            listOf("someone", "example.org").joinToString("@") to LeakKind.Email,
            // credentials and tokens
            "sk-ant-api03-AbCdEf1234567890" to LeakKind.Credential,
            "sk_live_51HxYzAbCdEf" to LeakKind.Credential,
            "ghp_ABCDEF1234567890abcdef12" to LeakKind.Credential,
            "github_pat_11ABCDEFG0123" to LeakKind.Credential,
            "AKIAIOSFODNN7EXAMPLE" to LeakKind.Credential,
            "AIzaSyD-abcdefghij1234567" to LeakKind.Credential,
            "eyJhbGciOiJIUzI1NiJ9" to LeakKind.Credential,
            "xoxb-1234-5678-abcd" to LeakKind.Credential,
            "Bearer abc.def" to LeakKind.Credential,
            "password" to LeakKind.Credential,
            "api_key" to LeakKind.Credential,
            "-----BEGIN PRIVATE KEY-----" to LeakKind.Credential,
            "Q2xhdWRlUm9ja3NIZXJlMTIzNDU2Nzg5MA" to LeakKind.Credential,
            // ids
            "550e8400-e29b-41d4-a716-446655440000" to LeakKind.Identifier,
            "da39a3ee5e6b4b0d3255bfef95601890afd80709" to LeakKind.Identifier,
            "15551234567" to LeakKind.Identifier,
            // free text
            "please fix the login bug" to LeakKind.FreeText,
            "line one\nline two" to LeakKind.ControlCharacter,
            "قائمة المهام" to LeakKind.FreeText,
            "x".repeat(200) to LeakKind.TooLong,
        )

    /** Plain words: harmless to the pattern layer, so only the schema allowlist can stop them. */
    private val wordCanaries = listOf("CanaryWord", "gptfivex", "zebracanary", "Projectx", "SonnetQ")

    private val allCanaries get() = contentCanaries.keys + wordCanaries

    @Test
    fun `B-SEC-191 the leak detector names each planted kind of content`() {
        contentCanaries.forEach { (canary, kind) -> assertEquals(kind, LeakDetector.detect(canary), "canary <$canary>") }
    }

    @Test
    fun `B-SEC-191 a planted value in any wire field is rejected`() {
        allCanaries.forEach { canary ->
            val placements =
                mapOf(
                    "prop value" to wire(props = mapOf("kind" to canary, "duration" to "Under1S")),
                    "prop key" to wire(props = mapOf("kind" to "Cold", "duration" to "Under1S", canary to "Cold")),
                    "event name" to wire(name = canary),
                    "install id" to wire(install = canary),
                    "session id" to wire(session = canary),
                    "app version" to wire(system = TestSystem.copy(appVersion = canary)),
                    "language" to wire(system = TestSystem.copy(lang = canary)),
                )
            placements.forEach { (where, event) ->
                assertIs<ScrubVerdict.Rejected>(PrivacyScrubber.check(event), "canary <$canary> in $where was accepted")
            }
        }
    }

    @Test
    fun `B-SEC-191 a rejection never carries the rejected text`() {
        allCanaries.forEach { canary ->
            val verdict = PrivacyScrubber.check(wire(props = mapOf("kind" to canary, "duration" to "Under1S")))
            assertIs<ScrubVerdict.Rejected>(verdict)
            assertFalse(canary in verdict.toString(), "rejection echoed the canary")
        }
    }

    @Test
    fun `B-SEC-191 the scrubber rejects schema drift - unknown events, missing, extra and out-of-range values`() {
        assertEquals(ScrubVerdict.Clean, PrivacyScrubber.check(wire()))
        assertIs<ScrubVerdict.Rejected>(PrivacyScrubber.check(wire(name = "unknown_event")))
        assertIs<ScrubVerdict.Rejected>(PrivacyScrubber.check(wire(props = mapOf("kind" to "Cold"))))
        assertIs<ScrubVerdict.Rejected>(PrivacyScrubber.check(wire(props = mapOf("kind" to "Hot", "duration" to "Under1S"))))
        assertIs<ScrubVerdict.Rejected>(PrivacyScrubber.check(wire(hourUtc = 1_793_527_200_001L)))
        val version = { v: String -> wire(name = "app_updated", props = mapOf("from_code" to v)) }
        assertEquals(ScrubVerdict.Clean, PrivacyScrubber.check(version("57")))
        listOf("-1", "057", "99999999999", "1e9", "57 ").forEach {
            assertIs<ScrubVerdict.Rejected>(PrivacyScrubber.check(version(it)), "version <$it>")
        }
    }

    @Test
    fun `B-SEC-191 a planted app version keeps every event out of the queue`() =
        runTest {
            val store = InMemoryAnalyticsStore()
            val sink = JsonDebugSink()
            val canary = "3.0.0 /home/me/secret"
            val engine = engine(store, sink, TestSystem.copy(appVersion = canary))
            engine.start()
            engine.openGate()
            EventSamples.all.forEach(engine::track)
            engine.setDetailedConsent(true)
            EventSamples.all.forEach(engine::track)
            engine.uploadNow()
            testScheduler.runCurrent()
            assertEquals(0, engine.queued.value)
            assertTrue(engine.dropped.value.rejected > 0)
            assertFalse(canary in store.text.orEmpty())
            assertTrue(sink.bodies.value.isEmpty())
        }

    @Test
    fun `B-SEC-191 canaries reaching emitters never reach the queue, the sent log, the debug output, storage or logs`() =
        runTest {
            val logs = mutableListOf<LogEntry>()
            AppLog.install(
                object : RedactingLogSink {
                    override fun write(entry: LogEntry) {
                        logs += entry
                    }
                },
                Severity.Debug,
            )
            val store = InMemoryAnalyticsStore()
            val remote = RecordingRemoteSink()
            val engine = engine(store, remote, TestSystem)
            engine.start()
            engine.openGate()
            engine.setDetailedConsent(true)
            engine.updateConditions(Online)
            // Emitters only hold canaries in failures and exceptions: the typed API cannot take them any further.
            allCanaries.forEach { canary ->
                val failure = Failure.Unexpected(Redacted.shape(IllegalStateException(canary)))
                engine.track(ErrorSeen(ErrorCategory.of(failure)))
            }
            EventSamples.all.forEach(engine::track)
            testScheduler.runCurrent()
            val queuedText = store.text
            assertNotNull(queuedText)
            engine.uploadNow()
            testScheduler.runCurrent()

            val everything =
                listOf(queuedText, store.text.orEmpty()) +
                    engine.sent.value.map { it.json } +
                    remote.batches.map { WireCodec.encode(it) } +
                    logs.map { it.toString() }
            assertTrue(remote.batches.isNotEmpty())
            allCanaries.forEach { canary ->
                everything.forEach { text -> assertFalse(canary in text, "canary <$canary> leaked") }
            }
        }

    @Test
    fun `B-SEC-191 the session id is never written to storage`() =
        runTest {
            val store = InMemoryAnalyticsStore()
            val ids = CountingIds()
            val engine = engine(store, JsonDebugSink(), TestSystem, ids)
            engine.start()
            engine.openGate()
            engine.track(AppStart(StartKind.Cold, DurationBucket.Under1S))
            engine.track(ErrorSeen(ErrorCategory.Network))
            testScheduler.runCurrent()
            val text = store.text.orEmpty()
            assertEquals(2, ids.issued.size, "one install id, one session id")
            assertTrue(ids.issued[0] in text, "the install id is stored")
            assertFalse(ids.issued[1] in text, "the session id was stored")
        }

    @Test
    fun `B-SEC-191 a broken sink never surfaces and its exception text is not logged`() =
        runTest {
            val logs = mutableListOf<LogEntry>()
            AppLog.install(
                object : RedactingLogSink {
                    override fun write(entry: LogEntry) {
                        logs += entry
                    }
                },
                Severity.Debug,
            )
            val engine = engine(InMemoryAnalyticsStore(), ThrowingSink(), TestSystem)
            engine.start()
            engine.openGate()
            engine.updateConditions(Online)
            engine.track(ErrorSeen(ErrorCategory.Server))
            val report = engine.uploadNow()
            testScheduler.runCurrent()
            assertIs<io.github.eslamasabry.oc.core.common.Outcome.Ok<UploadReport>>(report)
            assertEquals(HoldReason.BackingOff, report.value.held)
            assertEquals(1, engine.queued.value)
            assertTrue(logs.none { "boom with secret text" in it.toString() })
        }

    private fun TestScope.engine(
        store: AnalyticsStore,
        sink: AnalyticsSink,
        system: SystemProps,
        ids: IdSource = CountingIds(),
    ): AnalyticsEngine {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AnalyticsEngine(backgroundScope, TestDispatchers(dispatcher), FakeClock(), store, sink, system, ids)
    }
}
