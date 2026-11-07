package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalTestApi::class)
class SettingsHubGateTest {
    private fun hub() =
        SettingsUi(
            hub =
                HubUi(
                    groups = SettingsGroupId.entries.toImmutableList(),
                    server = ServerLine("Laptop", onPhone = false),
                    savedServers =
                        persistentListOf(
                            SettingsSavedServer(ProfileId("laptop"), "Laptop", connected = true)
                        ),
                    searchable = SettingsRowId.entries.toImmutableList(),
                )
        )

    @Test
    fun settings_hub_loaded() {
        FlutterGoldenGate.check("settings_hub_loaded", frame = FlutterGoldenGate.Frame.Screen) {
            SettingsScreen(hub(), RecordingActions())
        }
    }

    @Test
    fun `B-NAV-068 hub opens a group instead of exposing unrelated rows`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { SettingsThemed { SettingsScreen(hub(), actions) } }
        onNodeWithText("Laptop · Connected").assertExists()
        onNodeWithText("Appearance").assertDoesNotExist()
        onNodeWithText("This app").performClick()
        assertEquals(listOf("open:Group(id=ThisApp)"), actions.calls)
    }

    @Test
    fun `B-NAV-068 hub group rows are roomy, 64 dp with one line or two`() = runComposeUiTest {
        setContent { SettingsThemed { SettingsScreen(hub(), RecordingActions()) } }
        // F: lib/ui/screens/settings_screen.dart:324 (roomy: true), lib/ui/kit/kit_row.dart:121 (roomyHeight = 64)
        onNodeWithTag("settings-group-Agent").assertHeightIsEqualTo(64.dp)
        onNodeWithTag("settings-group-Help").assertHeightIsEqualTo(64.dp)
        onNodeWithTag("settings-group-Server").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `B-NAV-072 no match provides a visible clear action and results target their row`() =
        runComposeUiTest {
            val state = mutableStateOf(hub().copy(hub = hub().hub.copy(query = "missing")))
            val targets = mutableListOf<SettingsRowId>()
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onQuery(text: String) {
                        state.value = state.value.copy(hub = state.value.hub.copy(query = text))
                    }

                    override fun onTarget(row: SettingsRowId) {
                        targets += row
                    }
                }
            setContent { SettingsThemed { SettingsScreen(state.value, actions) } }
            onNodeWithText("Clear search").performClick()
            onNodeWithText("This app").assertExists()
            runOnIdle {
                state.value =
                    state.value.copy(
                        hub =
                            state.value.hub.copy(
                                query = "appearance",
                                results = persistentListOf(SettingsRowId.Appearance),
                            )
                    )
            }
            onNodeWithText("Appearance").performClick()
            assertEquals(listOf(SettingsRowId.Appearance), targets)
        }

    @Test
    fun `B-NAV-072 keyboard submit searches the current text before debounced results arrive`() =
        runComposeUiTest {
            val targets = mutableListOf<SettingsRowId>()
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onTarget(row: SettingsRowId) {
                        targets += row
                    }
                }
            setContent { SettingsThemed { SettingsScreen(hub(), actions) } }
            val field = onNode(hasSetTextAction())
            field.performTextInput("Appearance")
            field.performImeAction()
            assertEquals(listOf(SettingsRowId.Appearance), targets)
        }
}
