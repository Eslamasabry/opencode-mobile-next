package io.github.eslamasabry.oc.ui.shell.widgets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.widget.KitWidgetStylePreview
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelActions
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelMode
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelProblem
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelUi
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Standalone widget configuration; no app navigation or connection owner is created here. */
@Composable
public fun WidgetPanelScreen(state: WidgetPanelUi, actions: WidgetPanelActions) {
    val saving = stringResource(Res.string.widgetPanelSaving)
    val enabled = if (state.saving || state.loading || state.problem == WidgetPanelProblem.Stale) KitEnabled.Disabled(saving) else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(if (state.mode == WidgetPanelMode.Project) Res.string.widgetProjectTitle else Res.string.widgetV2ConfigureTitle),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.widgetPanelClose),
                onExit = actions::close,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading || state.saving,
        loadingLabel = saving,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.problem?.let {
                KitNotice(stringResource(
                    if (it == WidgetPanelProblem.Stale) Res.string.widgetV2StyleStale
                    else if (state.mode == WidgetPanelMode.Unavailable) Res.string.widgetPanelActionUnavailable
                    else if (state.mode == WidgetPanelMode.Project) Res.string.widgetProjectUnavailable
                    else Res.string.widgetV2StyleUnavailable,
                ))
            }
            if (!state.loading && state.mode == WidgetPanelMode.Style) {
                state.palette?.let { palette ->
                    KitWidgetStylePreview(palette.background, palette.text, palette.muted, palette.accent, state.widePreview, stringResource(Res.string.widgetPanelPreview))
                }
                KitSegmented(
                    listOf(KitSegment(false, stringResource(Res.string.widgetPanelSquare)), KitSegment(true, stringResource(Res.string.widgetPanelWide))),
                    state.widePreview,
                    stringResource(Res.string.widgetPanelPreview),
                    onChange = actions::preview,
                )
                KitSectionLabel(stringResource(Res.string.widgetPanelBackground))
                KitSegmented(
                    listOf(
                        KitSegment("solid", stringResource(Res.string.widgetPanelSolid)),
                        KitSegment("tinted", stringResource(Res.string.widgetPanelTinted)),
                        KitSegment("transparent", stringResource(Res.string.widgetPanelTransparent)),
                    ),
                    state.background,
                    stringResource(Res.string.widgetPanelBackground),
                    enabled = enabled,
                    onChange = actions::background,
                )
                KitSectionLabel(stringResource(Res.string.widgetPanelAccent))
                KitChoiceList(
                    listOf(
                        KitChoice("wallpaper", stringResource(Res.string.widgetV2AccentWallpaper), enabled = enabled),
                        KitChoice("app", stringResource(Res.string.widgetV2AccentApp), enabled = enabled),
                        KitChoice("green", stringResource(Res.string.widgetV2AccentGreen), enabled = enabled),
                        KitChoice("teal", stringResource(Res.string.widgetV2AccentTeal), enabled = enabled),
                        KitChoice("blue", stringResource(Res.string.widgetV2AccentBlue), enabled = enabled),
                        KitChoice("amber", stringResource(Res.string.widgetV2AccentAmber), enabled = enabled),
                    ),
                    selected = state.accent,
                    semanticsLabel = stringResource(Res.string.widgetPanelAccent),
                    onSelect = actions::accent,
                )
                KitButton(stringResource(Res.string.widgetV2SaveStyle), actions::saveStyle, Modifier.fillMaxWidth().testTag("widget-style-save"), enabled = enabled, working = state.saving)
            }
            if (!state.loading && state.mode == WidgetPanelMode.Project) {
                KitChoiceList(
                    choices = listOf(KitChoice("", stringResource(Res.string.chatsHomeAllProjects), enabled = enabled)) +
                        state.projects.map { KitChoice(it, it, enabled = enabled) },
                    selected = state.project,
                    semanticsLabel = stringResource(Res.string.widgetProjectTitle),
                    modifier = Modifier.testTag("widget-project-all"),
                    onSelect = actions::project,
                )
                // The Conversations widget's launcher Edit is for its project; style is a visible secondary page.
            }
            if (state.mode == WidgetPanelMode.Unavailable) {
                KitButton(stringResource(Res.string.widgetPanelClose), actions::close, role = KitButtonRole.Secondary)
            }
        }
    }
}
