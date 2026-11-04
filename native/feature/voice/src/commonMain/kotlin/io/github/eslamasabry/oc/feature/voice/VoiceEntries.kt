package io.github.eslamasabry.oc.feature.voice

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingViewModel
import io.github.eslamasabry.oc.feature.voice.screen.VoiceSettingsViewModel
import io.github.eslamasabry.oc.feature.voice.ui.TeachingScreen
import io.github.eslamasabry.oc.feature.voice.ui.VoiceSettingsScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.TeachingRoute
import io.github.eslamasabry.oc.ui.routes.VoiceRoute

/**
 * Settings › Voice typing (P12-VOICE). Reached from the composer's voice notes ("Voice settings")
 * and, once Settings is routed, from its Voice row (`SettingsOutside.VoiceSetup` →
 * `VoiceRoute.Settings`). The ONLY file in the module that imports androidx.navigation3.runtime
 * (Konsist K1).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class VoiceEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<TeachingRoute.Tips> {
            val vm = metroViewModel<TeachingViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            TeachingScreen(state, vm)
        }
        entry<VoiceRoute.Settings> {
            val vm = metroViewModel<VoiceSettingsViewModel>()
            val state by vm.presenter.state.collectAsStateWithLifecycle()
            VoiceSettingsScreen(state, vm.presenter)
        }
    }
}
