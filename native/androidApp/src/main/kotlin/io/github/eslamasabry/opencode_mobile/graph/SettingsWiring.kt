package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.NotifyChannel
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.android.notifications.R as NotifyR
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.LogSink
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.AnalyticsEngine
import io.github.eslamasabry.oc.engine.analytics.AnalyticsStore
import io.github.eslamasabry.oc.engine.analytics.DeviceClass
import io.github.eslamasabry.oc.engine.analytics.NoOpSink
import io.github.eslamasabry.oc.engine.analytics.OsFamily
import io.github.eslamasabry.oc.engine.analytics.ReleaseChannel
import io.github.eslamasabry.oc.engine.analytics.SystemProps
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.feature.chats.presentation.ClosedSettingsDoor
import io.github.eslamasabry.oc.feature.chats.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRun
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRunFlow
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRunPreferences
import io.github.eslamasabry.oc.feature.settings.presentation.AboutFacts
import io.github.eslamasabry.oc.feature.settings.presentation.AnalyticsChoices
import io.github.eslamasabry.oc.feature.settings.presentation.DiagnosticsExport
import io.github.eslamasabry.oc.feature.settings.presentation.DiagnosticsReport
import io.github.eslamasabry.oc.feature.settings.presentation.EngineAnalyticsChoices
import io.github.eslamasabry.oc.feature.settings.presentation.HandledErrors
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationChange
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationKeys
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationSettings
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChoices
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.feature.settings.presentation.PreferencesChoices
import io.github.eslamasabry.oc.feature.settings.presentation.ReportError
import io.github.eslamasabry.oc.feature.settings.presentation.ReportFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsHost
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOpeners
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateSettings
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceSettings
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.BackgroundRoute
import io.github.eslamasabry.oc.ui.routes.IntegrationsRoute
import io.github.eslamasabry.oc.ui.routes.DiagnosticsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import io.github.eslamasabry.oc.ui.routes.VoiceRoute
import io.github.eslamasabry.oc.ui.shell.nav.SystemMotion
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.shareAppMessage
import java.io.File
import java.util.Locale
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/*
 * P12-SETTINGS: the Android half of Settings and first run. Everything here is a binding or a thin platform adapter;
 * the logic lives in :feature:settings / :feature:onboarding and is tested there.
 */

private const val REPO = "https://github.com/Eslamasabry/opencode-mobile-next"

/** What MainActivity reads: the theme and language at the root, and the first-run start. */
@ContributesTo(AppScope::class)
interface SettingsAppGraph {
    val appPreferences: AppPreferences
    val firstRunStarter: FirstRunStarter
}

/**
 * The ONE device-preferences facade for the app graph (B-STATE-126). It is built after the preference file is warm
 * (its first read must see the stored values); until then [value] is the defaults.
 */
@SingleIn(AppScope::class)
@Inject
class AppPreferences(stores: PreviewStores, appScope: AppCoroutineScope) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val ready = CompletableDeferred<Preferences?>()
    private val mutable = MutableStateFlow(DevicePreferences.Defaults)
    val value: StateFlow<DevicePreferences> = mutable.asStateFlow()

    /** What `KitTheme` wears, live (B-KIT-010). */
    val appearance: StateFlow<KitAppearance> =
        mutable.map(PreferenceMapping::appearance).distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, PreferenceMapping.appearance(mutable.value))

    /** The chosen language tag; null follows the phone (B-L10N-001). */
    val localeTag: StateFlow<String?> =
        mutable.map(PreferenceMapping::localeTag).distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, PreferenceMapping.localeTag(mutable.value))

    /** [localeTag] once the stored value is known (never the defaults before warm-up), for the Activity's recreate. */
    val storedLocaleTag: Flow<String?> = flow { ready.await()?.let { p -> emitAll(p.locale.map { it.code }.distinctUntilChanged()) } }

    init {
        scope.launch {
            if (stores.warm() is Outcome.Failed) {
                ready.complete(null)
                return@launch
            }
            val prefs = Preferences(stores.prefs, stores.lanes.app, scope)
            ready.complete(prefs)
            prefs.value.collect { mutable.value = it }
        }
    }

    suspend fun preferences(): Preferences? = ready.await()
}

