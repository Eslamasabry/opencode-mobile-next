package io.github.eslamasabry.oc.feature.widgetactions.rb.o52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetPanelScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WidgetProjectPanelTest {
    @Composable
    private fun Theme(body: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            localeTag = "en",
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
        ) {
            Box(Modifier.size(412.dp, 915.dp)) { body() }
        }
    }

    @Test
    fun `B-NAV-068 Android project overlay selects and saves on the same tap`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions =
            object : WidgetPanelActions {
                override fun background(value: String) {}

                override fun accent(value: String) {}

                override fun mode(value: WidgetPanelMode) {}

                override fun project(value: String) {
                    calls += "project:$value"
                }

                override fun save() {
                    calls += "save"
                }

                override fun close() {
                    calls += "close"
                }
            }
        setContent {
            Theme {
                WidgetPanelScreen(
                    WidgetPanelUi(
                        mode = WidgetPanelMode.Project,
                        projects = listOf("", "IPTV_King", "settings-demo"),
                    ),
                    actions,
                )
            }
        }
        onAllNodesWithText("All projects").assertCountEquals(1)
        onNodeWithText("settings-demo").performClick()
        assertEquals(listOf("project:settings-demo", "save"), calls)
    }
}
