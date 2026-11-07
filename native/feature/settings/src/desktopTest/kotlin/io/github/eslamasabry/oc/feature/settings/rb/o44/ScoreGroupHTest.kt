package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.runtime.mutableStateOf
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Scoreboard 2026-11-07, group H: settings layout that differed from Flutter's goldens. */
@OptIn(ExperimentalTestApi::class)
class ScoreGroupHTest {
    /** A phone window, 412 dp wide like Flutter's goldens (test/revamp/screen_settings_1_golden_test.dart:27). */
    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), stillFrame = true) {
            CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(412f, 915f)) {
                Box(Modifier.requiredSize(412.dp, 915.dp)) { content() }
            }
        }
    }

    @Test
    fun `B-KIT-027 the Turn on notifications button spans the column to the rows' end edge`() = runComposeUiTest {
        val state =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Notifications),
                notifications =
                    NotificationFacts(
                        blocked = true,
                        profiles = persistentListOf(NotificationProfile(ProfileId("laptop"), "Laptop", false, true)),
                        background = BackgroundState.Off,
                    ),
            )
        setContent { Phone { SettingsScreen(state, RecordingActions()) } }
        // Flutter's solid button fills the column between the 16 dp gutters: 16..396 on a 412 dp phone
        // (settings_notifications_loaded_dark.png). The gutter used to count against the button's 400 dp cap,
        // which left it 12 dp short (368 dp).
        val button = onNodeWithTag("notifications-open-settings", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(button.left in 15.5.dp..16.5.dp, "button starts at ${button.left}")
        assertTrue(button.right in 395.5.dp..396.5.dp, "button ends at ${button.right}")
    }

    @Test
    fun `B-L10N-001 the language sheet says what the choice changes before the list`() = runComposeUiTest {
        val state =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.ThisApp)),
                group =
                    GroupUi(
                        SettingsGroupId.ThisApp,
                        persistentListOf(SettingsRowUi(SettingsRowId.Language, SettingsDoor.Page(SettingsPage.Language))),
                    ),
            )
        setContent { Phone { SettingsScreen(state, RecordingActions()) } }
        onNodeWithText("Language").performClick()
        val words =
            onNodeWithText(
                "Choose the language used throughout the app. Server messages and your text stay as written."
            ).assertExists().getBoundsInRoot()
        val first = onNodeWithText("Use system language").getBoundsInRoot()
        assertTrue(words.bottom < first.top, "the description comes before the choices")
    }

    @Test
    fun `B-KIT-017 Skip the guide is an accent action row offered only before the first question`() = runComposeUiTest {
        fun ui(seen: Boolean) =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Appearance),
                appearance =
                    AppearanceUi(
                        guide =
                            AppearanceGuideRecord(
                                seen = if (seen) listOf(AppearanceQuestion.Mode) else emptyList()
                            ),
                        questions = AppearanceQuestion.entries.toImmutableList(),
                    ),
            )
        val state = mutableStateOf(ui(seen = false))
        var skipped = 0
        val actions =
            object : SettingsActions by RecordingActions() {
                override fun onAppearanceSkip() {
                    skipped++
                }
            }
        setContent { Phone { SettingsScreen(state.value, actions) } }
        // F: lib/ui/screens/settings/appearance_screen.dart:661-670 (titleAccent: true, only while nothing is seen)
        onNodeWithText("Skip the guide").assertExists().performClick()
        assertEquals(1, skipped)
        // The accent colour itself is checked by the settings_appearance_guided_solid_home gate shot.
        state.value = ui(seen = true)
        waitForIdle()
        onNodeWithText("Skip the guide").assertDoesNotExist()
    }
}
