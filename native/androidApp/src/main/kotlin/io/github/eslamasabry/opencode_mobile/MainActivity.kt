package io.github.eslamasabry.opencode_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.shell.App
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.appGraph

/** Frozen FQCN (ADR §9.9). EntryRouter and ActivityResults attach here in slice 2. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                settings.appPreferences.storedLocaleTag.collect { tag -> if (AppLocale.changed(tag)) recreate() }
            }
            CompositionLocalProvider(
                LocalKitSecretSink provides KitSecretSink(graph.redactor::registerSecret)
            ) {
                App(graph.shell, platform, graph.metroViewModelFactory, appearance, AppLocale.applied)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onDestroy() {
        appGraph.composerFiles.detach(this)
        super.onDestroy()
    }
}
