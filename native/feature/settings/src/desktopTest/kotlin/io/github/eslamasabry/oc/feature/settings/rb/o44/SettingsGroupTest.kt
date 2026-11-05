package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitIconSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class SettingsGroupTest {
    @Test
    fun `B-L10N-001 Language opens locally and a choice calls the language action`() =
        runComposeUiTest {
            val calls = mutableListOf<AppLanguage>()
            val recorder = RecordingActions()
            val actions =
                object : SettingsActions by recorder {
                    override fun onLanguage(language: AppLanguage) {
                        calls += language
                    }
                }
            val state =
                SettingsUi(
                    stack =
                        persistentListOf(
                            SettingsPage.Hub,
                            SettingsPage.Group(SettingsGroupId.ThisApp),
                        ),
                    group =
                        GroupUi(
                            SettingsGroupId.ThisApp,
                            persistentListOf(
                                SettingsRowUi(
                                    SettingsRowId.Language,
                                    SettingsDoor.Page(SettingsPage.Language),
                                )
                            ),
                        ),
                )
            setContent { SettingsThemed { SettingsScreen(state, actions) } }
            onNodeWithText("Language").performClick()
            onNodeWithText("English").performClick()
            assertEquals(listOf(AppLanguage.English), calls)
            assertEquals(emptyList(), recorder.calls)
            onNodeWithTag("language-choices").assertDoesNotExist()
        }

    @Test
    fun `B-KIT-027 icon switch has one toggle target and preserves the checked state`() =
        runComposeUiTest {
            val checked = mutableStateOf(false)
            val calls = mutableListOf<Boolean>()
            setContent {
                SettingsThemed {
                    KitIconSwitchRow(
                        "Show reasoning",
                        KitIcons.idea,
                        checked.value,
                        {
                            calls += it
                            checked.value = it
                        },
                    )
                }
            }
            onNodeWithText("Show reasoning").assertIsOff().performClick().assertIsOn()
            assertEquals(listOf(true), calls)
            onAllNodes(isToggleable()).assertCountEquals(1)
        }

    @Test
    fun `B-KIT-027 phrase suggestions toggle in their group without opening another page`() =
        runComposeUiTest {
            val calls = mutableListOf<Boolean>()
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onPhraseChips(on: Boolean) {
                        calls += on
                    }
                }
            val state =
                SettingsUi(
                    stack =
                        persistentListOf(
                            SettingsPage.Hub,
                            SettingsPage.Group(SettingsGroupId.Conversations),
                        ),
                    group =
                        GroupUi(
                            SettingsGroupId.Conversations,
                            persistentListOf(
                                SettingsRowUi(
                                    SettingsRowId.PhraseChips,
                                    SettingsDoor.Page(SettingsPage.PhraseChips),
                                )
                            ),
                        ),
                    phraseChips = true,
                )
            setContent { SettingsThemed { SettingsScreen(state, actions) } }
            onAllNodes(isToggleable()).assertCountEquals(1)
            onNode(isToggleable()).performClick()
            assertEquals(listOf(false), calls)
        }
}
