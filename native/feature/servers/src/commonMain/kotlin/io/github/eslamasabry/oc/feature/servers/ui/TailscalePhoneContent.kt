package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The same phone checklist in the editor and its address-review helper. It never claims VPN
 * connectivity.
 */
@Composable
internal fun TailscalePhoneContent(
    appState: KitStepState,
    appSupporting: String,
    canCheck: Boolean,
    onCheck: () -> Unit,
    canOpen: Boolean,
    onOpen: () -> Unit,
    openFailed: Boolean,
    getApp: (() -> Unit)? = null,
) {
    KitRowGroup(label = stringResource(Res.string.tailscaleAppStep)) {
        KitStatusRow(
            stringResource(Res.string.tailscaleSetupAppTitle),
            appState,
            if (appState == KitStepState.Waiting)
                stringResource(Res.string.tailscaleSetupToDo, appSupporting)
            else appSupporting,
            action =
                getApp?.let {
                    KitAction(
                        stringResource(Res.string.tailscaleSetupGetApp),
                        it,
                        icon = KitIcons.download,
                    )
                },
        )
        KitStatusRow(
            stringResource(Res.string.tailscaleSetupVpnTitle),
            if (openFailed) KitStepState.Failed else KitStepState.Waiting,
            if (openFailed) stringResource(Res.string.tailscaleSetupOpenFailed)
            else
                stringResource(
                    Res.string.tailscaleSetupToDo,
                    stringResource(Res.string.tailscaleSetupVpnSupporting),
                ),
            action =
                if (canOpen)
                    KitAction(
                        stringResource(Res.string.tailscaleOpen),
                        onOpen,
                        icon = KitIcons.externalLink,
                    )
                else null,
        )
        if (canCheck)
            KitRow(
                stringResource(Res.string.tailscaleCheckAgain),
                leading = KitRowLeading.Glyph(KitIcons.retry),
                onClick = onCheck,
            )
    }
}

@Composable
internal fun TailscaleHelpContent() {
    var open by remember { mutableStateOf(false) }
    KitExpandRow(stringResource(Res.string.tailscaleHelp), open, onExpandedChange = { open = it }) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitText(stringResource(Res.string.tailscaleVpnHandoff))
            KitText(stringResource(Res.string.tailscaleAddressDetail))
            KitText(stringResource(Res.string.tailscaleSetupNoDeviceList))
            KitText(stringResource(Res.string.tailscaleReviewDetail))
            KitText(stringResource(Res.string.tailscaleServeHelp))
            KitText(stringResource(Res.string.tailscaleRecovery))
        }
    }
}