/** App-authored places only; a URL from a server never passes here. */
private fun Context.view(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SettingsHost>())
@Inject
class AndroidSettingsHost(
    private val application: Application,
    private val stores: PreviewStores,
    private val nav: AppNavigator,
    private val connections: ServerConnections,
    prefs: AppPreferences,
    platform: PlatformCapabilities,
    motion: SystemMotion,
    buildInfo: AppBuildInfo,
    appScope: AppCoroutineScope,
    analyticsEngine: AnalyticsEngine,
    private val dispatchers: AppDispatchers,
    private val diagnosticsLog: Diagnostics,
    updateSettings: AppUpdateSettings, // P12-FIX-PLATFORM-GIT: Settings › Updates on the real updater
    private val voiceSettings: AndroidVoiceSettings,
    private val gitHubReports: GitHubProblemReporter, // BUILD-DIAGNOSTICS-ATTENTION
) : SettingsHost {
    private val scope = CoroutineScope(appScope.coroutineContext)

    /** Rows whose page or engine is not in this build are absent (02 §3.7), never dead. */
    private val shipped =
        platform.copy(
            builtinLinux = false, voice = voiceSettings.offered, readAloud = false, backgroundService = true,
            inAppUpdater = true, desktopReleaseCheck = false,
        )

    override val facts: StateFlow<SettingsFacts> =
        combine(connections.servers, stores.profiles.activeId, motion.animatorScale) { servers, active, scale ->
                val live = servers.firstOrNull { it.id == active && it.connected } ?: servers.firstOrNull { it.connected }
                SettingsFacts(
                    platform = shipped,
                    server = live?.let { ServerLine(it.name, onPhone = false) },
                    dynamicColour = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    systemMotionOff = scale == 0f,
                    diagnostics = true,
                    opens = OPENS,
                    surfaceStyles = false,
                    transcriptDisplay = false,
                )
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                SettingsFacts(
                    platform = shipped, dynamicColour = Build.VERSION.SDK_INT >= 31, diagnostics = true, opens = OPENS,
                    surfaceStyles = false, transcriptDisplay = false,
                ),
            )

    override val choices: PersonalChoices = PreferencesChoices(prefs::preferences, prefs.value, scope)

    override val openers: SettingsOpeners = SettingsOpeners { place ->
        when (place) {
            SettingsOutside.Servers -> nav.open(ServersRoute.Home)
            SettingsOutside.Providers, SettingsOutside.Tools -> nav.open(IntegrationsRoute)
            SettingsOutside.StarOnGitHub -> application.view(REPO)
            SettingsOutside.WhatsNew, SettingsOutside.ReleaseNotes -> application.view("$REPO/releases")
            SettingsOutside.PrivacyPolicy ->
                application.view(if (prefs.localeTag.value == "ar") "$REPO/blob/master/assets/l10n/PRIVACY.ar.md" else "$REPO/blob/master/PRIVACY.md")
            SettingsOutside.ShareApp -> scope.launch { shareApp() }
            SettingsOutside.AndroidNotificationSettings -> openNotificationSettings()
            SettingsOutside.VoiceSetup -> nav.open(VoiceRoute.Settings)
            SettingsOutside.KeepRunning -> nav.open(BackgroundRoute.Home) // BUILD-ANDROID-BG-PLATFORM
            SettingsOutside.VoiceNotices -> application.view("$REPO/blob/master/THIRD_PARTY_NOTICES.md")
            SettingsOutside.KeptDiagnostics -> nav.open(DiagnosticsRoute.Kept)
            SettingsOutside.ReportOnGitHub ->
                gitHubReports.send(connections.servers.value.count { it.connected }.let { if (it > 0) "Connected to a server" else "Not connected" })
            else -> Unit // Absent rows: OPENS keeps them off screen.
        }
    }

    override val analytics: AnalyticsChoices = EngineAnalyticsChoices(analyticsEngine)
    override val voice: VoiceSettings? = voiceSettings.takeIf { it.offered }
    override val notifications: NotificationSettings = AndroidNotificationSettings(application, stores, prefs, scope)
    override val updates: UpdateSettings? = updateSettings
    override val about: AboutFacts =
        AboutFacts(version = buildInfo.versionName, build = buildInfo.versionCode.toString(), whatsNew = true, notices = false)

    override val diagnostics: DiagnosticsExport =
        object : DiagnosticsExport {
            override suspend fun report(): Outcome<String> {
                val servers = connections.servers.value
                val p = prefs.value.value
                val facts =
                    ReportFacts(
                        appVersion = buildInfo.versionName,
                        build = buildInfo.versionCode.toString(),
                        platform = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        language = p.locale.code ?: "system",
                        textSize = p.textSize.wire,
                        motion = p.effects.motion.wire,
                        savedServers = servers.size,
                        connectedServers = servers.count { it.connected },
                        errors =
                            gitHubReports.keptErrors()?.map { (at, source, message) -> ReportError(at, source, message) }
                                ?: diagnosticsLog.errors.value.map { ReportError(it.at.toString(), it.source, it.message, it.occurrences) },
                    )
                return DiagnosticsReport.text(facts).ok()
            }

            override suspend fun copy(text: String): Outcome<Unit> = withContext(dispatchers.main) {
                attempt({ Failure.StorageRefused() }) {
                    val clipboard = application.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("OpenCode Mobile report", text))
                }
            }

            override suspend fun share(text: String): Outcome<Unit> = withContext(dispatchers.main) {
                attempt({ Failure.StorageRefused() }) { sendText(text) }
            }
        }

    private fun sendText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        application.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun shareApp() {
        val text = getString(Res.string.shareAppMessage, REPO)
        withContext(dispatchers.main) { attempt({ Failure.StorageRefused() }) { sendText(text) } }
    }

    private fun openNotificationSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }

    private companion object {
        val OPENS: Set<SettingsOutside> =
            setOf(
                SettingsOutside.Servers, SettingsOutside.StarOnGitHub, SettingsOutside.WhatsNew,
                SettingsOutside.ReleaseNotes, SettingsOutside.PrivacyPolicy, SettingsOutside.ShareApp,
                SettingsOutside.AndroidNotificationSettings, SettingsOutside.VoiceSetup, SettingsOutside.VoiceNotices,
                SettingsOutside.ReportOnGitHub, SettingsOutside.KeptDiagnostics,
                SettingsOutside.KeepRunning,
                SettingsOutside.Providers, SettingsOutside.Tools,
            )
    }
}

