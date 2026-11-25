package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    @Test
    fun `B-SET-582 test notification explains why it stays active while blocked`() = runDesktopComposeUiTest(360, 2600) {
        val actions = Actions()
        setContent { SettingsThemed(width = 360f) { SettingsScreen(notifications(), actions) } }
        onNodeWithText("Send a test notification").performScrollTo().performClick()
        assertEquals(listOf("test"), actions.calls)
        onNodeWithText("while notifications are off", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `B-SET-566 What we collect uses calm body text with short lead-ins`() = runDesktopComposeUiTest(360, 2600) {
        val ui =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Privacy, SettingsPage.WhatWeCollect),
                privacy = PrivacyUi(WhatWeCollectSource.build(ConsentState(), RemoteSink), crashReports = false, usage = false),
            )
        setContent { SettingsThemed(width = 360f) { SettingsScreen(ui, RecordingActions()) } }
        val node =
            onNodeWithText("Why a connection dropped and whether it came back", useUnmergedTree = true)
                .performScrollTo()
                .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val weight = layouts.first().layoutInput.style.fontWeight ?: FontWeight.Normal
        assertTrue(weight.weight <= FontWeight.Normal.weight, "disclosure prose should use calm body weight, got $weight")
        onNodeWithText("Dropped connections", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun `B-SET-566 Updates names its target version and lays actions across the row`() =
        runDesktopComposeUiTest(360, 915) {
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Updates),
                    updates = UpdateFacts("3.0.0", "10001", state = UpdateState.Available("3.1.0", "41")),
                )
            setContent { SettingsThemed(width = 360f) { SettingsScreen(ui, RecordingActions()) } }
            val update = onNodeWithText("Update to 3.1.0").getBoundsInRoot()
            val check = onNodeWithText("Check now").getBoundsInRoot()
            assertTrue(abs(update.top.value - check.top.value) < 4f, "actions should share a row: $update vs $check")
        }

    @Test
    fun `B-SET-586 Colours wizard shows matching mode and leaves second swatch row crisp`() =
        runDesktopComposeUiTest(360, 915) {
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Appearance),
                    appearance =
                        AppearanceUi(
                            mode = AppearanceMode.Light,
                            guide = AppearanceGuideRecord(seen = listOf(AppearanceQuestion.Mode)),
                            question = AppearanceQuestion.Theme,
                            questions =
                                listOf(
                                    AppearanceQuestion.Mode,
                                    AppearanceQuestion.Theme,
                                    AppearanceQuestion.Text,
                                    AppearanceQuestion.Motion,
                                ),
                        ),
                )
            setContent {
                SettingsThemed(dark = false, fontScale = 1.3f, width = 360f) { SettingsScreen(ui, RecordingActions()) }
            }
            waitForIdle()
            val lightNode = onNodeWithText("Light", useUnmergedTree = true).fetchSemanticsNode()
            val parentSelected = lightNode.parent?.config?.getOrNull(SemanticsProperties.Selected)
            assertEquals(true, parentSelected, "Light mode segment should be selected in light preview")
            val violet = onNodeWithText("Violet", useUnmergedTree = true).getBoundsInRoot()
            val next = onNodeWithText("Next: Style").getBoundsInRoot()
            assertTrue(violet.bottom.value + 16f <= next.top.value, "Violet $violet too close to footer $next")
        }
}
