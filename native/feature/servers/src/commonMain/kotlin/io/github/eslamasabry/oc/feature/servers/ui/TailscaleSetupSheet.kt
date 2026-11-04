package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.TailscalePresence
import io.github.eslamasabry.oc.feature.servers.presentation.TailscaleSetup
import io.github.eslamasabry.oc.feature.servers.presentation.reviewedTailscaleAddress
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A visible way into the private-network handoff, without guessing whether a VPN is connected. */
@Composable
internal fun TailscaleSetupSheet(
    visible: Boolean,
    source: TailscaleSetup?,
    onDismiss: () -> Unit,
    onReviewed: (String) -> Unit,
) {
    var presence by remember { mutableStateOf(TailscalePresence.Unavailable) }
    var openFailed by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    // Kept through app handoff but never saved as state: a pasted URL could carry a credential.
    val address = remember { TextFieldState() }
    LaunchedEffect(visible) {
        if (visible) presence = source?.check() ?: TailscalePresence.Unsupported
    }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tailscaleTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(stringResource(Res.string.tailscaleIntro))
            KitNotice(
                stringResource(
                    when (presence) {
                        TailscalePresence.Installed -> Res.string.tailscaleInstalled
                        TailscalePresence.Missing -> Res.string.tailscaleMissing
                        TailscalePresence.Unavailable -> Res.string.tailscaleUnknown
                        TailscalePresence.Unsupported -> Res.string.tailscaleUnsupported
                    }
                )
            )
            if (presence == TailscalePresence.Installed)
                KitButton(
                    stringResource(Res.string.tailscaleOpen),
                    { openFailed = source?.open() != true },
                )
            if (openFailed) KitNotice(stringResource(Res.string.tailscaleSetupOpenFailed))
            if (presence != TailscalePresence.Unsupported)
                KitButton(
                    stringResource(Res.string.tailscaleCheckAgain),
                    {
                        presence = source?.check() ?: TailscalePresence.Unavailable
                        openFailed = false
                    },
                )
            KitText(stringResource(Res.string.tailscaleVpnHandoff))
            KitField(
                address,
                stringResource(Res.string.tailscaleAddressLabel),
                helper = stringResource(Res.string.tailscaleSetupAddressHelper),
            )
            if (invalid) KitNotice(stringResource(Res.string.tailscaleAddressError))
            KitText(stringResource(Res.string.tailscaleAddressDetail))
            KitButton(
                stringResource(Res.string.tailscaleContinue),
                {
                    val reviewed = reviewedTailscaleAddress(address.text.toString())
                    invalid = reviewed == null
                    if (reviewed != null) {
                        onReviewed(reviewed)
                        onDismiss()
                    }
                },
            )
        }
    }
}
