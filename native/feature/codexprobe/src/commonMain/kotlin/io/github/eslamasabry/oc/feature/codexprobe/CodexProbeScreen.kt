package io.github.eslamasabry.oc.feature.codexprobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.code.*
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun CodexProbeScreen(state: CodexProbeUi, actions: CodexProbeActions) {
    // A bearer token is deliberately ephemeral: no rememberSaveable, route, persisted form or
    // diagnostics.
    val address = remember { TextFieldState("ws://127.0.0.1:4141") }
    val directory = remember { TextFieldState() }
    val token = remember { TextFieldState() }
    var clip by remember { mutableStateOf<String?>(null) }
    var clipId by remember { mutableLongStateOf(0L) }
    KitClipboardEffect(if (clip == null) null else clipId, clip) { clip = null }
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.codexNativeWorking))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.codexNativeTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitNotice(message = stringResource(Res.string.codexNativeIntro))
            KitCodeBlock(
                source = actions.startCommand,
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.codexNativeCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = {
                    clipId++
                    clip = it
                },
            )
            KitNotice(message = stringResource(Res.string.codexNativeTokenHelp))
            KitField(
                address,
                stringResource(Res.string.codexNativeAddress),
                kind = KitFieldKind.Url,
                enabled = enabled,
                maxLength = 8192,
            )
            KitField(
                directory,
                stringResource(Res.string.codexNativeDirectory),
                kind = KitFieldKind.Path,
                enabled = enabled,
                maxLength = 4096,
            )
            KitField(
                token,
                stringResource(Res.string.codexNativeToken),
                kind = KitFieldKind.Secret,
                enabled = enabled,
                maxLength = 16384,
            )
            KitButton(
                stringResource(Res.string.codexNativeProbe),
                {
                    actions.probe(
                        address.text.toString(),
                        directory.text.toString(),
                        token.text.toString(),
                    )
                },
                enabled = enabled,
                working = state.busy,
            )
            if (state.probed) KitNotice(message = stringResource(Res.string.codexNativeProbed))
            state.failure?.let { failure ->
                KitNotice(
                    message =
                        stringResource(
                            when (failure) {
                                is Failure.SignIn -> Res.string.codexNativeAuthFailed
                                is Failure.Incompatible -> Res.string.codexNativeVersion
                                is Failure.Refused -> Res.string.codexNativeScopeFailed
                                else -> Res.string.codexNativeFailed
                            }
                        )
                )
            }
            if (state.connected) {
                KitNotice(
                    message =
                        stringResource(
                            if (state.accountQualified) Res.string.codexNativeReaderOpen
                            else Res.string.codexNativeVersion
                        )
                )
                KitButton(
                    stringResource(Res.string.codexNativePlans),
                    actions::plans,
                    enabled = enabled,
                )
                KitButton(
                    stringResource(Res.string.codexNativeClose),
                    actions::close,
                    enabled = enabled,
                    role = KitButtonRole.Secondary,
                )
            } else
                KitButton(
                    stringResource(Res.string.codexNativeOpen),
                    {
                        actions.open(
                            address.text.toString(),
                            directory.text.toString(),
                            token.text.toString(),
                        )
                    },
                    enabled = enabled,
                    role = KitButtonRole.Secondary,
                )
            KitNotice(message = stringResource(Res.string.codexNativeReadOnly))
        }
    }
}
