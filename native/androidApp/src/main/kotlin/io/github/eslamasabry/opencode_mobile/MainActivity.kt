package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import io.github.eslamasabry.oc.ui.kit.content.terminal.AndroidKitTerminalSlot
import io.github.eslamasabry.oc.ui.kit.content.terminal.LocalKitTerminalSlot
import io.github.eslamasabry.oc.ui.kit.content.terminal.LocalTerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitSecureWindow
import io.github.eslamasabry.oc.ui.kit.platform.androidKitSecureWindow
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.shell.App
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.AppNoticesAccessors
import io.github.eslamasabry.opencode_mobile.graph.termuxRetired
import io.github.eslamasabry.opencode_mobile.graph.IncomingShareHost
import io.github.eslamasabry.opencode_mobile.graph.MigrationRoot
import io.github.eslamasabry.opencode_mobile.graph.ProviderLogoGraph
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.TerminalLinksGraph
import io.github.eslamasabry.opencode_mobile.graph.UpdateNoticeAccessors
import io.github.eslamasabry.opencode_mobile.graph.UpdateNoticeHost
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import io.github.eslamasabry.opencode_mobile.graph.migration
import io.github.eslamasabry.opencode_mobile.graph.platformHub
import io.github.eslamasabry.opencode_mobile.graph.projectExport

/** Frozen FQCN (ADR §9.9). EntryRouter and ActivityResults attach here in slice 2. */
class MainActivity : ComponentActivity() {
    private var shellReady = false
    private var routeWhenReady = false

    /** P-110: the page the killed process was on, reopened once the shell is up. */
    private var lastRoute: List<AppRoute> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // B-270: first, before anything installs the decor. A language switch recreates this Activity on the old
        // window, and Android 15 would keep the page fitted between the bars (black bands) for good.
        DecorFit.release(window)
        // Before super.onCreate: the product's activity-lifecycle owners (ResumedActivities, the
        // mic/result registries, AppVisibility) must see this activity's own created/started
        // callbacks, as they did when the product started in Application.onCreate.
        (application as OcApplication).startProduct()
        super.onCreate(savedInstanceState)
        lastRoute = LastRoute.read(savedInstanceState)
        routeWhenReady =
            savedInstanceState == null &&
                intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        // B14 (QA wave 1): after super.onCreate, so a recreate (language switch) keeps edge-to-edge
        // too.
        enableEdgeToEdge()
        // Motion lead 2026-11-07: run at the display's full refresh rate (90/120 Hz phones never stay at 60).
        DisplayRefresh.follow(this)
        val graph = appGraph
        graph.previewRuntime.restoreSavedConnection()
        graph.composerFiles.attach(this)
        graph.androidSessionImportFiles.attach(this)
        graph.projectExport.attach(this)
        // The once-only Termux notice joins the app notice slot (idempotent).
        (graph as AppNoticesAccessors).appNotices.follow(graph.termuxRetired.notice)
        // DecorView's DecorContext need not wrap this Activity; pass the actual window for
        // system-bar contrast.
        val platform = AndroidKitPlatform(window.decorView, window)
        // P12-SETTINGS: stored appearance and language at the root; first run picks the welcome.
        val settings = graph as SettingsAppGraph
        settings.firstRunStarter.start()
        val secureWindow = androidKitSecureWindow(this)
        setContent {
            graph.kitChatEnvironment.Provide {
                val appearance by settings.appPreferences.appearance.collectAsState()
                // Nothing is drawn until the stored appearance is known (a few frames on the window's own background),
                // so the first frame is already in the person's theme.
                val appearanceKnown by settings.appPreferences.warmed.collectAsState()
                LaunchedEffect(Unit) {
                    settings.appPreferences.storedLocaleTag.collect { tag ->
                        if (AppLocale.changed(tag)) recreate()
                    }
                }
                if (!appearanceKnown) LaunchGround()
                if (appearanceKnown) CompositionLocalProvider(
                    LocalKitSecureWindow provides secureWindow,
                    LocalKitTerminalSlot provides AndroidKitTerminalSlot,
                    LocalTerminalLinkHost provides (graph as TerminalLinksGraph).terminalLinkHost,
                    LocalKitLogoLoader provides (graph as ProviderLogoGraph).kitLogoLoader,
                ) {
                    MigrationRoot(graph.migration, graph.shell.navigator, platform) {
                        App(
                            graph.shell,
                            platform,
                            graph.metroViewModelFactory,
                            appearance,
                            AppLocale.applied,
                            windowContent = { shell ->
                                UpdateNoticeHost((graph as UpdateNoticeAccessors).updateNotices) {
                                    IncomingShareHost(platformHub.incomingShare, shell)
                                }
                            },
                        )
                        // A parked launch is handed over only after the migration gate and shell
                        // have actually composed. This effect dies with its Activity's composition.
                        LaunchedEffect(Unit) {
                            withFrameNanos {}
                            if (!isDestroyed) {
                                shellReady = true
                                if (
                                    savedInstanceState == null &&
                                        intent.action == Intent.ACTION_MAIN &&
                                        intent.flags and
                                            Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
                                )
                                    settings.whatsNewStarter.start()
                                val navigator = graph.shell.navigator
                                navigator.restoreStack(
                                    LastRoute.reopen(lastRoute, navigator.top.value, ChatsRoute.Home)
                                )
                                lastRoute = emptyList()
                                if (routeWhenReady) {
                                    routeWhenReady = false
                                    platformHub.route(intent)
                                }
                                // Gap 3: "fully drawn" = the shell (home, or the welcome on first run) composed and one more
                                // frame on screen, so cold-start numbers (Macrobenchmark TTFD) cover real content, not the splash.
                                if (savedInstanceState == null) {
                                    withFrameNanos {}
                                    if (!isDestroyed) reportFullyDrawn()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Before the shell is up the saved page is still the one to reopen, not home.
        LastRoute.save(if (shellReady) appGraph.shell.navigator.snapshot() else lastRoute, outState)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shellReady) platformHub.route(intent) else routeWhenReady = true
    }

    override fun onResume() {
        super.onResume()
        // No enableEdgeToEdge() here (B-270): it could not undo the fitted decor, and its light/dark guess from the
        // phone's mode turned the status-bar icons dark over the app's dark page after every return.
        appGraph.projectExport.activate(this)
        platformHub.onResumed(this)
    }

    override fun onDestroy() {
        appGraph.projectExport.detach(this)
        shellReady = false
        appGraph.composerFiles.detach(this)
        appGraph.androidSessionImportFiles.detach(this)
        super.onDestroy()
    }
}
