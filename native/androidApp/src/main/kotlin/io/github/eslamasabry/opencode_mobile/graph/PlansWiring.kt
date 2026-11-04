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
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.supervisedContext
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProviderUsageGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.ConnectedProvider
import io.github.eslamasabry.oc.engine.integrations.usage.OpenCodeSignInSource
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageMonitor
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsagePorts
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageProfile
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageSource
import io.github.eslamasabry.oc.engine.integrations.usage.ProviderUsageReading
import io.github.eslamasabry.oc.engine.integrations.usage.RetiredQuotaBudgets
import io.github.eslamasabry.oc.engine.integrations.usage.SignInServer
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscriptionsEngine
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetFeed
import io.github.eslamasabry.oc.engine.integrations.usage.parseProviderUsageList
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCollectorUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansSource
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Unit P12-BUILD-INTEGRATIONS-B: Usage › Plans on Android. The subscriptions engine (`:engine:integrations-usage`) is
 * fed by every connected OpenCode server's connected providers (its sign-ins); its numbers, alert levels and the
 * widget feed live in the app's preference store under the Flutter keys, so the Usage widget draws them. Nothing
 * here switches the active server or replays a request.
 *
 * Provider-usage facets feed connected daemon readings without switching the current server or starting a host.
 * The quota collector and alert delivery have separate platform owners.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlansSource>())
