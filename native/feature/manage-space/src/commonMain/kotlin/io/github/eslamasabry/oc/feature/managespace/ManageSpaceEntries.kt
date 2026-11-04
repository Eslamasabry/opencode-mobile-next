package io.github.eslamasabry.oc.feature.managespace

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.eslamasabry.oc.feature.managespace.presentation.ManageSpaceViewModel
import io.github.eslamasabry.oc.feature.managespace.ui.ManageSpaceScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.ManageSpaceRoute

/** Explicitly supplied by the standalone host so it cannot resolve the product graph. */
public class ManageSpaceEntries(private val model: ManageSpaceViewModel) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ManageSpaceRoute.Home> {
            val state by model.state.collectAsState()
            ManageSpaceScreen(state, model)
        }
    }
}
