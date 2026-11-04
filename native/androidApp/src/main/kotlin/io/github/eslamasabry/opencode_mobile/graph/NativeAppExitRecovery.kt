package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicy
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitNotice
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitNotices
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.opencode_mobile.AppLifecycle
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android's real previous-process record, one notice per launch and one shared runtime start path.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PhoneExitNotices>())
class NativeAppExitRecovery(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val scope: AppCoroutineScope,
    private val stores: PreviewStores,
    private val runtime: BuiltinRuntime,
    private val phoneServer: PhoneServerSource,
    private val diagnostics: Diagnostics,
    private val clock: Clock,
) : PhoneExitNotices {
    private val mutable = MutableStateFlow<PhoneExitNotice?>(null)
    override val notice = mutable.asStateFlow()
    private var registered = false
    private var ran = false

    override fun dismiss() {
        mutable.value = null
    }

    /**
     * Registration only: no disk or process I/O blocks Application.onCreate or a lifecycle
     * callback.
     */
    fun start() {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (ran) return
                    ran = true
                    scope.launch {
                        try {
                            runOnce()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            /* Unknown launch information never authorizes a start. */
                        }
                    }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private suspend fun runOnce() {
        val (previous, exit) =
            withContext(dispatchers.io) {
                AppLifecycle.previousServices(app) to lastExit()
            }
        if (stores.warm() is Outcome.Failed) return
        phoneServer.refresh()
        val serverWas = "server" in previous && !phoneServer.facts.value.stoppedByPerson
        // An idle force-stop is housekeeping, not an interrupted-work problem.
        if (exit != null && exit.kind.notable && (exit.kind != ExitKind.ForceStop || serverWas)) {
            diagnostics.record(
                "android.exit",
                "kind=${exit.kind.name} reason=${exit.reason} subreason=${exit.subReason} importance=${exit.importance}",
                at = exit.at,
            )
        }
        if (!serverWas) return
        val owner =
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value?.takeIf {
                Regex("[A-Za-z0-9_-]{1,80}").matches(it)
            }
        val profile = owner?.let { stores.profiles.profile(ProfileId(it)) }
        val allowed = owner != null && profile != null && policyAllows(owner)
        val shown = exit?.let { report ->
            report.kind.phoneKind()?.let { kind -> PhoneExitNotice(kind, report.at, allowed) }
        }
        mutable.value = shown
        // The selected phone server is brought up by BuiltinRuntime's app-open owner; never issue a
        // second start.
        if (!allowed || stores.profiles.activeId.value == profile?.id) return
        if (exit?.kind?.notable != true) {
            // Clean exits use the generation-checked app-open path and create no crash-restart act.
            runtime.restoreAfterCleanExit(owner!!)
            return
        }
        val restarted = runtime.recoverAfterExit(owner!!)
        if (restarted !is Outcome.Ok || !restarted.value) return
        // The shared recovery owner records and acknowledges its immutable native receipt once.
        // Dismissal while the shared start was running is final for this launch.
        if (mutable.value === shown && shown != null) mutable.value = shown.copy(restored = true)
    }

    private fun policyAllows(profile: String): Boolean {
        val value = stores.prefs.get("oc.automation.$profile") ?: return true
        val raw = (value as? PrefValue.Str)?.value ?: return false
        return AutomationPolicy.fromJson(raw).allows(AutomationBehavior.RestartPhoneServer)
    }

    /** Matches Flutter's seen watermark; only numeric metadata crosses the OS boundary. */
    private fun lastExit(): ExitRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = app.getSystemService(ActivityManager::class.java) ?: return null
        val newest =
            manager
                .getHistoricalProcessExitReasons(app.packageName, 0, 16)
                .filter { it.processName == app.packageName }
                .maxByOrNull { it.timestamp } ?: return null
        val prefs = app.getSharedPreferences("oc_lifecycle", Context.MODE_PRIVATE)
        val at = newest.timestamp
        if (
            at <= prefs.getLong("seen_exit_timestamp", 0) ||
                at <= 0 ||
                at > clock.now().toEpochMilliseconds()
        )
            return null
        val record =
            ExitRecord(
                reason = newest.reason,
                timestampMs = at,
                subReason =
                    Regex("subreason=(\\d+)")
                        .find(newest.toString())
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: -1,
                status = newest.status,
                importance = newest.importance,
                processName = app.packageName,
                description = newest.description,
            )
        if (!prefs.edit().putLong("seen_exit_timestamp", at).commit()) return null
        return record
    }

    private fun ExitKind.phoneKind(): PhoneExitKind? =
        when (this) {
            ExitKind.ForceStop -> PhoneExitKind.ForceStop
            ExitKind.LowMemory -> PhoneExitKind.LowMemory
            ExitKind.MemoryLimit -> PhoneExitKind.MemoryLimit
            ExitKind.Crash -> PhoneExitKind.Crash
            ExitKind.Killed -> PhoneExitKind.Killed
            ExitKind.Normal,
            ExitKind.Update -> null
        }
}

@ContributesTo(AppScope::class)
interface NativeExitAccessors {
    val nativeAppExitRecovery: NativeAppExitRecovery
}
