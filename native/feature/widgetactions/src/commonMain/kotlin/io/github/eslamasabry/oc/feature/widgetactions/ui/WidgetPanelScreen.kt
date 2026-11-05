package io.github.eslamasabry.oc.feature.widgetactions.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitFloatingPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitPanelHeading
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Android's current overlay adapter, using the same kit form as WidgetPanel.Style/Project. */
@Composable
public fun WidgetPanelScreen(state: WidgetPanelUi, actions: WidgetPanelActions) {
    var preview by remember { mutableStateOf("card") }
    val enabled =
        if (state.saving) KitEnabled.Disabled(stringResource(Res.string.widgetV2Sending))
        else KitEnabled.Enabled
    val style = state.mode == WidgetPanelMode.Style
    val title =
        stringResource(
            if (style) Res.string.widgetV2ConfigureTitle else Res.string.widgetProjectTitle
        )
    KitFloatingPanel(
        if (state.saving) null else actions::close,
        stringResource(Res.string.widgetV2Cancel),
    ) {
        KitPanelHeading(title, stringResource(Res.string.widgetV2Cancel), actions::close, enabled)
        Spacer(Modifier.height(Space.S3.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (style) {
                KitWidgetStyleForm(
                    preview,
                    state.background,
                    state.accent,
                    accents = listOf("wallpaper", "app", "green", "teal", "blue", "amber"),
                    accentName = { accentName(it) },
                    accentColor = { widgetAccentColor(it) },
                    onPreviewSize = { preview = it },
                    onBackground = actions::background,
                    onAccent = actions::accent,
                    enabled = enabled,
                )
            } else {
                KitChoiceList(
                    choices =
                        listOf(
                            KitChoice(
                                "",
                                stringResource(Res.string.chatsHomeAllProjects),
                                enabled = enabled,
                            )
                        ) +
                            state.projects
                                .filter { it.isNotEmpty() }
                                .map { KitChoice(it, it, enabled = enabled) },
                    selected = state.project,
                    semanticsLabel = title,
                    onSelect = {
                        actions.project(it)
                        actions.save()
                    },
                )
            }
        }
        if (state.failed) {
            Spacer(Modifier.height(Space.S3.dp))
            KitNotice(
                stringResource(
                    if (style) Res.string.widgetV2StyleUnavailable
                    else Res.string.widgetProjectUnavailable
                )
            )
        }
        if (style) {
            Spacer(Modifier.height(Space.S4.dp))
            KitButton(
                stringResource(
                    if (state.saving) Res.string.widgetV2Sending else Res.string.widgetV2SaveStyle
                ),
                actions::save,
                enabled = enabled,
                working = state.saving,
                modifier = Modifier.fillMaxWidth(),
                fullWidth = true,
            )
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

public object WidgetPanelEntries {
    @Composable
    public fun Content(model: WidgetPanelViewModel) {
        val state by model.state.collectAsState()
        WidgetPanelScreen(state, model)
    }
}
