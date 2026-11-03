package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred

internal class MemoryPrefs(initial: Map<String, String> = emptyMap()) : PlansPrefs {
    val values = LinkedHashMap(initial)
    var refuse = false

    override fun getString(key: String): String? = values[key]

    override suspend fun setString(key: String, value: String): Boolean {
        if (refuse) return false
        values[key] = value
        return true
    }

    override suspend fun remove(key: String): Boolean {
        if (refuse) return false
        values.remove(key)
        return true
    }
}

/** A clock whose local time is UTC. */
internal class FakeClock(var now: Long) : PlansClock {
    override fun nowMs(): Long = now

    override fun minuteOfDay(epochMs: Long): Int = (((epochMs % DAY) + DAY) % DAY / MIN).toInt()
}

internal class FakeWidgetHost : UsageWidgetHost {
    var refreshes = 0
    override val supported: Boolean = true

    override suspend fun refreshNative() {
        refreshes++
    }
}

internal class FakeQuotaGateway(private val result: suspend () -> Outcome<ProviderQuotaSnapshot>) : ProviderQuotaGateway {
    var closed = 0

    override suspend fun readSnapshot(): Outcome<ProviderQuotaSnapshot> = result()

    override fun close() {
        closed++
    }
}

internal val REF_A = "a".repeat(64)

/** The Flutter providerQuotaFixture, already parsed. */
internal fun quotaSample(
    now: Long,
    account: String = "a",
    noReset: Boolean = false,
    fetched: Long = now,
    percent: Double = 100.0,
    provider: QuotaProvider = QuotaProvider.Codex,
    status: ProviderQuotaStatus = ProviderQuotaStatus.Ok,
    missingFirst: Boolean = false,
): ProviderQuotaSnapshot =
    ProviderQuotaSnapshot(
        provider = provider,
        status = status,
        freshness = QuotaFreshness.Fresh,
        fetchedAt = fetched,
        expiresAt = fetched + 60_000,
        account =
            ProviderQuotaAccount(
                ref = account.repeat(64),
                status = if (provider == QuotaProvider.Codex) QuotaAccountStatus.Matched else QuotaAccountStatus.SourceBound,
                plan = if (provider == QuotaProvider.Codex) "plus" else null,
            ),
        ordinaryUsageAllowed = if (provider == QuotaProvider.Codex) true else null,
        windows =
            listOf(
                if (missingFirst) {
                    ProviderQuotaWindow("primary", QuotaWindowStatus.Missing)
                } else {
                    ProviderQuotaWindow(
                        "primary",
                        QuotaWindowStatus.Reported,
                        usedPercent = percent,
                        durationSeconds = 18000,
                        resetsAt = if (noReset) null else fetched + 300_000,
                    )
                },
                ProviderQuotaWindow("secondary", QuotaWindowStatus.Missing),
            ),
    )

internal fun profile(id: String, stamp: String = "private-fixture-password") =
    QuotaProfile(id = id, name = "Synthetic", baseUrl = "https://$id.example", username = "fixture", credentialStamp = stamp)

/** The monitor rig of provider_quota_monitor_test.dart. */
internal class MonitorRig(start: Long) {
    val clock = FakeClock(start)
    val prefs = MemoryPrefs()
    val profiles = mutableListOf(profile("profile"))
    val deleted = mutableSetOf<String>()
    val reads = mutableListOf<String>()
    val alerts = mutableListOf<Map<String, String>>()
    val dismissed = mutableListOf<String>()
    val gateways = mutableListOf<FakeQuotaGateway>()
    var wifi: Boolean? = true
    var wifiThrows = false
    var alertFails = false
    var shown = true
    var shared: SharedNotifyRules? = null
    var pending: CompletableDeferred<Outcome<ProviderQuotaSnapshot>>? = null
    var response: ProviderQuotaSnapshot = quotaSample(start)
    private var counter = 0

    fun add(id: String) {
        profiles += profile(id)
    }

    fun rotate(stamp: String) {
        profiles[0] = profiles[0].copy(credentialStamp = stamp)
    }

    fun make(scope: kotlinx.coroutines.CoroutineScope) =
        ProviderQuotaMonitor(
            prefs = prefs,
            profiles = { profiles.toList() },
            isReadable = { id -> id !in deleted && profiles.any { it.id == id } },
            createGateway = { p, _ ->
                reads += p.id
                val waiting = pending
                FakeQuotaGateway { waiting?.await() ?: response.ok() }.also { gateways += it }
            },
            wifi = {
                if (wifiThrows) throw IllegalStateException("private-network-error")
                wifi
            },
            sink =
                object : QuotaAlertSink {
                    override suspend fun alert(profileId: String, key: String, token: String): Outcome<Boolean> {
                        if (alertFails) return Outcome.Failed(Failure.Device())
                        alerts += mapOf("profileID" to profileId, "key" to key, "token" to token)
                        return shown.ok()
                    }

                    override suspend fun dismiss(key: String) {
                        dismissed += key
                    }
                },
            sharedRules = { shared },
            clock = clock,
            secureRandom = { n -> ByteArray(n) { (++counter + it).toByte() } },
            scope = scope,
            foregroundIntervalMs = NEVER,
            backgroundIntervalMs = NEVER,
        )
}

/** Timers in tests never fire on their own: every read is one the test asked for. */
internal const val NEVER = 1_000L * DAY
