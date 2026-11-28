package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

private const val HISTORY_RECORD_LIMIT: Int = 524_288
private const val PASEO_ID: String = "paseo1"

/** Lists the keys one commit writes and removes, without touching a store. */
private class EditProbe : PrefEdit {
    val puts = HashSet<String>()
    val removes = HashSet<String>()

    override fun putString(key: String, value: String) {
        puts += key
    }

    override fun putBool(key: String, value: Boolean) {
        puts += key
    }

    override fun putLong(key: String, value: Long) {
        puts += key
    }

    override fun putStringList(key: String, value: List<String>) {
        puts += key
    }

    override fun putDouble(key: String, value: Double) {
        puts += key
    }

    override fun remove(key: String) {
        removes += key
    }
}

/** A [FakeKv] that can hold the write of one key until released and refuse the removal of chosen keys. */
private class RaceKv(val inner: FakeKv = FakeKv()) : KeyValueStore by inner {
    var holdPutOf: String? = null
    val reached = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val refusedRemoves = HashSet<String>()

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val probe = EditProbe().apply(edit)
        val held = holdPutOf
        if (held != null && held in probe.puts) {
            reached.complete(Unit)
            release.await()
        }
        return if (probe.removes.any { it in refusedRemoves }) {
            Outcome.Failed(Failure.StorageRefused())
        } else {
            inner.commit(lane, edit)
        }
    }
}

private class FixedUsageSource(private val result: UsageSourceResult) : UsageSource {
    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> = Outcome.Ok(result)
}

/** One saved Paseo server; [asked] completes when the monitor has read it. */
private class OnePaseoServer(private val reading: ProviderUsageReading) : PaseoUsagePorts {
    val asked = CompletableDeferred<Unit>()

    override fun paseoProfiles(): List<PaseoUsageProfile> = listOf(PaseoUsageProfile(PASEO_ID, "PC"))

    override fun isReadable(profileId: String): Boolean = profileId == PASEO_ID

    override suspend fun readServer(profileId: String): Outcome<List<ProviderUsageReading>> {
        asked.complete(Unit)
        return Outcome.Ok(listOf(reading))
    }
}

class UsageHistoryCleanupTest {
    private val kv = RaceKv()
    private var now: Instant = Instant.parse("2026-11-28T08:00:00Z")
    private var connections = listOf(IN_APP, PC)
    private var quotaAlerts = false
    private val paseoHistoryKey = UsageHistory.key(PASEO_ID)

    private fun makeEngine(source: UsageSource): UsageSubscriptionsEngine =
        UsageSubscriptionsEngine(
            store = kv,
            lane = LANE,
            connections = { connections },
            sources = listOf(source),
            alertsOnRule = { quotaAlerts },
            setQuotaAlerts = { quotaAlerts = it },
            clock = { now },
        )

    private fun codexReading(connection: String, account: String, used: Double): UsageReading =
        UsageReading(
            providerId = "codex",
            displayName = "codex",
            connectionId = connection,
            status = UsageReadingStatus.Ok,
            fetchedAt = now,
            windows = listOf(UsageWindow("5h", used, durationSeconds = 18_000, resetsAt = now + 5.hours)),
            accountRef = account,
            method = SignInMethod.Plan,
        )

    private fun bothConnectionsSource(): UsageSource =
        FixedUsageSource(
            UsageSourceResult(
                readings = listOf(codexReading("inapp", "b", used = 30.0), codexReading("pc", "a", used = 20.0))
            )
        )

    private fun paseoReading(): ProviderUsageReading =
        ProviderUsageReading(
            providerId = "claude",
            displayName = "Claude",
            fetchedAt = now,
            windows =
                listOf(ProviderUsageWindowReading(18_000L, "5h", remainingPercent = 65, resetsAt = now + 5.hours)),
            accountRef = "user@corp.io",
        )

    private fun manyWindowsReading(round: Int, windows: Int, reset: Instant): UsageReading =
        UsageReading(
            providerId = "codex",
            displayName = "codex",
            connectionId = "pc",
            status = UsageReadingStatus.Ok,
            fetchedAt = now,
            windows =
                List(windows) { index ->
                    UsageWindow(
                        "w$index",
                        ((round * 7 + index) % 1000) / 10.0,
                        durationSeconds = 604_800,
                        resetsAt = reset,
                    )
                },
            method = SignInMethod.Plan,
        )

