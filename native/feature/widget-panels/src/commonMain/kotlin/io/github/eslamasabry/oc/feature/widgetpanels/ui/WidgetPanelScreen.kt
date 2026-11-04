package io.github.eslamasabry.oc.feature.widgetpanels.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.*
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreview
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
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
    KitSheetHost(Modifier.fillMaxSize()) {
        KitSheet(
            visible = true,
            onDismissRequest = actions::close,
            title = title,
            closeLabel = stringResource(Res.string.kitSheetClose),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                state.problem?.let {
                    KitNotice(problemWords(it, state.route), tone = StatusTone.Failure)
                }
                when (state.route) {
                    is WidgetPanel.Style -> StyleBody(state, actions)
                    is WidgetPanel.Project -> ProjectBody(state, actions)
                    is WidgetPanel.Steer -> ComposeBody(state, actions)
                }
            }
        }
    }
}

@Composable
private fun StyleBody(state: WidgetPanelUi, actions: WidgetPanelActions) {
    KitWidgetPreview(
        title = stringResource(Res.string.widgetV2PreviewTitle),
        background = state.background,
        accent = state.accent,
        compact = state.previewSize == "compact",
        wide = state.previewSize == "list",
        modifier = Modifier.testTag("widget-style-preview"),
    )
    val waiting = stringResource(Res.string.widgetV2Busy)
    val enabled =
        if (state.saving || state.loading) KitEnabled.Disabled(waiting) else KitEnabled.Enabled
    KitChoiceList(
        choices =
            listOf(
                KitChoice("card", stringResource(Res.string.widgetV2SizeCard)),
                KitChoice("compact", stringResource(Res.string.widgetV2SizeCompact)),
                KitChoice("list", stringResource(Res.string.widgetV2SizeList)),
            ),
        selected = state.previewSize,
        semanticsLabel = stringResource(Res.string.widgetV2SizeLabel),
        onSelect = actions::choosePreviewSize,
    )
    KitChoiceList(
        choices =
            listOf(
                KitChoice(
                    "solid",
                    stringResource(Res.string.widgetV2BackgroundSolid),
                    enabled = enabled,
                ),
                KitChoice(
                    "tinted",
                    stringResource(Res.string.widgetV2BackgroundTinted),
                    enabled = enabled,
                ),
                KitChoice(
                    "transparent",
                    stringResource(Res.string.widgetV2BackgroundTransparent),
                    enabled = enabled,
                ),
            ),
        selected = state.background,
        semanticsLabel = stringResource(Res.string.widgetV2BackgroundLabel),
        onSelect = actions::chooseBackground,
    )
    KitChoiceList(
        choices =
            listOf(
                KitChoice(
                    "wallpaper",
                    stringResource(Res.string.widgetV2AccentWallpaper),
                    enabled = enabled,
                ),
                KitChoice("app", stringResource(Res.string.widgetV2AccentApp), enabled = enabled),
                KitChoice(
                    "green",
                    stringResource(Res.string.widgetV2AccentGreen),
                    enabled = enabled,
                ),
                KitChoice("teal", stringResource(Res.string.widgetV2AccentTeal), enabled = enabled),
                KitChoice("blue", stringResource(Res.string.widgetV2AccentBlue), enabled = enabled),
                KitChoice(
                    "amber",
                    stringResource(Res.string.widgetV2AccentAmber),
                    enabled = enabled,
                ),
            ),
        selected = state.accent,
        semanticsLabel = stringResource(Res.string.widgetV2AccentLabel),
        onSelect = actions::chooseAccent,
    )
    KitButton(
        stringResource(Res.string.widgetV2SaveStyle),
        actions::saveStyle,
        enabled = enabled,
        working = state.saving,
        modifier = Modifier.testTag("widget-style-save"),
    )
}

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
            ) + state.projects.map { KitChoice(it, it, enabled = enabled) },
        selected = state.project,
        semanticsLabel = stringResource(Res.string.widgetProjectTitle),
        onSelect = actions::chooseProject,
    )
}

@Composable
private fun ComposeBody(state: WidgetPanelUi, actions: WidgetPanelActions) {
    val field = remember { TextFieldState() }
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
            state.phase != WidgetComposePhase.Ready -> stringResource(Res.string.widgetV2Busy)
            else -> null
        }
    if (block != null && state.problem == null) KitNotice(block)
    val enabled = block?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled
    if (state.phase == WidgetComposePhase.Sent) KitText(stringResource(Res.string.widgetV2Accepted))
    KitField(
        field,
        label = stringResource(Res.string.widgetV2MessageHint),
        kind = KitFieldKind.Multiline,
        maxLength = 8000,
        enabled = enabled,
        modifier = Modifier.testTag("widget-message"),
    )
    KitButton(
        stringResource(Res.string.widgetV2Dictate),
        actions::dictate,
        role = KitButtonRole.Secondary,
        enabled = enabled,
        working = state.phase == WidgetComposePhase.Dictating,
    )
    KitButton(
        stringResource(Res.string.widgetV2Send),
        { actions.send(field.text.toString()) },
        enabled = enabled,
        working = state.phase == WidgetComposePhase.Sending,
        modifier = Modifier.testTag("widget-send"),
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
