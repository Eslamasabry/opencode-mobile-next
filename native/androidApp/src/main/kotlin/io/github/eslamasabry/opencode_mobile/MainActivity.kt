package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.shell.App
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.MigrationRoot
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import io.github.eslamasabry.opencode_mobile.graph.migration
import io.github.eslamasabry.opencode_mobile.graph.platformHub

/** Frozen FQCN (ADR §9.9). EntryRouter and ActivityResults attach here in slice 2. */
class MainActivity : ComponentActivity() {
    private var entryReady = false
    private var pendingEntry: Intent? = null

    private fun routePendingEntry() {
        if (!entryReady) return
        val pending = pendingEntry ?: return
        pendingEntry = null
        platformHub.route(pending)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreate (language switch, rotation) redelivers the original launch extras: route only a
        // fresh launch so a share or tap is not applied twice.
        if (savedInstanceState == null) pendingEntry = intent
        // B14 (QA wave 1): after super.onCreate, so a recreate (language switch) keeps edge-to-edge
        // too.
        enableEdgeToEdge()
        val graph = appGraph
        graph.composerFiles.attach(this)
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
                LocalKitSecretSink provides KitSecretSink(graph.redactor::registerSecret)
            ) {
                MigrationRoot(graph.migration, graph.shell.navigator, platform) {
                    App(
                        graph.shell,
                        platform,
                        graph.metroViewModelFactory,
                        appearance,
                        AppLocale.applied,
                    )
                    // Hold cold/live taps until migration has opened and the real shell has
                    // attached.
                    DisposableEffect(Unit) {
                        entryReady = true
                        routePendingEntry()
                        onDispose { entryReady = false }
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
        pendingEntry = intent
        routePendingEntry()
    }

    override fun onResume() {
        super.onResume()
        platformHub.onResumed(this)
    }

    override fun onDestroy() {
        entryReady = false
        pendingEntry = null
        appGraph.composerFiles.detach(this)
        super.onDestroy()
    }
}
