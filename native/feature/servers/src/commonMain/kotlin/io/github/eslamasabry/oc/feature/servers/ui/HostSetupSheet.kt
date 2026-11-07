package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.runtime.install.PinnedHostCommands
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitContentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Copy-only host instructions. A host name/port must come from the saved profile, when available.
 */
@Composable
internal fun HostSetupSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HostSetupContentSheet(visible = visible, onDismiss = onDismiss)
}

@Composable
internal fun HostSetupContentSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    serverName: String? = null,
    port: Int = 4096, // F: lib/ui/screens/host_management_screen.dart:29
    supportsUsbHostBridge: Boolean = true,
    onGuide: (() -> Unit)? = null,
) {
    val server = serverName ?: stringResource(Res.string.e7SetupDefaultServer)
    var clip by remember { mutableStateOf<String?>(null) }
    var clipId by remember { mutableLongStateOf(0L) }
    KitClipboardEffect(if (clip == null) null else clipId, clip, onDone = { clip = null })
    val copy: (String) -> Unit = { value ->
        clipId++
        clip = value
    }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.hostServiceTitle, server),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
    ) {
        Column {
            KitText(stringResource(Res.string.hostServiceIntro, server), tone = TextTone.Secondary)
            KitRowGroup(label = stringResource(Res.string.e7SetupHostFirstSetup)) {
                KitContentRow(
                    stringResource(Res.string.openCode2HostInstall),
                    supporting = stringResource(Res.string.openCode2HostInstallDetail),
                ) {
                    KitCodeBlock(
                        source = PinnedHostCommands.install(port),
                        kind = KitCodeKind.Command,
                        modifier = Modifier.testTag("host-command-install"),
                        wrap = true,
                        copyLabel = stringResource(Res.string.kitCodeCopyCommand),
                        copiedLabel = stringResource(Res.string.kitCopied),
                        onCopy = copy,
                    )
                }
                if (supportsUsbHostBridge)
                    KitContentRow(stringResource(Res.string.e7SetupUsbAccess)) {
                        KitCodeBlock(
                            source = "adb reverse tcp:$port tcp:$port",
                            kind = KitCodeKind.Command,
                            modifier = Modifier.testTag("host-command-adb-reverse"),
                            wrap = true,
                            copyLabel =
                                stringResource(
                                    Res.string.e7SetupCopyCommandLabel,
                                    stringResource(Res.string.e7SetupUsbAccess),
                                ),
                            copiedLabel = stringResource(Res.string.kitCopied),
                            onCopy = copy,
                        )
                    }
            }
            if (onGuide != null)
                KitButton(
                    stringResource(Res.string.e7SetupFullWalkthrough),
                    onGuide,
                    role = KitButtonRole.Tertiary,
                    icon = KitIcons.externalLink,
                    fullWidth = false,
                )
        }
    }
}
