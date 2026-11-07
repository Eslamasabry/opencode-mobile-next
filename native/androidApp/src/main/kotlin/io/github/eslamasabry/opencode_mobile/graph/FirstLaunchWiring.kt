package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.oc.android.tiles.PinnedShortcuts
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.opencode_mobile.MainActivity
import io.github.eslamasabry.opencode_mobile.hooks.FileHookMarker
import io.github.eslamasabry.opencode_mobile.hooks.FirstLaunchHooks
import io.github.eslamasabry.opencode_mobile.hooks.HookStep
import io.github.eslamasabry.opencode_mobile.hooks.JournalSource
import io.github.eslamasabry.opencode_mobile.hooks.StepResult
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * E00-S07: runs [FirstLaunchHooks] (plan 05 §7.9) on the first app open after the Flutter → native update. It starts
 * when an activity of this process is first resumed — never from a receiver-started process, where Android would
 * refuse the foreground starts — and waits for the migration gate and the stores. Steps, in order:
 *
 * - `H1.locale`: mirror `oc.appLocale` into the per-app language (Android 13+), only when the system has none.
 * - `H2.schedules`: re-arm every migrated scheduled prompt (WorkManager, plus exact alarms where granted; B-148).
 * - `H2.widgets`, `H2.tiles`: ask every placed widget and both Quick Settings tiles to redraw.
 * - `H2.shortcuts`: re-publish the launcher's pinned conversation shortcuts under the same ids.
 * - `H2.live`: resume live mode if `oc.keepLiveInBackground` was on, through the restriction-aware restore (a durable
 *   Android pause, such as the Android 15 `dataSync` 6 h cap, is never overridden).
 * - `H2.phoneServer`: bring back the phone server 2.1.4 left running (B-147).
 *
 * H3 (notification channels) is [PlatformHub.start]'s `ChannelRegistry.ensure()` on every start: it creates missing
 * ids only and never touches an existing channel, so every 2.1.4 channel keeps its id and the person's importance.
 */