/**
 * Notification choices under the Flutter keys (`oc.notify.*`, so a migrated install keeps them and the notification
 * engine reads the same values), Android's own on/off, and a real test notification on the coding channel
 * (B-ANDR-013: the test ignores live mode, never the platform permission).
 */
class AndroidNotificationSettings(
    private val application: Application,
    private val stores: PreviewStores,
    private val prefs: AppPreferences,
    scope: CoroutineScope,
) : NotificationSettings {
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val words = NotifyWords(application) { prefs.localeTag.value }
    private val mutable = MutableStateFlow(read())
    override val facts: StateFlow<NotificationFacts> = mutable.asStateFlow()

    init {
        scope.launch {
            stores.warm()
            mutable.value = read()
            stores.prefs.changes.collect { keys -> if (keys.any { it.startsWith("oc.notify.") }) mutable.value = read() }
        }
    }

    /** Re-reads Android's on/off: the page calls it on every visit through [facts] refresh below. */
    private fun read(): NotificationFacts {
        fun flag(key: String, fallback: Boolean): Boolean =
            readOrNull { (stores.prefs.get(key) as? PrefValue.Bool)?.value } ?: fallback
        fun minute(key: String): Long? = readOrNull { (stores.prefs.get(key) as? PrefValue.Int64)?.value }
        return NotificationFacts(
            blocked = !manager.areNotificationsEnabled(),
            finishedWork = flag(NotificationKeys.FINISHED, true),
            questions = flag(NotificationKeys.REQUESTS, true),
            quietHours = NotificationKeys.quiet(minute(NotificationKeys.QUIET_START), minute(NotificationKeys.QUIET_END)),
            wifiOnly = flag(NotificationKeys.WIFI_ONLY, false),
        )
    }

    /** Android's on/off can change outside the app (its settings, a long-press on a notification). */
    override fun refresh() {
        mutable.value = read()
    }

    override suspend fun set(change: NotificationChange): Outcome<Unit> {
        val result =
            stores.prefs.commit(stores.lanes.app) {
                when (change) {
                    is NotificationChange.FinishedWork -> putBool(NotificationKeys.FINISHED, change.on)
                    is NotificationChange.Questions -> putBool(NotificationKeys.REQUESTS, change.on)
                    is NotificationChange.WifiOnly -> putBool(NotificationKeys.WIFI_ONLY, change.on)
                    is NotificationChange.Quiet -> {
                        val hours = change.hours
                        if (hours == null) {
                            remove(NotificationKeys.QUIET_START)
                            remove(NotificationKeys.QUIET_END)
                        } else {
                            putLong(NotificationKeys.QUIET_START, hours.start.toLong())
                            putLong(NotificationKeys.QUIET_END, hours.end.toLong())
                        }
                    }
                }
            }
        mutable.value = read()
        return result
    }

    override suspend fun sendTest(): Outcome<Unit> {
        mutable.value = read()
        if (!manager.areNotificationsEnabled()) return Failure.Refused(RefusalReason.NotificationsBlocked).failed()
        return attempt({ Failure.StorageRefused() }) {
            ChannelRegistry(manager, words).ensure()
            val notification =
                Notification.Builder(application, NotifyChannel.CodingAction.id)
                    .setSmallIcon(NotifyR.drawable.notify_ic_status)
                    .setContentTitle(words.text(NotifyR.string.notify_test_title))
                    .setContentText(words.text(NotifyR.string.notify_test_body))
                    .setAutoCancel(true)
                    .build()
            manager.notify(TEST_ID, notification)
        }
    }

    private inline fun <T> readOrNull(read: () -> T?): T? =
        try {
            read()
        } catch (_: IllegalStateException) {
            null // the file is not warm yet: defaults until init re-reads
        }

    private companion object {
        const val TEST_ID = 0x7E57
    }
}

