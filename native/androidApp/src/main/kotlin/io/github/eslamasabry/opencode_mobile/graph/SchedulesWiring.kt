package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.schedules.ScheduleExactAlarms
import io.github.eslamasabry.oc.android.schedules.ScheduleNotifications
import io.github.eslamasabry.oc.android.schedules.ScheduleWorkPolicy
import io.github.eslamasabry.oc.android.schedules.WorkScheduleAlarms
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.supervisedContext
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.StoredProfileJson
import io.github.eslamasabry.oc.engine.schedules.PrefsScheduleBackend
import io.github.eslamasabry.oc.engine.schedules.ScheduleConnection
import io.github.eslamasabry.oc.engine.schedules.ScheduleEngine
import io.github.eslamasabry.oc.engine.schedules.ScheduleProfiles
import io.github.eslamasabry.oc.engine.schedules.ScheduledOpen
import io.github.eslamasabry.oc.feature.schedules.presentation.ConversationSeed
import io.github.eslamasabry.oc.feature.schedules.presentation.ModelChoice
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleServers
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleTarget
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute
import io.github.eslamasabry.opencode_mobile.MainActivity
import io.github.eslamasabry.opencode_mobile.ScheduledPromptReceiver as FlutterScheduleReceiver
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/*
 * P12-SCHEDULES Android wiring: the engine's ports over the preview stores and connection registry, the WorkManager
 * worker, the notification trampoline and the start-up re-arm. Everything else lives in :engine:schedules,
 * :android:schedules and :feature:schedules. Frozen-name note: the Flutter worker and receiver
 * (`io.github.eslamasabry.opencode_mobile.ScheduledPromptWorker` / `ScheduledPromptReceiver`) exist again in
 * FlutterSchedules.kt and only re-arm; native work uses new unique names (`oc.nschedule.*`).
 */

/** What OS components (worker, trampoline) need from the graph; Metro merges it into AndroidAppGraph. */
@ContributesTo(AppScope::class)
interface SchedulesGraphAccess {
    val schedules: ScheduleEngine
    val schedulesNavigator: AppNavigator
    val schedulesClock: Clock
    val scheduleAlarms: WorkScheduleAlarms
}

@BindingContainer
@ContributesTo(AppScope::class)
object SchedulesBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun alarms(application: Application, clock: Clock): WorkScheduleAlarms =
        WorkScheduleAlarms(
            application,
            ScheduledPromptWorker::class.java,
            { clock.now().toEpochMilliseconds() },
            ScheduleExactAlarms(application, FlutterScheduleReceiver::class.java),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun engine(
        application: Application,
        stores: PreviewStores,
        profiles: AppScheduleProfiles,
        clock: Clock,
        alarms: WorkScheduleAlarms,
    ): ScheduleEngine {
        val now = { clock.now().toEpochMilliseconds() }
        return ScheduleEngine(
            backend = PrefsScheduleBackend(stores.prefs, stores.lanes),
            profiles = profiles,
            alarms = alarms,
            notices = ScheduleNotifications(application, ScheduleOpenActivity::class.java),
            nowMs = now,
        )
    }
}

