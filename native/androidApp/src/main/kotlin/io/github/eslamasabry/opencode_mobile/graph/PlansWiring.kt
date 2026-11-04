package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.ConnectedProvider
import io.github.eslamasabry.oc.engine.integrations.usage.OpenCodeSignInSource
import io.github.eslamasabry.oc.engine.integrations.usage.RetiredQuotaBudgets
import io.github.eslamasabry.oc.engine.integrations.usage.SignInServer
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscriptionsEngine
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetFeed
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorTarget
import io.github.eslamasabry.oc.feature.plans.presentation.PlansSource
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Unit P12-BUILD-INTEGRATIONS-B: Usage › Plans on Android. The subscriptions engine (`:engine:integrations-usage`) is
 * fed by every connected OpenCode server's connected providers (its sign-ins); its numbers, alert levels and the
 * widget feed live in the app's preference store under the Flutter keys, so the Usage widget draws them. Nothing
 * here switches the active server or replays a request.
 *
 * The strict collector reader (PROTO) supplies existing, consented quota windows; no arbitrary remote collector is
 * probed. Remote collectors are read only after explicit trust bound to the current origin and credentials. Each
 * request uses a private, bounded, redirect-free transport. The in-app collector and Paseo host require their own host
 * ports.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlansSource>())
@Inject
class AndroidPlansSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val application: Application,
    private val clock: Clock,
    private val protoQuota: ProtoQuotaSource,
    private val dispatchers: AppDispatchers,
    private val facts: DeviceFacts,
    private val collectorHost: AndroidPlansCollectorHost,
    appScope: AppCoroutineScope,
) : PlansSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val state = MutableStateFlow(UsageSnapshot())
    override val snapshot: StateFlow<UsageSnapshot> = state.asStateFlow()
    private val creating = Mutex()
    private var engine: UsageSubscriptionsEngine? = null
    private var quota: PlansQuotaSource? = null
    private var paseo: AndroidPlansPaseoSource? = null

    private fun openCodeServers() =
        registry.live.value.filter { (_, graph) -> graph.profile.backend == BackendKind.OpenCode2 }

    /**
     * Built after the preference store is ready (the engine reads its cache in its constructor).
     */
    private suspend fun engine(): UsageSubscriptionsEngine? = creating.withLock {
        engine?.let {
            return@withLock it
        }
        if (stores.warm() !is Outcome.Ok) return@withLock null
        // B-STATE-068: the retired Remaining-page quota budgets go on load.
        RetiredQuotaBudgets.dropAll(stores.prefs, stores.lanes.app)
        val signIns =
            OpenCodeSignInSource(
                servers = {
                    openCodeServers().keys.map {
                        SignInServer(
                            it.raw,
                            isOpenCode = true,
                            requiresPasswordReentry =
                                stores.profiles.profile(it)?.reentry?.password != false,
                        )
                    }
                },
                isReadable = { id -> registry.live.value[ProfileId(id)] != null },
                providers = { server ->
                    val gateway = registry.live.value[ProfileId(server.id)]?.session?.server
                    gateway?.providers()?.map { response ->
                        response.providers.map { ConnectedProvider(it.id, it.name) }
                    } ?: Failure.Refused(RefusalReason.StaleScope).failed()
                },
            )
        val widget =
            UsageWidgetFeed(
                store = stores.prefs,
                lane = stores.lanes.app,
                isAndroid = true,
                refreshNative = { UsageWidgetProvider.refreshAll(application) },
                clock = { clock.now() },
            )
        val quotaSource =
            PlansQuotaSource(registry, stores, dispatchers, widget, collectorHost).also {
                quota = it
            }
        val paseoSource =
            AndroidPlansPaseoSource(registry, stores, widget, clock, facts, scope).also {
                paseo = it
            }
        UsageSubscriptionsEngine(
                store = stores.prefs,
                lane = stores.lanes.app,
                connections = {
                    openCodeServers().map { (id, graph) ->
                        UsageConnection(
                            id.raw,
                            graph.profile.name,
                            UsageConnectionKind.OpenCodeServer,
                        )
                    } + paseoSource.connections()
                },
                sources = listOf(signIns, protoQuota, quotaSource, paseoSource),
                alertsOnRule = { false },
                setQuotaAlerts = {},
                widget = widget,
                clock = { clock.now() },
            )
            .also { created ->
                engine = created
                scope.launch { created.snapshot.collect { state.value = it } }
            }
    }

    override suspend fun refresh() {
        engine()?.refresh()
    }

    override suspend fun collectors(): List<CollectorTarget> {
        engine() ?: return emptyList()
        return quota?.targets().orEmpty() + paseo?.targets().orEmpty()
    }

    override suspend fun setCollector(target: CollectorTarget, enabled: Boolean): Boolean {
        engine() ?: return false
        val changed =
            if (target.provider == AndroidPlansPaseoSource.PROVIDER)
                paseo?.setEnabled(target, enabled) == true
            else quota?.setEnabled(target, enabled) == true
        if (changed && !enabled) {
            if (
                target.provider == AndroidPlansPaseoSource.PROVIDER ||
                    target.provider == PlansQuotaSource.BUILTIN
            )
                engine?.removeProfile(target.profile.raw)
            else engine?.removeProvider(target.profile.raw, target.provider)
        }
        return changed
    }

    /**
     * B-SEC-178: a removed server takes its cached numbers, its overrides and its widget rows
     * along.
     */
    suspend fun removeProfile(profile: ProfileId): Boolean {
        val e = engine() ?: return false
        if (quota?.closeProfile(profile) == false) return false
        if (paseo?.removeProfile(profile) == false) return false
        return e.removeProfile(profile.raw)
    }
}

/**
 * Profile deletion: the usage engine forgets the server before the generic sweep removes
 * `oc.usageLast.<id>`.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class UsagePlansDeletionCleaner(private val plans: AndroidPlansSource) : ProfileResourceCleaner {
    override val owner: String = ":engine:integrations-usage (plans)"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        if (plans.removeProfile(profileId)) 1.ok() else Failure.StorageRefused().failed()
}