@Inject
class AndroidPlansSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val application: Application,
    private val clock: Clock,
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
    private val phoneUsage: PhoneUsageReader,
    private val collector: CollectorUsageSource,
) : PlansSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val state = MutableStateFlow(UsageSnapshot())
    override val snapshot: StateFlow<UsageSnapshot> = state.asStateFlow()
    private val creating = Mutex()
    private var engine: UsageSubscriptionsEngine? = null
    private var paseo: PaseoUsageMonitor? = null

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
                    openCodeServers().keys.map { SignInServer(it.raw, isOpenCode = true) }
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
        val usagePorts =
            object : PaseoUsagePorts {
                override fun phoneSupported(): Boolean = phoneUsage.supported

                override suspend fun readPhone(): Outcome<List<ProviderUsageReading>?> =
                    phoneUsage.read()

                override fun paseoProfiles(): List<PaseoUsageProfile> =
                    registry.live.value
                        .filterValues { it.session.server is ProviderUsageGateway }
                        .map { (id, graph) -> PaseoUsageProfile(id.raw, graph.profile.name) }

                override fun isReadable(profileId: String): Boolean =
                    registry.live.value[ProfileId(profileId)]?.session?.server is
                        ProviderUsageGateway

                override suspend fun readServer(
                    profileId: String
                ): Outcome<List<ProviderUsageReading>> {
                    val id = ProfileId(profileId)
                    val graph =
                        registry.live.value[id]
                            ?: return Failure.Refused(RefusalReason.StaleScope).failed()
                    val gateway =
                        graph.session.server as? ProviderUsageGateway
                            ?: return Failure.Refused(RefusalReason.StaleScope).failed()
                    val result = readPrivateUsage(graph)
                    if (registry.live.value[id] !== graph)
                        return Failure.Refused(RefusalReason.StaleScope).failed()
                    return when (result) {
                        is Outcome.Failed -> result
                        is Outcome.Ok -> {
                            try {
                                parseProviderUsageList(
                                        DartJson.decodeOrNull(result.value.toString()),
                                        clock.now(),
                                    )
                                    .ok()
                            } catch (
                                _:
                                    io.github.eslamasabry.oc.engine.integrations.usage.ProviderUsageListInvalid) {
                                Failure.Device().failed()
                            }
                        }
                    }
                }
            }
        val daemonUsage =
            PaseoUsageMonitor(
                store = stores.prefs,
                lane = stores.lanes.app,
                ports = usagePorts,
                widget = null,
                clock = { clock.now() },
                autoRead = true,
            )
        paseo = daemonUsage
        UsageSubscriptionsEngine(
                store = stores.prefs,
                lane = stores.lanes.app,
                connections = {
                    registry.live.value
                        .filterValues {
                            it.profile.backend == BackendKind.OpenCode2 ||
                                it.session.server is ProviderUsageGateway
                        }
                        .map { (id, graph) ->
                            UsageConnection(
                                id.raw,
                                graph.profile.name,
                                when {
                                    graph.session.server is ProviderUsageGateway ->
                                        UsageConnectionKind.PaseoServer
                                    graph.profile.managedOnPhone -> UsageConnectionKind.InAppUbuntu
                                    else -> UsageConnectionKind.OpenCodeServer
                                },
                            )
                        }
                        .let { known ->
                            val all =
                                if (phoneUsage.supported)
                                    known +
                                        UsageConnection(
                                            UsageConnection.PHONE_ID,
                                            "",
                                            UsageConnectionKind.PhoneAgents,
                                        )
                                else known
                            (all + collector.connections()).distinctBy { it.id }
                        }
                },
                sources =
                    listOf(
                        signIns,
                        collector,
                        PaseoUsageSource(daemonUsage) { usagePorts.paseoProfiles().map { it.id } },
                    ),
                alertsOnRule = collector::alertsOn,
                setQuotaAlerts = { collector.setAlertsOn(it) },
                alertBridge = collector,
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

    override fun collectors(): List<PlanCollectorUi> = collector.collectorOptions()

    override fun alertsOn(): Boolean = collector.alertsOn()

    override fun canAlert(): Boolean = true

    override fun phoneSignInOwner(): ProfileId? = phoneUsage.signInOwner()

    override suspend fun setAlertsOn(enabled: Boolean): Outcome<Unit> {
        val result = collector.setAlertsOn(enabled)
        if (result is Outcome.Ok) engine()?.refresh()
        return result
    }

    override suspend fun setUpCollector(profile: ProfileId): Outcome<Unit> =
        collector.setUpInApp(profile)

    override suspend fun consentRemoteCollector(
        profile: ProfileId,
        enabled: Boolean,
    ): Outcome<Unit> = collector.consentRemote(profile, enabled)

    override suspend fun turnOffCollector(profile: ProfileId): Outcome<Unit> =
        collector.turnOff(profile)

    /**
     * A one-shot reader owns its socket; refreshing Plans never closes an active chat connection.
     */
    private suspend fun readPrivateUsage(
        owner: ConnectionGraph
    ): Outcome<kotlinx.serialization.json.JsonObject> {
        val profile = owner.profile
        val location = owner.session.server.location.value
        fun current(): Boolean =
            registry.savedGraph(stores, profile.id) === owner &&
                owner.session.server.location.value == location
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val factory =
            factories.firstOrNull { it.backend == profile.backend }
                ?: return Failure.Unavailable(FeatureId("providerUsage")).failed()
        val credentials =
            when (val read = credentialSources.singleOrNull()?.resolve(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
                null -> return Failure.Unavailable(FeatureId("providerUsage")).failed()
            }
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val connection =
            ConnectionCoroutineScope(
                supervisedContext(
                    appScope.coroutineContext[Job],
                    dispatchers.default,
                    "provider-usage",
                )
            )
        var gateway: ServerGateway? = null
        return try {
            withTimeoutOrNull(15_000) {
                val pair =
                    factory.connect(
                        GatewaySpec(
                            profile,
                            credentials,
                            connection,
                            location,
                            reconnectAllowed = MutableStateFlow(false),
                        )
                    )
                when (pair) {
                    is Outcome.Failed -> pair
                    is Outcome.Ok -> {
                        gateway = pair.value.live
                        if (!current())
                            return@withTimeoutOrNull Failure.Refused(RefusalReason.StaleScope)
                                .failed()
                        val reader =
                            pair.value.live as? ProviderUsageGateway
                                ?: return@withTimeoutOrNull Failure.Unavailable(
                                        FeatureId("providerUsage")
                                    )
                                    .failed()
                        val result = reader.providerUsage()
                        if (!current()) Failure.Refused(RefusalReason.StaleScope).failed()
                        else result
                    }
                }
            } ?: Failure.TimedOut().failed()
        } finally {
            withContext(NonCancellable) {
                try {
                    gateway?.close()
                } finally {
                    connection.coroutineContext[Job]?.cancelAndJoin()
                }
            }
        }
    }

    /**
     * B-SEC-178: a removed server takes its cached numbers, its overrides and its widget rows
     * along.
     */
    suspend fun removeProfile(profile: ProfileId): Boolean {
        val e = engine() ?: return false
        if (phoneUsage.retire(profile)) {
            paseo?.removeProfile(UsageConnection.PHONE_ID)
            e.removeProfile(UsageConnection.PHONE_ID)
        }
        paseo?.removeProfile(profile.raw)
        e.removeProfile(profile.raw)
        return true
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
        if (plans.removeProfile(profileId)) 1.ok() else 0.ok()
}