/** Saved servers as schedules need them: the server identity fingerprint and a gateway for one run. */
@SingleIn(AppScope::class)
@Inject
class AppScheduleProfiles(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : ScheduleProfiles {
    override suspend fun ids(): List<String> {
        stores.warm()
        return stores.profiles.profiles.value.map { it.id.raw }
    }

    override suspend fun fingerprint(profileId: String): String? {
        stores.warm()
        val server = stores.profiles.profile(ProfileId(profileId))?.toServerProfile() ?: return null
        // Everything that names the server and the account, never the display name or a secret.
        return ScheduleEngine.fingerprint(
            listOf(
                server.backend.name, server.origin.toString(), server.username,
                server.managedOnPhone.toString(), server.trust.pinnedLeafSha256, server.trust.cleartextConsentAtMs?.toString(),
            ),
        )
    }

    /** Flutter `scheduledProfileFingerprint`: SHA-256 of the Dart `toJson()` of the saved profile without `name`. */
    override suspend fun legacyFingerprint(profileId: String): String? {
        stores.warm()
        val stored = stores.profiles.profile(ProfileId(profileId))?.profile ?: return null
        val row = StoredProfileJson.encode(stored).apply { remove("name") }
        return DartJson.sha256Hex(DartJson.encode(row))
    }

    /**
     * A private connection pinned to the schedule's project (Flutter `ScheduledAgentConnection`; B-AGENT-216/218): the
     * app's own connection may be looking at another project, and Paseo creates conversations in the pinned one. The
     * run closes exactly this connection; the person's connection is never touched.
     */
    override suspend fun open(profileId: String, projectPath: String, workspace: String?): Outcome<ScheduleConnection> {
        stores.warm()
        val loaded = stores.profiles.profile(ProfileId(profileId)) ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        if (loaded.cleartextUnconfirmed) return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val server = loaded.toServerProfile() ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val factory =
            factories.firstOrNull { it.backend == server.backend }
                ?: return Failure.Unavailable(FeatureId("backend.${server.backend}")).failed()
        val credentials =
            when (val read = credentialSources.singleOrNull()?.resolve(server)) {
                null -> ConnectionCredentials(null, null)
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        val scope = ConnectionCoroutineScope(supervisedContext(appScope.coroutineContext[Job], dispatchers.default, "schedule-run"))
        val location = ServerLocation(projectPath, workspace)
        return when (val pair = factory.connect(GatewaySpec(server, credentials, scope, location))) {
            is Outcome.Failed -> {
                scope.coroutineContext[Job]?.cancelAndJoin()
                pair
            }
            is Outcome.Ok -> {
                val gateway = pair.value.live
                if (gateway.location.value != location) gateway.setLocation(location)
                ScheduleConnection(gateway) {
                    gateway.close()
                    scope.coroutineContext[Job]?.cancelAndJoin()
                }.ok()
            }
        }
    }

    internal suspend fun gateway(id: ProfileId): Outcome<ServerGateway> {
        stores.warm()
        registry.live.value[id]?.let { return it.session.server.ok() }
        val loaded = stores.profiles.profile(id) ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        if (loaded.cleartextUnconfirmed) return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val server = loaded.toServerProfile() ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        return when (val opened = registry.open(server)) {
            is Outcome.Ok -> opened.value.session.server.ok()
            is Outcome.Failed -> opened
        }
    }
}

/** The server facts the schedule screens show (capability, the newest project, models, a conversation's seed). */
@ContributesBinding(AppScope::class)
@Inject
class AppScheduleServers(private val stores: PreviewStores, private val profiles: AppScheduleProfiles) : ScheduleServers {
    override suspend fun target(profileId: ProfileId): ScheduleTarget {
        stores.warm()
        val name = stores.profiles.profile(profileId)?.profile?.name
        return when (val gateway = profiles.gateway(profileId)) {
            // Offline: the saved schedules still list and edit; runs report the server as unreachable.
            is Outcome.Failed -> ScheduleTarget(name, available = name != null, recentProject = null)
            is Outcome.Ok -> {
                val server = gateway.value
                val recent = (server.sessionPage(limit = 1) as? Outcome.Ok)?.value?.items?.firstOrNull()?.directory
                ScheduleTarget(name, server.capabilities.value.scheduledPrompts, recent)
            }
        }
    }

    override suspend fun models(profileId: ProfileId): List<ModelChoice> {
        val server = (profiles.gateway(profileId) as? Outcome.Ok)?.value ?: return emptyList()
        val providers = (server.configuredProviders() as? Outcome.Ok)?.value ?: return emptyList()
        return providers.providers
            .flatMap { provider -> provider.modelIds.map { ModelChoice(provider.id, it, "${provider.name} · $it") } }
            .take(MAX_MODELS)
    }

    override suspend fun conversation(ref: ConversationRef): ConversationSeed? {
        val server = (profiles.gateway(ref.profileId) as? Outcome.Ok)?.value ?: return null
        val session = (server.session(ref.sessionId) as? Outcome.Ok)?.value ?: return null
        val model = session.selection?.model
        return ConversationSeed(session.directory, session.title, model?.providerId, model?.modelId, session.selection?.agent ?: session.agent)
    }

    private companion object {
        const val MAX_MODELS = 40
    }
}

/**
 * Re-arms every saved schedule when the app's UI first starts (after boot, an update or the Flutter → native
 * update), and cancels the work of a removed server. WorkManager keeps armed work across reboots on its own.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
class SchedulesBoot(engine: ScheduleEngine, stores: PreviewStores, appScope: AppCoroutineScope) : EntryInstaller {
    init {
        appScope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            engine.restoreAll()
            var known = stores.profiles.profiles.value.map { it.id.raw }.toSet()
            stores.profiles.profiles.collect { list ->
                val now = list.map { it.id.raw }.toSet()
                (known - now).forEach(engine::forgetProfile)
                known = now
            }
        }
    }

    override fun EntryProviderScope<NavKey>.install() = Unit
}

/** The WorkManager worker for timed and "Run now" runs. Always succeeds: a run is never retried by the OS. */
class ScheduledPromptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph as SchedulesGraphAccess
        val report = ScheduleWorkPolicy.run(graph.schedules, inputData, graph.schedulesClock.now().toEpochMilliseconds())
        AppLog.tagged("Schedules").i { "run ended: ${report?.status ?: "nothing to do"}" }
        return Result.success()
    }
}

/** The notification tap (B-NAV-054): the run's conversation, else the schedule's history, else nothing. */
class ScheduleOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = intent.getStringExtra(ScheduleNotifications.EXTRA_PROFILE).orEmpty()
        val schedule = intent.getStringExtra(ScheduleNotifications.EXTRA_SCHEDULE).orEmpty()
        val session = intent.getStringExtra(ScheduleNotifications.EXTRA_SESSION)
        val graph = appGraph
        val access = graph as SchedulesGraphAccess
        val main = Handler(Looper.getMainLooper())
        graph.appScope.launch {
            val target = access.schedules.resolveOpen(profile, schedule, session)
            main.post {
                when (target) {
                    is ScheduledOpen.Conversation ->
                        access.schedulesNavigator.openChat(ConversationRef(ProfileId(target.profileId), SessionId(target.sessionId)))
                    is ScheduledOpen.History ->
                        access.schedulesNavigator.open(SchedulesRoute.List(ProfileId(target.profileId), highlight = target.scheduleId))
                    null -> Unit // a deleted schedule or a changed server opens nothing but the app
                }
            }
        }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
