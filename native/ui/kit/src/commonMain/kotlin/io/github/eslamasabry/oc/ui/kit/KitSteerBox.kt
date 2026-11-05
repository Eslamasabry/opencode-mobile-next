package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.KitPanelHeading
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

public enum class KitSteerBoxPhase {
    Ready,
    Dictating,
    Sending,
    Sent,
}

/** Flutter's floating widget composer: two-to-five-line field, microphone and one Send action. */
@Composable
public fun KitSteerBox(
    title: String,
    state: TextFieldState,
    phase: KitSteerBoxPhase,
    onSend: (String) -> Unit,
    onDictate: () -> Unit,
    onClose: () -> Unit,
    message: String? = null,
    blockedReason: String? = null,
    dictationEnabled: KitEnabled = KitEnabled.Enabled,
) {
    Column(Modifier.fillMaxWidth()) {
        val idle = phase == KitSteerBoxPhase.Ready && blockedReason == null
        val busy = stringResource(Res.string.widgetV2Busy)
        val sent = phase == KitSteerBoxPhase.Sent
        val reason =
            blockedReason
                ?: if (sent) stringResource(Res.string.widgetV2Accepted)
                else stringResource(Res.string.widgetV2Sending)
        val focus = remember { FocusRequester() }
        KitPanelHeading(
            title,
            stringResource(Res.string.widgetV2Cancel),
            onClose,
            enabled =
                if (phase == KitSteerBoxPhase.Sending) KitEnabled.Disabled(reason)
                else KitEnabled.Enabled,
        )
        Spacer(Modifier.height(Space.S3.dp))
        KitField(
            state,
            stringResource(Res.string.widgetV2MessageHint),
            placeholder =
                stringResource(
                    Res.string.widgetV2MessageHint
                ), // F: lib/ui/kit/kit_steer_box.dart:141
            modifier = Modifier.testTag("widget-message"),
            kind = KitFieldKind.Multiline,
            maxLength = 8000, // F: lib/ui/kit/kit_steer_box.dart:92
            multilineLines = 2..5, // F: lib/ui/kit/kit_steer_box.dart:144-145
            focusRequester = focus,
            enabled =
                if (idle || (phase == KitSteerBoxPhase.Dictating && blockedReason == null))
                    KitEnabled.Enabled
                else KitEnabled.Disabled(reason),
        )
        (blockedReason ?: message)?.let {
            Spacer(Modifier.height(Space.S3.dp))
            KitNotice(it)
        }
        Spacer(Modifier.height(Space.S3.dp))
        Row(Modifier.fillMaxWidth()) {
            KitIconButton(
                KitIcons.mic,
                stringResource(Res.string.widgetV2Dictate),
                onDictate,
                modifier = Modifier.testTag("widget-mic"),
                enabled =
                    if (idle) dictationEnabled else KitEnabled.Disabled(blockedReason ?: busy),
                working = phase == KitSteerBoxPhase.Dictating,
            )
            Spacer(Modifier.width(Space.S3.dp))
            val text = state.text.toString().trim()
            val send = idle && text.isNotEmpty()
            KitButton(
                label =
                    stringResource(
                        if (sent) Res.string.widgetV2Accepted
                        else if (phase == KitSteerBoxPhase.Sending) Res.string.widgetV2Sending
                        else Res.string.widgetV2Send
                    ),
                onClick = { if (send) onSend(state.text.toString().trim()) },
                icon = if (sent) KitIcons.check else KitIcons.send,
                enabled =
                    if (send) KitEnabled.Enabled
                    else
                        KitEnabled.Disabled(
                            blockedReason
                                ?: if (text.isEmpty())
                                    stringResource(Res.string.widgetV2MessageHint)
                                else reason
                        ),
                working = phase == KitSteerBoxPhase.Sending,
                modifier = Modifier.weight(1f).testTag("widget-send"),
                fullWidth = true,
            )
        }
        LaunchedEffect(state) {
            try {
                focus.requestFocus()
            } catch (_: IllegalStateException) {}
        }
    }
}
