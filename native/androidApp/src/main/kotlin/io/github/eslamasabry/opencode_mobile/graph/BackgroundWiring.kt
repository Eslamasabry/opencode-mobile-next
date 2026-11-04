package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.background.AndroidLiveServicePort
import io.github.eslamasabry.oc.android.background.AndroidPauseFacts
import io.github.eslamasabry.oc.android.background.KeyValueDismissedPause
import io.github.eslamasabry.oc.android.background.KeyValueLiveOptIn
import io.github.eslamasabry.oc.android.background.LiveModeController
import io.github.eslamasabry.oc.android.background.LiveModeError
import io.github.eslamasabry.oc.android.background.LiveModeState
import io.github.eslamasabry.oc.android.background.LiveServiceBody
import io.github.eslamasabry.oc.android.background.LiveServiceSignals
import io.github.eslamasabry.oc.android.background.PauseNotice
import io.github.eslamasabry.oc.android.background.PauseNoticePhase
import io.github.eslamasabry.oc.android.background.PauseNoticeState
import io.github.eslamasabry.oc.android.background.PauseReceiptStore
import io.github.eslamasabry.oc.android.background.SharedPreferencesReceiptFile
import io.github.eslamasabry.oc.android.background.pauseGateway
import io.github.eslamasabry.oc.android.background.PauseReason as NativePauseReason
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLive
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLiveFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Unit BUILD-ANDROID-BG-PLATFORM: the live background connection made real in the app. One [LiveModeController] per
 * process over the frozen `BackgroundConnectionService` shell (`../BackgroundConnectionService.kt`), its durable pause
 * receipt and the Flutter opt-in key; the Background page (`:feature:background`) reads it through [BackgroundLive].
 *
 * Confinement (LiveModeController's rule): every call runs on the main dispatcher, so [main] is the app scope moved to
 * `dispatchers.main`. The controller is built only after the Flutter prefs file is warm (its opt-in is read at once).
 */
@SingleIn(AppScope::class)
@Inject
class BackgroundHub(
    private val application: Application,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val clock: Clock,
    private val stores: PreviewStores,
    private val activities: ResumedActivities,
) {
    private val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    val signals = LiveServiceSignals()
    private val optIn by lazy { KeyValueLiveOptIn(stores.prefs, stores.lanes.app) }
    val receipts: PauseReceiptStore by lazy {
        PauseReceiptStore(SharedPreferencesReceiptFile(application), AndroidPauseFacts(application, clock), optIn)
    }
    private val ready = CompletableDeferred<Pair<LiveModeController, PauseNotice>>()
    private val facts = MutableStateFlow(BackgroundLiveFacts(supported = true))
    val live: StateFlow<BackgroundLiveFacts> = facts.asStateFlow()

    /** The service shell's body; its stop callback reaches the controller once the app graph built it. */
    val serviceBody: LiveServiceBody by lazy {
        LiveServiceBody(signals, receipts) { reason, pause ->
            main.launch { ready.await().first.onServiceStopped(reason, pause) }
        }
    }

    private var started = false

    /** Called once from OcApplication-time warm-up (the first [BackgroundLive] use triggers it too). */
    fun start() {
        if (started) return
        started = true
        main.launch {
            stores.warm()
            val port = AndroidLiveServicePort(application, signals, receipts, NotificationPermission(application, activities))
            val controller = LiveModeController(port, optIn, { true }, main, clock)
            val notice = PauseNotice(controller.pauseGateway(main), KeyValueDismissedPause(stores.prefs, stores.lanes.app), main, clock)
            ready.complete(controller to notice)
            launch { combine(controller.state, notice.notices) { s, n -> project(s, n) }.collect { facts.value = it } }
            controller.restore()
        }
    }

    suspend fun controller(): LiveModeController {
        start()
        return ready.await().first
    }

    suspend fun notice(): PauseNotice {
        start()
        return ready.await().second
    }

    fun launchOnMain(block: suspend CoroutineScope.() -> Unit) {
        main.launch(block = block)
    }

    private fun project(s: LiveModeState, n: PauseNoticeState?): BackgroundLiveFacts =
        BackgroundLiveFacts(
            supported = true,
            enabled = s.enabled,
            active = s.active,
            busy = s.busy,
            stoppedByAndroid = s.stoppedByAndroidTimeout,
            batteryUnrestricted = s.batteryOptimizationIgnored,
            pause =
                n?.let {
                    val at = java.time.Instant.ofEpochMilli(it.atMs).atZone(ZoneId.systemDefault())
                    val today = java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(ZoneId.systemDefault())
                    PauseFacts(
                        reason = reasonOf(it.reason),
                        clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at),
                        day =
                            if (at.toLocalDate() == today.toLocalDate()) null
                            else DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(at),
                        resumeFailed = it.phase == PauseNoticePhase.Failed,
                    )
                },
            resuming = n?.phase == PauseNoticePhase.Resuming,
        )

    private fun reasonOf(r: NativePauseReason): PauseReason =
        when (r) {
            NativePauseReason.TimeLimit -> PauseReason.TimeLimit
            NativePauseReason.BatteryRestricted -> PauseReason.BatteryRestricted
            NativePauseReason.UserStopped -> PauseReason.UserStopped
            NativePauseReason.AppUpdated -> PauseReason.AppUpdated
            NativePauseReason.DeviceRestarted -> PauseReason.DeviceRestarted
            NativePauseReason.Interrupted, NativePauseReason.None -> PauseReason.Interrupted
        }
}

