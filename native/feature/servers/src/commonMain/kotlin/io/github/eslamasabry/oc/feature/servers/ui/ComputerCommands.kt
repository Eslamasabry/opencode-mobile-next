package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.feature.servers.presentation.SetupCommands
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The first-run Connect path and later Servers visits share these exact copyable programs. */
@Composable
internal fun ComputerCommands() {
    var expanded by remember { mutableStateOf(false) }
    var backend by remember { mutableStateOf(BackendKind.OpenCode2) }
    var clip by remember { mutableStateOf<String?>(null) }
    var clipId by remember { mutableLongStateOf(0L) }
    KitClipboardEffect(if (clip == null) null else clipId, clip) { clip = null }
    KitButton(
        stringResource(Res.string.firstRunShowCommands),
        { expanded = !expanded },
        role = KitButtonRole.Tertiary,
    )
    if (expanded)
        Column {
            KitChoiceList(
                choices =
                    listOf(
                        KitChoice(BackendKind.OpenCode2, "OpenCode"),
                        KitChoice(BackendKind.Paseo, "Paseo"),
                        KitChoice(BackendKind.Codex, "Codex"),
                    ),
                selected = backend,
                semanticsLabel = stringResource(Res.string.firstRunShowCommands),
                onSelect = { backend = it },
            )
            val copy: (String) -> Unit = { value ->
                clipId++
                clip = value
            }
            KitCodeBlock(
                SetupCommands.startFor(backend),
                KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.kitCopyDetails),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = copy,
            )
            if (backend == BackendKind.Paseo) {
                KitText(stringResource(Res.string.firstRunCommandsPaseoNetwork))
                KitCodeBlock(
                    SetupCommands.PASEO_START_PRIVATE_NETWORK,
                    KitCodeKind.Command,
                    wrap = true,
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    copiedLabel = stringResource(Res.string.kitCopied),
                    onCopy = copy,
                )
            }
            if (backend == BackendKind.Codex) {
                KitText(stringResource(Res.string.firstRunCommandsCodexUsb))
                KitCodeBlock(
                    SetupCommands.CODEX_USB,
                    KitCodeKind.Command,
                    wrap = true,
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    copiedLabel = stringResource(Res.string.kitCopied),
                    onCopy = copy,
                )
            }
        }
}
