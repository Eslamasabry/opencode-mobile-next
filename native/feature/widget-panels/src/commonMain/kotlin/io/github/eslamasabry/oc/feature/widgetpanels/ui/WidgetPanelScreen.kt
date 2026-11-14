package io.github.eslamasabry.oc.feature.widgetpanels.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.*
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitFloatingPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitPanelHeading
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.routes.WidgetPanel
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Launcher panels arrange kit parts only; tapping the kit scrim or Close finishes the private host.
 */
@Composable
public fun WidgetPanelScreen(state: WidgetPanelUi, actions: WidgetPanelActions) {
    val title =
        when (val route = state.route) {
            is WidgetPanel.Style -> stringResource(Res.string.widgetV2ConfigureTitle)
            is WidgetPanel.Project -> stringResource(Res.string.widgetProjectTitle)
            is WidgetPanel.Steer ->
                stringResource(
                    if (route.voice) Res.string.widgetV2VoiceTitle
                    else if (state.steerMode == "afterReply") Res.string.widgetV2AfterReplyTitle
                    else Res.string.widgetV2SteerTitle
                )
        }
    val locked = state.saving || state.phase == WidgetComposePhase.Sending
    val enabled =
        if (state.saving || state.loading)
            KitEnabled.Disabled(stringResource(Res.string.widgetV2Sending))
        else KitEnabled.Enabled
    KitFloatingPanel(
        onDismiss = if (locked) null else actions::close,
        dismissLabel = stringResource(Res.string.widgetV2Cancel),
    ) {
        when (state.route) {
            is WidgetPanel.Steer ->
                Column(Modifier.weight(1f, fill = false).verticalScroll(kitPageScrollState())) {
                    ComposeBody(state, actions, title)
                }
            is WidgetPanel.Style -> {
                KitPanelHeading(
                    title,
                    stringResource(Res.string.widgetV2Cancel),
                    actions::close,
                    enabled,
                )
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(kitPageScrollState())
                        .padding(top = Space.S3.dp)
                ) {
                    KitWidgetStyleForm(
                        state.previewSize,
                        state.background,
                        state.accent,
                        accents = listOf("wallpaper", "app", "green", "teal", "blue", "amber"),
                        accentName = { accentName(it) },
                        accentColor = { widgetAccentColor(it) },
                        onPreviewSize = actions::choosePreviewSize,
                        onBackground = actions::chooseBackground,
                        onAccent = actions::chooseAccent,
                        enabled = enabled,
                    )
                }
                state.problem?.let {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitNotice(problemWords(it, state.route), tone = StatusTone.Neutral)
                }
                Spacer(Modifier.height(Space.S4.dp))
                KitButton(
                    stringResource(
                        if (state.saving) Res.string.widgetV2Sending
                        else Res.string.widgetV2SaveStyle
                    ),
                    actions::saveStyle,
                    enabled = enabled,
                    working = state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("widget-style-save"),
                    fullWidth = true,
                )
            }
            is WidgetPanel.Project -> {
                KitPanelHeading(
                    title,
                    stringResource(Res.string.widgetV2Cancel),
                    actions::close,
                    enabled,
                )
                Spacer(Modifier.height(Space.S3.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(kitPageScrollState())) {
                    ProjectBody(state, actions)
                }
                state.problem?.let {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitNotice(problemWords(it, state.route), tone = StatusTone.Neutral)
                }
            }
        }
    }
}

@Composable
private fun accentName(id: String): String =
    stringResource(
        when (id) {
            "wallpaper" -> Res.string.widgetV2AccentWallpaper
            "app" -> Res.string.widgetV2AccentApp
            "green" -> Res.string.widgetV2AccentGreen
            "teal" -> Res.string.widgetV2AccentTeal
            "blue" -> Res.string.widgetV2AccentBlue
            else -> Res.string.widgetV2AccentAmber
        }
    )

@Composable
private fun ProjectBody(state: WidgetPanelUi, actions: WidgetPanelActions) {
    val enabled =
        if (state.saving || state.loading)
            KitEnabled.Disabled(stringResource(Res.string.widgetV2Busy))
        else KitEnabled.Enabled
    KitChoiceList(
        choices =
            listOf(
                KitChoice("", stringResource(Res.string.chatsHomeAllProjects), enabled = enabled)
            ) +
                state.projects
                    .filter { it.isNotEmpty() }
                    .map {
                        KitChoice(it, it, enabled = enabled)
                    },
        selected = state.project,
        semanticsLabel = stringResource(Res.string.widgetProjectTitle),
        onSelect = actions::chooseProject,
    )
}

@Composable
private fun ComposeBody(state: WidgetPanelUi, actions: WidgetPanelActions, title: String) {
    val field = rememberTextFieldState()
    LaunchedEffect(state.dictated) {
        state.dictated?.let { heard ->
            val previous = field.text.toString()
            field.edit {
                val joined = if (previous.isEmpty()) heard else previous.trimEnd() + " " + heard
                replace(0, length, joined.take(8000))
                placeCursorAtEnd()
            }
            actions.consumeDictation()
        }
    }
    val block =
        when {
            state.uncertain -> stringResource(Res.string.widgetV2Uncertain)
            state.steerMode == "restart" && state.composeBlocked ->
                stringResource(Res.string.widgetV2RestartUnavailable)
            state.composeBlocked -> stringResource(Res.string.widgetV2UnknownBehavior)
            else -> null
        }
    KitSteerBox(
        title = title,
        state = field,
        phase = KitSteerBoxPhase.valueOf(state.phase.name),
        onSend = actions::send,
        onDictate = actions::dictate,
        onClose = actions::close,
        message = state.problem?.takeIf { block == null }?.let { problemWords(it, state.route) },
        blockedReason = block,
    )
}

@Composable
private fun problemWords(problem: WidgetPanelProblem, route: WidgetPanel): String =
    stringResource(
        when {
            route is WidgetPanel.Style ->
                if (problem == WidgetPanelProblem.Stale) Res.string.widgetV2StyleStale
                else Res.string.widgetV2StyleUnavailable
            route is WidgetPanel.Project -> Res.string.widgetProjectUnavailable
            else ->
                when (problem) {
                    WidgetPanelProblem.Stale -> Res.string.widgetV2StaleRequest
                    WidgetPanelProblem.Unavailable -> Res.string.widgetV2Unavailable
                    WidgetPanelProblem.Unsupported -> Res.string.widgetV2Unsupported
                    WidgetPanelProblem.Busy -> Res.string.widgetV2Busy
                    WidgetPanelProblem.Invalid -> Res.string.widgetV2InvalidRequest
                    WidgetPanelProblem.Uncertain -> Res.string.widgetV2Uncertain
                    WidgetPanelProblem.DictationUnavailable ->
                        Res.string.widgetV2DictationUnavailable
                }
        }
    )
