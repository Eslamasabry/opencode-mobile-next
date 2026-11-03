package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.eslamasabry.oc.feature.about.managespace.ManageSpacePresenter
import io.github.eslamasabry.oc.feature.about.managespace.ManageSpaceScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.BuildProjectEntryChatsGraph
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.appGraph

/**
 * Frozen Flutter FQCN (B-ANDR-035): the application's `manageSpaceActivity`, so Android's "Clear storage" opens this
 * guard page instead of wiping at once. It runs only the page, never the app's navigation.
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = appGraph
        val port = (graph as BuildProjectEntryChatsGraph).manageSpacePort
        val appearanceSource = (graph as SettingsAppGraph).appPreferences.appearance
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by appearanceSource.collectAsState()
            val scope = rememberCoroutineScope()
            val page = remember { ManageSpacePresenter(port, scope, ::finish).also { it.start() } }
            val state by page.state.collectAsState()
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme(appearance = appearance, platform = platform, localeTag = AppLocale.applied ?: java.util.Locale.getDefault().toLanguageTag()) {
                    KitSheetHost { ManageSpaceScreen(state, page) }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
}
