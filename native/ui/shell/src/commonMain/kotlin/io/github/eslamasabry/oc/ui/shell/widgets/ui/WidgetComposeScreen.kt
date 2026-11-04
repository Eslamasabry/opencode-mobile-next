package io.github.eslamasabry.oc.ui.shell.widgets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposeActions
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposeNotice
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposePhase
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetComposeUi
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The widget's transient message box: type or dictate, inspect the words, explicitly Send. */
@Composable
public fun WidgetComposeScreen(state: WidgetComposeUi, actions: WidgetComposeActions) {
    val title = stringResource(when {
        state.voice -> Res.string.widgetV2VoiceTitle
        state.steerMode == "afterReply" -> Res.string.widgetV2AfterReplyTitle
        else -> Res.string.widgetV2SteerTitle
    })
    val sending = stringResource(Res.string.widgetPanelSending)
    val block = when {
        state.phase == WidgetComposePhase.Uncertain -> stringResource(Res.string.widgetV2Uncertain)
        state.loading -> stringResource(Res.string.widgetV2UnknownBehavior)
        !state.available -> stringResource(Res.string.widgetV2StaleRequest)
        !state.voice && state.steerMode == "restart" -> stringResource(Res.string.widgetV2RestartUnavailable)
        !state.voice && state.steerMode !in setOf("steer", "afterReply") -> stringResource(Res.string.widgetV2UnknownBehavior)
        else -> null
    }
    val text = remember { TextFieldState(state.text) }
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(actions::edited) }
    LaunchedEffect(state.text) {
        if (text.text.toString() != state.text) text.setTextAndPlaceCursorAtEnd(state.text)
    }
    KitScreen(
        topBar = {
            KitTopBar(title, exit = KitTopBarExit.Close, exitLabel = stringResource(Res.string.widgetPanelClose), onExit = actions::close)
        },
        loading = state.loading || state.phase in setOf(WidgetComposePhase.Dictating, WidgetComposePhase.Sending),
        loadingLabel = sending,
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            val notice = when (state.notice) {
                WidgetComposeNotice.Stale -> stringResource(Res.string.widgetV2StaleRequest)
                WidgetComposeNotice.Unavailable -> stringResource(Res.string.widgetV2Unavailable)
                WidgetComposeNotice.Unsupported -> stringResource(Res.string.widgetV2Unsupported)
                WidgetComposeNotice.Busy -> stringResource(Res.string.widgetPanelBusy)
                WidgetComposeNotice.Invalid -> stringResource(Res.string.widgetV2InvalidRequest)
                WidgetComposeNotice.Uncertain -> stringResource(Res.string.widgetV2Uncertain)
                WidgetComposeNotice.DictationUnavailable -> stringResource(Res.string.widgetV2DictationUnavailable)
                null -> null
            }
            if (block != null || notice != null) KitNotice(block ?: notice!!)
            if (state.phase == WidgetComposePhase.Sent) KitNotice(stringResource(Res.string.widgetPanelSent))
            KitField(
                text,
                stringResource(Res.string.widgetV2MessageHint),
                Modifier.testTag("widget-steer-field"),
                kind = KitFieldKind.Multiline,
                maxLength = 8000,
                enabled = if (state.canEdit) KitEnabled.Enabled else KitEnabled.Disabled(block ?: sending),
            )
            KitActionBlock(
                primary = KitAction(stringResource(Res.string.widgetV2Send), actions::send,
                    enabled = if (state.canSend) KitEnabled.Enabled else KitEnabled.Disabled(block ?: sending),
                    working = state.phase == WidgetComposePhase.Sending),
                secondary = KitAction(stringResource(Res.string.widgetPanelDictate), actions::dictate,
                    enabled = if (state.canSend) KitEnabled.Enabled else KitEnabled.Disabled(block ?: sending)),
            )
        }
    }
}
