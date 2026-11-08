package io.github.eslamasabry.oc.feature.widgetpanels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetComposePhase
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelActions
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelProblem
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelUi
import io.github.eslamasabry.oc.feature.widgetpanels.ui.WidgetPanelScreen
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.routes.WidgetPanel
import kotlin.test.Test

/** Audit capture only: the launcher widget overlay panels over a canned launcher backdrop. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : WidgetPanelActions {
        override fun close() {}
        override fun chooseBackground(id: String) {}
        override fun chooseAccent(id: String) {}
        override fun choosePreviewSize(id: String) {}
        override fun saveStyle() {}
        override fun chooseProject(project: String) {}
        override fun dictate() {}
        override fun send(text: String) {}
        override fun consumeDictation() {}
    }

    @Composable
    private fun Scene(state: WidgetPanelUi) {
        val colors =
            if (LocalKitColors.current.dark) listOf(Color(0xFF1B2A3A), Color(0xFF2E2140))
            else listOf(Color(0xFFCFE3F2), Color(0xFFE9DCEF))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
            WidgetPanelScreen(state, Taps)
        }
    }

    private fun shot(name: String, state: WidgetPanelUi, draft: String? = null) =
        Audit.guard("widget-panels__widget-overlay__${name}__build") {
            val setup: ComposeUiTest.() -> Unit =
                if (draft == null) {
                    {}
                } else {
                    { onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("widget-message"))).performTextInput(draft) }
                }
            Audit.shoot("widget-panels", "widget-overlay", name, setup = setup) { Scene(state) }
        }

    @Test
    fun widgetOverlay() {
        val longDraft = "Also add a test for the empty case and then explain in detail why the previous attempt failed on the second run"
        shot("style-loading", WidgetPanelUi(WidgetPanel.Style(7)))
        shot("style-tinted-green", WidgetPanelUi(WidgetPanel.Style(7), loading = false, background = "tinted", accent = "green"))
        shot("style-solid-wallpaper", WidgetPanelUi(WidgetPanel.Style(7), loading = false))
        shot("style-transparent-blue", WidgetPanelUi(WidgetPanel.Style(7), loading = false, background = "transparent", accent = "blue", previewSize = "tall"))
        shot("style-saving", WidgetPanelUi(WidgetPanel.Style(7), loading = false, background = "tinted", accent = "amber", saving = true))
        shot("style-failed", WidgetPanelUi(WidgetPanel.Style(7), loading = false, problem = WidgetPanelProblem.Unavailable))
        shot("project-list", WidgetPanelUi(WidgetPanel.Project(7), loading = false, projects = listOf("IPTV_King", "settings-demo", "oc_app", "a-project-with-an-extremely-long-name-that-has-to-wrap-or-truncate"), project = "settings-demo"))
        shot("project-empty", WidgetPanelUi(WidgetPanel.Project(7), loading = false))
        shot("project-loading", WidgetPanelUi(WidgetPanel.Project(7)))
        shot("steer", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer"), draft = "Also add a test for the empty case")
        shot("steer-long-draft", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer"), draft = longDraft)
        shot("after-reply", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "afterReply"))
        shot("steer-no-mode", WidgetPanelUi(WidgetPanel.Steer(7), loading = false))
        shot("steer-stale", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer", problem = WidgetPanelProblem.Stale), draft = "Also add a test for the empty case")
        shot("steer-uncertain", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer", uncertain = true, problem = WidgetPanelProblem.Uncertain))
        shot("steer-sending", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer", phase = WidgetComposePhase.Sending))
        shot("steer-sent", WidgetPanelUi(WidgetPanel.Steer(7), loading = false, steerMode = "steer", phase = WidgetComposePhase.Sent))
        shot("voice", WidgetPanelUi(WidgetPanel.Steer(7, voice = true), loading = false, dictated = "Start a new conversation about the release notes"))
        shot("voice-dictating", WidgetPanelUi(WidgetPanel.Steer(7, voice = true), loading = false, phase = WidgetComposePhase.Dictating))
        shot("voice-unavailable", WidgetPanelUi(WidgetPanel.Steer(7, voice = true), loading = false, problem = WidgetPanelProblem.DictationUnavailable))
    }
}
