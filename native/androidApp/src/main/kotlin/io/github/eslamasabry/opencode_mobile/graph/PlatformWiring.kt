package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import io.github.eslamasabry.oc.android.notifications.ShadeReply
import io.github.eslamasabry.oc.android.tiles.AttentionTileCache
import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.oc.android.tiles.PinnedShortcuts
import io.github.eslamasabry.oc.android.tiles.SharedTextIntake
import io.github.eslamasabry.oc.android.tiles.updates.AndroidUpdateFeed
import io.github.eslamasabry.oc.android.tiles.updates.AndroidUpdatePlatform
import io.github.eslamasabry.oc.android.tiles.updates.PrefsUpdateStore
import io.github.eslamasabry.oc.android.widgets.AndroidWidgetSurface
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetPersistence
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
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.engine.updates.AppUpdates
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.NoUpdateDoor
import io.github.eslamasabry.oc.feature.chats.presentation.UpdateDoor
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateLinks
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.UpdatesRoute
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
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
    fun appUpdates(application: Application, dispatchers: AppDispatchers, clock: Clock, build: AppBuildInfo): AppUpdates =
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

/** The Flutter gear dot: Update shows in the Conversations bar while a newer release waits, and opens Updates. */
@ContributesBinding(AppScope::class, replaces = [NoUpdateDoor::class])
@Inject
class AppUpdateDoor(updates: AppUpdates, private val navigator: AppNavigator, appScope: AppCoroutineScope) : UpdateDoor {
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
 * The release page of a waiting update (Open on GitHub after a signer / package / version refusal). The address comes
 * from the network, so it must be this project's GitHub release page and pass ExternalLinkPolicy (AGENTS.md).
 */
@ContributesBinding(AppScope::class)
@Inject
class ReleasePageLinks(private val application: Application) : UpdateLinks {
    private val policy = ExternalLinkPolicy()

    override suspend fun openReleasePage(url: String): Outcome<Unit> {
        if (!url.startsWith(RELEASES)) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val link =
            when (val decision = policy.check(url, LinkOrigin.ServerContent)) {
                is LinkDecision.Open -> decision.link
                // Checked against this project's release prefix above: the host is github.com, shown on the page.
                is LinkDecision.AskFirst -> decision.link
                is LinkDecision.Refuse -> return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            }
        return try {
            application.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Outcome.Ok(Unit)
        } catch (_: ActivityNotFoundException) {
            Outcome.Failed(Failure.Device())
        }
    }

