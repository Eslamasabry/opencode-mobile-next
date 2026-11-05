package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class ByServerTest {
    @Test
    fun `B-NAV-072 by server row names and preserves its owner and section`() = runComposeUiTest {
        val target = ProfileId("laptop")
        val calls = mutableListOf<Pair<ProfileId, SettingsOutside?>>()
        val actions =
            object : SettingsActions by RecordingActions() {
                override fun onSavedServer(profile: ProfileId, section: SettingsOutside?) {
                    calls += profile to section
                }
            }
        val state =
            SettingsUi(
                stack =
                    persistentListOf(
                        SettingsPage.Hub,
                        SettingsPage.ByServer(SettingsOutside.Providers),
                    ),
                hub =
                    HubUi(
                        savedServers =
                            persistentListOf(
                                SettingsSavedServer(target, "Laptop", connected = true)
                            )
                    ),
            )
        setContent { SettingsThemed { SettingsScreen(state, actions) } }
        onNodeWithText(
                "Each server signs in to providers on its own. Signing in on one never signs in the others."
            )
            .assertExists()
        onNodeWithText("Laptop").performClick()
        assertEquals(
            listOf<Pair<ProfileId, SettingsOutside?>>(target to SettingsOutside.Providers),
            calls,
        )
    }
}
