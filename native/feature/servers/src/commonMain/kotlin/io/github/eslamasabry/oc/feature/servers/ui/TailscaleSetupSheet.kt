package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.TailscalePresence
import io.github.eslamasabry.oc.feature.servers.presentation.TailscaleSetup
import io.github.eslamasabry.oc.feature.servers.presentation.reviewedTailscaleAddress
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
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
    var opened by remember { mutableStateOf(false) }
    var returned by remember { mutableStateOf(false) }
    // A URL may contain credentials. It is transient and cleared when the sheet closes.
    val address = remember { TextFieldState() }
    fun check() { presence = source?.check() ?: TailscalePresence.Unsupported }
    fun continueWithAddress() {
        val reviewed = reviewedTailscaleAddress(address.text.toString())
        invalid = reviewed == null
        if (reviewed != null) {
            onReviewed(reviewed)
            address.edit { replace(0, length, "") }
            onDismiss()
        }
    }
    LaunchedEffect(visible) {
        if (visible) check()
        else {
            address.edit { replace(0, length, "") }
            invalid = false
            returned = false
            opened = false
            openFailed = false
        }
    }
    LaunchedEffect(address) {
        snapshotFlow { address.text.toString() }.collect { invalid = false }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (visible) {
            check()
            if (opened) returned = true
        }
    }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tailscaleTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
        primary = KitAction(stringResource(Res.string.tailscaleContinue), ::continueWithAddress,
            icon = KitIcons.forward,
            enabled = KitEnabled.of(address.text.isNotBlank(), stringResource(Res.string.tailscaleSetupContinueReason))),
    ) {
        Column(Modifier.fillMaxWidth()) {
            KitText(stringResource(Res.string.tailscaleIntro), tone = TextTone.Secondary)
            TailscalePhoneContent(
                appState = when (presence) {
                    TailscalePresence.Installed -> KitStepState.Done
                    TailscalePresence.Unavailable -> KitStepState.Failed
                    else -> KitStepState.Waiting
                },
                appSupporting = stringResource(when (presence) {
                    TailscalePresence.Installed -> Res.string.tailscaleInstalled
                    TailscalePresence.Missing -> Res.string.tailscaleMissing
                    TailscalePresence.Unavailable -> Res.string.tailscaleUnknown
                    TailscalePresence.Unsupported -> Res.string.tailscaleUnsupported
                }),
                canCheck = presence != TailscalePresence.Unsupported, onCheck = ::check,
                canOpen = presence == TailscalePresence.Installed,
                onOpen = { opened = true; openFailed = source?.open() != true; if (openFailed) check() },
                openFailed = openFailed,
            )
            if (returned) KitNotice(stringResource(Res.string.tailscaleReturned), icon = KitIcons.info)
            KitSectionLabel(stringResource(Res.string.tailscaleAddressStep), margin = 0.dp)
            KitField(address, stringResource(Res.string.tailscaleAddressLabel), kind = KitFieldKind.Url,
                maxLength = 2048, // F: lib/ui/screens/tailscale_setup_screen.dart:121
                placeholder = "https://computer.tailnet-name.ts.net", // F: lib/ui/screens/tailscale_setup_screen.dart:118
                helper = stringResource(Res.string.tailscaleSetupAddressHelper),
                error = if (invalid) stringResource(Res.string.tailscaleAddressError) else null,
                onSubmit = ::continueWithAddress)
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            TailscaleHelpContent()
        }
    }
}
