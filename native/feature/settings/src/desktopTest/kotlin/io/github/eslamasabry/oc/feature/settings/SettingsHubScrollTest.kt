package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test

/** Jank pass: the hub keeps its place while a group is open over it. */
@OptIn(ExperimentalTestApi::class)
class SettingsHubScrollTest {
    private val hub = HubUi(groups = SettingsGroupId.entries.toPersistentList())

    @Test
    fun `JANK-SET-6 going back to the hub finds it where it was left`() = runComposeUiTest {
        var state by mutableStateOf(SettingsUi(stack = persistentListOf(SettingsPage.Hub), hub = hub))
        setContent {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    // Short enough that the last group is below the fold until the hub is scrolled.
                    Box(Modifier.size(412.dp, 260.dp)) { SettingsScreen(state, RecordingActions()) }
                }
            }
        }
        onNodeWithTag("settings-group-Help").performScrollTo()
        onNodeWithTag("settings-group-Help").assertIsDisplayed()
        state = state.copy(stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.Help)))
        waitForIdle()
        state = state.copy(stack = persistentListOf(SettingsPage.Hub))
        waitForIdle()
        onNodeWithTag("settings-group-Help").assertIsDisplayed()
    }
}
