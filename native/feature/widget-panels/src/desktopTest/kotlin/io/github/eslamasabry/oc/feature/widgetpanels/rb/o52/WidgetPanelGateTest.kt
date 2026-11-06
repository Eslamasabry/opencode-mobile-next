package io.github.eslamasabry.oc.feature.widgetpanels.rb.o52

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.*
import io.github.eslamasabry.oc.feature.widgetpanels.ui.WidgetPanelScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.routes.WidgetPanel
import kotlin.test.Test

internal open class WidgetCalls : WidgetPanelActions {
    val calls = mutableListOf<String>()

    override fun close() {
        calls += "close"
    }

    override fun chooseBackground(id: String) {
        calls += "background:$id"
    }

    override fun chooseAccent(id: String) {
        calls += "accent:$id"
    }

    override fun choosePreviewSize(id: String) {
        calls += "size:$id"
    }

    override fun saveStyle() {
        calls += "save"
    }

    override fun chooseProject(project: String) {
        calls += "project:$project"
    }

    override fun dictate() {
        calls += "dictate"
    }

    override fun send(text: String) {
        calls += "send:$text"
    }

    override fun consumeDictation() {}
}

/**
 * Same canned launcher backdrop and launch arguments as Flutter widget_overlay_golden_test.dart.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetPanelGateTest {
    @Composable
    private fun Scene(state: WidgetPanelUi) {
        val colors =
            if (LocalKitColors.current.dark) listOf(Color(0xFF1B2A3A), Color(0xFF2E2140))
            else listOf(Color(0xFFCFE3F2), Color(0xFFE9DCEF))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
            WidgetPanelScreen(state, WidgetCalls())
        }
    }

    private fun shot(name: String, state: WidgetPanelUi, draft: String? = null) {
        FlutterGoldenGate.check(
            name,
            frame = FlutterGoldenGate.Frame.Screen,
            act =
                if (draft == null) null
                else ({ onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("widget-message"))).performTextInput(draft) }),
        ) {
            Scene(state)
        }
    }

    @Test
    fun widget_overlay_configure() =
        shot(
            "widget_overlay_configure",
            WidgetPanelUi(
                WidgetPanel.Style(7),
                loading = false,
                background = "tinted",
                accent = "green",
            ),
        )

    @Test
    fun widget_overlay_project() =
        shot(
            "widget_overlay_project",
            WidgetPanelUi(
                WidgetPanel.Project(7),
                loading = false,
                projects = listOf("IPTV_King", "settings-demo", "oc_app"),
                project = "settings-demo",
            ),
        )

    @Test
    fun widget_overlay_steer() =
        shot(
            "widget_overlay_steer",
            WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer"),
            "Also add a test for the empty case",
        )

    @Test
    fun widget_overlay_after_reply() =
        shot(
            "widget_overlay_after_reply",
            WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "afterReply"),
        )

    @Test
    fun widget_overlay_failed() =
        shot(
            "widget_overlay_failed",
            WidgetPanelUi(
                WidgetPanel.Steer(7),
                loading = false,
                steerMode = "steer",
                problem = WidgetPanelProblem.Stale,
            ),
            "Also add a test for the empty case",
        )

    @Test
    fun widget_overlay_voice() =
        shot(
            "widget_overlay_voice",
            WidgetPanelUi(
                WidgetPanel.Steer(7, voice = true),
                loading = false,
                dictated = "Start a new conversation about the release notes",
            ),
        )
}