@SingleIn(AppScope::class)
@Inject
class FirstLaunchWiring(
    private val application: Application,
    appScope: AppCoroutineScope,
    private val stores: PreviewStores,
    private val background: BackgroundHub,
    private val runtime: BuiltinRuntime,
    private val clock: kotlin.time.Clock,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val log = AppLog.tagged("FirstLaunch")
    private val launched = AtomicBoolean(false)

    /**
     * Called once from OcApplication (main process). Arms the run for the first resumed activity. On the preview
     * package there is no journal, so the hooks find nothing adopted and write nothing.
     */
    fun start() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (!launched.compareAndSet(false, true)) return
                    application.unregisterActivityLifecycleCallbacks(this)
                    scope.launch { run() }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private suspend fun run() {
        // The journal says "complete" only once the gate has opened; warm() waits for that (plan 05 §7.3).
        if (!warm()) return
        val native = File(application.filesDir, "native")
        val hooks =
            FirstLaunchHooks(
                JournalSource(File(native, "migration.json")),
                FileHookMarker(File(native, "first-launch.json")),
                steps(),
                { clock.now().toEpochMilliseconds() },
            )
        val report =
            try {
                hooks.run()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                log.w { "first-launch hooks could not save their marker; they run again next time" }
                return
            }
        if (report != null) {
            log.i { "first-launch hooks ${report.state.wire}: ran ${report.ran}, later ${report.later}" }
        }
    }

    private suspend fun warm(): Boolean = stores.warm() is Outcome.Ok

    private fun step(id: String, body: suspend () -> StepResult): HookStep =
        object : HookStep {
            override val id: String = id

            override suspend fun run(): StepResult = body()
        }

    private fun steps(): List<HookStep> =
        listOf(
            step("H1.locale") {
                if (!warm()) return@step StepResult.Later
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@step StepResult.Done
                val tag = (stores.prefs.get(LOCALE_KEY) as? PrefValue.Str)?.value
                val manager = application.getSystemService(LocaleManager::class.java)
                if (tag != null && FirstLaunchRules.mirrorLocale(tag, manager.applicationLocales.toLanguageTags()))
                    manager.applicationLocales = LocaleList.forLanguageTags(tag)
                StepResult.Done
            },
            step("H2.schedules") {
                if (!warm()) return@step StepResult.Later
                // Through the graph, as the schedule receiver does: one engine, one sync lane.
                (application.appGraph as SchedulesGraphAccess).schedules.restoreAll()
                StepResult.Done
            },
            step("H2.widgets") {
                redrawWidgets()
                StepResult.Done
            },
            step("H2.tiles") {
                for (tile in FirstLaunchRules.TILES) {
                    try {
                        TileService.requestListeningState(application, ComponentName(application.packageName, tile))
                    } catch (_: Exception) {
                        // Not added, or the platform refused: nothing to redraw.
                    }
                }
                StepResult.Done
            },
            step("H2.shortcuts") {
                republishPinnedShortcuts()
                StepResult.Done
            },
            step("H2.live") {
                if (!warm()) return@step StepResult.Later
                if ((stores.prefs.get(LIVE_KEY) as? PrefValue.Bool)?.value != true) return@step StepResult.Done
                if (!runtime.isForeground) return@step StepResult.Later
                // LiveModeController is main-confined: the hub runs the restore there.
                background.launchOnMain { background.controller().restore() }
                StepResult.Done
            },
            step("H2.phoneServer") {
                if (!warm()) return@step StepResult.Later
                if (runtime.resumeAfterUpdate()) StepResult.Done else StepResult.Later
            },
        )

    /** Every placed widget of every provider of this app gets an update broadcast (its own onUpdate redraws it). */
    private fun redrawWidgets() {
        val manager = AppWidgetManager.getInstance(application) ?: return
        manager.installedProviders
            .map { it.provider }
            .filter { it.packageName == application.packageName }
            .map { it to manager.getAppWidgetIds(it) }
            .filter { (_, ids) -> ids.isNotEmpty() }
            .forEach { (provider, ids) ->
                application.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
    }

    /**
     * Pinned conversation shortcuts keep their ids (`oc.session:<profile>:<session>`); each is updated in place so its
     * tap opens the native MainActivity with the same two extras. Only the fields set here change: the launcher keeps
     * the icon and the person's placement. Manifest (static) shortcuts are left alone.
     */
    private fun republishPinnedShortcuts() {
        val pinned = ShortcutManagerCompat.getShortcuts(application, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        val updated =
            pinned.mapNotNull { info ->
                val (profile, session) = FirstLaunchRules.pinnedSession(info.id) ?: return@mapNotNull null
                val label = info.shortLabel
                ShortcutInfoCompat.Builder(application, info.id)
                    .setShortLabel(label)
                    .setLongLabel(info.longLabel ?: label)
                    .setIntent(
                        Intent(Intent.ACTION_MAIN)
                            .setClass(application, MainActivity::class.java)
                            .putExtra(LaunchActions.EXTRA_PROFILE, profile)
                            .putExtra(LaunchActions.EXTRA_SESSION, session)
                    )
                    .build()
            }
        if (updated.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(application, updated)
    }

    private companion object {
        const val LOCALE_KEY = "oc.appLocale"
        const val LIVE_KEY = "oc.keepLiveInBackground"
    }
}

/** The pure decisions of the hooks (unit-tested without Android). */
internal object FirstLaunchRules {
    /** Both tiles, frozen Flutter FQCNs (`:android:tiles` manifest). */
    val TILES: List<String> =
        listOf(
            "io.github.eslamasabry.opencode_mobile.PauseTileService",
            "io.github.eslamasabry.opencode_mobile.AttentionTileService",
        )

    /**
     * H1: write the per-app language only when the app chose one (`oc.appLocale` holds a tag; absent = follow the
     * phone) and the system holds none yet; a language the person set in system settings is never overwritten.
     */
    fun mirrorLocale(appLocale: String?, systemTags: String): Boolean =
        appLocale in LANGUAGES && systemTags.isBlank()

    /** `oc.session:<profileId>:<sessionId>` → (profile, session); anything else is not ours to touch. */
    fun pinnedSession(id: String): Pair<String, String>? =
        PINNED.matchEntire(id)?.destructured?.let { (profile, session) -> profile to session }

    private val PINNED = Regex(Regex.escape(PinnedShortcuts.ID_PREFIX) + "([^:]+):(.+)")

    /** 2.1.4 `AppLocaleStore.supportedLanguages`: `oc.appLocale` holds one of these bare codes, or is absent. */
    private val LANGUAGES = setOf("en", "ar", "es", "ja", "pt", "ru", "zh")
}

/** How OcApplication reaches the wiring. */
@ContributesTo(AppScope::class)
interface FirstLaunchAccessors {
    val firstLaunchHooks: FirstLaunchWiring
}
