package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.install.PinnedHostCommands
import io.github.eslamasabry.oc.engine.runtime.install.UbuntuHostScript
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Copy-only host setup, as Flutter's HostManagementScreen: the command runs on the person's
 * computer.
 */
@Composable
internal fun HostSetupSheet(visible: Boolean, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(UbuntuHostScript.Command.Install) }
    var clip by remember { mutableStateOf<String?>(null) }
    var clipId by remember { mutableLongStateOf(0L) }
    KitClipboardEffect(if (clip == null) null else clipId, clip) { clip = null }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.linuxHostSetupTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitNotice(message = stringResource(Res.string.linuxHostSetupIntro))
            KitNotice(message = stringResource(Res.string.linuxHostSetupSafety))
            KitRowGroup {
                for (command in HOST_COMMANDS) {
                    KitRow(
                        title = stringResource(command.word()),
                        onClick = { chosen = command },
                    )
                }
            }
            KitText(stringResource(chosen.word()))
            KitCodeBlock(
                source = "OPENCODE_PORT=4096 bash ubuntu-opencode.sh ${chosen.wireName}",
                kind = KitCodeKind.Command,
                modifier = Modifier.testTag("host-setup-command"),
                wrap = true,
                copyLabel = stringResource(Res.string.linuxHostSetupCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                // This text is authored by the app and contains no credential. Copy the bundled
                // script verbatim:
                // redacting shell variable assignments would change the program the person is about
                // to run.
                onCopy = {
                    clipId++
                    clip = UbuntuHostScript.copyCommand(chosen)
                },
            )
            KitNotice(message = stringResource(Res.string.linuxHostSetupCopyDetail))
            KitText(stringResource(Res.string.secHostOfficialInstall))
            KitCodeBlock(
                source = PinnedHostCommands.install(),
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.linuxHostSetupCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = {
                    clipId++
                    clip = PinnedHostCommands.install()
                },
            )
            KitText(stringResource(Res.string.secHostPinnedFront))
            KitNotice(stringResource(Res.string.secHostPinnedFrontHelp))
            KitCodeBlock(
                source = PinnedHostCommands.teamFront,
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.linuxHostSetupCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = {
                    clipId++
                    clip = PinnedHostCommands.teamFront
                },
            )
            KitText(stringResource(Res.string.linuxHostSetupUsb))
            KitCodeBlock(
                source = "adb reverse tcp:4096 tcp:4096",
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.linuxHostSetupCopyUsb),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = {
                    clipId++
                    clip = it
                },
            )
        }
    }
}

private val HOST_COMMANDS =
    listOf(
        UbuntuHostScript.Command.Install,
        UbuntuHostScript.Command.Update,
        UbuntuHostScript.Command.Start,
        UbuntuHostScript.Command.Stop,
        UbuntuHostScript.Command.Restart,
        UbuntuHostScript.Command.Status,
    )

private fun UbuntuHostScript.Command.word(): StringResource =
    when (this) {
        UbuntuHostScript.Command.Install -> Res.string.linuxHostSetupInstall
        UbuntuHostScript.Command.Update -> Res.string.linuxHostSetupUpdate
        UbuntuHostScript.Command.Start -> Res.string.linuxHostSetupStart
        UbuntuHostScript.Command.Stop -> Res.string.linuxHostSetupStop
        UbuntuHostScript.Command.Restart -> Res.string.linuxHostSetupRestart
        else -> Res.string.linuxHostSetupStatus
    }
