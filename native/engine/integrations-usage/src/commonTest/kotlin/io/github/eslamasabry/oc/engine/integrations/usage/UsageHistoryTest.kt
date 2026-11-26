package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private class HistoryScriptedSource(var next: UsageSourceResult = UsageSourceResult()) : UsageSource {
    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> = Outcome.Ok(next)
}

private class StaticPaseoPorts(
    private val profiles: List<PaseoUsageProfile>,
    var readings: List<ProviderUsageReading> = emptyList(),
) : PaseoUsagePorts {
    override fun paseoProfiles(): List<PaseoUsageProfile> = profiles

    override fun isReadable(profileId: String): Boolean = profiles.any { it.id == profileId }

    override suspend fun readServer(profileId: String): Outcome<List<ProviderUsageReading>> = Outcome.Ok(readings)

    override fun phoneSupported(): Boolean = true

    override suspend fun readPhone(): Outcome<List<ProviderUsageReading>?> = Outcome.Ok(readings)
}

class UsageHistoryTest {
    private val kv = FakeKv()
    private var now: Instant = Instant.parse("2026-11-26T08:00:00Z")
    private var connections = listOf(IN_APP, PC)
    private var quotaAlerts = false

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

    private fun sampleReading(
        provider: String = "codex",
        connection: String = "pc",
        account: String? = "acct-1",
        used: Double,
        resetsAt: Instant? = now + 4.hours,
    ): UsageReading =
        UsageReading(
            providerId = provider,
            displayName = provider,
            connectionId = connection,
            status = UsageReadingStatus.Ok,
            fetchedAt = now,
            windows = listOf(UsageWindow("5h", used, durationSeconds = 18_000, resetsAt = resetsAt)),
            accountRef = account,
            method = SignInMethod.Plan,
        )

    @Test
    fun `B-STATE-149 appends readings across refreshes and reloads across simulated restart`() = runTest {
        val reset = now + 5.hours
        val source = HistoryScriptedSource()
        val first = makeEngine(source)
        val flow: Flow<Map<WindowKey, List<UsageSample>>> = first.history
        assertTrue(flow.first().isEmpty())

        source.next = UsageSourceResult(readings = listOf(sampleReading(used = 12.0, resetsAt = reset)))
        first.refresh()

        now += 10.minutes
        source.next = UsageSourceResult(readings = listOf(sampleReading(used = 28.0, resetsAt = reset)))
        first.refresh(force = true)

        val key = WindowKey(usageSubscriptionKey("codex", "acct-1", "pc"), "5h")
        val beforeRestart = first.history.value[key]
        assertNotNull(beforeRestart)
        assertEquals(2, beforeRestart.size)
        assertEquals(12.0, beforeRestart[0].usedPercent)
        assertEquals(28.0, beforeRestart[1].usedPercent)
        assertEquals(reset, beforeRestart[1].resetsAt)

        val raw = assertNotNull(kv.str(UsageHistory.key("pc")))
        assertFalse(raw.contains("token"))

        val restarted = makeEngine(HistoryScriptedSource())
        assertEquals(beforeRestart, restarted.history.value[key])
    }

    @Test
    fun `B-STATE-149 dedupes matching samples and updates fetchedAt only after 15 minutes`() = runTest {
        val reset = now + 5.hours
        val history = UsageHistory(kv, LANE, clock = { now })
        val key = WindowKey("codex|acct-1", "5h")
        val t0 = now

        history.recordSample("pc", key, UsageSample(t0, 40.0, reset))
        val commitsAfterFirst = kv.commits

        // 5 minutes later, identical value and resetsAt: no append, no fetchedAt update, no write.
        history.recordSample("pc", key, UsageSample(t0 + 5.minutes, 40.0, reset))
        assertEquals(listOf(UsageSample(t0, 40.0, reset)), history.forWindow(key))
        assertEquals(commitsAfterFirst, kv.commits)

        // Exactly 15 minutes later: still not strictly > 15 min, so no update.
        history.recordSample("pc", key, UsageSample(t0 + 15.minutes, 40.0, reset))
        assertEquals(listOf(UsageSample(t0, 40.0, reset)), history.forWindow(key))
        assertEquals(commitsAfterFirst, kv.commits)

        // 16 minutes later (> 15 min): do NOT append; update last sample's fetchedAt in place.
        val t16 = t0 + 16.minutes
        history.recordSample("pc", key, UsageSample(t16, 40.0, reset))
        assertEquals(listOf(UsageSample(t16, 40.0, reset)), history.forWindow(key))
        assertTrue(kv.commits > commitsAfterFirst)

        // Older out-of-order reading is ignored.
        history.recordSample("pc", key, UsageSample(t0 + 1.minutes, 99.0, reset))
        assertEquals(listOf(UsageSample(t16, 40.0, reset)), history.forWindow(key))
    }

