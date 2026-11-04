package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorTarget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Only an explicit, credential-bound opt-in permits the deployment extension to receive Basic
 * authentication.
 */
internal class PlansQuotaSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val widget: UsageWidgetFeed,
    private val host: AndroidPlansCollectorHost,
) : UsageSource {
    private val lane = Mutex()
    private val blocked = HashSet<ProfileId>()
    private val calls = HashMap<ProfileId, MutableSet<HttpProviderQuotaGateway>>()

    private fun consent(profile: ProfileId): Map<String, String> =
        (DartJson.decodeOrNull(stores.prefs.string(key(profile)).orEmpty()) as? Map<*, *>)
            .orEmpty()
            .entries
            .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            .toMap()

    private fun fingerprint(origin: String, username: String, password: String): String =
        DartJson.sha256Hex(DartJson.encode(listOf(origin, username, password)))

    suspend fun targets(): List<CollectorTarget> {
        val output = ArrayList<CollectorTarget>()
        for ((id, graph) in registry.live.value) {
            if (id in blocked || !graph.session.server.capabilities.value.providerRuntimeRefresh)
                continue
            val profile = stores.profiles.profile(id) ?: continue
            if (profile.reentry.password) continue
            val p = profile.profile
            if (host.applies(graph.profile)) {
                output +=
                    CollectorTarget(
                        id,
                        p.name,
                        BUILTIN,
                        host.enabled(id),
                        DartJson.sha256Hex(graph.profile.origin.toString()),
                    )
                continue
            }
            val secret = (stores.profiles.secret(id) as? Outcome.Ok)?.value ?: continue
            if (!HttpProviderQuotaGateway.canReadProfile(p.baseUrl, p.username, secret, false))
                continue
            val bound = fingerprint(p.baseUrl, p.username, secret)
            for (provider in listOf("codex", "glm", "minimax")) {
                output +=
                    CollectorTarget(id, p.name, provider, consent(id)[provider] == bound, bound)
            }
        }
        return output
    }

    suspend fun setEnabled(target: CollectorTarget, enabled: Boolean): Boolean = lane.withLock {
        if (target.profile in blocked) return@withLock false
        val current =
            targets().firstOrNull {
                it.profile == target.profile &&
                    it.provider == target.provider &&
                    it.binding == target.binding
            } ?: return@withLock false
        if (target.provider == BUILTIN) {
            val changed = if (enabled) host.setup(target.profile) else host.disable(target.profile)
            return@withLock changed && (enabled || widget.clearForProfile(target.profile.raw))
        }
        val values = consent(target.profile).toMutableMap()
        if (enabled) values[target.provider] = current.binding else values.remove(target.provider)
        val result =
            stores.prefs.commit(stores.lanes.app) {
                if (values.isEmpty()) remove(key(target.profile))
                else putString(key(target.profile), DartJson.encode(values))
            }
        if (result !is Outcome.Ok) return@withLock false
        if (!enabled) {
            calls[target.profile]?.forEach { it.close() }
            widget.removeProvider(target.profile.raw, target.provider)
        }
        true
    }

    suspend fun closeProfile(id: ProfileId): Boolean {
        blocked += id
        calls.remove(id)?.forEach { it.close() }
        return lane.withLock { widget.clearForProfile(id.raw) }
    }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        val readings = ArrayList<UsageReading>()
        val failures = ArrayList<UsageFailure>()
        val unreached = HashSet<String>()
        val enabledTargets =
            targets()
                .filter { it.enabled }
                .flatMap { target ->
                    if (target.provider == BUILTIN)
                        listOf("codex", "glm", "minimax").map { target.copy(provider = it) }
                    else listOf(target)
                }
        for (target in enabledTargets) {
            val id = target.profile
            if (id in blocked) continue
            val graph = registry.live.value[id] ?: continue
            val profile = stores.profiles.profile(id) ?: continue
            val secret = (stores.profiles.secret(id) as? Outcome.Ok)?.value ?: continue
            val p = profile.profile
            val local = host.applies(graph.profile)
            fun stillConsented(): Boolean =
                if (local)
                    host.enabled(id) &&
                        target.binding == DartJson.sha256Hex(graph.profile.origin.toString())
                else
                    consent(id)[target.provider] == target.binding &&
                        target.binding == fingerprint(p.baseUrl, p.username, secret)
            if (!stillConsented()) continue
            val provider = QuotaProvider.entries.first { it.wire == target.provider }
            val gateway =
                HttpProviderQuotaGateway(
                    p.baseUrl,
                    p.username,
                    secret,
                    profile.reentry.password,
                    AndroidQuotaTransport(dispatchers),
                    provider,
                    inAppToken = if (local) ({ host.token(id) }) else null,
                )
            calls.getOrPut(id) { HashSet() } += gateway
            val result =
                try {
                    gateway.readSnapshot()
                } finally {
                    calls[id]?.remove(gateway)
                    gateway.close()
                }
            lane.withLock {
                if (id in blocked || registry.live.value[id] !== graph || !stillConsented())
                    return@withLock
                val name =
                    when (provider) {
                        QuotaProvider.Codex -> "Codex"
                        QuotaProvider.Glm -> "GLM"
                        QuotaProvider.Minimax -> "MiniMax"
                        else -> return@withLock
                    }
                fun bare(status: UsageReadingStatus) =
                    UsageReading(
                        target.provider,
                        name,
                        id.raw,
                        status,
                        context.now,
                        method = SignInMethod.Plan,
                    )
                when (result) {
                    is QuotaRead.Failed ->
                        when (result.failure.kind) {
                            QuotaFailureKind.CollectorAuth -> {
                                readings += bare(UsageReadingStatus.AuthRequired)
                                widget.markAuthNeeded(id.raw, p.name, target.provider)
                            }
                            QuotaFailureKind.Unavailable -> unreached += id.raw
                            else ->
                                failures +=
                                    UsageFailure(id.raw, target.provider, UsageProblem.Broken)
                        }
                    is QuotaRead.Read -> {
                        val snapshot = result.snapshot
                        when {
                            snapshot.canShowWindows -> {
                                val windows =
                                    snapshot.windows.mapNotNull { w ->
                                        w.usedPercent?.let {
                                            UsageWindow(w.id, it, w.durationSeconds, w.resetsAt)
                                        }
                                    }
                                readings +=
                                    UsageReading(
                                        target.provider,
                                        name,
                                        id.raw,
                                        if (windows.isEmpty()) UsageReadingStatus.NoLimits
                                        else UsageReadingStatus.Ok,
                                        snapshot.fetchedAt,
                                        windows,
                                        snapshot.account.ref,
                                        snapshot.account.plan,
                                        SignInMethod.Plan,
                                        checkedAt = context.now,
                                        problem =
                                            if (snapshot.isStale(context.now)) UsageProblem.Offline
                                            else null,
                                    )
                                widget.record(
                                    id.raw,
                                    p.name,
                                    QuotaWidgetInput(
                                        target.provider,
                                        !snapshot.isStale(context.now),
                                        snapshot.fetchedAt,
                                        snapshot.windows.map {
                                            QuotaWidgetWindow(
                                                it.remainingPercent,
                                                it.durationSeconds,
                                                it.resetsAt,
                                            )
                                        },
                                    ),
                                )
                            }
                            snapshot.status == ProviderQuotaStatus.AuthRequired ->
                                readings += bare(UsageReadingStatus.AuthRequired)
                            snapshot.status == ProviderQuotaStatus.Unconfigured ->
                                if (context.lastReading(id.raw, target.provider) != null)
                                    readings += bare(UsageReadingStatus.NotSignedIn)
                            snapshot.status == ProviderQuotaStatus.RateLimited ->
                                failures +=
                                    UsageFailure(id.raw, target.provider, UsageProblem.RateLimited)
                            else ->
                                failures +=
                                    UsageFailure(id.raw, target.provider, UsageProblem.Broken)
                        }
                    }
                }
            }
        }
        return Outcome.Ok(
            UsageSourceResult(readings = readings, failures = failures, unreached = unreached)
        )
    }

    companion object {
        const val BUILTIN = "builtin"

        fun key(id: ProfileId): String = "oc.quotaConsent.${id.raw}"
    }
}