    private companion object {
        const val RELEASES = "https://github.com/Eslamasabry/opencode-mobile-next/releases/"
    }
}

/** One pending request with where it came from. */
private data class Waiting(val profile: ProfileId, val request: PendingRequest)

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
) {
    private val log = AppLog.tagged("Platform")
    private val prefs = application.getSharedPreferences("oc_platform", Context.MODE_PRIVATE)
    private val words = NotifyWords(application) { null }
    private val channels = ChannelRegistry(application.getSystemService(NotificationManager::class.java), words)
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

    @Volatile private var started = 0
    private val visible = MutableStateFlow(0)
    private val alerted = mutableMapOf<String, String>() // alert key -> request id (or turn marker)
    private val running = mutableMapOf<ConversationRef, Boolean>()
    private var knownProfiles: Set<ProfileId> = emptySet()
    private var pendingShare: String? = null
    private var shareFrom: AppRoute? = null

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
                        application.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
                },
        )

    /** Called once from OcApplication.onCreate, before any OS component runs. */
    fun start() {
        if (started++ > 0) return
        WidgetFeed.persistence = widgetPersistence
        channels.ensure()
        application.registerActivityLifecycleCallbacks(Visibility())
        appScope.launch { updates.start() }
        appScope.launch { watchFeed() }
        appScope.launch { watchProfiles() }
        appScope.launch { navigator.top.collect { top -> seedShare(top) } }
    }

    val scope: CoroutineScope get() = appScope

    // ---- Activity -------------------------------------------------------------------------------------------------

    /** Every resume: continue an install that waited for permission, check for an update when due. */
    fun onResumed(activity: Activity) {
        appScope.launch { updates.onResumed() }
        askNotificationsOnce(activity)
    }

    /** A launcher shortcut, pinned conversation, notification tap or share. Each extra is consumed once. */
    fun route(intent: Intent?) {
        if (intent == null) return
        val share = SharedTextIntake.consume(intent)
        if (share != null) {
            pendingShare = share.text
            shareFrom = navigator.top.value
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
        when (LaunchActions.consume(intent)) {
            LaunchAction.Connect -> navigator.open(ServersRoute.Home)
            LaunchAction.NewTask -> navigator.open(ChatsRoute.New(profile = stores.profiles.activeId.value))
            null -> Unit
            // Activity, background and the other Flutter actions land on Conversations (needs-you rows lead).
            else -> navigator.land()
        }
    }

    private fun alertTap(intent: Intent): ConversationRef? {
        val session = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID)?.takeIf { it.isNotBlank() }
        val profile = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)?.takeIf { it.isNotBlank() }
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
        // QA wave 4 N4: never mid-task (it popped up on the way back from the file picker into a chat); the calm
        // place is the conversation list, where the alerts it enables are about.
        if (navigator.top.value != ChatsRoute.Home) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean(ASKED_NOTIFY, true).apply()
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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

    // ---- Share into a new conversation ----------------------------------------------------------------------------

    /** The shared words become the draft of the conversation the person opens next (07 §7.7). */
    private fun seedShare(top: AppRoute?) {
        val text = pendingShare ?: return
        if (top !is ChatRoute || top == shareFrom) return
        pendingShare = null
        val owner = draftOwners.firstOrNull() ?: return
        appScope.launch {
            val session = owner.open(top.ref)
            val ready = withTimeoutOrNull(SEED_WAIT_MS) { session.state.first { it.ready } }
            if (ready != null && ready.text.isBlank()) {
                session.changed(text)
                session.flush()
            }
        }
    }

    // ---- Feed: alerts, widgets, tile ------------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pendingEverywhere(): Flow<List<Waiting>> =
        registry.live.flatMapLatest { live ->
            val flows =
                live.entries.mapNotNull { (id, graph) -> requestsOf(graph)?.map { list -> list.map { Waiting(id, it) } } }
            if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { parts -> parts.toList().flatten() }
        }

    private fun requestsOf(graph: ConnectionGraph): Flow<List<PendingRequest>>? =
        (graph as? ChatGraphAccessors)?.chatRequests?.engine?.pending

    private suspend fun watchFeed() {
        combine(runtime.snapshot, pendingEverywhere(), visible) { feed, waiting, shown -> Triple(feed, waiting, shown > 0) }
            .conflate()
            .collect { (feed, waiting, appVisible) ->
                publishWidgets(feed, waiting)
                publishTile(waiting)
                alerts(feed, waiting, appVisible)
            }
    }

    private suspend fun watchProfiles() {
        stores.profiles.profiles.collect { list ->
            val now = list.map { it.id }.toSet()
            val gone = knownProfiles - now
            knownProfiles = now
            for (id in gone) {
                // Deletion leaves no launcher shortcut, tile count or widget rows of that server behind (B-STATE-063).
                AttentionTileCache.clearOwner(application, id.raw)
                PinnedShortcuts.withdrawProfile(application, id.raw)
                widgets.clearProfile(id.raw)
            }
        }
    }

    private fun publishTile(waiting: List<Waiting>) {
        val owner = stores.profiles.activeId.value?.raw ?: waiting.firstOrNull()?.profile?.raw ?: return
        AttentionTileCache.write(application, waiting.size, owner, clock.now().toEpochMilliseconds())
    }

    private suspend fun publishWidgets(feed: ChatFeedSnapshot, waiting: List<Waiting>) {
        val profile = stores.profiles.activeId.value ?: return
        val json = widgetSnapshot(feed, waiting, profile)
        widgets.publish(json, profile.raw, null)
    }

    private fun widgetSnapshot(feed: ChatFeedSnapshot, waiting: List<Waiting>, active: ProfileId): String {
        val now = clock.now().toEpochMilliseconds()
        val live = registry.live.value
        val names = stores.profiles.profiles.value.associate { it.id to it.profile.name }
        val sessions = JSONArray()
        feed.items
            .filter { !it.isSubagent }
            .take(WIDGET_SESSIONS)
            .forEach { item ->
                val request = waiting.firstOrNull { it.profile == item.profileId && it.request.sessionId == item.sessionId }
                sessions.put(
                    JSONObject()
                        .put("id", item.sessionId.raw)
                        .put("profileID", item.profileId.raw)
                        .put("title", item.title.orEmpty())
                        .put("project", item.projectName)
                        .put("server", names[item.profileId].orEmpty())
                        .put("busy", item.status == ChatStatus.Running)
                        .put("needsYou", item.status == ChatStatus.NeedsYou)
                        .put("requestID", request?.request?.id?.raw.orEmpty())
                        .put("updatedAt", item.lastActivityMs),
                )
            }
        val servers = JSONArray()
        stores.profiles.profiles.value.forEach {
            servers.put(
                JSONObject()
                    .put("name", it.profile.name)
                    .put("state", if (live.containsKey(it.id)) "online" else "offline")
                    .put("since", now)
                    .put("phone", false),
            )
        }
        return JSONObject()
            .put("version", 2)
            .put("profileID", active.raw)
            .put("connected", live.containsKey(active))
            .put("updatedAt", now)
            .put("lastSeenAt", now)
            .put("runningCount", feed.items.count { !it.isSubagent && it.status == ChatStatus.Running })
            .put("pendingCount", waiting.size)
            .put("sessions", sessions)
            .put("servers", servers)
            .toString()
    }

    private fun alerts(feed: ChatFeedSnapshot, waiting: List<Waiting>, appVisible: Boolean) {
        val items = feed.items.associateBy { it.ref }
        val names = stores.profiles.profiles.value.associate { it.id to it.profile.name }
        val facts =
            AlertFacts(
                liveModeActive = false,
                // The process is alive with a live connection: this is the reply watch of the preview (no service).
                replyWatchActive = registry.live.value.isNotEmpty(),
                appVisible = appVisible,
                permissionGranted = notificationsAllowed(),
                serverAlertsOn = true,
                quietHours = false,
                onWifi = null,
                wifiOnly = false,
            )
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
            when (val route = AlertPolicy.route(alert, facts)) {
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
        // Finished replies (B-ANDR-040, -048): Running -> anything else while the app is not on screen.
        for (item in feed.items) {
            val was = running[item.ref] == true
            val now = item.status == ChatStatus.Running
            running[item.ref] = now
            if (!was || now || item.status == ChatStatus.NeedsYou) continue
            val alert = finishedAlert(item, names[item.profileId])
            val route = AlertPolicy.route(alert, facts.copy(subAgentSession = item.isSubagent))
            if (route is AlertRoute.Post) shade.show(alert, route)
        }
    }

    private fun requestAlert(key: String, w: Waiting, item: ChatFeedItem?, server: String?): CodingAlert {
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

    private fun finishedAlert(item: ChatFeedItem, server: String?): CodingAlert =
        CodingAlert(
            kind = AlertKind.Complete,
            sessionId = item.sessionId.raw,
            key = AlertKeys.status(item.profileId.raw, item.sessionId.raw),
            profileId = item.profileId.raw,
            subtext = NotificationText.subtitle(server, item.projectName),
            chatTitle = item.title.orEmpty(),
            detail = NotificationText.firstReplyLine(listOf(item.preview)),
        )

    private fun metadataString(r: PendingRequest.Permission, key: String): String? =
        (r.request.metadata[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    // ---- Shade answers --------------------------------------------------------------------------------------------

    private suspend fun answer(answer: ShadeAnswer): Outcome<Unit> {
        val graph =
            registry.live.value[answer.profile]
                ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline)) // reposted with Open only
        val engine =
            (graph as? ChatGraphAccessors)?.chatRequests?.engine ?: return Outcome.Failed(Failure.Unexpected())
        val reply =
            when (val r = answer.reply) {
                ShadeReply.AllowOnce -> RequestAnswer.Permission(PermissionReply.Once)
                ShadeReply.Reject ->
                    if (answer.kind == AlertKind.Question) RequestAnswer.Decline
                    else RequestAnswer.Permission(PermissionReply.Reject)
                is ShadeReply.Text -> RequestAnswer.Question(persistentListOf(persistentListOf(r.text)))
            }
        val result = engine.answerNow(answer.request, reply)
        log.i { "shade answer ${answer.kind} ${if (result is Outcome.Ok) "sent" else "not sent"}" }
        return result
    }

    private companion object {
        const val ASKED_NOTIFY = "oc.notify.asked"
        const val REQUEST_NOTIFY = 4747
        const val WIDGET_SESSIONS = 12
        const val SEED_WAIT_MS = 10_000L
        val FILE_KEYS = listOf("filePath", "filepath", "file_path", "path", "file")
    }
}
