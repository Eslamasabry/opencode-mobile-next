package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    @Test
    fun hub() {
        val ui = SettingsUi(hub = HubUi(groups = SettingsGroupId.entries.toImmutableList(), server = ServerLine("Home laptop", onPhone = false), searchable = SettingsRowId.entries.toImmutableList()))
        Looks.shoot("native-05-settings-hub") { SettingsScreen(ui, RecordingActions()) }
    }
}