    @Test
    fun `B-STATE-149 a history full at 64 windows of 300 samples is read back after a restart`() = runTest {
        val windows = UsageHistory.MAX_WINDOWS_PER_CONNECTION
        val rounds = UsageHistory.MAX_SAMPLES_PER_WINDOW
        val reset = now + 7.days
        val history = UsageHistory(kv, LANE, clock = { now })
        repeat(rounds) { round ->
            now += 1.minutes
            history.recordReadings(listOf(manyWindowsReading(round, windows, reset)))
        }
        val inMemory = history.samples.value
        assertEquals(windows, inMemory.size)
        assertTrue(inMemory.values.all { it.size == rounds })

        val stored = assertNotNull(kv.inner.str(UsageHistory.key("pc")))
        assertTrue(stored.length <= HISTORY_RECORD_LIMIT, "the stored record is ${stored.length} characters")

        val restarted = UsageHistory(kv, LANE, clock = { now }, initialConnections = listOf("pc"))
        val reloaded = restarted.samples.value
        assertEquals(inMemory.keys, reloaded.keys)
        for ((key, samples) in reloaded) {
            assertTrue(samples.size >= 200, "window ${key.windowId} kept ${samples.size} samples")
            assertEquals(inMemory.getValue(key).takeLast(samples.size), samples)
        }
    }

    @Test
    fun `B-SEC-178 the widget feed writes no usage history for the Paseo rows it stores`() = runTest {
        val history = UsageHistory(kv, LANE, clock = { now })
        val widget = UsageWidgetFeed(kv, LANE, isAndroid = true, clock = { now }).apply { attachHistory(history) }

        widget.recordReadings(PASEO_ID, "PC", listOf(paseoReading()))

        assertNotNull(kv.inner.str(UsageWidgetFeed.key(PASEO_ID)))
        assertNull(kv.inner.str(paseoHistoryKey))
        assertTrue(history.samples.value.isEmpty())
    }

    @Test
    fun `B-SEC-178 a refused history delete after consent is withdrawn mid-write is not reloaded and is retried`() =
        runTest {
            kv.inner.values[PaseoUsageMonitor.key(PASEO_ID)] = PrefValue.Bool(true)
            val history = UsageHistory(kv, LANE, clock = { now })
            val widget = UsageWidgetFeed(kv, LANE, isAndroid = true, clock = { now }).apply { attachHistory(history) }
            val monitor = PaseoUsageMonitor(kv, LANE, OnePaseoServer(paseoReading()), widget, clock = { now })
            monitor.attachHistory(history)
            kv.holdPutOf = paseoHistoryKey
            kv.refusedRemoves += paseoHistoryKey

            val read = async { monitor.refresh() }
            kv.reached.await()
            assertTrue(monitor.setEnabled(PASEO_ID, false))
            kv.release.complete(Unit)
            read.await()

            // The store refused the delete: nothing of the withdrawn reader is shown or loaded again.
            assertTrue(history.samples.value.isEmpty())
            history.loadConnection(PASEO_ID)
            assertTrue(history.samples.value.isEmpty())

            // Once the store accepts removals again, the next use of the history finishes the delete.
            kv.refusedRemoves.clear()
            history.loadConnection(PASEO_ID)
            assertNull(kv.inner.str(paseoHistoryKey))
            assertTrue(history.samples.value.isEmpty())
        }

