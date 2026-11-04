package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.*
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
import io.github.eslamasabry.oc.feature.plans.presentation.ComputerReaderUi
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
 * Not wired yet (engines exist, adapters do not): the in-app Ubuntu quota collector (`:engine:integrations-quota`
 * needs an OkHttp QuotaTransport), Paseo/phone usage lists (PaseoUsageMonitor needs the Paseo usage call) and the
 * alert delivery bridge.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlansSource>())
@Inject
class AndroidPlansSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val application: Application,
    private val clock: Clock,
    private val dispatchers: io.github.eslamasabry.oc.core.common.AppDispatchers,
    appScope: AppCoroutineScope,
) : PlansSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val state = MutableStateFlow(UsageSnapshot())
    override val snapshot: StateFlow<UsageSnapshot> = state.asStateFlow()
    private val creating = Mutex()
    private var engine: UsageSubscriptionsEngine? = null
    private var reader: ComputerUsageSource? = null
    private val readers = MutableStateFlow<List<ComputerReaderUi>>(emptyList())
    override val computerReaders: StateFlow<List<ComputerReaderUi>> = readers.asStateFlow()

    private fun publishReaders() {
        val source = reader ?: return
        readers.value =
            openCodeServers()
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

            override suspend fun remove(id: String): Outcome<Unit> =
                if (!current()) Failure.Refused(RefusalReason.StaleScope).failed()
                else
                    operations?.removeTerminal(id)
                        ?: io.github.eslamasabry.oc.core.domain.unavailable("terminal")

            override fun close() {
                closed = true
            }
        }
    }

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
        stores.profileScope.register(
            KeyShape(
                "oc.usageComputerReader.",
                KeyScope.Profile,
                PrefType.Bool,
                ":engine:integrations-usage",
                "Explicit computer usage reader consent",
            )
        )
        val computer =
            ComputerUsageSource(
                stores.prefs,
                stores.lanes.app,
                servers = {
                    openCodeServers().map { (id, graph) ->
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
                    }
                },
                sources = listOf(signIns, computer),
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
        publishReaders()
    }

    /**
     * B-SEC-178: a removed server takes its cached numbers, its overrides and its widget rows
     * along.
     */
    suspend fun removeProfile(profile: ProfileId): Boolean {
        val e = engine() ?: return false
        reader?.forget(profile.raw)
        e.removeProfile(profile.raw)
        publishReaders()
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
