package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.android.platform.AndroidManageSpaceStorage
import io.github.eslamasabry.oc.core.common.CommonBindings
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceEngine
import io.github.eslamasabry.oc.feature.managespace.ManageSpaceEntries
import io.github.eslamasabry.oc.feature.managespace.presentation.ManageSpaceViewModel
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.routes.ManageSpaceRoute
import io.github.eslamasabry.oc.ui.shell.StandaloneRoot
import io.github.eslamasabry.opencode_mobile.graph.ManageSpacePreferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings' Manage space entry. No intent extras, product graph, profile secrets, or runtime
 * startup.
 */
class ManageSpaceActivity : ComponentActivity() {
    private lateinit var holder: StorageModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holder =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        StorageModel(applicationContext) as T
                },
            )[StorageModel::class.java]
        holder.storage.attach(this)
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by holder.appearance.collectAsState()
            val localeTag by holder.localeTag.collectAsState()
            StandaloneRoot(
                start = ManageSpaceRoute.Home,
                installers = setOf(ManageSpaceEntries(holder.screen)),
                platform = platform,
                appearance = appearance,
                localeTag = localeTag,
                onFinish = holder.screen::back,
            )
        }
    }

    override fun onDestroy() {
        holder.storage.detach(this)
        super.onDestroy()
    }

    private class StorageModel(context: Context) : ViewModel() {
        private val dispatchers = CommonBindings.dispatchers()
        val appearance = MutableStateFlow(KitAppearance.Default)
        val localeTag = MutableStateFlow<String?>(null)
        val storage =
            AndroidManageSpaceStorage(context, dispatchers) {
                ManageSpacePreferences.read(context).savedServers
            }

        init {
            viewModelScope.launch {
                val display = withContext(dispatchers.io) { ManageSpacePreferences.read(context) }
                appearance.value = display.appearance
                localeTag.value = display.localeTag
            }
        }

        val screen = ManageSpaceViewModel(ManageSpaceEngine(storage), storage::finish)

        override fun onCleared() {
            storage.cancelExport()
            screen.viewModelScope.cancel()
        }
    }
}
