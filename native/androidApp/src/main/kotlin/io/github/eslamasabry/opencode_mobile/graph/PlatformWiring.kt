package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKeys
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertPolicy
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AndroidAlertShade
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.android.notifications.DeviceLock
import io.github.eslamasabry.oc.android.notifications.FrozenExtras
import io.github.eslamasabry.oc.android.notifications.NotificationActionBody
import io.github.eslamasabry.oc.android.notifications.NotificationText
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.android.notifications.QuestionShape
import io.github.eslamasabry.oc.android.notifications.ReplyStopper
import io.github.eslamasabry.oc.android.notifications.ShadeAnswer
import io.github.eslamasabry.oc.android.notifications.ShadeAnswerer
import io.github.eslamasabry.oc.android.notifications.ShadeReply
import io.github.eslamasabry.oc.android.tiles.AttentionTileCache
import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.oc.android.tiles.PinnedSession
import io.github.eslamasabry.oc.android.tiles.PinnedShortcuts
import io.github.eslamasabry.oc.android.tiles.SharedTextIntake
import io.github.eslamasabry.oc.android.tiles.ShortcutClear
import io.github.eslamasabry.oc.android.tiles.updates.AndroidUpdateFeed
import io.github.eslamasabry.oc.android.tiles.updates.AndroidUpdatePlatform
import io.github.eslamasabry.oc.android.tiles.updates.PrefsUpdateStore
import io.github.eslamasabry.oc.android.widgets.AndroidWidgetSurface
import io.github.eslamasabry.oc.android.widgets.PublishResult
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetPersistence
import io.github.eslamasabry.oc.android.widgets.WidgetPresentation
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.attention.identity.RunIdentity
import io.github.eslamasabry.oc.engine.attention.identity.RunMessage
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.entry.EntryLinkParser
import io.github.eslamasabry.oc.engine.entry.ParsedLink
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.AppUpdates
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.NoUpdateDoor
import io.github.eslamasabry.oc.feature.chats.presentation.UpdateDoor
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFailure as SettingsUpdateFailure
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateSettings
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState as SettingsUpdateState
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateLinks
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.PlansRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.UpdatesRoute
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

/*
 * Unit P12-PLATFORM: the Android platform pieces made live in the preview app. Notifications (channels, permission /
 * question alerts with Allow once / Reject from the shade, finished-reply alerts), both home-screen widget families
 * fed from the real conversation feed, the Quick Settings attention tile count, launcher / pinned shortcuts, share
 * into the app and the in-app update notice. Report: docs/native/units/P12-PLATFORM.md.
 */

@BindingContainer
@ContributesTo(AppScope::class)
object PlatformBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun widgetPersistence(
        stores: PreviewStores,
        appScope: AppCoroutineScope,
        clock: Clock,
        profileScope: ProfileScope,
    ): WidgetPersistence =
        WidgetPersistence(
            stores.prefs,
            stores.lanes,
            profileScope,
            appScope,
            clock,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun appUpdates(
        application: Application,
        dispatchers: AppDispatchers,
        clock: Clock,
        build: AppBuildInfo,
    ): AppUpdates =
        AppUpdates(
            AndroidUpdateFeed(dispatchers.io, userAgent = "OpenCodeMobile/${build.versionName}"),
            AndroidUpdatePlatform(application, dispatchers.io),
            PrefsUpdateStore(application, dispatchers.io),
            clock,
            supported = true,
        )
}

/** OS components (receivers, the Activity) reach the platform hub through the graph. */
@ContributesTo(AppScope::class)
interface PlatformAccessors {
    val platform: PlatformHub
}

internal val Context.platformHub: PlatformHub
    get() = (appGraph as PlatformAccessors).platform

/**
 * The Flutter gear dot: Update shows in the Conversations bar while a newer release waits, and
 * opens Updates.
 */
@ContributesBinding(AppScope::class, replaces = [NoUpdateDoor::class])
@Inject
class AppUpdateDoor(
    updates: AppUpdates,
    private val navigator: AppNavigator,
    appScope: AppCoroutineScope,
) : UpdateDoor {
    override val ready: StateFlow<Boolean> =
        updates.state
            .map { it.hasPendingRelease }
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, updates.state.value.hasPendingRelease)

    override fun open() {
        navigator.open(UpdatesRoute.Home)
    }
}

/**
 * The release page of a waiting update (Open on GitHub after a signer / package / version refusal).
 * The address comes from the network, so it must be this project's GitHub release page and pass
 * ExternalLinkPolicy (AGENTS.md).
 */
@ContributesBinding(AppScope::class)
@Inject
class ReleasePageLinks(private val opener: ExternalLinkOpener) : UpdateLinks {
    private val policy = ExternalLinkPolicy()

    override suspend fun openReleasePage(url: String): Outcome<Unit> {
        val link =
            PlatformRules.releaseLink(policy, url)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        // The app's one opener: browsable intents only, security failures become a Failure (AGENTS
        // rule 8).
        return opener.open(link)
    }
}

/**
 * Settings › Updates on the real updater (B-MIGR-012): Check now and the automatic-check switch are
 * reachable with no release waiting, and after a failed first check. The page's one main action
 * downloads, cancels or installs.
 */
@SingleIn(AppScope::class)
@Inject
class AppUpdateSettings(
    private val updates: AppUpdates,
    build: AppBuildInfo,
    appScope: AppCoroutineScope,
) : UpdateSettings {
    private val format =
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
        )

    override val facts: StateFlow<UpdateFacts> =
        updates.state
            .map {
                PlatformRules.updateFacts(it, build) { ms -> format.format(java.util.Date(ms)) }
            }
            .stateIn(
                appScope,
                SharingStarted.Eagerly,
                PlatformRules.updateFacts(updates.state.value, build) { "" },
            )

    override suspend fun checkNow(): Outcome<Unit> = updates.checkNow()

    override suspend fun setChecks(on: Boolean): Outcome<Unit> = updates.setEnabled(on)

    override suspend fun act(): Outcome<Unit> =
        when (updates.state.value.phase) {
            UpdatePhase.Downloading,
            UpdatePhase.Verifying -> Outcome.Ok(updates.cancel())
            UpdatePhase.Available,
            UpdatePhase.ReadyToInstall,
            UpdatePhase.Failed -> updates.updateNow()
            else -> Outcome.Ok(Unit)
        }
}

/** One pending request with where it came from. */
internal data class Waiting(val profile: ProfileId, val request: PendingRequest)

