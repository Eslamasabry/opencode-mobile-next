package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryFacts
import io.github.eslamasabry.oc.feature.phonesetup.presentation.ExitFact
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SettingsTarget
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupKeepAlive
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupMaker
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKill
import kotlin.time.Clock
import kotlinx.coroutines.withContext

/**
 * Issue #138: some phones' battery managers kill the app while the phone setup runs. This reads what Android recorded
 * about the last process (`ApplicationExitInfo`, through [ExitHistory]) and this app's battery rights (`PowerManager`,
 * `ActivityManager`), and opens Settings for the person. It never asks for the battery exemption itself
 * (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is a Play policy risk here): the card guides, the person flips the switch.
 *
 * The setup itself runs under `SetupService`, a `specialUse` foreground service with its notification, which has no
 * `dataSync` cap; Android 15's six-hour budget does not touch it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NativeSetupKeepAlive(
    private val app: Application,
    private val exits: ExitHistory,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) : SetupKeepAlive {
    override suspend fun interruption(): SystemKill? =
        withContext(dispatchers.io) {
            val history = exits.history(HISTORY)
            SystemKill.pick(
                history.entries.map {
                    ExitFact(
                        reason = it.reason,
                        atMs = it.at.toEpochMilliseconds(),
                        memoryPressure = it.kind == ExitKind.LowMemory || it.kind == ExitKind.MemoryLimit,
                        closedBySystem = it.kind.closedBySystem,
                    )
                },
                clock.now().toEpochMilliseconds(),
            )
        }

    override suspend fun facts(): BatteryFacts =
        withContext(dispatchers.io) {
            BatteryFacts(
                maker = SetupMaker.of(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()),
                ignoringOptimizations =
                    (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                        ?.isIgnoringBatteryOptimizations(app.packageName),
                backgroundRestricted =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isBackgroundRestricted
                    else null,
            )
        }

    override fun tryOpen(target: SettingsTarget): Boolean {
        val intent =
            when (target) {
                SettingsTarget.AppDetails ->
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                is SettingsTarget.Screen ->
                    Intent().setComponent(ComponentName(target.packageName, target.className))
            }
        return try {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private companion object {
        const val HISTORY = 5
    }
}
