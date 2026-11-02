package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.e7SetupCameraDisabled
import io.github.eslamasabry.oc.ui.words.res.e7SetupCameraFailed
import io.github.eslamasabry.oc.ui.words.res.e7SetupCameraFailedDetail
import io.github.eslamasabry.oc.ui.words.res.e7SetupCameraNeeded
import io.github.eslamasabry.oc.ui.words.res.e7SetupCameraPrivacy
import io.github.eslamasabry.oc.ui.words.res.e7SetupCameraSettingsDetail
import io.github.eslamasabry.oc.ui.words.res.e7SetupCloseScanner
import io.github.eslamasabry.oc.ui.words.res.e7SetupEmptyPairClipboard
import io.github.eslamasabry.oc.ui.words.res.e7SetupNoCamera
import io.github.eslamasabry.oc.ui.words.res.e7SetupNoCameraDetail
import io.github.eslamasabry.oc.ui.words.res.e7SetupOpenAppSettings
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressLong
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressMissing
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairInvalid
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairLong
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairNoUrls
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairPasswordMissing
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairPasswordType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairShape
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairTooMany
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairUrlsType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairUsernameType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPastePairing
import io.github.eslamasabry.oc.ui.words.res.previewQrPairingCodeLabel
import io.github.eslamasabry.oc.ui.words.res.e7SetupScanInstruction
import io.github.eslamasabry.oc.ui.words.res.e7SetupScanPairing
import io.github.eslamasabry.oc.ui.words.res.pairingScannerAllowCamera
import io.github.eslamasabry.oc.ui.words.res.pairingScannerStarting
import io.github.eslamasabry.oc.ui.words.res.previewQrScanRejected
import org.jetbrains.compose.resources.stringResource

/** The plain sentence for each pairing problem; no sentence ever echoes the input (B-PROTO-065). */
@Composable
public fun pairingProblemText(problem: PairingProblem): String =
    stringResource(
        when (problem) {
            PairingProblem.Empty -> Res.string.e7SetupEmptyPairClipboard
            PairingProblem.TooLong -> Res.string.e7SetupPairLong
            PairingProblem.NotJson -> Res.string.e7SetupPairInvalid
            PairingProblem.Shape -> Res.string.e7SetupPairShape
            PairingProblem.NoUrls -> Res.string.e7SetupPairNoUrls
            PairingProblem.UrlsType -> Res.string.e7SetupPairUrlsType
            PairingProblem.TooMany -> Res.string.e7SetupPairTooMany
            PairingProblem.AddressType -> Res.string.e7SetupPairAddressType
            PairingProblem.AddressLong -> Res.string.e7SetupPairAddressLong
            PairingProblem.AddressMissing -> Res.string.e7SetupPairAddressMissing
            PairingProblem.UsernameType -> Res.string.e7SetupPairUsernameType
            PairingProblem.PasswordMissing -> Res.string.e7SetupPairPasswordMissing
            PairingProblem.PasswordType -> Res.string.e7SetupPairPasswordType
        }
    )

/**
 * Scan pairing code (plan 07 E03, Flutter `PairingScannerScreen`): the camera fills the page with the instruction and
 * the privacy line under it, and "Paste pairing code" is always there — a missing, refused or broken camera never
 * blocks pairing (B-ANDR-049). Each camera stage says what happened and offers its one honest act.
 */
@Composable
public fun PairingScannerScreen(
    state: PairingScannerUi,
    actions: PairingScannerActions,
    slot: QrScannerSlot,
    accepts: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val handle = remember { QrScannerHandle() }
    // "Paste instead" is the kit's secret field and its Paste button: the code carries a password.
    val code = remember { TextFieldState() }
    LaunchedEffect(code) {
        snapshotFlow { code.text.toString() }
            .collect { text ->
                if (PairingText.looksLikePayload(text) || ShareLink.origin(text) != null) {
                    code.edit { replace(0, length, "") }
                    actions.pasted(text)
                }
            }
    }
    val noCamera = !slot.supported || handle.stage == ScanStage.NoCamera
    KitScreen(
        modifier = modifier.fillMaxSize(),
        page = true,
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.e7SetupScanPairing),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.e7SetupCloseScanner),
                onExit = actions::close,
            )
        },
        loading = !noCamera && handle.stage == ScanStage.Starting,
        loadingLabel = stringResource(Res.string.pairingScannerStarting),
        bottom = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                if (!noCamera) {
                    KitText(stringResource(Res.string.e7SetupScanInstruction))
                    KitText(stringResource(Res.string.e7SetupCameraPrivacy), role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
                if (state.rejected && state.problem == null) KitNotice(stringResource(Res.string.previewQrScanRejected))
                state.problem?.let { KitErrorNotice(pairingProblemText(it)) }
                KitField(
                    code,
                    stringResource(Res.string.previewQrPairingCodeLabel),
                    modifier = Modifier.testTag("pairing-paste"),
                    kind = KitFieldKind.Secret,
                    helper = stringResource(Res.string.e7SetupPastePairing),
                    onSubmit = {
                        val text = code.text.toString()
                        code.edit { replace(0, length, "") }
                        actions.pasted(text)
                    },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (noCamera) {
                KitStateView(
                    kind = KitStateKind.Missing,
                    title = stringResource(Res.string.e7SetupNoCamera),
                    body = stringResource(Res.string.e7SetupNoCameraDetail),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // The screen's tag sits on its own box so the slot's own semantics (and tags) stay intact.
                Box(Modifier.fillMaxSize().testTag("pairing-viewfinder")) {
                    slot.Viewfinder(handle, accepts, actions::scanned, actions::rejected, Modifier.fillMaxSize())
                }
                when (handle.stage) {
                    ScanStage.Denied ->
                        KitStateView(
                            kind = KitStateKind.Missing,
                            title = stringResource(Res.string.e7SetupCameraNeeded),
                            body = stringResource(Res.string.e7SetupCameraPrivacy),
                            primary = KitAction(stringResource(Res.string.pairingScannerAllowCamera), { handle.allowRequests++ }),
                            prerequisite = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    ScanStage.Blocked ->
                        KitStateView(
                            kind = KitStateKind.Missing,
                            title = stringResource(Res.string.e7SetupCameraDisabled),
                            body = stringResource(Res.string.e7SetupCameraSettingsDetail),
                            primary = KitAction(stringResource(Res.string.e7SetupOpenAppSettings), { handle.settingsRequests++ }),
                            prerequisite = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    ScanStage.Failed ->
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.e7SetupCameraFailed),
                            body = stringResource(Res.string.e7SetupCameraFailedDetail),
                            primary = KitAction(stringResource(Res.string.commonRetry), { handle.retries++ }),
                            modifier = Modifier.fillMaxSize(),
                        )
                    else -> Unit
                }
            }
        }
    }
}
