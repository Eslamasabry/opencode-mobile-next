package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
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
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProviderUsageGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.integrations.usage.ConnectedProvider
import io.github.eslamasabry.oc.engine.integrations.usage.OpenCodeSignInSource
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageMonitor
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsagePorts
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageProfile
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageSource
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageStatus
import io.github.eslamasabry.oc.engine.integrations.usage.ProviderUsageMonitorSettings
import io.github.eslamasabry.oc.engine.integrations.usage.ProviderUsageMonitorState
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
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorTarget
import io.github.eslamasabry.oc.feature.plans.presentation.ComputerReaderUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAlertPreferences
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCollectorUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPreferences
import io.github.eslamasabry.oc.feature.plans.presentation.PlansSource
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
 * The strict collector reader (PROTO) supplies existing, consented quota windows; no arbitrary remote collector is
 * probed. Remote collectors are read only after explicit trust bound to the current origin and credentials. Each
 * request uses a private, bounded, redirect-free transport. The in-app collector and Paseo host require their own host
 * ports. The optional local collector (LINUX-2) is read on demand through QuotaWiring.
 * Additional readers use their saved consent and existing authenticated transport; they never change the selected
 * connection or obtain credentials from provider configuration.
 * Provider-usage facets feed connected daemon readings without switching the current server or starting a host.
 * The quota collector and alert delivery have separate platform owners.
 *
 * It is also the Usage hub's acknowledged alert and widget choices ([PlansPreferences]): every
 * choice is written through the engine or the shared `oc.notify.*` preferences before it shows, and
 * the one subscription-alert owner (AndroidSubscriptionAlerts) reads the same quiet hours and
 * Wi-Fi choice.
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
    private val collector: AndroidQuotaCollector,
    private val additional: AndroidAdditionalUsageSources,
    private val appScope: AppCoroutineScope,
    // AGENT-2: provider-usage monitors (daemon readings, the phone's agents, Codex reads) and its
    // collector consent.
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val phoneUsage: PhoneUsageReader,
    private val collectorUsage: CollectorUsageSource,
    private val codexReads: AppCodexReads,
    private val subscriptionAlerts: AndroidSubscriptionAlerts,
    private val quotaPolicy: QuotaCollectionPolicy,
) : PlansSource, ProviderUsageMonitorSettings, PlansPreferences {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val state = MutableStateFlow(UsageSnapshot())
    override val snapshot: StateFlow<UsageSnapshot> = state.asStateFlow()
    private val mutablePreferences = MutableStateFlow(PlanAlertPreferences())
    override val preferences: StateFlow<PlanAlertPreferences> = mutablePreferences.asStateFlow()
    private val creating = Mutex()
    private var engine: UsageSubscriptionsEngine? = null
    private var quota: PlansQuotaSource? = null
    private var paseo: AndroidPlansPaseoSource? = null
    private var paseoMonitor: PaseoUsageMonitor? = null
    private var explicitMonitor: PaseoUsageMonitor? = null
    private val monitorStates =
        MutableStateFlow<Map<ProfileId, ProviderUsageMonitorState>>(emptyMap())
    override val states: StateFlow<Map<ProfileId, ProviderUsageMonitorState>> =
        monitorStates.asStateFlow()
    private var monitorLoopStarted = false

    /**
     * App visibility for the usage monitors (B-AGENT-191/313): reads pause while no activity is
     * started, as Flutter gates them on the app lifecycle. This source can be built after the first
     * activity started, so it starts visible and only follows real start/stop transitions; a stop
     * that is part of a configuration change does not count.
     */
    private val appVisible = MutableStateFlow(true)
    private var startedActivities = 0
    private var changingConfiguration = false

    init {
        // Loading saved monitor choices never reads a daemon. Only an explicit opt-in admits its
        // timer reads.
        scope.launch {
            val e = engine() ?: return@launch
            // An edited server's old numbers go (its record stays in the feed: not a deletion).
            launch {
                var known = stores.profiles.profiles.value.associateBy { it.id }
                stores.profiles.profiles.collect { profiles ->
                    val next = profiles.associateBy { it.id }
                    for ((id, before) in known) {
                        val now = next[id]
                        if (now != null && now != before) e.removeProfile(id.raw, deleted = false)
                    }
                    known = next
                }
            }
            launch {
                var phoneOwner = stores.prefs.string("oc.builtinServerOwner")
                stores.prefs.changes.collect { keys ->
                    if ("oc.builtinServerOwner" in keys) {
                        val next = stores.prefs.string("oc.builtinServerOwner")
                        if (next != phoneOwner)
                            e.removeProfile(UsageConnection.PHONE_ID, deleted = false)
                        phoneOwner = next
                    }
                    if (
                        keys.any {
                            it.startsWith("oc.notify.") ||
                                it == UsageSubscriptionsEngine.ALERTS_KEY ||
                                it == UsageWidgetSettings.KEY
                        }
                    )
                        project(e)
                }
            }
        }
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (changingConfiguration) changingConfiguration = false
                    else startedActivities++
                    appVisible.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    if (activity.isChangingConfigurations) {
                        changingConfiguration = true
                        return
                    }
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                    appVisible.value = startedActivities > 0
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        scope.launch {
            appVisible.collect { visible ->
                paseoMonitor?.setRuntime(visible, false)
                explicitMonitor?.setRuntime(visible, false)
                engine?.setForeground(visible)
            }
        }
    }

    private var reader: ComputerUsageSource? = null
    private val readers = MutableStateFlow<List<ComputerReaderUi>>(emptyList())
    override val computerReaders: StateFlow<List<ComputerReaderUi>> = readers.asStateFlow()

    private fun publishReaders() {
        val source = reader ?: return
        readers.value =
            liveOpenCodeServers()
                .filterValues { !it.profile.managedOnPhone }
                .map { (id, graph) ->
                    ComputerReaderUi(
                        id.raw,
                        graph.profile.name,
                        source.enabled(id.raw),
                        source.problemFor(id.raw),
                        source.foundOn(id.raw),
                    )
                }
    }

    override suspend fun setComputerReaderEnabled(id: String, enabled: Boolean): Outcome<Unit> {
        engine() ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (stores.profiles.profile(ProfileId(id)) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val result = reader!!.setEnabled(id, enabled)
        if (result is Outcome.Ok) refresh()
        publishReaders()
        return result
    }

    override suspend fun readComputerNow(id: String): Outcome<Unit> {
        engine() ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val source = reader ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (!source.enabled(id)) return Failure.Refused(RefusalReason.StaleScope).failed()
        source.readNow(id)
        refresh()
        return Unit.ok()
    }

    private fun terminalPort(server: ComputerUsageServer): ComputerTerminalPort {
        val id = ProfileId(server.id)
        val graph = registry.live.value[id]
        val gateway = graph?.session?.server
        val operations = gateway as? ServerOperationsGateway
        val revision = gateway?.scopeRevision
        var closed = false
        fun current() =
            !closed &&
                graph != null &&
                registry.live.value[id] === graph &&
                graph.session.server === gateway &&
                gateway.scopeRevision == revision
        return object : ComputerTerminalPort {
            override suspend fun start(
                command: String,
                arguments: List<String>,
                title: String,
            ): Outcome<String> {
                if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
                return operations?.createCommandTerminal(command, arguments, title)?.map { it.id }
                    ?: io.github.eslamasabry.oc.core.domain.unavailable("terminal")
            }

            override suspend fun connect(id: String): Outcome<ComputerUsageChannel> {
                if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
                return operations?.connectTerminal(id)?.map { channel ->
                    object : ComputerUsageChannel {
                        override val output = channel.output

                        override suspend fun close() = channel.close()
                    }
                } ?: io.github.eslamasabry.oc.core.domain.unavailable("terminal")
            }

            // B-AGENT-072: cleanup is not generation-gated. The exact PTY id goes back to the same
            // gateway object that created it, so a reconnect or scope change mid-read cannot leak
            // the temporary terminal on the computer.
            override suspend fun remove(id: String): Outcome<Unit> =
                operations?.removeTerminal(id)
                    ?: io.github.eslamasabry.oc.core.domain.unavailable("terminal")

            override fun close() {
                closed = true
            }
        }
    }

    private fun liveOpenCodeServers() =
        registry.live.value.filter { (_, graph) -> graph.profile.backend == BackendKind.OpenCode2 }

    private fun openCodeServers() =
        stores.profiles.profiles.value.filter { it.profile.backend == BackendKind.OpenCode2 }

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
        // AGENT-1: explicit per-server computer usage readers (one-shot PTY reads; the consent key
        // `oc.usageComputerReader.<id>` is registered in the shared ProfileScope).
        val computer =
            ComputerUsageSource(
                stores.prefs,
                stores.lanes.app,
                servers = {
                    liveOpenCodeServers().map { (id, graph) ->
                        ComputerUsageServer(
                            id.raw,
                            graph.profile.name,
                            inApp = graph.profile.managedOnPhone,
                        )
                    }
                },
                createPort = ::terminalPort,
                loadSource = {
                    kotlinx.coroutines.withContext(dispatchers.io) {
                        application.assets.open("quota/read_once.mjs").bufferedReader().use {
                            it.readText()
                        }
                    }
                },
                clock = { clock.now() },
                laneFor = { stores.lanes.forProfile(ProfileId(it)) },
                profileExists = { stores.profiles.profile(ProfileId(it)) != null },
            )
        reader = computer
        publishReaders()
        val signIns =
            OpenCodeSignInSource(
                servers = {
                    openCodeServers().map {
                        SignInServer(
                            it.id.raw,
                            isOpenCode = true,
                            requiresPasswordReentry = it.reentry.password,
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
        paseoMonitor = daemonUsage
        val monitorPorts =
            object : PaseoUsagePorts {
                override fun paseoProfiles(): List<PaseoUsageProfile> =
                    stores.profiles.profiles.value
                        .filter { it.profile.backend == BackendKind.Paseo }
                        .map { PaseoUsageProfile(it.id.raw, it.profile.name) }

                override fun isReadable(profileId: String): Boolean =
                    stores.profiles.profile(ProfileId(profileId))?.profile?.backend ==
                        BackendKind.Paseo

                override suspend fun readServer(
                    profileId: String
                ): Outcome<List<ProviderUsageReading>> = usagePorts.readServer(profileId)
            }
        explicitMonitor =
            PaseoUsageMonitor(
                store = stores.prefs,
                lane = stores.lanes.app,
                ports = monitorPorts,
                widget = widget,
                clock = { clock.now() },
                autoRead = false,
            )
        appVisible.value.let { visible ->
            daemonUsage.setRuntime(visible, false)
            explicitMonitor?.setRuntime(visible, false)
        }
        if (!monitorLoopStarted) {
            monitorLoopStarted = true
            scope.launch {
                while (isActive) {
                    delay(5.minutes)
                    explicitMonitor?.refresh()
                    if (quotaPolicy.runningAllowed) {
                        engine?.setForeground(quotaPolicy.foreground.value)
                        engine?.refresh()
                    }
                    publishMonitorStates()
                }
            }
        }
        publishMonitorStates()
        UsageSubscriptionsEngine(
                store = stores.prefs,
                lane = stores.lanes.app,
                connections = {
                    (openCodeServers().map { saved ->
                            UsageConnection(
                                saved.id.raw,
                                saved.profile.name,
                                UsageConnectionKind.OpenCodeServer,
                            )
                        } +
                            additional.connections() +
                            paseoSource.connections() +
                            // AGENT-2: live daemons with a provider-usage call, the phone's own
                            // agents, the usage collectors and Codex reads.
                            registry.live.value
                                .filterValues { it.session.server is ProviderUsageGateway }
                                .map { (id, graph) ->
                                    UsageConnection(
                                        id.raw,
                                        graph.profile.name,
                                        UsageConnectionKind.PaseoServer,
                                    )
                                } +
                            (if (phoneUsage.supported)
                                listOf(
                                    UsageConnection(
                                        UsageConnection.PHONE_ID,
                                        "",
                                        UsageConnectionKind.PhoneAgents,
                                    )
                                )
                            else emptyList()) +
                            collectorUsage.connections() +
                            codexReads.connections())
                        .distinctBy { it.id }
                },
                sources =
                    listOf(
                        signIns,
                        additional,
                        protoQuota,
                        quotaSource,
                        paseoSource,
                        collector.usageSource(),
                        // AGENT-2
                        collectorUsage,
                        codexReads.usageSource,
                        PaseoUsageSource(daemonUsage) { usagePorts.paseoProfiles().map { it.id } },
                        // AGENT-1: explicit computer usage readers.
                        computer,
                    ),
                alertBridge =
                    object : UsageAlertBridge {
                        override fun legacyRules(): List<LegacyQuotaRule> =
                            (additional.alertBridge.legacyRules() + collectorUsage.legacyRules())
                                .distinctBy { "${it.connectionId}|${it.providerId}" }

                        override suspend fun reconcile(
                            wanted: List<QuotaWatch>,
                            alertsOn: Boolean,
                        ) {
                            additional.alertBridge.reconcile(wanted, alertsOn)
                            collectorUsage.reconcile(wanted, alertsOn)
                        }
                    },
                alertsOnRule = { stores.prefs.bool("oc.notify.quotaAlerts") == true },
                setQuotaAlerts = { enabled ->
                    stores.prefs.commit(stores.lanes.app) {
                        putBool("oc.notify.quotaAlerts", enabled)
                    }
                },
                widget = widget,
                clock = { clock.now() },
                subscriptionAlerts = subscriptionAlerts.delivery,
                alertCovered = { subscription ->
                    subscription.readFrom?.let {
                        collectorUsage.covers(it.id, subscription.providerId)
                    } == true
                },
            )
            .also { created ->
                created.setForeground(quotaPolicy.foreground.value)
                engine = created
                project(created)
                scope.launch { created.snapshot.collect { state.value = it } }
            }
    }

    override val widgetSettings: UsageWidgetSettings
        get() = engine?.widgetSettings ?: UsageWidgetSettings()

    override suspend fun setWidgetSettings(settings: UsageWidgetSettings): Boolean =
        engine()?.setWidgetSettings(settings) == true

    override val defaultThreshold: Double
        get() = engine?.defaultThreshold ?: 80.0

    override val resetNotice: Boolean
        get() = engine?.notifyOnResetDefault ?: false

    override suspend fun setThreshold(key: String?, percent: Double?): Boolean {
        val owner = engine() ?: return false
        return if (key == null) percent?.let { owner.setDefaultThreshold(it) } ?: false
        else owner.setThresholdFor(key, percent)
    }

    override suspend fun setResetNotice(key: String?, value: Boolean): Boolean {
        val owner = engine() ?: return false
        return if (key == null) owner.setNotifyOnResetDefault(value)
        else owner.setNotifyOnReset(key, value)
    }

    override suspend fun refresh(force: Boolean) {
        engine()?.let { owner ->
            owner.setForeground(quotaPolicy.foreground.value)
            owner.refresh(force)
        }
        publishReaders()
    }

    private fun project(e: UsageSubscriptionsEngine) {
        mutablePreferences.value =
            PlanAlertPreferences(
                enabled = e.alertsOn,
                threshold = e.defaultThreshold.roundToInt(),
                notifyReset = e.notifyOnResetDefault,
                quietStart =
                    stores.prefs.long(QUIET_START)?.takeIf { it in 0..1439 }?.toInt(),
                quietEnd = stores.prefs.long(QUIET_END)?.takeIf { it in 0..1439 }?.toInt(),
                wifiOnly = stores.prefs.bool(WIFI) == true,
                widget = e.widgetSettings,
            )
    }

    private suspend fun save(write: suspend (UsageSubscriptionsEngine) -> Boolean): Outcome<Unit> {
        val e = engine() ?: return Failure.StorageRefused().failed()
        val saved = write(e)
        project(e)
        return if (saved) Unit.ok() else Failure.StorageRefused().failed()
    }

    override suspend fun setAlerts(on: Boolean): Outcome<Unit> = save { e ->
        e.setAlertsOn(on)
        e.alertsOn == on
    }

    override suspend fun setDefaultThreshold(level: Int): Outcome<Unit> = save {
        it.setDefaultThreshold(level.toDouble())
    }

    override suspend fun setDefaultReset(on: Boolean): Outcome<Unit> = save {
        it.setNotifyOnResetDefault(on)
    }

    override suspend fun setThreshold(key: String, level: Int?): Outcome<Unit> = save {
        if (it.subscription(key) == null) false else it.setThresholdFor(key, level?.toDouble())
    }

    override suspend fun setReset(key: String, on: Boolean): Outcome<Unit> = save {
        if (it.subscription(key) == null) false else it.setNotifyOnReset(key, on)
    }

    override suspend fun setQuiet(start: Int?, end: Int?): Outcome<Unit> = save {
        if (
            (start == null) != (end == null) ||
                (start != null && start !in 0..1439) ||
                (end != null && end !in 0..1439)
        )
            false
        else
            stores.prefs.commit(stores.lanes.app) {
                if (start == null || end == null) {
                    remove(QUIET_START)
                    remove(QUIET_END)
                } else {
                    putLong(QUIET_START, start.toLong())
                    putLong(QUIET_END, end.toLong())
                }
            } is Outcome.Ok
    }

    override suspend fun setWifiOnly(on: Boolean): Outcome<Unit> = save {
        stores.prefs.commit(stores.lanes.app) { putBool(WIFI, on) } is Outcome.Ok
    }

    override suspend fun setWidget(settings: UsageWidgetSettings): Outcome<Unit> = save {
        it.setWidgetSettings(settings)
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
                engine?.removeProfile(target.profile.raw, deleted = false)
            else engine?.removeProvider(target.profile.raw, target.provider)
        }
        return changed
    }

    override fun supported(profile: ProfileId): Boolean {
        val graph = registry.savedGraph(stores, profile) ?: return false
        return graph.session.server is ProviderUsageGateway
    }

    override fun state(profile: ProfileId): ProviderUsageMonitorState {
        val saved = monitorStates.value[profile]
        if (saved != null) return saved
        val monitor = explicitMonitor ?: return ProviderUsageMonitorState()
        return monitorState(profile, monitor)
    }

    private fun monitorState(
        profile: ProfileId,
        monitor: PaseoUsageMonitor,
    ): ProviderUsageMonitorState {
        val enabled =
            (stores.prefs.get(PaseoUsageMonitor.key(profile.raw))
                    as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Bool)
                ?.value == true
        return ProviderUsageMonitorState(
            enabled = enabled,
            status = if (enabled) monitor.statusFor(profile.raw) else PaseoUsageStatus.Off,
            lastReadAtMs = monitor.lastReadAt(profile.raw)?.toEpochMilliseconds(),
            providerCount = monitor.readingsFor(profile.raw)?.size ?: 0,
        )
    }

    private fun publishMonitorStates() {
        val monitor = explicitMonitor ?: return
        monitorStates.value =
            stores.profiles.profiles.value
                .filter { it.profile.backend == BackendKind.Paseo }
                .associate { it.id to monitorState(it.id, monitor) }
    }

    override suspend fun setEnabled(profile: ProfileId, enabled: Boolean): Outcome<Unit> {
        engine() ?: return Failure.Device().failed()
        if (!supported(profile)) return Failure.Unavailable(FeatureId("providerUsage")).failed()
        val monitor = explicitMonitor ?: return Failure.Device().failed()
        val before = state(profile)
        monitorStates.value =
            monitorStates.value +
                (profile to
                    before.copy(
                        enabled = enabled,
                        status = if (enabled) PaseoUsageStatus.Checking else PaseoUsageStatus.Off,
                    ))
        val saved = monitor.setEnabled(profile.raw, enabled)
        publishMonitorStates()
        return if (saved) Unit.ok() else Failure.StorageRefused().failed()
    }

    override suspend fun refresh(profile: ProfileId): Outcome<Unit> {
        engine() ?: return Failure.Device().failed()
        val monitor = explicitMonitor ?: return Failure.Device().failed()
        if (!supported(profile) || !state(profile).enabled)
            return Failure.Unavailable(FeatureId("providerUsage")).failed()
        monitorStates.value =
            monitorStates.value +
                (profile to state(profile).copy(status = PaseoUsageStatus.Checking))
        monitor.refresh()
        publishMonitorStates()
        return if (monitor.statusFor(profile.raw) == PaseoUsageStatus.Current) Unit.ok()
        else Failure.Network().failed()
    }

    override fun collectorRows(): List<PlanCollectorUi> = collectorUsage.collectorOptions()

    override fun alertsOn(): Boolean = collectorUsage.alertsOn()

    override fun canAlert(): Boolean = true

    override fun phoneSignInOwner(): ProfileId? = phoneUsage.signInOwner()

    override suspend fun setAlertsOn(enabled: Boolean): Outcome<Unit> {
        val result = collectorUsage.setAlertsOn(enabled)
        if (result is Outcome.Ok) engine()?.refresh()
        return result
    }

    override suspend fun setUpCollector(profile: ProfileId): Outcome<Unit> =
        collectorUsage.setUpInApp(profile)

    override suspend fun consentRemoteCollector(
        profile: ProfileId,
        enabled: Boolean,
    ): Outcome<Unit> = collectorUsage.consentRemote(profile, enabled)

    override suspend fun turnOffCollector(profile: ProfileId): Outcome<Unit> =
        collectorUsage.turnOff(profile)

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
        if (quota?.closeProfile(profile) == false) return false
        if (paseo?.removeProfile(profile) == false) return false
        // AGENT-2: the provider-usage monitors forget it too.
        if (phoneUsage.retire(profile)) {
            paseoMonitor?.removeProfile(UsageConnection.PHONE_ID)
            e.removeProfile(UsageConnection.PHONE_ID)
        }
        paseoMonitor?.removeProfile(profile.raw)
        explicitMonitor?.removeProfile(profile.raw)
        monitorStates.value = monitorStates.value - profile
        reader?.forget(profile.raw)
        val removed = e.removeProfile(profile.raw)
        publishReaders()
        project(e)
        return removed
    }

    private companion object {
        // The shared notification choices; QuotaCollectionPolicy reads the same keys.
        const val QUIET_START = "oc.notify.quietStart"
        const val QUIET_END = "oc.notify.quietEnd"
        const val WIFI = "oc.notify.wifiOnly"
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object PlansPreferenceBindings {
    @Provides fun preferences(source: AndroidPlansSource): PlansPreferences = source
}

@ContributesTo(AppScope::class)
interface PlansGraph {
    val plansSource: AndroidPlansSource
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

/**
 * B-STATE-146/147: budget owners share locks and profile lanes across replacement Usage screens.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(
    AppScope::class,
    binding = binding<io.github.eslamasabry.oc.feature.usage.presentation.UsageBudgetAccess>(),
)
class AndroidUsageBudgets(private val stores: PreviewStores) :
    io.github.eslamasabry.oc.feature.usage.presentation.UsageBudgetAccess {
    private val locks = io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetLocks()

    override suspend fun open(
        profile: ProfileId
    ): io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgets? {
        if (stores.warm() !is Outcome.Ok) return null
        val saved = stores.profiles.profile(profile)?.profile ?: return null
        return io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgets(
            stores.prefs,
            stores.lanes.forProfile(profile),
            locks,
            profile.raw,
            // Dart hashes '$origin\n$username' (usage_screen.dart), so Flutter-saved budgets match.
            "${saved.baseUrl}\n${saved.username}",
            isCurrent = {
                val now = stores.profiles.profile(profile)?.profile
                now?.baseUrl == saved.baseUrl && now.username == saved.username
            },
            isProfilePresent = { stores.profiles.profile(profile) != null },
        )
    }
}

/**
 * Kept separate from the Plans binding so both product surfaces share the same explicit monitor
 * owner.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ProviderUsageMonitorSettings>())
@Inject
class AndroidProviderUsageMonitorSettings(private val plans: AndroidPlansSource) :
    ProviderUsageMonitorSettings by plans