/** The Conversations gear opens Settings (replaces the preview door that opened Servers). */
@ContributesBinding(AppScope::class, replaces = [ClosedSettingsDoor::class, PreviewSettingsDoor::class])
@Inject
class SettingsGearDoor(private val navigator: AppNavigator) : SettingsDoor {
    override val available: Boolean = true

    override fun open() {
        navigator.open(SettingsRoute.Home)
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object SettingsBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun analytics(
        application: Application,
        appScope: AppCoroutineScope,
        dispatchers: AppDispatchers,
        clock: Clock,
        buildInfo: AppBuildInfo,
    ): AnalyticsEngine =
        AnalyticsEngine(
            scope = CoroutineScope(appScope.coroutineContext),
            dispatchers = dispatchers,
            clock = clock,
            store = FileAnalyticsStore(File(application.filesDir, "native/analytics/queue.json")),
            sink = NoOpSink,
            system =
                SystemProps(
                    appVersion = buildInfo.versionName,
                    appBuild = buildInfo.versionCode,
                    channel =
                        when (buildInfo.channel) {
                            Channel.Stable -> ReleaseChannel.Stable
                            Channel.Preview -> ReleaseChannel.Preview
                            Channel.Desktop -> ReleaseChannel.Desktop
                        },
                    os = OsFamily.Android,
                    osMajor = Build.VERSION.SDK_INT,
                    device = DeviceClass.Phone,
                    lang = Locale.getDefault().language.takeIf { it in setOf("en", "ar", "es", "ja", "pt", "ru", "zh") } ?: "en",
                    isDebug = buildInfo.debuggable,
                ),
        ).also {
            it.start()
            appScope.launch { it.openGate() }
        }

    /** The one error list Report a problem reads; the app's warnings and errors reach it from the log root at once. */
    @Provides
    @SingleIn(AppScope::class)
    fun diagnostics(redactor: Redactor, clock: Clock): Diagnostics =
        Diagnostics(redactor, clock).also { DiagnosticsLogSink.target = it }
}

/**
 * The redacting log root's delegate (OcApplication): warnings and errors the app logs become Report a problem's
 * handled errors once the graph has built [Diagnostics]. Entries arrive already redacted; Diagnostics sanitises again.
 */
object DiagnosticsLogSink : LogSink {
    @Volatile
    var target: Diagnostics? = null

