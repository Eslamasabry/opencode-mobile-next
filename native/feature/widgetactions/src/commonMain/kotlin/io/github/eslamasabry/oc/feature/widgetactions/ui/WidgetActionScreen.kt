package io.github.eslamasabry.oc.feature.widgetactions.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitFloatingPanel
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WidgetActionScreen(state: WidgetActionUi, actions: WidgetActionActions) {
    val field = remember { TextFieldState() }
    LaunchedEffect(state.dictatedWords) {
        val words = state.dictatedWords ?: return@LaunchedEffect
        val existing = field.text.toString()
        val combined =
            (existing + if (existing.isEmpty()) words else " $words").take(
                WidgetActionViewModel.MAX_TEXT
            )
        field.edit { replace(0, length, combined) }
        actions.takeDictatedWords()
    }
    val sending = state.phase == WidgetActionPhase.Sending
    val phase =
        when {
            state.phase == WidgetActionPhase.Accepted -> KitSteerBoxPhase.Sent
            sending -> KitSteerBoxPhase.Sending
            state.listening || state.dictating -> KitSteerBoxPhase.Dictating
            else -> KitSteerBoxPhase.Ready
        }
    val blocked =
        when (state.phase) {
            WidgetActionPhase.Uncertain -> stringResource(Res.string.widgetActionCheckFirst)
            WidgetActionPhase.Rejected -> stringResource(Res.string.widgetActionRejected)
            else -> null
        }
    KitFloatingPanel(
        if (sending) null else actions::close,
        stringResource(Res.string.widgetV2Cancel),
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            KitSteerBox(
                title =
                    stringResource(
                        if (state.newConversation) Res.string.widgetV2VoiceTitle
                        else Res.string.widgetActionTitle
                    ),
                state = field,
                phase = phase,
                blockedReason = blocked,
                message =
                    if (state.dictationFailed)
                        stringResource(Res.string.widgetV2DictationUnavailable)
                    else null,
                onSend = actions::send,
                onDictate = actions::dictate,
                onClose = actions::close,
                dictationEnabled =
                    if (state.voiceAvailable) KitEnabled.Enabled
                    else
                        KitEnabled.Disabled(
                            stringResource(Res.string.widgetV2DictationUnavailable)
                        ),
            )
            // This host uses an in-app capture instead of Android's speech window; retain its
            // visible Finish path.
            if (state.listening) {
                Spacer(Modifier.height(Space.S3.dp))
                KitButton(
                    stringResource(Res.string.widgetPanelFinishRecording),
                    actions::finishDictation,
                    working = state.dictating,
                    role = KitButtonRole.Secondary,
                )
            }
            if (
                state.phase == WidgetActionPhase.Rejected ||
                    state.phase == WidgetActionPhase.Uncertain
            ) {
                Spacer(Modifier.height(Space.S3.dp))
                KitButton(
                    stringResource(Res.string.widgetActionOpen),
                    actions::openConversation,
                    role = KitButtonRole.Secondary,
                )
            }
        }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == WidgetActionPhase.Accepted) {
            delay(700) // F: lib/ui/screens/widgets/widget_steer_screen.dart:19
            actions.close()
        }
    }
}

public object WidgetActionEntries {
    @Composable
    public fun Content(model: WidgetActionViewModel) {
        val state by model.state.collectAsState()
        WidgetActionScreen(state, model)
    }
}
