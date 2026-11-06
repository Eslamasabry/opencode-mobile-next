package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitIconSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class SettingsGroupTest {
    @Test
    fun `B-NAV-068 wide selected group retains the hub and its row actions`() = runComposeUiTest {
        val actions = RecordingActions()
        val state =
            SettingsUi(
                stack =
                    persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.ThisApp)),
                hub =
                    HubUi(
                        groups = persistentListOf(SettingsGroupId.Server, SettingsGroupId.ThisApp)
                    ),
                group =
                    GroupUi(
                        SettingsGroupId.ThisApp,
                        persistentListOf(
                            SettingsRowUi(
                                SettingsRowId.Appearance,
                                SettingsDoor.Page(SettingsPage.Appearance),
                            )
                        ),
                    ),
            )
        setContent {
            // The app root provides the platform (KitPlatform is provided once at the app root).
            KitTheme(platform = DesktopKitPlatform(), stillFrame = true) {
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(1000f, 800f)) {
                    Box(Modifier.requiredSize(1000.dp, 800.dp)) { SettingsScreen(state, actions) }
                }
            }
        }
        onNodeWithTag("kit-screen-pane-list").assertExists()
        onNodeWithTag("kit-screen-pane-detail").assertExists()
        onNodeWithTag("settings-group-ThisApp").assertIsSelected()
        onNodeWithTag("settings-appearance").performClick()
        onNodeWithTag("settings-group-Server").performClick()
        assertEquals(listOf("row:Appearance", "open:Group(id=Server)"), actions.calls)
    }

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
