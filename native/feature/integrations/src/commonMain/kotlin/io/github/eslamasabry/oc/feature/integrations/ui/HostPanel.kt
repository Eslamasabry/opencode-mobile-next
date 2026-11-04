package io.github.eslamasabry.oc.feature.integrations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.host.ByoHostFailureCode
import io.github.eslamasabry.oc.engine.integrations.host.ByoHostPhase
import io.github.eslamasabry.oc.feature.integrations.presentation.HostPanelActions
import io.github.eslamasabry.oc.feature.integrations.presentation.HostPanelUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Visible native preparation and recovery; no credentials are accepted while the reviewed bundle is
 * unpublished.
 */
@Composable
internal fun HostPanel(state: HostPanelUi, actions: HostPanelActions) {
    var expanded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Boolean?>(null) }
    var clipboard by remember { mutableStateOf<String?>(null) }
    var clipVersion by remember { mutableLongStateOf(0) }
    KitClipboardEffect(if (clipboard == null) null else clipVersion, clipboard) { clipboard = null }
    KitRowGroup {
        KitRow(
            stringResource(Res.string.hostSecTitle),
            supporting = stringResource(Res.string.hostSecIntro),
            supportingMaxLines = 4,
            onClick = { expanded = !expanded },
        )
    }
    if (!expanded) return
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.hostSecWorking))
        else KitEnabled.Enabled
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        KitNotice(stringResource(Res.string.hostSecBundleMissing))
        KitButton(
            stringResource(Res.string.hostSecRefresh),
            actions::refreshHosts,
            enabled = enabled,
        )
        state.failure?.let { failure ->
            KitNotice(
                stringResource(
                    when (failure) {
                        ByoHostFailureCode.TailnetRequired,
                        ByoHostFailureCode.InvalidTarget -> Res.string.hostSecTargetFailure
                        ByoHostFailureCode.HostKeyChanged,
                        ByoHostFailureCode.IdentityChanged -> Res.string.hostSecIdentityFailure
                        ByoHostFailureCode.BundleUnavailable -> Res.string.hostSecBundleMissing
                        ByoHostFailureCode.RevocationFailed -> Res.string.hostSecRevokeFailure
                        ByoHostFailureCode.Storage -> Res.string.hostSecStorageFailure
                        else -> Res.string.hostSecFailure
                    }
                )
            )
        }
        if (state.machines.isNotEmpty())
            KitRowGroup(label = stringResource(Res.string.hostSecSaved)) {
                state.machines.forEach { machine ->
                    KitRow(
                        machine.id,
                        selected = state.selected == machine.id,
                        onClick = { actions.chooseHost(machine.id) },
                    )
                }
            }
        val address = remember { TextFieldState() }
        val port = remember { TextFieldState("22") }
        KitField(address, stringResource(Res.string.hostSecAddress), maxLength = 320)
        KitField(port, stringResource(Res.string.hostSecPort), maxLength = 5)
        KitButton(
            stringResource(Res.string.hostSecInspect),
            { actions.inspectHost(address.text.toString(), port.text.toString()) },
            enabled = enabled,
        )
        state.fingerprint?.let { fingerprint ->
            KitNotice(stringResource(Res.string.hostSecTrustHelp))
            KitText(fingerprint)
            KitButton(
                stringResource(Res.string.hostSecTrust),
                actions::prepareHostIdentity,
                enabled = enabled,
            )
        }
        state.publicKey?.let { publicKey ->
            KitNotice(stringResource(Res.string.hostSecPublicHelp))
            KitCodeBlock(
                publicKey,
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.hostSecCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = {
                    clipVersion++
                    clipboard = publicKey
                },
            )
        }
        state.preparation.forEach { step ->
            KitText(
                stringResource(
                    when (step.id) {
                        "tailnet" -> Res.string.hostSecStepTailnet
                        "account" -> Res.string.hostSecStepAccount
                        "sshNetwork" -> Res.string.hostSecStepNetwork
                        "linger" -> Res.string.hostSecStepLinger
                        "sshPolicy" -> Res.string.hostSecStepPolicy
                        else -> Res.string.hostSecStepIdentity
                    }
                )
            )
            KitCodeBlock(
                step.commands,
                kind = KitCodeKind.Command,
                wrap = true,
                copyLabel = stringResource(Res.string.hostSecCopy),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = {
                    clipVersion++
                    clipboard = step.commands
                },
            )
        }
        if (state.selected != null) {
            KitButton(
                stringResource(Res.string.hostSecResume),
                actions::resumeHost,
                enabled = enabled,
            )
            KitText(
                stringResource(
                    if (state.phase == ByoHostPhase.Ready) Res.string.hostSecVerified
                    else Res.string.hostSecNotConnected
                )
            )
            KitButton(
                stringResource(Res.string.hostSecRevoke),
                { confirmRemove = true },
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.hostSecForget),
                { confirmRemove = false },
                enabled = enabled,
            )
        }
    }
    KitConfirm(
        confirmRemove?.let { revoke ->
            KitConfirmSpec(
                title =
                    stringResource(
                        if (revoke) Res.string.hostSecRevoke else Res.string.hostSecForget
                    ),
                confirmLabel = stringResource(Res.string.hostSecRemoveConfirm),
                kind = KitConfirmKind.Destructive,
                body =
                    stringResource(
                        if (revoke) Res.string.hostSecRevokeHelp else Res.string.hostSecForgetHelp
                    ),
            )
        },
        {
            confirmRemove?.let(actions::removeHost)
            confirmRemove = null
        },
        { confirmRemove = null },
    )
}