@SingleIn(AppScope::class)
@Inject
class PlatformHub(
    private val application: Application,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val clock: Clock,
    private val updates: AppUpdates,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val navigator: AppNavigator,
    private val draftOwners: Set<ConversationDrafts>,
    private val appPreferences: AppPreferences,
    private val monitoring: AndroidProfileMonitoring,
    private val sharedProjectFolders: NativeSharedProjectFolders,
    private val widgetRunner: NativeWidgetRunner,
    private val widgetPersistence: WidgetPersistence,
    private val redactor: Redactor,
    private val background: BackgroundHub,
) {
    private val mainDispatcher = dispatchers.main
    private val appScope = CoroutineScope(appScope.coroutineContext + mainDispatcher)
    private val log = AppLog.tagged("Platform")
    private val prefs = application.getSharedPreferences("oc_platform", Context.MODE_PRIVATE)
    // P12-FIX-SETTINGS: alerts speak the language chosen in Settings, not only the phone's
    // (B-L10N-013).
    private val words = NotifyWords(application) { appPreferences.localeTag.value }
    private val channels =
        ChannelRegistry(application.getSystemService(NotificationManager::class.java), words)
    val shade = AndroidAlertShade(application, words, channels)
    private val widgets = AndroidWidgetSurface(application, widgetPersistence)
    private val widgetPresentation = WidgetPresentation(application, redactor)
    private val widgetSafe = io.github.eslamasabry.oc.engine.attention.widget.WidgetText(redactor)

    @Volatile private var started = 0
    private val visible = MutableStateFlow(0)
    private val alerted = mutableMapOf<String, String>() // alert key -> request id (or turn marker)
    private val running = mutableMapOf<ConversationRef, Boolean>()
    private val runEvents =
        mutableMapOf<ConversationRef, io.github.eslamasabry.oc.core.domain.events.RunOutcome>()
    private val runWatchers = mutableMapOf<ProfileId, kotlinx.coroutines.Job>()
    private val eventStarted = mutableSetOf<ConversationRef>()
    private val endedKinds = mutableMapOf<ConversationRef, AlertKind>()
    private val endedReasons =
        mutableMapOf<ConversationRef, io.github.eslamasabry.oc.android.notifications.StopReason>()
    private var lastWaiting: List<Waiting> = emptyList()
    private var wasVisible = false
    private val statusPosted =
        mutableSetOf<String>() // finished / stopped alerts still in the shade
    private var knownProfiles: Set<ProfileId> = emptySet()
    private val share = ShareSeed()
    private var widgetModel: WidgetModel? = null
    private var widgetBaseModel: WidgetModel? = null
    private var widgetStampMs = 0L
    private val serverSince = mutableMapOf<String, Pair<Boolean, Long>>()
    private var publishedPins: Pair<String, List<PinnedSession>>? = null

    /** Answers a shade tap on the exact pending request of the live connection (plan 11 §10.6). */
    val actionBody: NotificationActionBody =
        NotificationActionBody(
            shade = shade,
            answerer =
                io.github.eslamasabry.oc.android.notifications.GuardedShadeAnswerer(
                    questionOf = { answer ->
                        val pending =
                            (registry.live.value[answer.profile] as? ChatGraphAccessors)
                                ?.chatRequests
                                ?.engine
                                ?.pending
                                ?.value
                        val question =
                            pending
                                ?.filterIsInstance<PendingRequest.Question>()
                                ?.firstOrNull {
                                    it.id == answer.request && it.sessionId == answer.session
                                }
                                ?.question
                        question?.let { q ->
                            io.github.eslamasabry.oc.android.notifications.QuestionShape(
                                q.prompts.map {
                                    io.github.eslamasabry.oc.android.notifications.QuestionShape
                                        .Prompt(
                                            it.choices.map { c -> c.label },
                                            it.multiple,
                                            it.custom,
                                        )
                                }
                            )
                        }
                    },
                    delegate = ShadeAnswerer { answer -> answer(answer) },
                ),
            stopper =
                ReplyStopper { ref ->
                    val graph = registry.live.value[ref.profileId]
                    val stillRunning =
                        runtime.snapshot.value.items.any {
                            // A run waiting on a permission or question is still a run to stop.
                            it.ref == ref &&
                                (it.status == ChatStatus.Running ||
                                    it.status == ChatStatus.NeedsYou)
                        }
                    if (graph == null || !stillRunning) Outcome.Failed(Failure.NotFound())
                    else graph.session.server.stop(ref.sessionId).map { Unit }
                },
            lock =
                object : DeviceLock {
                    override val sdkInt: Int = Build.VERSION.SDK_INT

                    override fun isLocked(): Boolean =
                        application.getSystemService(KeyguardManager::class.java)?.isDeviceLocked ==
                            true
                },
        )

    /** Called once from OcApplication.onCreate, before any OS component runs. */
    fun start() {
        if (started++ > 0) return
        WidgetFeed.persistence = widgetPersistence
        WidgetFeed.backgroundRunner = widgetRunner
        sharedProjectFolders.start()
        monitoring.start()
        channels.ensure()
        // B-L10N-002: channel names follow an acknowledged language save (the stored value, never
        // an attempt).
        appScope.launch { appPreferences.storedLocaleTag.collect { channels.relocalize() } }
        application.registerActivityLifecycleCallbacks(Visibility())
        appScope.launch { updates.start() }
        appScope.launch { watchFeed() }
        appScope.launch { watchRunEvents() }
        appScope.launch { watchProfiles() }
        // A pin or active-server change need not change any feed row. Observe the acknowledged
        // preference save too.
        appScope.launch {
            stores.prefs.changes.collect { keys ->
                if (keys.any { it.startsWith(PINS_PREFIX) }) publishPins(runtime.snapshot.value)
            }
        }
        appScope.launch {
            stores.profiles.activeId.collect { publishPins(runtime.snapshot.value) }
        }
        appScope.launch { navigator.top.collect { top -> seedShare(top) } }
    }

    val scope: CoroutineScope
        get() = appScope

    val appLocaleTag: String?
        get() = appPreferences.localeTag.value

    // ---- Activity
    // -------------------------------------------------------------------------------------------------

    /**
     * Every resume: continue an install that waited for permission, check for an update when due.
     */
    fun onResumed(activity: Activity) {
        appScope.launch { updates.onResumed() }
    }

    /**
     * A launcher shortcut, pinned conversation, notification tap or share. Each extra is consumed
     * once.
     */
    fun route(intent: Intent?) {
        if (intent == null) return
        if (monitoring.route(intent)) return
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            // B-ANDR-072: the one VIEW filter. Consumed once; route identifiers only.
            val raw = intent.dataString
            intent.data = null
            when (val link = raw?.let(EntryLinkParser::parse)) {
                is ParsedLink.Session -> openSessionLink(link)
                // AI Team links: the team target is deferred under D6, so they land on the list.
                is ParsedLink.TeamGate,
                is ParsedLink.TeamRun -> navigator.land()
                null -> Unit
            }
            return
        }
        val share = SharedTextIntake.consume(intent)
        if (share != null) {
            this.share.arrived(share.text, runtime.snapshot.value.items.map { it.ref }.toSet())
            navigator.open(ChatsRoute.New(profile = stores.profiles.activeId.value))
            return
        }
        LaunchActions.consumeSessionShortcut(intent)?.let { tap ->
            val profile = ProfileId(tap.profileId)
            if (
                profile == stores.profiles.activeId.value &&
                    stores.profiles.profile(profile) != null
            ) {
                navigator.openChat(ConversationRef(profile, SessionId(tap.sessionId)))
            }
            return
        }
        alertTap(intent)?.let { ref ->
            shade.dismiss(AlertKeys.status(ref.profileId.raw, ref.sessionId.raw))
            navigator.openChat(ref)
            return
        }
        when (val action = LaunchActions.consume(intent)) {
            LaunchAction.PhoneSetup,
            LaunchAction.PhoneSetupDone ->
                when (
                    val tap = SetupTapRoutes.routeFor(action, navigator.top.value)
                ) { // B-ANDR-050
                    is SetupTap.Open -> navigator.open(tap.route)
                    SetupTap.Stay -> Unit
                    SetupTap.Land,
                    SetupTap.NotSetup -> navigator.land()
                }
            LaunchAction.Connect -> navigator.open(ServersRoute.Home)
            LaunchAction.NewTask ->
                navigator.open(ChatsRoute.New(profile = stores.profiles.activeId.value))
            LaunchAction.Usage,
            LaunchAction.UsageSettings,
            is LaunchAction.UsageTarget -> navigator.open(PlansRoute)
            null -> Unit
            // Activity, background and the other Flutter actions land on Conversations (needs-you
            // rows lead).
            else -> navigator.land()
        }
    }

    /**
     * Flutter `_openSessionForLink`: a saved server opens that exact session (connecting first);
     * an unknown server, one that needs its secret again, or a failed connection shows Servers.
     * Nothing is sent, created or resumed.
     */
    private fun openSessionLink(link: ParsedLink.Session) {
        val saved = stores.profiles.profile(link.profileId)
        if (saved == null || saved.reentry.password || saved.reentry.agentToken) {
            navigator.open(ServersRoute.Home)
            return
        }
        appScope.launch {
            if (runtime.connect(link.profileId) !is Outcome.Ok) {
                navigator.open(ServersRoute.Home)
                return@launch
            }
            navigator.openChat(ConversationRef(link.profileId, link.sessionId))
        }
    }

    private fun alertTap(intent: Intent): ConversationRef? {
        val session =
            intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID)?.takeIf {
                it.isNotBlank()
            }
        val profile =
            intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)?.takeIf {
                it.isNotBlank()
            }
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID)
        if (session == null || profile == null) return null
        return ConversationRef(ProfileId(profile), SessionId(session))
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private inner class Visibility : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            visible.value += 1
            monitoring.visible(visible.value > 0)
        }

        override fun onActivityStopped(activity: Activity) {
            visible.value = (visible.value - 1).coerceAtLeast(0)
            monitoring.visible(visible.value > 0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    // ---- Share into a new conversation
    // ----------------------------------------------------------------------------

    /**
     * The shared words become the draft of the new conversation made for them (07 §7.7); kept until
     * saved.
     */
    private fun seedShare(top: AppRoute?) {
        val ref = share.target(top) ?: return
        val text = share.text ?: return
        val owner = draftOwners.firstOrNull() ?: return share.notSaved(ref)
        appScope.launch {
            val session = owner.open(ref)
            val ready = withTimeoutOrNull(SEED_WAIT_MS) { session.state.first { it.ready } }
            if (ready == null) return@launch share.notSaved(ref)
            session.changed(share.merged(ready.text, text))
            if (session.flush() is Outcome.Ok) share.saved(ref) else share.notSaved(ref)
        }
    }

    /**
     * Every alert this app put in the shade goes; requests already alerted are not posted again.
     */
    private fun clearShade() {
        for (key in alerted.keys) shade.dismiss(key)
        for (key in statusPosted) shade.dismiss(key)
        statusPosted.clear()
        alerted.clear()
    }

    // ---- Feed: alerts, widgets, tile
    // ------------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pendingEverywhere(): Flow<List<Waiting>> =
        registry.live.flatMapLatest { live ->
            val flows =
                live.entries.mapNotNull { (id, graph) ->
                    requestsOf(graph)?.map { list -> list.map { Waiting(id, it) } }
                }
            if (flows.isEmpty()) flowOf(emptyList())
            else combine(flows) { parts -> parts.toList().flatten() }
        }

    private fun requestsOf(graph: ConnectionGraph): Flow<List<PendingRequest>>? =
        (graph as? ChatGraphAccessors)?.chatRequests?.engine?.pending

    private suspend fun watchFeed() {
        combine(runtime.snapshot, pendingEverywhere(), visible) { feed, waiting, shown ->
                Triple(feed, waiting, shown > 0)
            }
            .conflate()
            .collect { (feed, waiting, appVisible) ->
                publishTile(waiting)
                publishPins(feed)
                alerts(feed, waiting, appVisible)
                // Last: its bounded detail reads (up to four) must never hold back an alert.
                publishWidgets(feed, waiting)
            }
    }

    /**
     * End events and polled state share one run identity: late errors replace a completion in
     * place.
     */
    private suspend fun watchRunEvents() {
        registry.live.collect { live ->
            for (id in (runWatchers.keys - live.keys).toList()) {
                runWatchers.remove(id)?.cancel()
                runEvents.keys.removeAll { it.profileId == id }
                eventStarted.removeAll { it.profileId == id }
            }
            for ((id, graph) in live) if (id !in runWatchers)
                runWatchers[id] = appScope.launch {
                    graph.session.server.events.all.collect { envelope ->
                        if (registry.live.value[id] !== graph) return@collect
                        when (val event = envelope.event) {
                            is io.github.eslamasabry.oc.core.domain.events.GatewayEvent.RunStarted -> {
                                val ref = ConversationRef(id, event.sessionId)
                                eventStarted += ref
                                endedKinds.remove(ref)
                                endedReasons.remove(ref)
                                runEvents.remove(ref)
                                shade.dismiss(AlertKeys.status(id.raw, event.sessionId.raw))
                            }
                            is io.github.eslamasabry.oc.core.domain.events.GatewayEvent.RunEnded -> {
                                val ref = ConversationRef(id, event.sessionId)
                                val prior = runEvents[ref]
                                if (
                                    prior
                                        !is
                                        io.github.eslamasabry.oc.core.domain.events.RunOutcome.Failed
                                )
                                    runEvents[ref] = event.outcome
                                val failed =
                                    event.outcome
                                        as?
                                        io.github.eslamasabry.oc.core.domain.events.RunOutcome.Failed
                                if (failed != null)
                                    endedReasons[ref] =
                                        when (failed.kind) {
                                            io.github.eslamasabry.oc.core.model.transcript
                                                .MessageErrorKind
                                                .ProviderAuth ->
                                                io.github.eslamasabry.oc.android.notifications
                                                    .StopReason
                                                    .Auth
                                            io.github.eslamasabry.oc.core.model.transcript
                                                .MessageErrorKind
                                                .ContextOverflow ->
                                                io.github.eslamasabry.oc.android.notifications
                                                    .StopReason
                                                    .Context
                                            io.github.eslamasabry.oc.core.model.transcript
                                                .MessageErrorKind
                                                .OutputLength ->
                                                io.github.eslamasabry.oc.android.notifications
                                                    .StopReason
                                                    .Length
                                            io.github.eslamasabry.oc.core.model.transcript
                                                .MessageErrorKind
                                                .ContentFilter ->
                                                io.github.eslamasabry.oc.android.notifications
                                                    .StopReason
                                                    .Filter
                                            io.github.eslamasabry.oc.core.model.transcript
                                                .MessageErrorKind
                                                .ModelNotFound ->
                                                io.github.eslamasabry.oc.android.notifications
                                                    .StopReason
                                                    .Model
                                            else ->
                                                io.github.eslamasabry.oc.android.notifications
                                                    .StopReason
                                                    .Other
                                        }
                                alerts(runtime.snapshot.value, lastWaiting, visible.value > 0)
                            }
                            else -> Unit
                        }
                    }
                }
        }
    }

    private suspend fun watchProfiles() {
        stores.profiles.profiles.collect { list ->
            val now = list.map { it.id }.toSet()
            val gone = knownProfiles - now
            knownProfiles = now
            for (id in gone) {
                // Deletion leaves no launcher shortcut, tile count or widget rows of that server
                // behind (B-STATE-063).
                AttentionTileCache.clearOwner(application, id.raw)
                PinnedShortcuts.withdrawProfile(application, id.raw)
                clearProfile(id)
            }
        }
    }

    private fun publishTile(waiting: List<Waiting>) {
        val profile = stores.profiles.activeId.value ?: return
        if (!registry.live.value.containsKey(profile)) return
        AttentionTileCache.write(
            application,
            waiting.count { it.profile == profile },
            profile.raw,
            clock.now().toEpochMilliseconds(),
        )
    }

    /**
     * Explicit disconnect withdraws the launcher promises; a lifecycle suspension never calls this.
     */
    suspend fun onExplicitDisconnect(profile: ProfileId) =
        kotlinx.coroutines.withContext(mainDispatcher) {
            // The launcher shows the active server only: another server's disconnect leaves it.
            if (profile != stores.profiles.activeId.value) return@withContext
            if (PinnedShortcuts.withdrawAll(application)) publishedPins = null
            AttentionTileCache.clear(application)
        }

    private suspend fun publishWidgets(feed: ChatFeedSnapshot, waiting: List<Waiting>) {
        val profile = stores.profiles.activeId.value ?: return
        // Startup's empty feed is not a server read: keep the saved target until a real listing
        // arrives.
        if (
            feed.items.isEmpty() &&
                feed.revision == 0L &&
                !feed.loading &&
                registry.live.value[profile] == null
        )
            return
        val savedProfiles = stores.profiles.profiles.value
        val names = savedProfiles.associate { it.id to it.profile.name }
        val base =
            PlatformRules.widgetModel(feed, waiting, profile, names, registry.live.value.keys)
        val loaded = stores.profiles.profile(profile) ?: return
        if (!stores.lanes.forProfile(profile).isAdmitting) return
        val location =
            registry.live.value[profile]?.session?.server?.location?.value
                ?: io.github.eslamasabry.oc.core.model.ServerLocation.Default
        val fingerprint = widgetFingerprint(loaded)
        val scopeToken =
            widgetDigest(
                "${profile.raw}\u0000${location.directory.orEmpty()}\u0000${location.workspace.orEmpty()}"
            )
        val target =
            JSONObject()
                .put("profileID", profile.raw)
                .put("scopeToken", scopeToken)
                .put("fingerprint", fingerprint)
        location.directory?.let { target.put("directory", it) }
        location.workspace?.let { target.put("workspace", it) }
        // Unchanged content is not rewritten or redrawn: clock stamps move only when the content
        // does (B-ANDR-117).
        if (
            base == widgetBaseModel &&
                stores.prefs.string("oc.widgetTarget.${profile.raw}") == target.toString()
        )
            return
        val graph = registry.live.value[profile]
        // A failed or bounded-out read leaves the marker absent. Unknown identity never authorizes
        // a status patch.
        val rows =
            withTimeoutOrNull(WIDGET_IDENTITIES_WAIT_MS) {
                base.rows.map { row ->
                    if (row.profile != profile.raw || graph == null) row
                    else widgetIdentity(graph, row)
                }
            } ?: base.rows
        if (stores.profiles.activeId.value != profile || registry.live.value[profile] !== graph)
            return
        val model = base.copy(rows = rows)
        val details = mutableMapOf<Pair<String, String>, WidgetDetail>()
        // Only the four launcher-visible rows. Each read is bounded and never mutates the server.
        for (row in model.rows.take(4)) {
            if (row.profile != profile.raw) continue
            val id = ProfileId(row.profile)
            val rowGraph = registry.live.value[id] ?: continue
            val rowLocation = rowGraph.session.server.location.value
            if (row.project != rowLocation.directory.orEmpty()) continue
            val page =
                withTimeoutOrNull(2_000L) {
                    rowGraph.session.server.messagePage(SessionId(row.id), limit = EXCERPT_MESSAGES)
                }
            if (
                registry.live.value[id] !== rowGraph ||
                    rowGraph.session.server.location.value != rowLocation
            )
                continue
            val messages =
                (page as? Outcome.Ok)?.value?.items.orEmpty().filter { it.info.sessionId == row.id }
            val permission =
                waiting
                    .firstOrNull { it.profile == id && it.request.sessionId.raw == row.id }
                    ?.request as? PendingRequest.Permission
            val call =
                permission?.request?.tool?.let { named ->
                    messages
                        .firstOrNull { it.info.id == named.messageId.raw }
                        ?.parts
                        ?.firstOrNull { it.type == PartType.Tool && it.callId == named.callId }
                }
            val callTarget =
                call?.tool?.input?.let { input ->
                    val keys =
                        if (call.toolName in listOf("bash", "shell")) listOf("command")
                        else listOf("filePath", "filepath", "file_path", "path", "file")
                    keys.firstNotNullOfOrNull { key ->
                        (input[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    }
                }
            details[row.profile to row.id] =
                WidgetDetail(
                    preview = widgetPresentation.assistantPreview(messages),
                    ask =
                        permission
                            ?.request
                            ?.let {
                                widgetPresentation.ask(
                                    it.permission,
                                    it.patterns,
                                    call?.toolName,
                                    callTarget,
                                )
                            }
                            .orEmpty(),
                )
        }
        // A read must never publish a result against a replaced profile, location, request or feed.
        val freshWaiting =
            registry.live.value.flatMap { (id, live) ->
                (live as? ChatGraphAccessors)?.chatRequests?.engine?.pending?.value.orEmpty().map {
                    Waiting(id, it)
                }
            }
        if (freshWaiting != waiting || stores.profiles.profiles.value != savedProfiles) return
        val current =
            PlatformRules.widgetModel(
                runtime.snapshot.value,
                freshWaiting,
                profile,
                stores.profiles.profiles.value.associate { it.id to it.profile.name },
                registry.live.value.keys,
            )
        if (current != base || stores.profiles.activeId.value != profile) return
        val now = clock.now().toEpochMilliseconds()
        val json =
            JSONObject(widgetJson(model, now, details))
                .put("scopeToken", scopeToken)
                .put("onDemand", JSONObject().put("fingerprint", fingerprint))
        val jsonRows = json.getJSONArray("sessions")
        for (i in 0 until jsonRows.length()) {
            val row = jsonRows.getJSONObject(i)
            if (row.optString("profileID") != profile.raw) continue
            if (row.optBoolean("busy") && row.optString("requestID").isBlank()) {
                if (row.has("runMarker") && row.has("startedAt")) {
                    // The bounded identity read already found the run.
                    row.put("onDemandActions", JSONArray(listOf("stop")))
                } else {
                    val gateway = registry.live.value[profile]?.session?.server
                    val page =
                        gateway?.let {
                            withTimeoutOrNull(2_000) {
                                it.messagePage(SessionId(row.getString("id")), limit = 6)
                            }
                        } as? Outcome.Ok
                    val marker = page?.value?.items?.let(::widgetRunMessage)
                    val markerTime = marker?.info?.time?.createdMs
                    if (marker != null && markerTime != null && markerTime > 0) {
                        row.put("runMarker", marker.info.id)
                            .put("startedAt", markerTime)
                            .put("onDemandActions", JSONArray(listOf("stop")))
                    }
                }
            }
            val permission =
                waiting
                    .firstOrNull {
                        it.profile == profile && it.request.id.raw == row.optString("requestID")
                    }
                    ?.request as? PendingRequest.Permission ?: continue
            val ask = permission.request
            val command =
                metadataString(permission, "command")
                    ?: metadataString(permission, "cmd")
                    ?: ask.patterns
                        .firstOrNull { it != "*" && it.isNotBlank() }
                        ?.takeIf { ask.permission in setOf("bash", "shell") }
            val file =
                FILE_KEYS.firstNotNullOfOrNull { metadataString(permission, it) }
                    ?: ask.patterns
                        .firstOrNull { it != "*" && it.isNotBlank() }
                        ?.takeIf {
                            ask.permission in
                                setOf(
                                    "edit",
                                    "read",
                                    "write",
                                    "patch",
                                    "multiedit",
                                    "external_directory",
                                )
                        }
            val title =
                NotificationText.permissionAsk(
                    ask.permission,
                    command?.let(redactor::redact),
                    file?.let(redactor::redact),
                )
            if (title != null && title.target.isNotBlank()) {
                row.put("askAction", title.action.wire)
                    .put("askTarget", title.target)
                    .put(
                        "ask",
                        JSONObject()
                            .put(
                                "kind",
                                if (title.action.wire == "edit") "write" else title.action.wire,
                            )
                            .put(
                                "q",
                                words.text(
                                    when (title.action) {
                                        io.github.eslamasabry.oc.android.notifications.AskAction
                                            .Run ->
                                            io.github.eslamasabry.oc.android.notifications.R.string
                                                .notify_ask_run
                                        io.github.eslamasabry.oc.android.notifications.AskAction
                                            .Read ->
                                            io.github.eslamasabry.oc.android.notifications.R.string
                                                .notify_ask_read
                                        io.github.eslamasabry.oc.android.notifications.AskAction
                                            .Edit ->
                                            io.github.eslamasabry.oc.android.notifications.R.string
                                                .notify_ask_edit
                                        io.github.eslamasabry.oc.android.notifications.AskAction
                                            .Folder ->
                                            io.github.eslamasabry.oc.android.notifications.R.string
                                                .notify_ask_folder
                                    },
                                    title.target,
                                ),
                            )
                            .put("file", shadeText(command ?: file.orEmpty())),
                    )
                    .put("onDemandActions", JSONArray(listOf("allowOnce", "reject")))
            }
        }
        if (
            widgets.publish(json.toString(), profile.raw, target.toString()) !=
                io.github.eslamasabry.oc.android.widgets.PublishResult.Refused
        ) {
            widgetBaseModel = base
            widgetModel = model
            widgetStampMs = now
        }
    }

    /**
     * The transaction calls even without a cached snapshot; a failed native clear stops the generic
     * sweep.
     */
    suspend fun clearProfile(profile: ProfileId): Outcome<Unit> {
        return when (widgets.clearProfile(profile.raw)) {
            io.github.eslamasabry.oc.android.widgets.ClearOutcome.Failed ->
                Outcome.Failed(Failure.StorageRefused())
            else -> {
                widgetModel = null
                Outcome.Ok(Unit)
            }
        }
    }

    suspend fun runWidgetAction(raw: String) {
        stores.warm()
        io.github.eslamasabry.oc.android.widgets.NativeWidgetActions.execute(application, raw)
    }

    private suspend fun widgetIdentity(graph: ConnectionGraph, row: WidgetRow): WidgetRow {
        val gateway = graph.session.server
        val messages = ArrayList<RunMessage>()
        var cursor: String? = null
        val seen = HashSet<String>()
        repeat(WIDGET_IDENTITY_PAGES) {
            val page =
                when (
                    val read =
                        gateway.messagePage(SessionId(row.id), cursor, WIDGET_IDENTITY_PAGE_SIZE)
                ) {
                    is Outcome.Failed -> return row
                    is Outcome.Ok -> read.value
                }
            if (registry.live.value[graph.profile.id] !== graph) return row
            messages +=
                page.items
                    .filter { it.info.sessionId == row.id }
                    .map { message ->
                        RunMessage(
                            id = message.info.id,
                            role = if (message.info.role == Role.User) "user" else "assistant",
                            createdAt = message.info.time?.createdMs,
                            partTypes = message.parts.map { it.rawType },
                        )
                    }
            val marker = RunIdentity.marker(messages)
            if (marker != null) {
                val started =
                    messages.firstOrNull { it.id == marker && it.role == "user" }?.createdAt
                return row.copy(
                    runMarker = marker,
                    startedAt = started?.takeIf { it > 0 },
                    promptCount = RunIdentity.promptCount(messages),
                )
            }
            cursor = page.nextCursor ?: return row
            if (!seen.add(cursor!!)) return row
        }
        return row
    }

    private data class WidgetDetail(val preview: String, val ask: Map<String, String>)

    private fun widgetJson(
        model: WidgetModel,
        now: Long,
        details: Map<Pair<String, String>, WidgetDetail>,
    ): String {
        val sessions = JSONArray()
        for (row in model.rows) {
            sessions.put(
                JSONObject()
                    .put("id", row.id)
                    .put("profileID", row.profile)
                    .put(
                        "title",
                        widgetSafe.sessionTitle(
                            row.title,
                            words
                                .localized()
                                .getString(
                                    io.github.eslamasabry.oc.android.widgets.R.string.widget_v2_new
                                ),
                        ),
                    )
                    .put("preview", widgetSafe.preview(row.preview))
                    .put("project", widgetSafe.project(row.project))
                    .put("server", widgetSafe.title(row.server))
                    .put("busy", row.busy)
                    .put("needsYou", row.needsYou)
                    .put("onDemandActions", JSONArray())
                    .put("requestID", row.requestId)
                    .put("updatedAt", row.updatedAt)
                    .apply {
                        val detail = details[row.profile to row.id]
                        if (detail != null) {
                            if (detail.preview.isNotEmpty()) put("preview", detail.preview)
                            if (detail.ask.isNotEmpty()) put("ask", JSONObject(detail.ask))
                        }
                    }
                    .apply {
                        row.runMarker?.let { put("runMarker", it) }
                        row.startedAt?.let { put("startedAt", it) }
                        row.promptCount?.let { put("promptCount", it) }
                    }
            )
        }
        val servers = JSONArray()
        for (server in model.servers) {
            val since = serverSince[server.id]?.takeIf { it.first == server.online }?.second ?: now
            serverSince[server.id] = server.online to since
            servers.put(
                JSONObject()
                    .put("name", server.name)
                    .put("state", if (server.online) "online" else "offline")
                    .put("since", since)
                    .put("phone", false)
            )
        }
        return JSONObject()
            .put("version", 2)
            .put("profileID", model.profile)
            .put("connected", model.connected)
            .put("updatedAt", now)
            .put("lastSeenAt", now)
            .put("runningCount", model.running)
            .put("pendingCount", model.pending)
            .put("sessions", sessions)
            .put("servers", servers)
            .toString()
    }

    /**
     * Launcher shortcuts for the active server's pinned conversations (B-ANDR-030/-053),
     * republished on change.
     */
    private fun publishPins(feed: ChatFeedSnapshot) {
        val profile = stores.profiles.activeId.value ?: return
        if (!registry.live.value.containsKey(profile)) return
        val raw =
            try {
                (stores.prefs.get(PINS_PREFIX + profile.raw) as? PrefValue.Str)?.value
            } catch (_: IllegalStateException) {
                return // preferences not warm yet: the next feed change publishes
            }
        val pinned = PlatformRules.pinnedIds(raw).toSet()
        val pins =
            feed.items
                .filter {
                    it.profileId == profile &&
                        it.sessionId.raw in pinned &&
                        !it.isSubagent &&
                        !it.inOtherFolder
                }
                .sortedByDescending { it.lastActivityMs }
                .map { PinnedSession(it.sessionId.raw, PlatformRules.widgetText(it.title)) }
                .take(PinnedShortcuts.MAX_SHORTCUTS)
        val next = profile.raw to pins
        if (next == publishedPins) return
        if (PinnedShortcuts.publish(application, profile.raw, pins)) publishedPins = next
    }

    private fun alerts(feed: ChatFeedSnapshot, waiting: List<Waiting>, appVisible: Boolean) {
        lastWaiting = waiting
        // Back in the app (launcher, list or a tap): what the alerts said is on screen now (Flutter
        // clears them on
        // resume; B-ANDR-011/-040). The request ids stay remembered so the same request is not
        // posted again.
        if (PlatformRules.cameToForeground(wasVisible, appVisible)) clearShade()
        wasVisible = appVisible
        val items = feed.items.associateBy { it.ref }
        val names = stores.profiles.profiles.value.associate { it.id to it.profile.name }
        val facts =
            AlertFacts(
                liveModeActive = background.live.value.enabled && background.live.value.active,
                // The process is alive with a live connection: this is the reply watch of the
                // preview (no service).
                replyWatchActive = false,
                appVisible = appVisible,
                permissionGranted = notificationsAllowed(),
                serverAlertsOn = true, // narrowed per kind below by the person's Settings choices
                quietHours = quietNow(),
                onWifi = null,
                wifiOnly = false,
            )
        val requestsOn = choice(REQUESTS_ON)
        val finishedOn = choice(FINISHED_ON)
        // Requests: one alert per conversation, for its oldest waiting request (B-ANDR-011).
        val current = mutableMapOf<String, Waiting>()
        for (w in waiting) {
            val key = AlertKeys.input(w.profile.raw, w.request.sessionId.raw)
            if (
                key !in current ||
                    (current[key]?.request !is PendingRequest.Permission &&
                        w.request is PendingRequest.Permission)
            )
                current[key] = w
        }
        for ((key, w) in current) {
            if (alerted[key] == w.request.id.raw) continue
            val item = items[ConversationRef(w.profile, w.request.sessionId)]
            val alert = requestAlert(key, w, item, names[w.profile])
            val allowed =
                facts.copy(
                    serverAlertsOn = PlatformRules.choiceAllows(alert.kind, requestsOn, finishedOn)
                )
            when (val route = AlertPolicy.route(alert, allowed)) {
                is AlertRoute.Post -> {
                    if (
                        shade.show(alert, route)
                            is io.github.eslamasabry.oc.android.notifications.AlertShown.Shown
                    ) {
                        alerted[key] = w.request.id.raw
                    }
                }
                is AlertRoute.Skip -> Unit
            }
        }
        // Answered here or elsewhere: its alert goes.
        for (key in alerted.keys.filter { it.startsWith("input:") && it !in current }) {
            shade.dismiss(key)
            alerted.remove(key)
        }
        // Finished replies (B-ANDR-040, -048) and stopped runs (B-ANDR-011) while the app is not on
        // screen.
        for (item in feed.items) {
            val ref = item.ref
            val busyNow = item.status == ChatStatus.Running || item.status == ChatStatus.NeedsYou
            val was = running[ref] == true || ref in eventStarted
            if (busyNow && running[ref] != true && ref !in runEvents) endedKinds.remove(ref)
            val ending = runEvents[ref]
            val kind =
                when {
                    ending is io.github.eslamasabry.oc.core.domain.events.RunOutcome.Failed ->
                        AlertKind.Error
                    ending is io.github.eslamasabry.oc.core.domain.events.RunOutcome.Interrupted ->
                        null
                    ending != null && was -> AlertKind.Complete
                    else -> PlatformRules.endedAlertKind(was, item.status)
                }
            running[ref] = busyNow && ending == null
            if (kind == null || item.isSubagent) continue
            if (
                endedKinds[ref] == kind ||
                    (endedKinds[ref] == AlertKind.Error && kind == AlertKind.Complete)
            )
                continue
            // Claim even when suppressed (foreground/quiet/off): a later repaint never replays a
            // finished run.
            endedKinds[ref] = kind
            eventStarted.remove(ref)
            val alert =
                endedAlert(kind, item, names[item.profileId])
                    .copy(
                        reason =
                            endedReasons[ref]
                                ?: io.github.eslamasabry.oc.android.notifications.StopReason.Other
                    )
            val allowed =
                facts.copy(
                    subAgentSession = item.isSubagent,
                    serverAlertsOn = PlatformRules.choiceAllows(kind, requestsOn, finishedOn),
                )
            val route = AlertPolicy.route(alert, allowed) as? AlertRoute.Post ?: continue
            if (kind == AlertKind.Complete && alert.detail.isEmpty()) {
                // The feed row carries no reply text: read the last reply's first line, as Flutter
                // does (B-ANDR-048).
                appScope.launch {
                    val line =
                        withTimeoutOrNull(EXCERPT_WAIT_MS) { lastReplyLine(item.ref) }.orEmpty()
                    // A late excerpt may not overwrite an error, a new run or a foreground return.
                    if (
                        endedKinds[item.ref] == kind &&
                            visible.value == 0 &&
                            background.live.value.enabled &&
                            background.live.value.active &&
                            registry.live.value.containsKey(item.profileId)
                    )
                        shade.show(alert.copy(detail = line), route)
                }
            } else {
                shade.show(alert, route)
            }
            statusPosted += alert.key
        }
    }

    private fun shadeText(value: String): String = NotificationText.redact(redactor.redact(value))

    private fun requestAlert(
        key: String,
        w: Waiting,
        item: ChatFeedItem?,
        server: String?,
    ): CodingAlert {
        val subtext = shadeText(NotificationText.subtitle(server, item?.projectName))
        return when (val r = w.request) {
            is PendingRequest.Permission -> {
                val command =
                    metadataString(r, "command")
                        ?: metadataString(r, "cmd")
                        ?: r.request.patterns
                            .firstOrNull { it != "*" && it.isNotBlank() }
                            ?.takeIf { r.request.permission in setOf("bash", "shell") }
                val file =
                    FILE_KEYS.firstNotNullOfOrNull { metadataString(r, it) }
                        ?: r.request.patterns
                            .firstOrNull { it != "*" && it.isNotBlank() }
                            ?.takeIf {
                                r.request.permission in
                                    setOf(
                                        "edit",
                                        "read",
                                        "write",
                                        "patch",
                                        "multiedit",
                                        "external_directory",
                                    )
                            }
                CodingAlert(
                    kind = AlertKind.Permission,
                    sessionId = r.sessionId.raw,
                    key = key,
                    requestId = r.id.raw,
                    profileId = w.profile.raw,
                    subtext = subtext,
                    chatTitle = shadeText(item?.title.orEmpty()),
                    detail = shadeText(command ?: file ?: r.request.permission),
                    ask =
                        NotificationText.permissionAsk(
                            r.request.permission,
                            command?.let(redactor::redact),
                            file?.let(redactor::redact),
                        ),
                )
            }
            is PendingRequest.Question -> {
                val prompt = r.question.prompts.firstOrNull()
                CodingAlert(
                    kind = AlertKind.Question,
                    sessionId = r.sessionId.raw,
                    key = key,
                    requestId = r.id.raw,
                    profileId = w.profile.raw,
                    subtext = subtext,
                    chatTitle = shadeText(item?.title.orEmpty()),
                    detail = shadeText(prompt?.question.orEmpty()),
                    options =
                        io.github.eslamasabry.oc.android.notifications
                            .QuestionShape(
                                r.question.prompts.map {
                                    io.github.eslamasabry.oc.android.notifications.QuestionShape
                                        .Prompt(
                                            it.choices.map { choice -> choice.label },
                                            it.multiple,
                                            it.custom,
                                        )
                                }
                            )
                            .shadeOptions
                            .map { NotificationText.redact(redactor.redact(it), 24) },
                )
            }
        }
    }

    private suspend fun lastReplyLine(ref: ConversationRef): String {
        val graph = registry.live.value[ref.profileId] ?: return ""
        val page =
            graph.session.server.messagePage(ref.sessionId, limit = EXCERPT_MESSAGES) as? Outcome.Ok
                ?: return ""
        return NotificationText.firstReplyLine(PlatformRules.replyTexts(page.value.items))
    }

    /**
     * The person's Settings switch (Flutter keys, so a migrated choice holds); on until read
     * otherwise.
     */
    private fun choice(key: String): Boolean =
        try {
            (stores.prefs.get(key) as? PrefValue.Bool)?.value ?: true
        } catch (_: IllegalStateException) {
            true // not warm yet
        }

    /** The shared stored quiet-hours choice is authoritative for requests and finished replies. */
    private fun quietNow(): Boolean {
        fun minute(key: String): Int? =
            try {
                (stores.prefs.get(key) as? PrefValue.Int64)?.value?.toInt()?.takeIf {
                    it in 0 until 1440
                }
            } catch (_: IllegalStateException) {
                null
            }
        val start = minute("oc.notify.quietStart") ?: return false
        val end = minute("oc.notify.quietEnd") ?: return false
        val local =
            java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds())
                .atZone(java.time.ZoneId.systemDefault())
        val now = local.hour * 60 + local.minute
        return start == end ||
            if (start < end) now in start until end else now >= start || now < end
    }

    private fun endedAlert(kind: AlertKind, item: ChatFeedItem, server: String?): CodingAlert =
        CodingAlert(
            kind = kind,
            sessionId = item.sessionId.raw,
            key = AlertKeys.status(item.profileId.raw, item.sessionId.raw),
            profileId = item.profileId.raw,
            subtext = NotificationText.subtitle(server, item.projectName),
            chatTitle = item.title.orEmpty(),
            // A feed preview can be the person's prompt: finished text is fetched from assistant
            // messages only.
            detail = "",
        )

    private fun metadataString(r: PendingRequest.Permission, key: String): String? =
        (r.request.metadata[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }

    // ---- Shade answers
    // --------------------------------------------------------------------------------------------

    private suspend fun answer(answer: ShadeAnswer): Outcome<Unit> {
        val graph =
            registry.live.value[answer.profile]
                ?: return Outcome.Failed(
                    Failure.Network(NetworkWhy.Offline)
                ) // reposted with Open only
        val engine =
            (graph as? ChatGraphAccessors)?.chatRequests?.engine
                ?: return Outcome.Failed(Failure.Unexpected())
        val pending =
            engine.pending.value.firstOrNull {
                it.id == answer.request && it.sessionId == answer.session
            }
        val matches =
            when (answer.kind) {
                AlertKind.Permission -> pending is PendingRequest.Permission
                AlertKind.Question -> pending is PendingRequest.Question
                else -> false
            }
        if (!matches) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val typedReply = answer.reply as? ShadeReply.Text
        if (pending is PendingRequest.Question && typedReply != null) {
            val shape =
                QuestionShape(
                    pending.question.prompts.map { prompt ->
                        QuestionShape.Prompt(
                            prompt.choices.map { it.label },
                            prompt.multiple,
                            prompt.custom,
                        )
                    }
                )
            if (!shape.accepts(typedReply.text))
                return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        // Flutter trims the shade reply before sending; the accepted text is the trimmed text.
        val shadeReply = typedReply?.let { ShadeReply.Text(it.text.trim()) } ?: answer.reply
        val reply = ShadeReplyAnswers.of(answer.kind, shadeReply) // B-PROTO-046 (BuildRestWiring)
        val result = engine.answerNow(answer.request, reply)
        log.i { "shade answer ${answer.kind} ${if (result is Outcome.Ok) "sent" else "not sent"}" }
        return result
    }

    private companion object {
        const val REQUEST_NOTIFY = 4747
        const val EXCERPT_WAIT_MS = 5_000L
        const val EXCERPT_MESSAGES = 6
        const val SEED_WAIT_MS = 10_000L
        const val WIDGET_IDENTITIES_WAIT_MS = 8_000L
        const val WIDGET_IDENTITY_PAGES = 3
        const val WIDGET_IDENTITY_PAGE_SIZE = 100
        const val REQUESTS_ON = "oc.notify.requests"
        const val FINISHED_ON = "oc.notify.finishedRuns"
        const val PINS_PREFIX = "oc.sessionPins."
        val FILE_KEYS = listOf("filePath", "filepath", "file_path", "path", "file")
    }
}

// ---- Pure rules (unit-tested in PlatformRulesTest; no Android types)
// ----------------------------------------------

/** One conversation row of the home-screen widget, already safe to show (B-SEC-181). */
internal data class WidgetRow(
    val id: String,
    val profile: String,
    val title: String,
    val project: String,
    val server: String,
    val busy: Boolean,
    val needsYou: Boolean,
    /**
     * Only for the snapshot's own server: no request identity crosses from another connection
     * (inline actions).
     */
    val requestId: String,
    val updatedAt: Long,
    val runMarker: String? = null,
    val startedAt: Long? = null,
    /** Real user prompts in the bounded identity read; OC2 timeline records never count. */
    val promptCount: Int? = null,
    val preview: String = "",
)

internal data class WidgetServer(val name: String, val online: Boolean, val id: String = name)

/** What the widgets show, without clock stamps: equal models are never republished (B-ANDR-117). */
internal data class WidgetModel(
    val profile: String,
    val connected: Boolean,
    val running: Int,
    val pending: Int,
    val rows: List<WidgetRow>,
    val servers: List<WidgetServer>,
)

internal object PlatformRules {
    const val WIDGET_TEXT = 80
    const val WIDGET_SESSIONS = 12

    /** Flutter `safeWidgetTitle`: one line, secrets masked, at most 80 characters. */
    fun widgetText(value: String?): String = NotificationText.redact(value.orEmpty(), WIDGET_TEXT)

    fun widgetModel(
        feed: ChatFeedSnapshot,
        waiting: List<Waiting>,
        active: ProfileId,
        names: Map<ProfileId, String>,
        online: Set<ProfileId>,
    ): WidgetModel {
        val rows =
            feed.items
                .filter { !it.isSubagent }
                .sortedWith(
                    compareBy<ChatFeedItem> {
                            when {
                                it.status == ChatStatus.NeedsYou ||
                                    waiting.any { w ->
                                        w.profile == it.profileId &&
                                            w.request.sessionId == it.sessionId
                                    } -> 0
                                it.status == ChatStatus.Running -> 1
                                else -> 2
                            }
                        }
                        .thenByDescending { it.lastActivityMs }
                )
                .take(WIDGET_SESSIONS)
                .map { item ->
                    val request = waiting.firstOrNull {
                        it.profile == item.profileId && it.request.sessionId == item.sessionId
                    }
                    val own = item.profileId == active
                    WidgetRow(
                        id = item.sessionId.raw,
                        profile = item.profileId.raw,
                        title = widgetText(item.title),
                        project = item.directory.orEmpty(),
                        server = widgetText(names[item.profileId]),
                        busy = item.status == ChatStatus.Running,
                        needsYou = item.status == ChatStatus.NeedsYou || request != null,
                        requestId = if (own) request?.request?.id?.raw.orEmpty() else "",
                        updatedAt = item.lastActivityMs,
                        preview = widgetText(item.preview),
                    )
                }
        return WidgetModel(
            profile = active.raw,
            connected = active in online,
            running = feed.items.count { !it.isSubagent && it.status == ChatStatus.Running },
            pending = waiting.size,
            rows = rows,
            servers =
                names.map { (id, name) -> WidgetServer(widgetText(name), id in online, id.raw) },
        )
    }

    /**
     * The alert a run that stopped running deserves: a finished reply, or a stopped / error alert
     * when it failed (Flutter posts the stopped notification, never "finished", for a failed run;
     * B-ANDR-011). Null otherwise.
     */
    fun endedAlertKind(wasRunning: Boolean, now: ChatStatus): AlertKind? =
        when {
            !wasRunning -> null
            now == ChatStatus.Running || now == ChatStatus.NeedsYou -> null
            now == ChatStatus.Failed -> AlertKind.Error
            else -> AlertKind.Complete
        }

    /** The app came on screen since the last feed pass: outstanding alerts are cleared then. */
    fun cameToForeground(wasVisible: Boolean, visible: Boolean): Boolean = visible && !wasVisible

    /**
     * The person's Settings choices: questions/permissions and finished-work alerts are separate
     * (B-MIGR-005).
     */
    fun choiceAllows(kind: AlertKind, requestsOn: Boolean, finishedOn: Boolean): Boolean =
        if (kind.isRequest) requestsOn else finishedOn

    /**
     * The text of assistant replies, oldest first, without synthetic parts (input of
     * `firstReplyLine`).
     */
    fun replyTexts(messages: List<MessageWithParts>): List<String> =
        messages
            .filter { it.info.role == Role.Assistant }
            .map { m ->
                m.parts
                    .filter { it.type == PartType.Text && !it.synthetic }
                    .joinToString("\n") { it.text }
            }
            .filter { it.isNotBlank() }

    /**
     * The pinned session ids of a Flutter `oc.sessionPins.<profile>` value (`{"<scope>":
     * ["ses_1", …]}`), in order.
     */
    fun pinnedIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val json =
            try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return emptyList()
        val out = LinkedHashSet<String>()
        for ((_, ids) in json) {
            val list = ids as? JsonArray ?: continue
            for (id in list) (id as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?.let(out::add)
        }
        return out.toList()
    }

    /**
     * The release page of a waiting update, or null. The address came from the network: only this
     * project's GitHub release pages, checked by [ExternalLinkPolicy]; the button the person tapped
     * names GitHub, which is the consent an Ask-first link needs (the same rule as the Git page's
     * "Open GitHub").
     */
    fun releaseLink(policy: ExternalLinkPolicy, url: String): ApprovedLink? {
        if (!url.startsWith(RELEASES)) return null
        return when (val decision = policy.check(url, LinkOrigin.ServerContent)) {
            is LinkDecision.Open -> decision.link
            is LinkDecision.AskFirst -> decision.link.takeIf { decision.shownHost == "github.com" }
            is LinkDecision.Refuse -> null
        }
    }

    /** The Settings Updates page's view of the updater. */
    fun updateFacts(u: AppUpdateState, build: AppBuildInfo, date: (Long) -> String): UpdateFacts {
        val version = u.release?.version.orEmpty()
        val state =
            when (u.phase) {
                UpdatePhase.Checking -> SettingsUpdateState.Checking
                UpdatePhase.Available ->
                    SettingsUpdateState.Available(version, u.downloadSize?.let { mb(it) }.orEmpty())
                UpdatePhase.Downloading,
                UpdatePhase.Verifying ->
                    SettingsUpdateState.Downloading(
                        version,
                        ((u.progress ?: 0f) * 100).toInt().coerceIn(0, 100),
                    )
                UpdatePhase.ReadyToInstall -> SettingsUpdateState.Downloaded(version)
                UpdatePhase.Installing -> SettingsUpdateState.Installing
                UpdatePhase.Failed ->
                    SettingsUpdateState.Failed(
                        SettingsUpdateFailure.valueOf((u.failure ?: UpdateFailure.Network).name)
                    )
                UpdatePhase.None ->
                    u.checkedAt?.let {
                        SettingsUpdateState.UpToDate(date(it.toEpochMilliseconds()))
                    } ?: SettingsUpdateState.NeverChecked
            }
        return UpdateFacts(
            current = u.installed?.version ?: build.versionName,
            build = (u.installed?.build ?: build.versionCode).toString(),
            checksOn = u.enabled,
            state = state,
        )
    }

    private fun mb(bytes: Long): String {
        val tenths = (bytes * 10 + 524_288) / 1_048_576
        return "${tenths / 10}.${tenths % 10}"
    }

    private const val RELEASES = "https://github.com/Eslamasabry/opencode-mobile-next/releases/"
}

/**
 * Shared words waiting for the conversation made for them (07 §7.7; Flutter creates a specific
 * session). Only a chat opened straight from New conversation that did not exist when the share
 * arrived takes them; an unrelated chat never does. They stay waiting until they are really saved
 * in that chat's draft, so a slow restore or a failed save never loses them.
 */
internal class ShareSeed {
    var text: String? = null
        private set

    private var known: Set<ConversationRef> = emptySet()
    private var previous: AppRoute? = null
    private var inFlight: ConversationRef? = null

    fun arrived(shared: String, existing: Set<ConversationRef>) {
        text = shared
        known = existing
        inFlight = null
    }

    /** The chat that should take the words now [top] is on screen, or null. */
    fun target(top: AppRoute?): ConversationRef? {
        val before = previous
        previous = top
        if (text == null || top !is ChatRoute || inFlight != null) return null
        if (before !is ChatsRoute.New || top.ref in known) return null
        inFlight = top.ref
        return top.ref
    }

    /** The draft now holds the words: they are done. */
    fun saved(ref: ConversationRef) {
        if (inFlight == ref) {
            text = null
            inFlight = null
        }
    }

    /** Not saved: keep them for the next new conversation. */
    fun notSaved(ref: ConversationRef) {
        if (inFlight == ref) inFlight = null
    }

    /** The words added to what the draft already holds, never replacing it. */
    fun merged(existing: String, shared: String): String =
        if (existing.isBlank()) shared else existing.trimEnd() + "\n\n" + shared
}

/**
 * Launcher records are deleted before the profile's credentials, and a failed withdrawal refuses
 * deletion.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class LauncherProfileCleaner(
    private val application: Application,
    private val stores: PreviewStores,
) : ProfileResourceCleaner {
    override val owner: String = ":android:tiles"
    override val order: Int = DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        // Flutter stored both surfaces as app-wide values with their owner inside the JSON. The
        // generic
        // dotted-key sweep cannot find them. Unknown ownership is withdrawn rather than retaining
        // private titles.
        val legacyPins = stores.prefs.get("oc.pinnedShortcuts")
        val pinOwner = legacyOwner(legacyPins)
        val removePins = legacyPins != null && (pinOwner == null || pinOwner == profileId.raw)
        val legacyTile = stores.prefs.get("oc.attentionTile")
        val tileOwner = legacyOwner(legacyTile)
        val removeTile = legacyTile != null && (tileOwner == null || tileOwner == profileId.raw)
        if (removePins && pinOwner == null && !PinnedShortcuts.withdrawAll(application)) {
            return Outcome.Failed(Failure.StorageRefused())
        }
        val shortcuts = PinnedShortcuts.withdrawProfile(application, profileId.raw)
        if (shortcuts == ShortcutClear.Failed) return Outcome.Failed(Failure.StorageRefused())
        val tile = AttentionTileCache.clearOwner(application, profileId.raw)
        if (AttentionTileCache.hasOwner(application, profileId.raw))
            return Outcome.Failed(Failure.StorageRefused())
        if (removePins || removeTile) {
            val saved =
                stores.prefs.commit(stores.lanes.app) {
                    if (removePins) remove("oc.pinnedShortcuts")
                    if (removeTile) remove("oc.attentionTile")
                }
            if (saved is Outcome.Failed) return saved
        }
        return Outcome.Ok(
            (if (shortcuts == ShortcutClear.Cleared) 1 else 0) +
                (if (tile) 1 else 0) +
                (if (removePins) 1 else 0) +
                (if (removeTile) 1 else 0)
        )
    }

    private fun legacyOwner(value: PrefValue?): String? {
        val raw = (value as? PrefValue.Str)?.value ?: return null
        return try {
            JSONObject(raw).optString("profileID", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
