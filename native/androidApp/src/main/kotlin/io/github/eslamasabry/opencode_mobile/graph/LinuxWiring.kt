package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.pace.DefaultPacer
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.feature.linux.host.AbsentLinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifecycle
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxResetFlow
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxResetMarker
import io.github.eslamasabry.oc.feature.linux.presentation.PortPreviewOpener
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitSource
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitState
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitStatus
import io.github.eslamasabry.oc.feature.phone.presentation.DeveloperOptionsOpened
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveInfo
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSettings
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneNavigator
import io.github.eslamasabry.oc.feature.phone.presentation.PhonePage
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProcessSource
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessReading
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/*
 * P12-LINUX: the Android bindings of :feature:linux and :feature:phone.
 *
 * The built-in Linux itself (`:android:linux`, BuiltinLinux) cannot join this APK yet: its S1 shims declare
 * io.github.eslamasabry.opencode_mobile.MainActivity / R / BuildConfig, the same FQCNs as this app (S1-LINUX report P1,
 * fixed by S3-RTSPLIT), and the proot/loader jniLibs are not in the module (P3). Until then the Linux and phone-server
 * seams are bound to honest "not on this build" values: the pages open, say so in plain words and offer nothing that
 * cannot work. Keep running and the child-process-limit card are real Android bindings. The swap to BuiltinLinux is
 * one block below (see docs/native/units/P12-LINUX.md, "Integrator").
 */
@BindingContainer
@ContributesTo(AppScope::class)
object LinuxWiring {
    private const val PREFS = "oc_native_linux"

    private fun prefs(app: Application) = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- The built-in Linux (swap point: an adapter over BuiltinLinux once :android:linux is on this classpath)

    @Provides @SingleIn(AppScope::class) fun linuxHost(): LinuxHost = AbsentLinuxHost(LinuxPresence.NotOnThisBuild)

    @Provides
    @SingleIn(AppScope::class)
    fun linuxLifecycle(): LinuxLifecycle =
        object : LinuxLifecycle {
            override suspend fun installed(): Boolean? = false

