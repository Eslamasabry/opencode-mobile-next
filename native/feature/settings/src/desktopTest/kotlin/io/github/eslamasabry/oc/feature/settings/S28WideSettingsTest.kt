package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.GroupUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** s28-app story 8: Settings on a wide window is two panes, as Flutter's KitScreen.twoPane (settings_screen.dart:459). */
@OptIn(ExperimentalTestApi::class)
class S28WideSettingsTest {
    private val groups = persistentListOf(SettingsGroupId.ThisApp, SettingsGroupId.Help)

    private fun androidx.compose.ui.test.ComposeUiTest.wide(state: SettingsUi, actions: RecordingActions) =
        setContent {
            // SettingsThemed draws a 412 dp phone; this is a 1280 × 800 window.
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalKitRedactor provides Redactor(),
                    LocalKitWindow provides KitWindowInfo(1280f, 800f),
                ) {
                    Box(Modifier.size(1280.dp, 800.dp)) { SettingsScreen(state, actions) }
                }
            }
        }

    @Test
    fun `B-NAV-103 the wide hub shows its first group in the detail pane, as Flutter`() = runComposeUiTest {
        val actions = RecordingActions()
        val first = GroupUi(SettingsGroupId.ThisApp, persistentListOf())
        wide(SettingsUi(hub = HubUi(groups = groups), firstGroup = first), actions)
        onNodeWithTag("settings-list-pane").assertExists()
        onNodeWithTag("settings-detail-pane").assertExists()
        onNodeWithTag("settings-group-Help").performClick()
        onNodeWithTag("settings-group-ThisApp").performClick() // the one shown: nothing happens
        assertEquals(listOf("open:${SettingsPage.Group(SettingsGroupId.Help)}"), actions.calls)
    }

    @Test
    fun `B-NAV-105 a wide hub with no group asks to choose one`() = runComposeUiTest {
        wide(SettingsUi(hub = HubUi(groups = persistentListOf())), RecordingActions())
        onNodeWithText("Choose a group of settings").assertExists()
    }

    @Test
    fun `B-NAV-104 choosing another group on a wide window replaces the one shown`() = runComposeUiTest {
        val actions = RecordingActions()
        wide(
            SettingsUi(
                stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.Help)),
                hub = HubUi(groups = groups),
            ),
            actions,
        )
        onNodeWithTag("settings-detail-pane").assertExists()
        onNodeWithTag("settings-group-ThisApp").performClick()
        assertEquals(listOf("back", "open:${SettingsPage.Group(SettingsGroupId.ThisApp)}"), actions.calls)
    }
}
