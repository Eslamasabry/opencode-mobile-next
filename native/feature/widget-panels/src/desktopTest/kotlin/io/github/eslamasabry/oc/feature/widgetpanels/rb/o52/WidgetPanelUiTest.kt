package io.github.eslamasabry.oc.feature.widgetpanels.rb.o52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.*
import io.github.eslamasabry.oc.feature.widgetpanels.ui.WidgetPanelScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.routes.WidgetPanel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WidgetPanelUiTest {
    @Composable
    private fun Scene(state: WidgetPanelUi, actions: WidgetPanelActions) {
        KitTheme(
            platform = DesktopKitPlatform(),
            localeTag = "en",
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
        ) {
            Box(Modifier.size(412.dp, 915.dp)) { WidgetPanelScreen(state, actions) }
        }
    }

    @Test
    fun `B-NAV-068 widget style choices call the existing scoped actions`() = runComposeUiTest {
        val actions = WidgetCalls()
        setContent { Scene(WidgetPanelUi(WidgetPanel.Style(7), loading = false), actions) }
        onNodeWithText("Tinted").performClick()
        onNodeWithContentDescription("Blue").performClick()
        onNodeWithTag("widget-style-save").performClick()
        assertEquals(listOf("background:tinted", "accent:blue", "save"), actions.calls)
    }

    @Test
    fun `B-NAV-068 project selection preserves the complete project value`() = runComposeUiTest {
        val actions = WidgetCalls()
        setContent {
            Scene(
                WidgetPanelUi(
                    WidgetPanel.Project(7),
                    loading = false,
                    projects = listOf("", "settings-demo"),
                ),
                actions,
            )
        }
        onNodeWithText("settings-demo").performClick()
        assertEquals(listOf("project:settings-demo"), actions.calls)
        onAllNodesWithText("All projects").assertCountEquals(1)
    }

    @Test
    fun `B-NAV-021 empty widget message cannot send and sending holds dismissal`() =
        runComposeUiTest {
            val actions = WidgetCalls()
            setContent {
                Scene(
                    WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer"),
                    actions,
                )
            }
            onNodeWithTag("widget-send").assertIsNotEnabled()
            onNodeWithTag("widget-message").performTextInput("  Keep this draft  ")
            onNodeWithTag("widget-send").performClick()
            assertEquals(listOf("send:Keep this draft"), actions.calls)
        }
}
