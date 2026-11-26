package io.github.eslamasabry.oc.feature.agenttools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.agenttools.ui.AgentToolsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val PHONE_WIDTH_DP = 360
private const val TALL_HEIGHT_DP = 1800
private const val TEST_DENSITY = 2.625f
private const val EDGE_EPSILON_DP = 0.5f

@OptIn(ExperimentalTestApi::class)
class AgentToolsLayoutTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `B-482 hides raw internal path at top level and shows plain folder name`() {
        runScreen(fontScale = 1.0f) { _, _ ->
            onAllNodesWithText(AgentToolsTestFixture.RAW_DIRECTORY, substring = true, useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodesWithText(AgentToolsTestFixture.PLAIN_FOLDER, useUnmergedTree = true).onFirst().assertIsDisplayed()
            onNodeWithText("Details", useUnmergedTree = true).performClick()
            waitForIdle()
            onAllNodesWithText(AgentToolsTestFixture.RAW_DIRECTORY, substring = true, useUnmergedTree = true)
                .onFirst()
                .assertIsDisplayed()
        }
    }

    @Test
    fun `B-482 every visible heading text and button keeps gutter margins at 360 dp text 1_0`() {
        assertBodyMargins(fontScale = 1.0f)
    }

    @Test
    fun `B-482 every visible heading text and button keeps gutter margins at 360 dp text 1_3`() {
        assertBodyMargins(fontScale = 1.3f)
    }

    @Test
    fun `B-482 terminals section sits above maintenance and keeps one-tap terminal actions`() {
        runScreen(fontScale = 1.0f) { actions, _ ->
            val terminalsTop = onNodeWithText("Terminals", useUnmergedTree = true).getUnclippedBoundsInRoot().top
            val maintenanceTop = onNodeWithText("Maintenance", useUnmergedTree = true).getUnclippedBoundsInRoot().top
            assertTrue(
                terminalsTop < maintenanceTop,
                "Expected Terminals ($terminalsTop) above Maintenance ($maintenanceTop)",
            )
            onNodeWithText("New terminal").performClick()
            onNodeWithText("Terminals in this project").performClick()
            onNodeWithText("Shell 1").performClick()
            onNodeWithText("Refresh agent tools").performClick()
            assertTrue("new-terminal" in actions.events, "New terminal should trigger in one tap: ${actions.events}")
            assertTrue(
                "list-terminals" in actions.events,
                "Terminals in this project should trigger in one tap: ${actions.events}",
            )
            assertTrue("open-terminal:t1" in actions.events, "Shell 1 should open in one tap: ${actions.events}")
            assertTrue("refresh" in actions.events, "Refresh should trigger in one tap: ${actions.events}")
        }
    }

    private fun assertBodyMargins(fontScale: Float) {
        runScreen(fontScale = fontScale) { _, gutterDp ->
            val topBarBottom =
                onNodeWithText("Agent tools", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom + 12.dp
            val minStart = (gutterDp - EDGE_EPSILON_DP).dp
            val maxEnd = (PHONE_WIDTH_DP - gutterDp + EDGE_EPSILON_DP).dp
            val textNodes =
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes()
            val bodyTexts = textNodes.filter { it.boundsInRoot.top / TEST_DENSITY >= topBarBottom.value }
            assertTrue(bodyTexts.isNotEmpty(), "Expected visible body text nodes")
            for (node in bodyTexts) {
                val leftDp = (node.boundsInRoot.left / TEST_DENSITY).dp
                val rightDp = (node.boundsInRoot.right / TEST_DENSITY).dp
                val label = node.config[SemanticsProperties.Text].joinToString { it.text }
                assertTrue(
                    leftDp >= minStart,
                    "Text '$label' touches start edge at $leftDp (min $minStart, scale $fontScale)",
                )
                assertTrue(
                    rightDp <= maxEnd,
                    "Text '$label' touches end edge at $rightDp (max $maxEnd, scale $fontScale)",
                )
            }
            for (buttonLabel in listOf("New terminal", "Terminals in this project", "Refresh agent tools")) {
                val bounds = onNodeWithText(buttonLabel).getUnclippedBoundsInRoot()
                assertTrue(
                    bounds.left >= minStart,
                    "Button '$buttonLabel' touches start edge at ${bounds.left} (min $minStart, scale $fontScale)",
                )
                assertTrue(
                    bounds.right <= maxEnd,
                    "Button '$buttonLabel' touches end edge at ${bounds.right} (max $maxEnd, scale $fontScale)",
                )
            }
        }
    }

    private fun runScreen(
        fontScale: Float,
        block: ComposeUiTest.(AgentToolsTestFixture.RecordingActions, Int) -> Unit,
    ) {
        val actions = AgentToolsTestFixture.RecordingActions()
        var gutterDp = 16
        runDesktopComposeUiTest((PHONE_WIDTH_DP * TEST_DENSITY).toInt(), (TALL_HEIGHT_DP * TEST_DENSITY).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(TEST_DENSITY, fontScale)) {
                    KitTheme(
                        appearance =
                            KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        gutterDp = LocalKitTokens.current.gutter
                        CompositionLocalProvider(
                            LocalKitWindow provides KitWindowInfo(PHONE_WIDTH_DP.toFloat(), TALL_HEIGHT_DP.toFloat())
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                AgentToolsScreen(AgentToolsTestFixture.fullState, actions)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            block(actions, gutterDp)
        }
    }
}