    @Test
    fun `B-STATE-149 later resetsAt drops old window samples except old window last sample`() = runTest {
        val r1 = now + 5.hours
        val r2 = now + 10.hours
        val r3 = now + 15.hours
        val history = UsageHistory(kv, LANE, clock = { now })
        val key = WindowKey("claude|acct-1", "weekly")

        val s1 = UsageSample(now, 10.0, r1)
        val s2 = UsageSample(now + 1.hours, 35.0, r1)
        val s3 = UsageSample(now + 2.hours, 72.0, r1)
        history.recordSample("pc", key, s1)
        history.recordSample("pc", key, s2)
        history.recordSample("pc", key, s3)
        assertEquals(listOf(s1, s2, s3), history.forWindow(key))

        // First reset: keep s3 (old window's last sample) + append new sample.
        val s4 = UsageSample(now + 5.hours + 5.minutes, 4.0, r2)
        history.recordSample("pc", key, s4)
        assertEquals(listOf(s3, s4), history.forWindow(key))

        val s5 = UsageSample(now + 6.hours, 18.0, r2)
        history.recordSample("pc", key, s5)
        assertEquals(listOf(s3, s4, s5), history.forWindow(key))

        // Second reset: previous window's last sample becomes s5; s3 and s4 are dropped.
        val s6 = UsageSample(now + 10.hours + 5.minutes, 2.0, r3)
        history.recordSample("pc", key, s6)
        assertEquals(listOf(s5, s6), history.forWindow(key))

        val reloaded = UsageHistory(kv, LANE, clock = { now }, initialConnections = listOf("pc"))
        assertEquals(listOf(s5, s6), reloaded.forWindow(key))
    }

    @Test
    fun `B-STATE-149 caps each window at 300 newest samples across long-running refreshes`() = runTest {
        val reset = now + 7.hours
        val history = UsageHistory(kv, LANE, clock = { now })
        val key = WindowKey("codex|acct-1", "5h")
        val total = 350

        for (i in 0 until total) {
            val pct = (i % 100).toDouble() + (i / 100) * 0.1
            history.recordSample("pc", key, UsageSample(now + i.seconds, pct, reset))
        }

        val kept = history.forWindow(key)
        assertEquals(UsageHistory.MAX_SAMPLES_PER_WINDOW, kept.size)
        assertEquals(now + 50.seconds, kept.first().fetchedAt)
        assertEquals(now + (total - 1).seconds, kept.last().fetchedAt)

        val reloaded = UsageHistory(kv, LANE, clock = { now })
        reloaded.loadConnection("pc")
        assertEquals(kept, reloaded.forWindow(key))
    }

    @Test
    fun `B-STATE-149 enforces 90-day retention age cap on append and restart reload alongside count cap`() = runTest {
        assertEquals(90.days, UsageHistory.MAX_SAMPLE_AGE)
        val key = WindowKey("codex|acct-1", "weekly")
        val reset = now + 7.days
        val history = UsageHistory(kv, LANE, clock = { now })

        val tooOld = UsageSample(now, 10.0, reset)
        history.recordSample("pc", key, tooOld)

        // Advance 1 day and record a second sample (will be exactly 90 days old later).
        now += 1.days
        val boundary = UsageSample(now, 25.0, reset)
        history.recordSample("pc", key, boundary)

        // Advance another 90 days: tooOld is 91 days old (dropped), boundary is 90 days old (kept).
        now += 90.days
        val reloaded = UsageHistory(kv, LANE, clock = { now }, initialConnections = listOf("pc"))
        assertEquals(listOf(boundary), reloaded.forWindow(key))

        // Appending a fresh sample keeps boundary while it is 90 days old.
        val currentReset = now + 7.days
        val fresh = UsageSample(now, 40.0, currentReset)
        reloaded.recordSample("pc", key, fresh)
        assertEquals(listOf(boundary, fresh), reloaded.forWindow(key))

        // Advance 2 more days within the same window: boundary is 92 days old and pruned by age cap.
        now += 2.days
        val newer = UsageSample(now, 55.0, currentReset)
        reloaded.recordSample("pc", key, newer)
        assertEquals(listOf(fresh, newer), reloaded.forWindow(key))

        // Already-expired sample (> 90 days old) is never stored.
        val expiredKey = WindowKey("claude|acct-2", "5h")
        reloaded.recordSample("pc", expiredKey, UsageSample(now - 91.days, 15.0, now - 90.days))
        assertTrue(reloaded.forWindow(expiredKey).isEmpty())
    }