    @Test
    fun `B-SEC-178 a refused history delete after a profile is removed mid-write is retried by the next write`() =
        runTest {
            val history = UsageHistory(kv, LANE, clock = { now })
            val ports = OnePaseoServer(paseoReading())
            val monitor = PaseoUsageMonitor(kv, LANE, ports, widget = null, clock = { now }, autoRead = true)
            monitor.attachHistory(history)
            kv.holdPutOf = paseoHistoryKey
            kv.refusedRemoves += paseoHistoryKey

            val read = async { monitor.refresh() }
            kv.reached.await()
            monitor.removeProfile(PASEO_ID)
            kv.release.complete(Unit)
            read.await()
            assertTrue(history.samples.value.isEmpty())

            kv.refusedRemoves.clear()
            val pcKey = WindowKey("codex|conn:pc", "5h")
            val pcSample = UsageSample(now, 40.0, now + 5.hours)
            history.recordSample("pc", pcKey, pcSample)
            assertNull(kv.inner.str(paseoHistoryKey))

            val restarted = UsageHistory(kv, LANE, clock = { now }, initialConnections = listOf(PASEO_ID, "pc"))
            assertEquals(mapOf(pcKey to listOf(pcSample)), restarted.samples.value)
        }

    @Test
    fun `B-SEC-178 a Paseo read still waiting to write when its profile is removed writes no history`() = runTest {
        val history = UsageHistory(kv, LANE, clock = { now })
        val ports = OnePaseoServer(paseoReading())
        val monitor = PaseoUsageMonitor(kv, LANE, ports, widget = null, clock = { now }, autoRead = true)
        monitor.attachHistory(history)
        kv.holdPutOf = UsageHistory.key("pc")
        kv.refusedRemoves += paseoHistoryKey

        // Another write holds the history while the monitor's reading waits behind it.
        val other = async { history.recordSample("pc", WindowKey("codex|conn:pc", "5h"), 40.0, now + 5.hours) }
        kv.reached.await()
        val read = async { monitor.refresh() }
        ports.asked.await()
        monitor.removeProfile(PASEO_ID)
        kv.release.complete(Unit)
        other.await()
        read.await()

        // Nothing was written, so a store that refuses the delete leaves nothing behind.
        assertNull(kv.inner.str(paseoHistoryKey))
        assertEquals(setOf(WindowKey("codex|conn:pc", "5h")), history.samples.value.keys)
    }

    private suspend fun CoroutineScope.assertNoHistoryForPcAfter(
        removal: suspend (UsageSubscriptionsEngine) -> Boolean,
    ) {
        val pcKey = WindowKey(usageSubscriptionKey("codex", "a", "pc"), "5h")
        val inAppKey = WindowKey(usageSubscriptionKey("codex", "b", "inapp"), "5h")
        val engine = makeEngine(bothConnectionsSource())
        kv.holdPutOf = UsageSubscriptionsEngine.lastKey("inapp")

        // The refresh has merged both readings and is saving them when the removal arrives.
        val refresh = async { engine.refresh() }
        kv.reached.await()
        assertTrue(removal(engine))
        kv.release.complete(Unit)
        refresh.await()

        assertNull(kv.inner.str(UsageHistory.key("pc")))
        assertTrue(engine.historyStore.forWindow(pcKey).isEmpty())
        assertEquals(1, engine.historyStore.forWindow(inAppKey).size)
    }

    @Test
    fun `B-SEC-178 a refresh still saving when a profile is deleted writes no history for it`() = runTest {
        assertNoHistoryForPcAfter { engine ->
            connections = listOf(IN_APP)
            engine.removeProfile("pc")
        }
    }

    @Test
    fun `B-SEC-178 a refresh still saving when consent is withdrawn writes no history for that reader`() = runTest {
        assertNoHistoryForPcAfter { engine -> engine.removeProvider("pc", "codex") }
    }

    @Test
    fun `B-SEC-178 a refused cache delete on consent withdrawal still removes the usage history`() = runTest {
        val pcKey = WindowKey(usageSubscriptionKey("codex", "a", "pc"), "5h")
        val engine = makeEngine(bothConnectionsSource())
        engine.refresh()
        assertEquals(1, engine.historyStore.forWindow(pcKey).size)
        assertNotNull(kv.inner.str(UsageHistory.key("pc")))

        kv.refusedRemoves += UsageSubscriptionsEngine.lastKey("pc")
        assertFalse(engine.removeProfile("pc", deleted = false))

        assertNull(kv.inner.str(UsageHistory.key("pc")))
        assertTrue(engine.historyStore.forWindow(pcKey).isEmpty())
    }
}
