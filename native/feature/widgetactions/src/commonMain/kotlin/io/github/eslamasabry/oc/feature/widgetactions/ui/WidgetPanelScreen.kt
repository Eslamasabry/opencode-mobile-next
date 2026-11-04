package io.github.eslamasabry.oc.feature.widgetactions.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WidgetPanelScreen(state: WidgetPanelUi, actions: WidgetPanelActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (state.mode == WidgetPanelMode.Style) Res.string.widgetV2ConfigureTitle
                        else Res.string.widgetProjectTitle
                    ),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.widgetActionClose),
                onExit = actions::close,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val enabled =
                if (state.saving) KitEnabled.Disabled(stringResource(Res.string.widgetPanelSaving))
                else KitEnabled.Enabled
            if (state.mode == WidgetPanelMode.Style) {
                KitChoiceList(
                    choices =
                        listOf(
                            KitChoice("solid", stringResource(Res.string.widgetPanelSolid)),
                            KitChoice("tinted", stringResource(Res.string.widgetPanelTinted)),
                            KitChoice(
                                "transparent",
                                stringResource(Res.string.widgetPanelTransparent),
                            ),
                        ),
                    semanticsLabel = stringResource(Res.string.widgetPanelBackground),
                    selected = state.background,
                    onSelect = actions::background,
                )
                KitChoiceList(
                    choices =
                        listOf(
                            KitChoice(
                                "wallpaper",
                                stringResource(Res.string.widgetV2AccentWallpaper),
                            ),
                            KitChoice("app", stringResource(Res.string.widgetV2AccentApp)),
                            KitChoice("green", stringResource(Res.string.widgetV2AccentGreen)),
                            KitChoice("teal", stringResource(Res.string.widgetV2AccentTeal)),
                            KitChoice("blue", stringResource(Res.string.widgetV2AccentBlue)),
                            KitChoice("amber", stringResource(Res.string.widgetV2AccentAmber)),
                        ),
                    semanticsLabel = stringResource(Res.string.widgetPanelAccent),
                    selected = state.accent,
                    onSelect = actions::accent,
                )
                KitButton(
                    label = stringResource(Res.string.widgetProjectTitle),
                    onClick = { actions.mode(WidgetPanelMode.Project) },
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                )
            } else {
                KitChoiceList(
                    choices =
                        listOf(KitChoice("", stringResource(Res.string.widgetPanelAllProjects))) +
                            state.projects.map { KitChoice(it, it) },
                    semanticsLabel = stringResource(Res.string.widgetProjectTitle),
                    selected = state.project,
                    onSelect = actions::project,
                )
                KitButton(
                    label = stringResource(Res.string.widgetV2ConfigureTitle),
                    onClick = { actions.mode(WidgetPanelMode.Style) },
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                )
            }
            if (state.failed) KitGroupNote(stringResource(Res.string.widgetV2StyleUnavailable))
            KitButton(
                label = stringResource(Res.string.widgetPanelSave),
                onClick = actions::save,
                enabled = enabled,
                working = state.saving,
            )
        }
    }
}

public object WidgetPanelEntries {
    @Composable
    public fun Content(model: WidgetPanelViewModel) {
        val state by model.state.collectAsState()
        WidgetPanelScreen(state, model)
    }
}