    @Test
    fun `B-STATE-149 records from Paseo monitor and widget feed paths`() = runTest {
        val reset = now + 5.hours
        val history = UsageHistory(kv, LANE, clock = { now })
        val widget = UsageWidgetFeed(kv, LANE, isAndroid = true, clock = { now }).apply { attachHistory(history) }
        val ports =
            StaticPaseoPorts(
                profiles = listOf(PaseoUsageProfile("paseo1", "PC")),
                readings =
                    listOf(
                        ProviderUsageReading(
                            providerId = "claude",
                            displayName = "Claude",
                            fetchedAt = now,
                            windows =
                                listOf(
                                    ProviderUsageWindowReading(
                                        18_000L,
                                        "5h",
                                        remainingPercent = 70,
                                        resetsAt = reset,
                                    )
                                ),
                            accountRef = "u@corp.io",
                        )
                    ),
            )
        val monitor = PaseoUsageMonitor(kv, LANE, ports, widget = null, clock = { now }, autoRead = true)
        monitor.attachHistory(history)
        monitor.refresh()

        val key = WindowKey(usageSubscriptionKey("claude", "u@corp.io", "paseo1"), "5h")
        assertEquals(listOf(UsageSample(now, 30.0, reset)), history.forWindow(key))

        // Widget feed publishSubscriptions also appends new samples.
        now += 10.minutes
        val snapshot =
            UsageSnapshot(
                subscriptions =
                    listOf(
                        UsageSubscription(
                            key = key.subscriptionKey,
                            providerId = "claude",
                            displayName = "Claude",
                            state = SubscriptionState.Ok,
                            windows = listOf(UsageWindow("5h", 45.0, durationSeconds = 18_000L, resetsAt = reset)),
                            fetchedAt = now,
                            readFrom = UsageConnection("paseo1", "PC", UsageConnectionKind.PaseoServer),
                        )
                    ),
                loaded = true,
            )
        widget.publishSubscriptions(snapshot, UsageWidgetSettings())
        assertEquals(2, history.forWindow(key).size)
        assertEquals(45.0, history.forWindow(key).last().usedPercent)
    }

    @Test
    fun `B-SEC-178 profile and provider deletion remove history and profile sweep finds the key`() = runTest {
        val reset = now + 5.hours
        val source =
            HistoryScriptedSource(
                UsageSourceResult(
                    readings =
                        listOf(
                            sampleReading(
                                provider = "codex",
                                connection = "pc",
                                account = "a",
                                used = 20.0,
                                resetsAt = reset,
                            ),
                            sampleReading(
                                provider = "glm",
                                connection = "pc",
                                account = "g",
                                used = 50.0,
                                resetsAt = reset,
                            ),
                            sampleReading(
                                provider = "codex",
                                connection = "inapp",
                                account = "b",
                                used = 30.0,
                                resetsAt = reset,
                            ),
                        )
                )
            )
        val engine = makeEngine(source)
        engine.refresh()

        assertTrue(UsageHistory.key("pc") in kv.scopedKeys("pc"))
        assertFalse(UsageHistory.key("pc") in kv.scopedKeys("inapp"))

        val pcCodex = WindowKey(usageSubscriptionKey("codex", "a", "pc"), "5h")
        val pcGlm = WindowKey(usageSubscriptionKey("glm", "g", "pc"), "5h")
        val inAppCodex = WindowKey(usageSubscriptionKey("codex", "b", "inapp"), "5h")
        assertEquals(1, engine.history.value[pcCodex]?.size)
        assertEquals(1, engine.history.value[pcGlm]?.size)
        assertEquals(1, engine.history.value[inAppCodex]?.size)

        assertTrue(engine.removeProvider("pc", "codex"))
        assertNull(engine.history.value[pcCodex])
        assertEquals(1, engine.history.value[pcGlm]?.size)

        connections = listOf(IN_APP)
        assertTrue(engine.removeProfile("pc"))
        assertNull(kv.str(UsageHistory.key("pc")))
        assertNull(engine.history.value[pcGlm])
        assertEquals(1, engine.history.value[inAppCodex]?.size)
    }

