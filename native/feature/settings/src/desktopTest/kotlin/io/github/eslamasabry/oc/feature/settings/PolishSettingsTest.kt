package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Polish sweep, Settings: #39 Quiet hours stays lit while Android blocks notifications (B-582), and #22 a Theme heading
 * over no themes at all (B-565).
 */
@OptIn(ExperimentalTestApi::class)
class PolishSettingsTest {
    /** [RecordingActions] does not record the quiet-hours switch; this one adds it to the same list. */
    private class Actions(delegate: RecordingActions = RecordingActions()) : SettingsActions by delegate {
        val calls: MutableList<String> = delegate.calls

        override fun onQuietHours(on: Boolean) {
            calls += "quiet:$on"
        }
    }

    private fun notifications() =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Notifications),
            notifications = NotificationFacts(blocked = true, quietHours = QuietHours(22 * 60, 8 * 60)),
        )

    private fun appearance(packs: List<PackId>) =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    packs = packs.toImmutableList(),
                    question = AppearanceQuestion.Theme,
                    questions =
                        listOf(
                            AppearanceQuestion.Mode,
                            AppearanceQuestion.Theme,
                            AppearanceQuestion.AccentColour,
                            AppearanceQuestion.Style,
                            AppearanceQuestion.Shape,
                            AppearanceQuestion.Text,
                            AppearanceQuestion.Motion,
                        ),
                ),
        )

    @Test
    fun `B-SET-582 quiet hours is dimmed while Android blocks notifications`() = runDesktopComposeUiTest(360, 2600) {
        val actions = Actions()
        setContent { SettingsThemed(width = 360f) { SettingsScreen(notifications(), actions) } }
        onNodeWithText("Quiet hours").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(emptyList(), actions.calls, "a blocked switch takes no change")
        for (row in listOf("Quiet hours start", "Quiet hours end")) {
            onNodeWithText(row).performScrollTo().assertIsNotEnabled().performClick()
        }
        assertEquals(emptyList(), actions.calls, "a blocked time row opens nothing")
    }

    @Test
    fun `B-SET-565 no colour heading without colours`() = runDesktopComposeUiTest(360, 2600) {
        setContent { SettingsThemed(width = 360f) { SettingsScreen(appearance(emptyList()), RecordingActions()) } }
        onNodeWithTag("appearance-mode").assertExists()
        onNodeWithText("Theme").assertDoesNotExist()
    }

    @Test
    fun `B-SET-565 named themes keep their Theme heading`() = runDesktopComposeUiTest(360, 2600) {
        val packs = listOf(PackId.Opencode, PackId.Nord)
        setContent { SettingsThemed(width = 360f) { SettingsScreen(appearance(packs), RecordingActions()) } }
        onNodeWithText("Theme").assertExists()
    }
}
