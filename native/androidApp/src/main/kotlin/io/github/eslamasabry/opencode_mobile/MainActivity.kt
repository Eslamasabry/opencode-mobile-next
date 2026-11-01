package io.github.eslamasabry.opencode_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.shell.App
import io.github.eslamasabry.opencode_mobile.graph.appGraph

/** Frozen FQCN (ADR §9.9). EntryRouter and ActivityResults attach here in slice 2. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = appGraph
        val platform = AndroidKitPlatform(window.decorView)
        setContent { App(graph.shell, platform, graph.metroViewModelFactory) }
    }
}
