package io.github.eslamasabry.oc.feature.preview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.preview.presentation.PortsActions
import io.github.eslamasabry.oc.feature.preview.presentation.PortsUi
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBlock
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.routes.PreviewPort
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.devPreviewMenu
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoHostBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoHostTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoServerBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoneFound
import io.github.eslamasabry.oc.ui.words.res.devPreviewOnPhone
import io.github.eslamasabry.oc.ui.words.res.devPreviewOnServer
import io.github.eslamasabry.oc.ui.words.res.devPreviewPortTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewSheetSubtitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.previewQrPortHelp
import io.github.eslamasabry.oc.ui.words.res.previewQrPortInvalid
import io.github.eslamasabry.oc.ui.words.res.previewQrPortLabel
import io.github.eslamasabry.oc.ui.words.res.previewQrPortOpen
import org.jetbrains.compose.resources.stringResource

/**
 * "Preview a running app" (Flutter chat dev-preview sheet): the dev servers this conversation mentioned, newest first,
 * each naming where it opens, and a typed port as the visible path when nothing was mentioned.
 */
@Composable
public fun PortsScreen(state: PortsUi, actions: PortsActions, modifier: Modifier = Modifier) {
    val typed = remember { TextFieldState() }
    KitScreen(
        modifier = modifier,
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.devPreviewMenu),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.blocked?.let { why ->
                val noServer = why == PreviewBlock.NoServer
                KitNotice(
                    message = stringResource(if (noServer) Res.string.devPreviewNoServerBody else Res.string.devPreviewNoHostBody),
                    title = stringResource(if (noServer) Res.string.devPreviewNoServerTitle else Res.string.devPreviewNoHostTitle),
                )
            }
            KitSectionLabel(stringResource(Res.string.devPreviewSheetSubtitle))
            if (state.rows.isEmpty()) KitText(stringResource(Res.string.devPreviewNoneFound))
            val onPhone = stringResource(Res.string.devPreviewOnPhone)
            for (row in state.rows) {
                KitRow(
                    title = stringResource(Res.string.devPreviewPortTitle, row.port.toString()),
                    supporting = row.host?.let { stringResource(Res.string.devPreviewOnServer, it) } ?: onPhone,
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.open(PreviewPort(row.port, row.https, row.path)) },
                )
            }
            KitField(
                typed,
                stringResource(Res.string.previewQrPortLabel),
                kind = KitFieldKind.Number,
                placeholder = "5173",
                helper = stringResource(Res.string.previewQrPortHelp),
                error = if (state.invalidPort) stringResource(Res.string.previewQrPortInvalid) else null,
                maxLength = 5,
                onSubmit = { actions.openTyped(typed.text.toString()) },
            )
            KitButton(
                stringResource(Res.string.previewQrPortOpen),
                { actions.openTyped(typed.text.toString()) },
                role = KitButtonRole.Secondary,
            )
        }
    }
}
