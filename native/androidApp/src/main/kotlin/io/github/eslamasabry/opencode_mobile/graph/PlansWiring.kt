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
import io.github.eslamasabry.oc.core.data.kv.bool
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
 * Additional readers use their saved consent and existing authenticated transport; they never change the selected
 * connection or obtain credentials from provider configuration.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlansSource>())
@Inject
class AndroidPlansSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val application: Application,
    private val clock: Clock,
    private val additional: AndroidAdditionalUsageSources,
    appScope: AppCoroutineScope,
) : PlansSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val state = MutableStateFlow(UsageSnapshot())
    override val snapshot: StateFlow<UsageSnapshot> = state.asStateFlow()
    private val creating = Mutex()
    private var engine: UsageSubscriptionsEngine? = null

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
                        } + additional.connections())
                        .distinctBy { it.id }
                },
                sources = listOf(signIns, additional),
                alertBridge = additional.alertBridge,
                alertsOnRule = { stores.prefs.bool("oc.notify.quotaAlerts") == true },
                setQuotaAlerts = { enabled ->
                    stores.prefs.commit(stores.lanes.app) {
                        putBool("oc.notify.quotaAlerts", enabled)
                    }
                },
                widget = widget,
                clock = { clock.now() },
            )
            .also { created ->
                engine = created
                scope.launch { created.snapshot.collect { state.value = it } }
            }
    }

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

    override suspend fun refresh() {
        engine()?.refresh()
    }

    /**
     * B-SEC-178: a removed server takes its cached numbers, its overrides and its widget rows
     * along.
     */
    suspend fun removeProfile(profile: ProfileId): Boolean {
        val e = engine() ?: return false
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