/** OS components (the service shell) reach the hub through the graph. */
@ContributesTo(AppScope::class)
interface BackgroundAccessors {
    val background: BackgroundHub
}

internal val Context.backgroundHub: BackgroundHub
    get() = (appGraph as BackgroundAccessors).background

/** The Background page's port over the controller. Each command waits for Android, never for the page. */
@ContributesBinding(AppScope::class)
@Inject
class AndroidBackgroundLive(private val hub: BackgroundHub) : BackgroundLive {
    override val facts: StateFlow<BackgroundLiveFacts> get() = hub.live

    init {
        hub.start()
    }

    override suspend fun setEnabled(on: Boolean): Outcome<Boolean> {
        val controller = hub.controller()
        val landed = onMain { controller.setEnabled(on) }
        return when (controller.state.value.lastError) {
            null -> landed.ok()
            LiveModeError.NotificationsRequired -> Failure.Refused(RefusalReason.NotificationsBlocked).failed()
            LiveModeError.CouldNotChange, LiveModeError.AndroidAppOnly -> landed.ok()
        }
    }

    override suspend fun resume(): Outcome<Boolean> {
        val notice = hub.notice()
        return onMain { notice.resume() }.ok()
    }

    override suspend fun dismissPause() {
        val notice = hub.notice()
        onMain { notice.dismiss() }
    }

    override suspend fun allowUnrestrictedBattery() {
        val controller = hub.controller()
        onMain { controller.requestBatteryOptimizationExemption() }
    }

    override fun refresh() {
        hub.launchOnMain {
            hub.notice().refresh()
            hub.controller().refreshStatus()
        }
    }

    private suspend fun <T> onMain(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        hub.launchOnMain { result.complete(block()) }
        return result.await()
    }
}

/**
 * The notification permission (API 33+): a request shows Android's prompt on the resumed activity and waits a bounded
 * time for the person's answer; the switch is tried again by the person, never automatically.
 */
private class NotificationPermission(private val app: Application, private val activities: ResumedActivities) : PermissionGate {
    override suspend fun status(permission: AppPermission): PermissionStatus =
        when {
            permission != AppPermission.Notifications -> PermissionStatus.NotApplicable
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PermissionStatus.Granted
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ->
                PermissionStatus.Granted
            else -> PermissionStatus.Denied
        }

    override suspend fun request(permission: AppPermission): PermissionStatus {
        if (status(permission) == PermissionStatus.Granted) return PermissionStatus.Granted
        val activity = activities.current ?: return PermissionStatus.Denied
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        return withTimeoutOrNull(PROMPT_WAIT) {
            while (status(permission) != PermissionStatus.Granted) delay(POLL)
            PermissionStatus.Granted
        } ?: PermissionStatus.Denied
    }

    override fun openSettings(permission: AppPermission) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                .setData(Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    private companion object {
        const val REQUEST_CODE = 0x4B47
        val PROMPT_WAIT = 30.seconds
        val POLL = 200.milliseconds
    }
}

/**
 * Where a phone-setup notification tap lands (B-ANDR-050; Flutter `phone_setup_routes.dart`). The ongoing setup row
 * (`phone_setup`) opens the setup's progress, which in this build is the Linux page that shows it; a tap while that
 * page is already on top changes nothing. The result row (`phone_setup_done`) just brings the app back (null = land).
 */
internal object SetupTapRoutes {
    fun routeFor(action: io.github.eslamasabry.oc.android.tiles.LaunchAction, top: io.github.eslamasabry.oc.ui.routes.AppRoute?):
        SetupTap =
        when (action) {
            io.github.eslamasabry.oc.android.tiles.LaunchAction.PhoneSetup ->
                if (top == io.github.eslamasabry.oc.ui.routes.LinuxRoute.Home) SetupTap.Stay
                else SetupTap.Open(io.github.eslamasabry.oc.ui.routes.LinuxRoute.Home)
            io.github.eslamasabry.oc.android.tiles.LaunchAction.PhoneSetupDone -> SetupTap.Land
            else -> SetupTap.NotSetup
        }
}

internal sealed interface SetupTap {
    data class Open(val route: io.github.eslamasabry.oc.ui.routes.AppRoute) : SetupTap

    /** The setup page is already on top. */
    data object Stay : SetupTap

    /** Bring the app back where it was going (Conversations). */
    data object Land : SetupTap

    data object NotSetup : SetupTap
}