    @Test
    fun `B-SEC-178 removeProvider returns false and keeps history intact when storage refuses`() = runTest {
        val reset = now + 5.hours
        val source =
            HistoryScriptedSource(
                UsageSourceResult(
                    readings =
                        listOf(
                            sampleReading(
                                provider = "codex",
                                connection = "pc",
                                account = "a",
                                used = 20.0,
                                resetsAt = reset,
                            ),
                            sampleReading(
                                provider = "glm",
                                connection = "pc",
                                account = "g",
                                used = 50.0,
                                resetsAt = reset,
                            ),
                        )
                )
            )
        val engine = makeEngine(source)
        engine.refresh()
        val pcCodex = WindowKey(usageSubscriptionKey("codex", "a", "pc"), "5h")
        assertEquals(1, engine.history.value[pcCodex]?.size)

        kv.refuse = true
        assertFalse(engine.removeProvider("pc", "codex"))
        assertEquals(1, engine.history.value[pcCodex]?.size)
    }

    @Test
    fun `B-STATE-149 concurrent loadConnection and recordSample stay serialized without losing samples`() = runTest {
        val reset = now + 5.hours
        val key = WindowKey("codex|acct-1", "5h")
        val seed = UsageHistory(kv, LANE, clock = { now })
        seed.recordSample("pc", key, UsageSample(now, 10.0, reset))

        val fresh = UsageHistory(kv, LANE, clock = { now })
        coroutineScope {
            listOf(
                async { fresh.loadConnection("pc") },
                async { fresh.recordSample("pc", key, UsageSample(now + 1.minutes, 25.0, reset)) },
                async { fresh.loadConnection("pc") },
            ).awaitAll()
        }

        val samples = fresh.forWindow(key)
        assertEquals(2, samples.size)
        assertEquals(10.0, samples[0].usedPercent)
        assertEquals(25.0, samples[1].usedPercent)
    }

    @Test
    fun `B-STATE-149 existing oc usageLast without history and corrupt history degrade cleanly`() = runTest {
        // Seed only legacy oc.usageLast.pc (upgrade path: no oc.usageHistory.pc yet).
        val reset = now + 4.hours
        val seedSource =
            HistoryScriptedSource(
                UsageSourceResult(readings = listOf(sampleReading(used = 25.0, resetsAt = reset)))
            )
        val seeder = makeEngine(seedSource)
        seeder.refresh()
        kv.values.remove(UsageHistory.key("pc"))

        val upgraded = makeEngine(HistoryScriptedSource())
        assertEquals(1, upgraded.snapshot.value.subscriptions.size)
        assertTrue(upgraded.history.value.isEmpty())

        // Corrupt JSON, wrong version, and oversized blobs all degrade gracefully to empty.
        for (bad in listOf("{not-json", "{\"v\":99,\"w\":[]}", "[]", "x".repeat(600_000))) {
            kv.values[UsageHistory.key("pc")] = PrefValue.Str(bad)
            val engine = makeEngine(HistoryScriptedSource())
            assertTrue(engine.history.value.isEmpty())
            assertEquals(1, engine.snapshot.value.subscriptions.size)
        }

        // Subsequent valid refresh heals persisted history cleanly.
        now += 5.minutes
        val healingSource =
            HistoryScriptedSource(
                UsageSourceResult(readings = listOf(sampleReading(used = 35.0, resetsAt = reset)))
            )
        val healed = makeEngine(healingSource)
        healed.refresh()
        val key = WindowKey(usageSubscriptionKey("codex", "acct-1", "pc"), "5h")
        assertEquals(listOf(UsageSample(now, 35.0, reset)), healed.history.value[key])
    }
}
