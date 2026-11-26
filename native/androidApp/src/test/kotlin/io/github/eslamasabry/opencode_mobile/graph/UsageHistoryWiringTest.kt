package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.integrations.quota.ProviderQuotaSnapshot
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaProvider
import io.github.eslamasabry.oc.engine.integrations.usage.UsageHistory
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSample
import io.github.eslamasabry.oc.engine.integrations.usage.WindowKey
import io.github.eslamasabry.oc.engine.integrations.usage.forWindow
import io.github.eslamasabry.oc.engine.integrations.usage.usageSubscriptionKey
import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

private class MemoryUsageKv : KeyValueStore {
    val entries = LinkedHashMap<String, PrefValue>()

    override fun get(key: String): PrefValue? = entries[key]

    override fun keys(prefix: String): Set<String> = entries.keys.filter { it.startsWith(prefix) }.toSet()

    override val changes: SharedFlow<Set<String>> = MutableSharedFlow()

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        val staged = LinkedHashMap<String, PrefValue?>()
        object : PrefEdit {
            override fun putString(key: String, value: String) {
                staged[key] = PrefValue.Str(value)
            }

            override fun putBool(key: String, value: Boolean) {
                staged[key] = PrefValue.Bool(value)
            }

            override fun putLong(key: String, value: Long) {
                staged[key] = PrefValue.Int64(value)
            }

            override fun putStringList(key: String, value: List<String>) {
                staged[key] = PrefValue.StrList(value)
            }

            override fun putDouble(key: String, value: Double) {
                staged[key] = PrefValue.Dbl(value)
            }

            override fun remove(key: String) {
                staged[key] = null
            }
        }.edit()
        for ((k, v) in staged) if (v == null) entries.remove(k) else entries[k] = v
        return Outcome.Ok(Unit)
    }

    override suspend fun compareAndSet(
        lane: WriterLane,
        key: String,
        expected: String?,
        next: String?,
    ): Outcome<Boolean> = Outcome.Failed(Failure.StorageRefused())
}

private object EmptySecrets : SecretStore {
    override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(null)

    override suspend fun write(name: String, value: String): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun delete(name: String): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(emptySet())
}

class UsageHistoryWiringTest {
    private val accountRef = "a".repeat(64)
    private val baseTime = Instant.parse("2026-11-26T09:00:00Z")

    private fun codexSnapshot(
        fetchedAt: Instant,
        usedPercent: Double,
        resetsAt: Instant,
    ): ProviderQuotaSnapshot {
        val fetchedMs = fetchedAt.toEpochMilliseconds()
        val expiresMs = (fetchedAt + 30.minutes).toEpochMilliseconds()
        val resetMs = resetsAt.toEpochMilliseconds()
        val raw =
            """
            {
              "schemaVersion": 1,
              "provider": "codex",
              "source": "codex.wham",
              "status": "ok",
              "freshness": "fresh",
              "fetchedAtMs": $fetchedMs,
              "expiresAtMs": $expiresMs,
              "account": {"status": "matched", "ref": "$accountRef", "plan": "pro"},
              "ordinaryUsageAllowed": true,
              "windows": [
                {
                  "id": "five_hour",
                  "status": "reported",
                  "usedPercent": $usedPercent,
                  "durationSeconds": 18000,
                  "resetsAtMs": $resetMs
                }
              ]
            }
            """
                .trimIndent()
        return assertNotNull(ProviderQuotaSnapshot.fromJson(Json.parseToJsonElement(raw)))
    }

    @Test
    fun `B-STATE-149 quota alerts collector path converts snapshots and persists usage history`() = runTest {
        val kv = MemoryUsageKv()
        val lane = WriterLane("collector-test")
        val history = UsageHistory(kv, lane, clock = { baseTime })
        val r1 = baseTime + 5.hours
        val r2 = baseTime + 10.hours

        val firstReading =
            collectorSnapshotReading("p1", QuotaProvider.Codex, "Codex", codexSnapshot(baseTime, 25.0, r1))
        recordCollectorQuotaHistory(history, listOf(firstReading))

        val secondReading =
            collectorSnapshotReading(
                "p1",
                QuotaProvider.Codex,
                "Codex",
                codexSnapshot(baseTime + 20.minutes, 25.0, r1),
            )
        recordCollectorQuotaHistory(history, listOf(secondReading))

        val thirdReading =
            collectorSnapshotReading(
                "p1",
                QuotaProvider.Codex,
                "Codex",
                codexSnapshot(baseTime + 30.minutes, 60.0, r1),
            )
        recordCollectorQuotaHistory(history, listOf(thirdReading))

        val resetReading =
            collectorSnapshotReading(
                "p1",
                QuotaProvider.Codex,
                "Codex",
                codexSnapshot(baseTime + 5.hours + 1.minutes, 5.0, r2),
            )
        recordCollectorQuotaHistory(history, listOf(resetReading))

        val windowKey = WindowKey(usageSubscriptionKey("codex", accountRef, "p1"), "five_hour")
        val expected =
            listOf(
                UsageSample(baseTime + 30.minutes, 60.0, r1),
                UsageSample(baseTime + 5.hours + 1.minutes, 5.0, r2),
            )
        assertEquals(expected, history.forWindow(windowKey))
        assertNotNull(kv.string(UsageHistory.key("p1")))

        val restarted = UsageHistory(kv, lane, clock = { baseTime }, initialConnections = listOf("p1"))
        assertEquals(expected, restarted.forWindow(windowKey))
    }

    @Test
    fun `B-SEC-178 usage history has one canonical ProfileScope owner and sweeps on profile deletion`() = runTest {
        val kv = MemoryUsageKv()
        val lane = WriterLane("scope-test")
        val scope = DefaultProfileScope(kv, EmptySecrets, lane)
        registerStateKeys(scope)
        PlansLegacyCollectors.register(scope)

        val registered = scope.shapes().filter { it.prefix == UsageHistory.KEY_PREFIX }
        assertEquals(1, registered.size)
        assertEquals(":engine:integrations-usage", registered.single().ownerModule)

        val history = UsageHistory(kv, lane, clock = { baseTime })
        val r1 = baseTime + 5.hours
        val k1 = WindowKey(usageSubscriptionKey("codex", accountRef, "p1"), "five_hour")
        val k2 = WindowKey(usageSubscriptionKey("codex", accountRef, "p2"), "five_hour")
        history.recordSample("p1", k1, UsageSample(baseTime, 20.0, r1))
        history.recordSample("p2", k2, UsageSample(baseTime, 30.0, r1))
        assertNotNull(kv.string(UsageHistory.key("p1")))
        assertNotNull(kv.string(UsageHistory.key("p2")))

        assertTrue(scope.sweep(ProfileId("p1")) is Outcome.Ok)
        assertNull(kv.string(UsageHistory.key("p1")))
        assertNotNull(kv.string(UsageHistory.key("p2")))
    }

    @Test
    fun `B-STATE-149 QuotaCollectorWiring and PlansWiring hook all reading paths into UsageHistory`() {
        val collectorSource =
            RepoFiles.text(
                "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/QuotaCollectorWiring.kt"
            )
        assertTrue("recordCollectorQuotaHistory(history, readings)" in collectorSource)

        val plansWiring =
            RepoFiles.text(
                "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlansWiring.kt"
            )
        for (target in listOf("widget", "daemonUsage", "explicitMonitor?", "paseoSource", "collectorUsage")) {
            assertTrue("$target.attachHistory(created.historyStore)" in plansWiring, "missing $target history wiring")
        }
    }
}