    override fun write(entry: LogEntry) {
        val error = HandledErrors.fromLog(entry) ?: return
        target?.record(error.source, error.message)
    }
}

/**
 * Feeds Report a problem from the connections (Flutter feeds handled connection errors into its report): a saved
 * server's stream stopping or dropping is recorded by its failure kind, never its address. Started once with first run.
 */
@SingleIn(AppScope::class)
@Inject
class DiagnosticsFeed(
    private val registry: ConnectionRegistry,
    private val diagnostics: Diagnostics,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            registry.live
                .flatMapLatest { live ->
                    if (live.isEmpty()) emptyFlow()
                    else live.values.map { graph -> graph.session.status.withPrevious() }.merge()
                }
                .collect { (previous, next) ->
                    HandledErrors.fromStream(previous, next)?.let { diagnostics.record(it.source, it.message) }
                }
        }
    }

    private fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = flow {
        var previous: T? = null
        collect { next ->
            emit(previous to next)
            previous = next
        }
    }
}

/** The analytics document in app-private storage (plan 16 §5.6); the engine wraps every call. */
private class FileAnalyticsStore(private val file: File) : AnalyticsStore {
    override suspend fun read(): String? = if (file.exists()) file.readText() else null

    override suspend fun write(text: String) {
        file.parentFile?.mkdirs()
        val next = File(file.parentFile, file.name + ".tmp")
        next.writeText(text)
        if (!next.renameTo(file)) error("analytics store not saved")
    }

    override suspend fun delete() {
        file.delete()
    }
}

/**
 * First run (B-NAV-017): once the saved servers are readable, a device with none starts on the welcome; the first
 * server that answers while the person is still in setup lands them on Conversations.
 */
@SingleIn(AppScope::class)
@Inject
class FirstRunStarter(
    private val diagnosticsFeed: DiagnosticsFeed,
    private val stores: PreviewStores,
    private val connections: ServerConnections,
    private val nav: AppNavigator,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private var started = false
    private val flow =
        FirstRunFlow(
            FirstRun(
                object : FirstRunPreferences {
                    override suspend fun read(key: String): String? = (stores.prefs.get(key) as? PrefValue.Str)?.value

                    override suspend fun write(key: String, value: String) {
                        stores.prefs.commit(stores.lanes.app) { putString(key, value) }
                    }
                },
            ),
            nav,
        )

    /** Main thread, from MainActivity.onCreate; idempotent across Activity recreation. */
    fun start() {
        if (started) return
        started = true
        diagnosticsFeed.start() // Report a problem's connection errors, from the first connect on
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            val saved = stores.profiles.profiles.value.map { it.id }.toSet()
            withContext(dispatchers.main) { flow.start(stores.profiles.activeId.value, saved) }
            connections.servers
                .map { list -> list.any { it.connected } }
                .distinctUntilChanged()
                .collect { connected ->
                    if (connected) withContext(dispatchers.main) { flow.onConnected(nav.top.value) }
                }
        }
    }
}

/** The phone's own language, for "Use system language". */
internal fun systemLocaleTag(): String = Resources.getSystem().configuration.locales[0].toLanguageTag()

/**
 * The app language on Android (B-L10N-001). Words come from Compose resources, which read the platform locale, so a
 * new choice sets the process default and recreates the Activity: the saved back stack and the ViewModels survive, so
 * the person stays on the Language page, now in the new language and direction. The first Activity of a process reads
 * the stored choice straight from the preference file (read-only), so an Arabic start never flashes English.
 */
object AppLocale {
    /** The tag the current resources were built with; null until the first Activity attaches. */
    @Volatile
    var applied: String? = null
        private set

    fun wrap(base: Context): Context {
        val tag = applied ?: (storedTag(base) ?: systemLocaleTag()).also { applied = it }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** True when [tag] (null = the phone's) differs from what is applied; records it, so the caller recreates once. */
    fun changed(tag: String?): Boolean {
        val want = tag ?: systemLocaleTag()
        if (want == applied) return false
        applied = want
        return true
    }

    private fun storedTag(base: Context): String? {
        val file = base.getSharedPreferences(
            io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec.FILE_NAME, Context.MODE_PRIVATE,
        )
        val raw = try {
            file.getString(io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec.KEY_PREFIX + "oc.appLocale", null)
        } catch (_: ClassCastException) {
            null
        }
        return io.github.eslamasabry.oc.engine.settings.prefs.LocaleChoice.entries
            .firstOrNull { it.code != null && it.code == raw }?.code
    }
}
