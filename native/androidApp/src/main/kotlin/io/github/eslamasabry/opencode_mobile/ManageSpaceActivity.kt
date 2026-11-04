package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageActions
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageScreen
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageViewModel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.MigrationRoot
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import io.github.eslamasabry.opencode_mobile.graph.migration
import io.github.eslamasabry.opencode_mobile.graph.projectExport

/**
 * Frozen Android Settings surface. No launch extras, application navigation, first-run starter or
 * server startup.
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intent = Intent(this, ManageSpaceActivity::class.java)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = appGraph
        graph.projectExport.attach(this)
        val platform = AndroidKitPlatform(window.decorView, window)
        val settings = graph as SettingsAppGraph
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            CompositionLocalProvider(
                LocalKitPlatform provides platform,
                LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                LocalKitSecretSink provides KitSecretSink(graph.redactor::registerSecret),
            ) {
                MigrationRoot(graph.migration, graph.shell.navigator, platform) {
                    KitTheme(
                        appearance = appearance,
                        platform = platform,
                        localeTag = AppLocale.applied ?: "en",
                    ) {
                        val vm = metroViewModel<ProjectStorageViewModel>()
                        val state by vm.state.collectAsState()
                        val actions =
                            remember(vm) {
                                object : ProjectStorageActions by vm {
                                    override fun back() {
                                        vm.stopExport()
                                        finish()
                                    }
                                }
                            }
                        ProjectStorageScreen(state, actions, manageSpace = true)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        val clean = Intent(this, ManageSpaceActivity::class.java)
        super.onNewIntent(clean)
        setIntent(clean)
    }

    override fun onResume() {
        super.onResume()
        appGraph.projectExport.activate(this)
    }

    override fun onDestroy() {
        appGraph.projectExport.detach(this)
        super.onDestroy()
    }
}
