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
import io.github.eslamasabry.oc.ui.kit.content.terminal.AndroidKitTerminalSlot
import io.github.eslamasabry.oc.ui.kit.content.terminal.LocalKitTerminalSlot
import androidx.compose.runtime.withFrameNanos
import io.github.eslamasabry.oc.ui.kit.content.terminal.LocalTerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.shell.App
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
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
import io.github.eslamasabry.opencode_mobile.graph.termuxMigration

/** Frozen FQCN (ADR §9.9). EntryRouter and ActivityResults attach here in slice 2. */
class MainActivity : ComponentActivity() {
    private var shellReady = false
    private var routeWhenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: the product's activity-lifecycle owners (ResumedActivities, the
        // mic/result registries, AppVisibility) must see this activity's own created/started
        // callbacks, as they did when the product started in Application.onCreate.
        (application as OcApplication).startProduct()
        super.onCreate(savedInstanceState)
        routeWhenReady =
            savedInstanceState == null &&
                intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        // B14 (QA wave 1): after super.onCreate, so a recreate (language switch) keeps edge-to-edge
        // too.
        enableEdgeToEdge()
        val graph = appGraph
        graph.previewRuntime.restoreSavedConnection()
        graph.composerFiles.attach(this)
        graph.androidSessionImportFiles.attach(this)
        graph.projectExport.attach(this)
        graph.termuxMigration.attach(this)
        // DecorView's DecorContext need not wrap this Activity; pass the actual window for
        // system-bar contrast.
        val platform = AndroidKitPlatform(window.decorView, window)
        // P12-SETTINGS: stored appearance and language at the root; first run picks the welcome.
        val settings = graph as SettingsAppGraph
        settings.firstRunStarter.start()
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            LaunchedEffect(Unit) {
                settings.appPreferences.storedLocaleTag.collect { tag ->
                    if (AppLocale.changed(tag)) recreate()
                }
            }
            CompositionLocalProvider(
                LocalKitSecretSink provides KitSecretSink(graph.redactor::registerSecret),
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
                                    intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
                            )
                                settings.whatsNewStarter.start()
                            if (routeWhenReady) {
                                routeWhenReady = false
                                platformHub.route(intent)
                            }
                        }
                    }
                }
            }
        }
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
        appGraph.projectExport.activate(this)
        appGraph.termuxMigration.activate(this)
        platformHub.onResumed(this)
    }

    override fun onStop() {
        appGraph.termuxMigration.onAppLeftForeground()
        super.onStop()
    }

    override fun onDestroy() {
        appGraph.termuxMigration.detach(this)
        appGraph.projectExport.detach(this)
        shellReady = false
        appGraph.composerFiles.detach(this)
        appGraph.androidSessionImportFiles.detach(this)
        super.onDestroy()
    }
}