            override suspend fun erase(): Outcome<Unit> = Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))

            override suspend fun startSetup(): Outcome<Boolean> = Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))

            override suspend fun repair(): Outcome<Unit> = Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
        }

    @Provides
    @SingleIn(AppScope::class)
    fun phoneServerSource(): PhoneServerSource =
        object : PhoneServerSource {
            private val notInstalled = Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
            override val facts: StateFlow<PhoneServerFacts> =
                MutableStateFlow(PhoneServerFacts(status = RuntimeStatus(installed = false))).asStateFlow()

            override suspend fun refresh() = Unit

            override suspend fun start(): Outcome<Unit> = notInstalled

            override suspend fun stop(): Outcome<Unit> = notInstalled

            override suspend fun restart(): Outcome<Unit> = notInstalled

            override suspend fun updateOpenCode(): Outcome<Unit> = notInstalled
        }

    /** Nothing of the app's own runs without the built-in Linux: an honest empty reading. */
    @Provides
    fun phoneProcessSource(): PhoneProcessSource =
        object : PhoneProcessSource {
            override suspend fun read(): Outcome<ProcessReading> = Outcome.Ok(ProcessReading(emptyList(), count = null, limitLifted = false))

            override suspend fun stop(pid: Int): Outcome<Unit> = Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
        }

    // ---- Real Android bindings

    /** The device-wide reset marker `oc.linuxReset.pending` and the Reply speed reading it clears. */
    @Provides
    @SingleIn(AppScope::class)
    fun linuxResetMarker(app: Application): LinuxResetMarker =
        object : LinuxResetMarker {
            override suspend fun read(): String? = prefs(app).getString(LinuxResetFlow.MARKER_KEY, null)

            override suspend fun write(value: String) {
                prefs(app).edit().putString(LinuxResetFlow.MARKER_KEY, value).commit()
            }

            override suspend fun clear() {
                prefs(app).edit().remove(LinuxResetFlow.MARKER_KEY).commit()
            }

            override suspend fun forgetReplySpeed() {
                prefs(app).edit().remove("oc.replySpeed.inApp").commit()
            }
        }

    /** A port preview: an app-authored loopback URL, still through ExternalLinkPolicy (AGENTS invariant 8). */
    @Provides
    fun portPreviewOpener(app: Application): PortPreviewOpener =
        PortPreviewOpener { url ->
            when (val decision = ExternalLinkPolicy().check(url, LinkOrigin.AppAuthored)) {
                is LinkDecision.Open -> app.startView(decision.link.uri)
                is LinkDecision.AskFirst, is LinkDecision.Refuse -> false
            }
        }

    @Provides
    fun keepAliveSettings(app: Application): KeepAliveSettings =
        object : KeepAliveSettings {
            override suspend fun info(): KeepAliveInfo =
                KeepAliveInfo(
                    manufacturer = Build.MANUFACTURER.orEmpty(),
                    brand = Build.BRAND.orEmpty(),
                    sdkInt = Build.VERSION.SDK_INT,
                    batteryOptimizationIgnored =
                        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(app.packageName) == true,
                )

            override suspend fun open(setting: KeepAliveSetting): Boolean =
                when (setting) {
                    KeepAliveSetting.Battery -> app.startSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    // No public intent opens every maker's autostart screen; the app's own page holds the switches.
                    KeepAliveSetting.Autostart, KeepAliveSetting.AppDetails ->
                        app.startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                }
        }

    @Provides
    fun childLimitSource(app: Application): ChildLimitSource =
        object : ChildLimitSource {
            override suspend fun status(): ChildLimitStatus {
                val resolver = app.contentResolver
                // Android 14+ "Disable child process restrictions" writes this flag false (plan 11 §7.2); never written here.
                val monitor = Settings.Global.getString(resolver, "settings_enable_monitor_phantom_procs")
                return ChildLimitStatus(
                    sdkInt = Build.VERSION.SDK_INT,
                    state =
                        when (monitor) {
                            "false" -> ChildLimitState.Lifted
                            "true" -> ChildLimitState.Enforced
                            else -> ChildLimitState.Unknown
                        },
                    developerOptionsEnabled = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
                    canOpenSettings = true,
                )
            }

            override suspend fun openDeveloperOptions(): DeveloperOptionsOpened =
                when {
                    app.startSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) -> DeveloperOptionsOpened.Developer
                    app.startSettings(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) -> DeveloperOptionsOpened.About
                    else -> DeveloperOptionsOpened.None
                }

            override suspend fun lastAndroidKill(): Instant? = null

            override fun queuesOften(): Boolean = false

            override suspend fun hiddenAt(): Instant? =
                prefs(app).getLong("oc.childLimitCardHidden", -1L).takeIf { it >= 0 }?.let(Instant::fromEpochMilliseconds)

            override suspend fun hide(at: Instant) {
                prefs(app).edit().putLong("oc.childLimitCardHidden", at.toEpochMilliseconds()).commit()
            }
        }

    /** PhonePage → routes. Setup pages are not in this build: they open the Linux page, which says so plainly. */
    @Provides
    fun phoneNavigator(nav: AppNavigator): PhoneNavigator =
        object : PhoneNavigator {
            override fun open(page: PhonePage) {
                nav.open(
                    when (page) {
                        PhonePage.Running -> PhoneRoute.Running
                        PhonePage.KeepRunning -> PhoneRoute.KeepRunning
                        PhonePage.Linux, PhonePage.SetupStart, PhonePage.SetupProgress -> LinuxRoute.Home
                    },
                )
            }

            override fun back() = nav.back()
        }

    /**
     * The Pacer the phone and Linux pages tick on (S3-PHONEUI R3: none was bound). Their timers run only while their
     * page is shown, so a foreground-only visibility is exact for them. Remove this when the shell binds the app's own.
     */
    @Provides @SingleIn(AppScope::class) fun pacer(): Pacer = DefaultPacer(MutableStateFlow(Visibility.Foreground))

    private fun Context.startSettings(intent: Intent): Boolean =
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    private fun Context.startView(uri: String): Boolean = startSettings(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}
