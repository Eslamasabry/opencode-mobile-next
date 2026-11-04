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
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
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
import io.github.eslamasabry.oc.android.notifications.ReplyStopper
import io.github.eslamasabry.oc.android.notifications.ShadeAnswer
import io.github.eslamasabry.oc.android.notifications.ShadeAnswerer
import io.github.eslamasabry.oc.android.tiles.AttentionTileCache
import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.oc.android.tiles.PinnedSession
import io.github.eslamasabry.oc.android.tiles.PinnedShortcuts
import io.github.eslamasabry.oc.android.tiles.SharedTextIntake
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
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
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
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.entry.EntryLinkParser
import io.github.eslamasabry.oc.engine.entry.ParsedLink
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
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val updates: AppUpdates,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val navigator: AppNavigator,
    private val draftOwners: Set<ConversationDrafts>,
    private val appPreferences: AppPreferences,
    private val widgetActions: NativeWidgetActions,
    private val monitored: NativeMonitorInbox,
    private val sharedProjectFolders: NativeSharedProjectFolders,
    redactor: Redactor,
) {
    private val log = AppLog.tagged("Platform")
    private val prefs = application.getSharedPreferences("oc_platform", Context.MODE_PRIVATE)
    // P12-FIX-SETTINGS: alerts speak the language chosen in Settings, not only the phone's
    // (B-L10N-013).
    private val words = NotifyWords(application) { appPreferences.localeTag.value }
    private val channels =
        ChannelRegistry(application.getSystemService(NotificationManager::class.java), words)
    val shade = AndroidAlertShade(application, words, channels)
    private val widgetPersistence =
        WidgetPersistence(
            stores.prefs,
            stores.lanes,
            DefaultProfileScope(stores.prefs, stores.secrets, stores.lanes.app),
            appScope,
            clock,
        )
    private val widgets = AndroidWidgetSurface(application, widgetPersistence)
    private val widgetPresentation = WidgetPresentation(application, redactor)

    @Volatile private var started = 0
    private val visible = MutableStateFlow(0)
    private val alerted = mutableMapOf<String, String>() // alert key -> request id (or turn marker)
    private val running = mutableMapOf<ConversationRef, Boolean>()
    private var wasVisible = false
    private val statusPosted =
        mutableSetOf<String>() // finished / stopped alerts still in the shade
    private var knownProfiles: Set<ProfileId> = emptySet()
    private val share = ShareSeed()
    private var widgetModel: WidgetModel? = null
    private var widgetStampMs = 0L
    private var widgetActionStamp = -1L
    private val serverSince = mutableMapOf<String, Pair<Boolean, Long>>()
    private var publishedPins: Pair<String, List<PinnedSession>>? = null

    /** Answers a shade tap on the exact pending request of the live connection (plan 11 §10.6). */
    val actionBody: NotificationActionBody =
        NotificationActionBody(
            shade = shade,
            answerer = ShadeAnswerer { answer -> answer(answer) },
            stopper = ReplyStopper { Outcome.Failed(Failure.NotFound()) },
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
        widgetActions.start()
        sharedProjectFolders.start()
        channels.ensure()
        // B-L10N-002: channel names follow an acknowledged language save (the stored value, never
        // an attempt).
        appScope.launch { appPreferences.storedLocaleTag.collect { channels.relocalize() } }
        application.registerActivityLifecycleCallbacks(Visibility())
        appScope.launch { updates.start() }
        appScope.launch { watchFeed() }
        appScope.launch { watchProfiles() }
        appScope.launch { navigator.top.collect { top -> seedShare(top) } }
    }

    val scope: CoroutineScope
        get() = appScope

    // ---- Activity
    // -------------------------------------------------------------------------------------------------

    /**
     * Every resume: continue an install that waited for permission, check for an update when due.
     */
    fun onResumed(activity: Activity) {
        appScope.launch { updates.onResumed() }
        askNotificationsOnce(activity)
    }

    /**
     * A launcher shortcut, pinned conversation, notification tap or share. Each extra is consumed
     * once.
     */
    fun route(intent: Intent?) {
        if (intent == null) return
        if (monitored.handleIntent(intent)) return
        if (usageAlertTap(intent)) return
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
            navigator.openChat(ConversationRef(ProfileId(tap.profileId), SessionId(tap.sessionId)))
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
            LaunchAction.Usage ->
                navigator.open(io.github.eslamasabry.oc.ui.routes.UsageRoute.Hub())
            LaunchAction.UsageSettings ->
                navigator.open(
                    io.github.eslamasabry.oc.ui.routes.UsageRoute.Hub(widgetSettings = true)
                )
            is LaunchAction.UsageTarget ->
                navigator.open(
                    io.github.eslamasabry.oc.ui.routes.UsageRoute.Hub(
                        fixProvider = action.provider,
                        fixConnection = action.target,
                        readFrom = action.step == "set_up",
                        confirmFix = true,
                    )
                )
            LaunchAction.Connect -> navigator.open(ServersRoute.Home)
            LaunchAction.NewTask ->
                navigator.open(ChatsRoute.New(profile = stores.profiles.activeId.value))
            null -> Unit
            // Activity, background and the other Flutter actions land on Conversations (needs-you
            // rows lead).
            else -> navigator.land()
        }
    }

    private fun usageAlertTap(intent: Intent): Boolean {
        val kind = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND)
        if (kind != "quota" && kind != "quota_reset") return false
        val token = intent.getStringExtra(FrozenExtras.EXTRA_MONITOR_TOKEN)
        val key = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_KEY)
        val profile = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)
        for (extra in
            listOf(
                FrozenExtras.EXTRA_CODING_ALERT_KIND,
                FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID,
                FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID,
                FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID,
                FrozenExtras.EXTRA_CODING_ALERT_KEY,
                FrozenExtras.EXTRA_MONITOR_TOKEN,
            )) intent.removeExtra(extra)
        if (key != null && Regex("^quota-(sub|reset)\\|[a-f0-9]{16}$").matches(key))
            shade.dismiss(key)
        navigator.open(
            io.github.eslamasabry.oc.ui.routes.UsageRoute.Hub(
                alertProfile = profile,
                alertToken = token?.takeIf { Regex("^[a-f0-9]{64}$").matches(it) } ?: "invalid",
            )
        )
        return true
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

    private fun askNotificationsOnce(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (prefs.getBoolean(ASKED_NOTIFY, false)) return
        if (stores.profiles.profiles.value.isEmpty()) return // nothing to tell yet
        // QA wave 4 N4: never mid-task (it popped up on the way back from the file picker into a
        // chat); the calm
        // place is the conversation list, where the alerts it enables are about.
        if (navigator.top.value != ChatsRoute.Home) return
        if (
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
            return
        prefs.edit().putBoolean(ASKED_NOTIFY, true).apply()
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private inner class Visibility : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            visible.value += 1
        }

        override fun onActivityStopped(activity: Activity) {
            visible.value = (visible.value - 1).coerceAtLeast(0)
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
        combine(runtime.snapshot, pendingEverywhere(), visible, widgetActions.revision) {
                feed,
                waiting,
                shown,
                _ ->
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
                widgets.clearProfile(id.raw)
            }
        }
    }

    private fun publishTile(waiting: List<Waiting>) {
        val owner =
            stores.profiles.activeId.value?.raw ?: waiting.firstOrNull()?.profile?.raw ?: return
        AttentionTileCache.write(
            application,
            waiting.size,
            owner,
            clock.now().toEpochMilliseconds(),
        )
    }

    private suspend fun publishWidgets(feed: ChatFeedSnapshot, waiting: List<Waiting>) {
        val profile = stores.profiles.activeId.value ?: return
        val savedProfiles = stores.profiles.profiles.value
        val names = savedProfiles.associate { it.id to it.profile.name }
        val model =
            PlatformRules.widgetModel(feed, waiting, profile, names, registry.live.value.keys)
        // Unchanged content is not rewritten or redrawn: clock stamps move only when the content
        // does (B-ANDR-117).
        val actionStamp = widgetActions.stamp()
        if (model == widgetModel && actionStamp == widgetActionStamp) return
        val details = mutableMapOf<Pair<String, String>, WidgetDetail>()
        // Only the four launcher-visible rows. Each read is bounded and never mutates the server.
        for (row in model.rows.take(4)) {
            if (row.profile != profile.raw) continue
            val id = ProfileId(row.profile)
            val graph = registry.live.value[id] ?: continue
            val location = graph.session.server.location.value
            if (row.project != location.directory.orEmpty()) continue
            val page =
                withTimeoutOrNull(2_000L) {
                    graph.session.server.messagePage(SessionId(row.id), limit = EXCERPT_MESSAGES)
                }
            if (
                registry.live.value[id] !== graph || graph.session.server.location.value != location
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
            val target =
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
                                    target,
                                )
                            }
                            .orEmpty(),
                )
        }
        // A read must never publish a result against a replaced profile, location, request or feed.
        val freshWaiting =
            registry.live.value.flatMap { (id, graph) ->
                (graph as? ChatGraphAccessors)?.chatRequests?.engine?.pending?.value.orEmpty().map {
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
        if (current != model || stores.profiles.activeId.value != profile) return
        val now = clock.now().toEpochMilliseconds()
        val publication = widgetActions.decorate(widgetJson(model, now, details), feed)
        if (
            widgets.publish(publication.snapshot, profile.raw, publication.target) !=
                PublishResult.Refused
        ) {
            widgetModel = model
            widgetActionStamp = actionStamp
            widgetStampMs = now
        }
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
                    .put("title", widgetPresentation.title(row.title))
                    .put("project", widgetPresentation.project(row.project))
                    .put("server", row.server)
                    .put("busy", row.busy)
                    .put("needsYou", row.needsYou)
                    .put("onDemandActions", JSONArray())
                    .put("updatedAt", row.updatedAt)
                    .apply {
                        val detail = details[row.profile to row.id]
                        // decorate() strips the request from rows outside the widget's own
                        // target; the actions need it on the rows inside.
                        if (row.requestId.isNotEmpty()) put("requestID", row.requestId)
                        if (detail != null) {
                            if (detail.preview.isNotEmpty()) put("preview", detail.preview)
                            if (detail.ask.isNotEmpty()) put("ask", JSONObject(detail.ask))
                        }
                    }
            )
        }
        val servers = JSONArray()
        // A deleted server leaves no remembered online/offline stamp behind.
        serverSince.keys.retainAll(model.servers.map { it.id }.toSet())
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
        val raw =
            try {
                (stores.prefs.get(PINS_PREFIX + profile.raw) as? PrefValue.Str)?.value
            } catch (_: IllegalStateException) {
                return // preferences not warm yet: the next feed change publishes
            }
        val titles =
            feed.items
                .filter { it.profileId == profile }
                .associate { it.sessionId.raw to it.title.orEmpty() }
        val pins =
            PlatformRules.pinnedIds(raw).map {
                PinnedSession(it, PlatformRules.widgetText(titles[it]))
            }
        val next = profile.raw to pins
        if (next == publishedPins) return
        if (PinnedShortcuts.publish(application, profile.raw, pins)) publishedPins = next
    }

    private fun alerts(feed: ChatFeedSnapshot, waiting: List<Waiting>, appVisible: Boolean) {
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
                liveModeActive = false,
                // The process is alive with a live connection: this is the reply watch of the
                // preview (no service).
                replyWatchActive = registry.live.value.isNotEmpty(),
                appVisible = appVisible,
                permissionGranted = notificationsAllowed(),
                serverAlertsOn = true, // narrowed per kind below by the person's Settings choices
                quietHours = false,
                onWifi = null,
                wifiOnly = false,
            )
        val requestsOn = choice(REQUESTS_ON)
        val finishedOn = choice(FINISHED_ON)
        // Requests: one alert per conversation, for its oldest waiting request (B-ANDR-011).
        val current = mutableMapOf<String, Waiting>()
        for (w in waiting) {
            val key = AlertKeys.input(w.profile.raw, w.request.sessionId.raw)
            if (key !in current) current[key] = w
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
                    shade.show(alert, route)
                    alerted[key] = w.request.id.raw
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
            val was = running[item.ref] == true
            running[item.ref] = item.status == ChatStatus.Running
            val kind = PlatformRules.endedAlertKind(was, item.status) ?: continue
            val alert = endedAlert(kind, item, names[item.profileId])
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
                    shade.show(alert.copy(detail = line), route)
                }
            } else {
                shade.show(alert, route)
            }
            statusPosted += alert.key
        }
    }

    private fun requestAlert(
        key: String,
        w: Waiting,
        item: ChatFeedItem?,
        server: String?,
    ): CodingAlert {
        val subtext = NotificationText.subtitle(server, item?.projectName)
        return when (val r = w.request) {
            is PendingRequest.Permission -> {
                val command = metadataString(r, "command")
                val file = FILE_KEYS.firstNotNullOfOrNull { metadataString(r, it) }
                CodingAlert(
                    kind = AlertKind.Permission,
                    sessionId = r.sessionId.raw,
                    key = key,
                    requestId = r.id.raw,
                    profileId = w.profile.raw,
                    subtext = subtext,
                    chatTitle = item?.title.orEmpty(),
                    detail = NotificationText.redact(command ?: file ?: r.request.permission),
                    ask = NotificationText.permissionAsk(r.request.permission, command, file),
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
                    chatTitle = item?.title.orEmpty(),
                    detail = NotificationText.redact(prompt?.question.orEmpty()),
                    options = prompt?.choices?.map { it.label }.orEmpty(),
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

    private fun endedAlert(kind: AlertKind, item: ChatFeedItem, server: String?): CodingAlert =
        CodingAlert(
            kind = kind,
            sessionId = item.sessionId.raw,
            key = AlertKeys.status(item.profileId.raw, item.sessionId.raw),
            profileId = item.profileId.raw,
            subtext = NotificationText.subtitle(server, item.projectName),
            chatTitle = item.title.orEmpty(),
            detail =
                if (kind == AlertKind.Complete)
                    NotificationText.firstReplyLine(listOf(item.preview))
                else "",
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
        val reply = ShadeReplyAnswers.of(answer.kind, answer.reply) // B-PROTO-046 (BuildRestWiring)
        val result = engine.answerNow(answer.request, reply)
        log.i { "shade answer ${answer.kind} ${if (result is Outcome.Ok) "sent" else "not sent"}" }
        return result
    }

    private companion object {
        const val ASKED_NOTIFY = "oc.notify.asked"
        const val REQUEST_NOTIFY = 4747
        const val EXCERPT_WAIT_MS = 5_000L
        const val EXCERPT_MESSAGES = 6
        const val SEED_WAIT_MS = 10_000L
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
