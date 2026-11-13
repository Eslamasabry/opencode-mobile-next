package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.EdgeDockSetting
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings › Conversations › Other conversations: Rings / Edge light / Hidden (edge light plan S1b). */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherConversationsChoiceTest {
    private class Setting(start: OtherConversationsMode) : EdgeDockSetting {
        private val state = MutableStateFlow(start)
        override val mode: StateFlow<OtherConversationsMode> get() = state

        override fun setMode(mode: OtherConversationsMode) {
            state.value = mode
        }
    }

    private fun rig(start: OtherConversationsMode, body: suspend (SettingsPresenter, Setting, TestScope) -> Unit) =
        runTest {
            val setting = Setting(start)
            val p =
                SettingsPresenter(
                    backgroundScope,
                    MutableStateFlow(phoneFacts()),
                    FakeChoices(),
                    RecordingOpeners(),
                    notifications = FakeNotifications(),
                    edgeDock = setting,
                )
            runCurrent()
            body(p, setting, this)
        }

    @Test
    fun `settings_default_isRings an app with nothing saved shows Rings`() {
        assertEquals(OtherConversationsMode.Rings, OtherConversationsMode.migrate(saved = null, legacyShown = null))
    }

    @Test
    fun `migration_dockShownTrue_isRings`() {
        assertEquals(OtherConversationsMode.Rings, OtherConversationsMode.migrate(saved = null, legacyShown = "true"))
    }

    @Test
    fun `migration_dockShownFalse_isHidden`() {
        assertEquals(OtherConversationsMode.Hidden, OtherConversationsMode.migrate(saved = null, legacyShown = "false"))
    }

    @Test
    fun `migration_savedChoiceWinsOverTheOldSwitch`() {
        assertEquals(OtherConversationsMode.EdgeLight, OtherConversationsMode.migrate("edge-light", "false"))
        assertEquals(OtherConversationsMode.Rings, OtherConversationsMode.migrate("rings", "false"))
        // An unknown saved word falls back to what the old switch said.
        assertEquals(OtherConversationsMode.Hidden, OtherConversationsMode.migrate("nonsense", "false"))
    }

    @Test
    fun `every choice survives its saved word`() {
        for (mode in OtherConversationsMode.entries) {
            assertEquals(mode, OtherConversationsMode.fromStored(mode.stored))
        }
    }

    @Test
    fun `the row shows the current choice and picking one saves it`() =
        rig(OtherConversationsMode.Rings) { p, setting, scope ->
            p.onOpen(SettingsPage.Group(SettingsGroupId.Conversations)); scope.runCurrent()
            fun door() = p.state.value.group!!.rows.first { it.id == SettingsRowId.EdgeDock }.door
            assertEquals(SettingsDoor.Choice(OtherConversationsMode.Rings), door())
            p.onOtherConversations(OtherConversationsMode.EdgeLight); scope.runCurrent()
            assertEquals(OtherConversationsMode.EdgeLight, setting.mode.value)
            assertEquals(SettingsDoor.Choice(OtherConversationsMode.EdgeLight), door())
            p.onOtherConversations(OtherConversationsMode.Hidden); scope.runCurrent()
            assertEquals(OtherConversationsMode.Hidden, setting.mode.value)
        }

    @Test
    fun `tapping the row changes nothing by itself (the page opens the list)`() =
        rig(OtherConversationsMode.Rings) { p, setting, scope ->
            p.onRow(SettingsRowId.EdgeDock); scope.runCurrent()
            assertEquals(OtherConversationsMode.Rings, setting.mode.value)
        }
}
