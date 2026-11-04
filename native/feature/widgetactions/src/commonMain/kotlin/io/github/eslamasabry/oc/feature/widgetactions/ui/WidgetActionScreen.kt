package io.github.eslamasabry.oc.feature.widgetactions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WidgetActionScreen(state: WidgetActionUi, actions: WidgetActionActions) {
    val field = remember { TextFieldState() }
    val editing = state.phase == WidgetActionPhase.Editing
    val sending = state.phase == WidgetActionPhase.Sending
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.widgetActionTitle),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.widgetActionClose),
                onExit = actions::close,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (editing || sending) {
                KitGroupNote(stringResource(Res.string.widgetActionHint))
                KitField(
                    state = field,
                    label = stringResource(Res.string.widgetActionMessage),
                    kind = KitFieldKind.Multiline,
                    maxLength = WidgetActionViewModel.MAX_TEXT,
                    enabled =
                        if (editing) KitEnabled.Enabled
                        else KitEnabled.Disabled(stringResource(Res.string.widgetActionSending)),
                )
                KitButton(
                    label = stringResource(Res.string.widgetActionSend),
                    onClick = { actions.send(field.text.toString()) },
                    modifier = Modifier.fillMaxWidth(),
                    working = sending,
                    enabled =
                        if (editing && field.text.isNotBlank()) KitEnabled.Enabled
                        else
                            KitEnabled.Disabled(
                                stringResource(
                                    if (sending) Res.string.widgetActionSending
                                    else Res.string.widgetActionEnterMessage
                                )
                            ),
                )
            } else {
                KitStateView(
                    kind =
                        if (state.phase == WidgetActionPhase.Accepted) KitStateKind.Calm
                        else KitStateKind.Error,
                    title =
                        stringResource(
                            when (state.phase) {
                                WidgetActionPhase.Accepted -> Res.string.widgetActionAccepted
                                WidgetActionPhase.Uncertain -> Res.string.widgetActionUncertain
                                else -> Res.string.widgetActionRejected
                            }
                        ),
                    body =
                        stringResource(
                            if (state.phase == WidgetActionPhase.Uncertain)
                                Res.string.widgetActionCheckFirst
                            else Res.string.widgetActionOpenHint
                        ),
                )
                KitButton(
                    label = stringResource(Res.string.widgetActionOpen),
                    onClick = actions::openConversation,
                    modifier = Modifier.fillMaxWidth(),
                    role = KitButtonRole.Secondary,
                )
                KitButton(
                    label = stringResource(Res.string.widgetActionClose),
                    onClick = actions::close,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
