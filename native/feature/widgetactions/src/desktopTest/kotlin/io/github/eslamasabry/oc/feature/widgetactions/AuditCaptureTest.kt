package io.github.eslamasabry.oc.feature.widgetactions

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionActions
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionPhase
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionUi
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetPanelActions
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetPanelMode
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetPanelUi
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetActionScreen
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetPanelScreen
import kotlin.test.Test

/** Audit capture only: the widget message box and the widget style/project panel. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object ActionTaps : WidgetActionActions {
        override fun dictate() {}
        override fun finishDictation() {}
        override fun takeDictatedWords() {}
        override fun send(text: String) {}
        override fun close() {}
        override fun openConversation() {}
    }

    private object PanelTaps : WidgetPanelActions {
        override fun background(value: String) {}
        override fun accent(value: String) {}
        override fun mode(value: WidgetPanelMode) {}
        override fun project(value: String) {}
        override fun save() {}
        override fun close() {}
    }

    private fun action(name: String, ui: WidgetActionUi) =
        Audit.guard("widgetactions__widget-message__${name}__build") {
            Audit.shoot("widgetactions", "widget-message", name) { WidgetActionScreen(ui, ActionTaps) }
        }

    @Test
    fun widgetMessage() {
        action("editing", WidgetActionUi())
        action("editing-voice-available", WidgetActionUi(voiceAvailable = true))
        action("new-conversation", WidgetActionUi(newConversation = true, voiceAvailable = true))
        action("listening", WidgetActionUi(newConversation = true, voiceAvailable = true, listening = true, dictating = true))
        action("dictated", WidgetActionUi(newConversation = true, voiceAvailable = true, dictatedWords = "Start a new conversation about the release notes"))
        action("dictation-failed", WidgetActionUi(voiceAvailable = true, dictationFailed = true))
        action("sending", WidgetActionUi(phase = WidgetActionPhase.Sending))
        action("accepted", WidgetActionUi(phase = WidgetActionPhase.Accepted))
        action("rejected", WidgetActionUi(phase = WidgetActionPhase.Rejected))
        action("uncertain", WidgetActionUi(phase = WidgetActionPhase.Uncertain))
    }

    private fun panel(name: String, ui: WidgetPanelUi) =
        Audit.guard("widgetactions__widget-panel__${name}__build") {
            Audit.shoot("widgetactions", "widget-panel", name) { WidgetPanelScreen(ui, PanelTaps) }
        }

    @Test
    fun widgetPanel() {
        panel("style-solid", WidgetPanelUi())
        panel("style-tinted-green", WidgetPanelUi(background = "tinted", accent = "green"))
        panel("style-transparent-blue", WidgetPanelUi(background = "transparent", accent = "blue"))
        panel("style-saving", WidgetPanelUi(background = "tinted", accent = "amber", saving = true))
        panel("style-failed", WidgetPanelUi(failed = true))
        panel("project-list", WidgetPanelUi(mode = WidgetPanelMode.Project, projects = listOf("", "IPTV_King", "settings-demo", "a-project-with-an-extremely-long-name-that-has-to-wrap-or-truncate"), project = "settings-demo"))
        panel("project-all", WidgetPanelUi(mode = WidgetPanelMode.Project, projects = listOf("", "oc_app")))
        panel("project-empty", WidgetPanelUi(mode = WidgetPanelMode.Project))
    }
}
