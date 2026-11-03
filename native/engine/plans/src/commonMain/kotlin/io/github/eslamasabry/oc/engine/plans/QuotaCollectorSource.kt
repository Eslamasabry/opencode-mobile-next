package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** The proper name of a provider the quota readers know. */
public fun usageQuotaName(provider: QuotaProvider): String =
    when (provider) {
        QuotaProvider.Codex -> "Codex"
        QuotaProvider.Claude -> "Claude"
        QuotaProvider.MiniMax -> "MiniMax"
        QuotaProvider.Glm -> "GLM"
    }

/** A reading that says only how the sign-in stands, with no numbers. */
public fun usageBareReading(connectionId: String, provider: QuotaProvider, status: UsageReadingStatus, now: Long): UsageReading =
    UsageReading(
        providerId = provider.id,
        displayName = usageQuotaName(provider),
        connectionId = connectionId,
        status = status,
        fetchedAt = now,
        method = SignInMethod.Plan,
    )

/** The display numbers of a quota snapshot as one connection's reading. */
public fun usageReadingFromSnapshot(connectionId: String, provider: QuotaProvider, snapshot: ProviderQuotaSnapshot): UsageReading {
    val windows =
        snapshot.windows.mapNotNull { w ->
            w.usedPercent?.let { UsageWindow(w.id, it, durationSeconds = w.durationSeconds, resetsAt = w.resetsAt) }
        }
    return UsageReading(
        providerId = provider.id,
        displayName = usageQuotaName(provider),
        connectionId = connectionId,
        status = if (windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
        fetchedAt = snapshot.fetchedAt,
        windows = windows,
        accountRef = snapshot.account.ref,
        plan = snapshot.account.plan,
        method = SignInMethod.Plan,
    )
}

/**
 * The collector source (port of QuotaCollectorSource): In-app Ubuntu's reader for every provider, and remote collectors
 * only when consented to (a monitor rule exists). Also the bridge to the quota monitor's alerts.
 */
public class QuotaCollectorSource(
    private val profiles: () -> List<QuotaProfile>,
    private val isReadable: (String) -> Boolean,
    private val isInApp: (QuotaProfile) -> Boolean,
    /** In-app Ubuntu's reader was set up for this profile and not turned off. */
    private val inAppEnabled: (String) -> Boolean,
    private val createGateway: (QuotaProfile, QuotaProvider) -> ProviderQuotaGateway,
    private val monitor: ProviderQuotaMonitor,
    private val sharedRules: () -> SharedNotifyRules,
    private val timeoutMs: Long = 12_000L,
    private val reuseForMs: Long = 4 * 60_000L,
) : UsageSource,
    UsageAlertBridge {
    private val snapshots = HashMap<String, ProviderQuotaSnapshot>()

    private fun applies(profile: QuotaProfile, provider: QuotaProvider): Boolean {
        if (!profile.isOpenCode || !isReadable(profile.id) || !quotaCollectionAvailable(provider)) return false
        return if (isInApp(profile)) inAppEnabled(profile.id) else monitor.rulesFor(profile.id, provider) != null
    }

    override suspend fun read(context: UsageReadContext): UsageSourceResult {
        val readings = mutableListOf<UsageReading>()
        val failures = mutableListOf<UsageFailure>()
        val unreached = LinkedHashSet<String>()
        for (profile in profiles()) {
            if (profile.id in unreached) continue
            for (provider in QuotaProvider.entries) {
                if (!applies(profile, provider)) continue
                val id = provider.id
                var snapshot: ProviderQuotaSnapshot? = null
                if (!context.force) {
                    val seen = monitor.observationFor(profile.id, provider)
                    val checked = seen.checkedAt
                    if (seen.status == QuotaMonitorStatus.Current && seen.snapshot != null && checked != null &&
                        context.now - checked < reuseForMs
                    ) {
                        snapshot = seen.snapshot
                    }
                }
                if (snapshot == null) {
                    val gateway = createGateway(profile, provider)
                    val result =
                        try {
                            withTimeoutOrNull(timeoutMs) { guarded { gateway.readSnapshot() } }
                        } finally {
                            guarded { gateway.close() }
                        }
                    when (result) {
                        null -> {
                            // A time-out or a socket error: the host did not answer.
                            if (isInApp(profile)) failures += UsageFailure(profile.id, id, UsageProblem.Broken) else unreached += profile.id
                            continue
                        }
                        is Outcome.Failed -> {
                            when (quotaFailureKind(result.failure)) {
                                QuotaFailureKind.CollectorAuth ->
                                    readings += usageBareReading(profile.id, provider, UsageReadingStatus.AuthRequired, context.now)
                                QuotaFailureKind.Unavailable ->
                                    if (isInApp(profile)) {
                                        failures += UsageFailure(profile.id, id, UsageProblem.Broken)
                                    } else {
                                        unreached += profile.id
                                    }
                                QuotaFailureKind.Unsupported, QuotaFailureKind.InvalidResponse ->
                                    failures += UsageFailure(profile.id, id, UsageProblem.Broken)
                            }
                            continue
                        }
                        is Outcome.Ok -> snapshot = result.value
                    }
                }
                snapshots["${profile.id}|$id"] = snapshot
                when {
                    snapshot.status == ProviderQuotaStatus.Ok && snapshot.canShowWindows ->
                        readings += usageReadingFromSnapshot(profile.id, provider, snapshot)
                    snapshot.status == ProviderQuotaStatus.Unconfigured -> {
                        // Only a provider that was signed in here before stays listed.
                        if (context.lastReading?.invoke(profile.id, id) != null) {
                            readings += usageBareReading(profile.id, provider, UsageReadingStatus.NotSignedIn, context.now)
                        }
                    }
                    snapshot.status == ProviderQuotaStatus.AuthRequired ->
                        readings += usageBareReading(profile.id, provider, UsageReadingStatus.AuthRequired, context.now)
                    snapshot.status == ProviderQuotaStatus.RateLimited ->
                        failures += UsageFailure(profile.id, id, UsageProblem.RateLimited)
                    else -> failures += UsageFailure(profile.id, id, UsageProblem.Broken)
                }
            }
        }
        return UsageSourceResult(readings = readings, failures = failures, unreached = unreached)
    }

    /** The monitor alerts for a collector reading whose rule exists (or, for In-app Ubuntu, is created next). */
    override fun covers(connectionId: String, providerId: String): Boolean {
        val profile = profiles().firstOrNull { it.id == connectionId } ?: return false
        val provider = QuotaProvider.byId(providerId) ?: return false
        return monitor.rulesFor(profile.id, provider) != null || (isInApp(profile) && inAppEnabled(profile.id))
    }

    override fun legacyRules(): List<LegacyQuotaRule> =
        profiles().flatMap { profile ->
            QuotaProvider.entries.mapNotNull { provider ->
                monitor.rulesFor(profile.id, provider)?.let {
                    LegacyQuotaRule(profile.id, provider.id, it.account, it.threshold)
                }
            }
        }

    override suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean) {
        val all = profiles()
        for (watch in wanted) {
            val profile = all.firstOrNull { it.id == watch.connectionId } ?: continue
            val provider = QuotaProvider.byId(watch.providerId) ?: continue
            val rules = monitor.rulesFor(profile.id, provider)
            if (rules == null) {
                // Only In-app Ubuntu's own reader is enrolled automatically; a remote collector needs the consent that
                // created its rule.
                val snapshot = snapshots["${profile.id}|${provider.id}"]
                if (!alertsOn || !isInApp(profile) || !inAppEnabled(profile.id) || snapshot == null) continue
                val shared = sharedRules()
                monitor.enroll(
                    profile.id,
                    snapshot,
                    notifications = shared.quotaAlerts,
                    threshold = watch.threshold,
                    wifiOnly = shared.wifiOnly,
                    quietStart = if (shared.quietEnabled) shared.quietStart else null,
                    quietEnd = if (shared.quietEnabled) shared.quietEnd else null,
                )
            } else if (abs(rules.threshold - watch.threshold) > 0.001) {
                monitor.setPolicy(
                    profile.id,
                    provider,
                    notifications = rules.notifications,
                    wifiOnly = rules.wifiOnly,
                    threshold = watch.threshold,
                    quietStart = rules.quietStart,
                    quietEnd = rules.quietEnd,
                )
            }
        }
    }
}
