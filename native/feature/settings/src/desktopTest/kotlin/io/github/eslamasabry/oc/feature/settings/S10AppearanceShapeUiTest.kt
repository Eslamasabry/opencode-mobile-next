package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.style.Corners
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** B-166: the Shape question of Appearance › Style shows the corners choice with the migrated value. */
@OptIn(ExperimentalTestApi::class)
class S10AppearanceShapeUiTest {
    private fun shape() =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    packs = listOf(PackId.Opencode).toImmutableList(),
                    style = SurfaceStyle.Soft,
                    corners = Corners.Sharp,
                    fineTuned = true,
                    guide = AppearanceGuideRecord(done = true),
                    question = AppearanceQuestion.Shape,
                    questions =
                        listOf(
                            AppearanceQuestion.Mode,
                            AppearanceQuestion.Theme,
                            AppearanceQuestion.Style,
                            AppearanceQuestion.Shape,
                            AppearanceQuestion.Text,
                            AppearanceQuestion.Motion,
                        ),
                ),
        )

    @Test
    fun `B-166 the Shape question offers Round Soft and Sharp and a pick reaches the corners action`() =
        runComposeUiTest {
            val picked = mutableListOf<Corners>()
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onCorners(corners: Corners) {
                        picked += corners
                    }
                }
            setContent { SettingsThemed { SettingsScreen(shape(), actions) } }
            onNodeWithTag("appearance-corners").performScrollTo().assertExists()
            onNodeWithText("Sharp").assertExists()
            onNodeWithText("Round").performClick()
            assertEquals(listOf(Corners.Round), picked)
        }
}
