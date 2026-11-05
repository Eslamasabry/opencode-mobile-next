package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.scanner.KitScanner
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

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
 * the instruction under it. Recovery states return to the editor through "Paste it instead" (B-ANDR-049). Each camera stage says what happened and offers its one honest act.
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
    val started = remember(handle.allowRequests, handle.retries) { TimeSource.Monotonic.markNow() }
    val stage = if (slot.supported) handle.stage else ScanStage.NoCamera
    // Flutter returns to the calling editor for paste; credentials never enter the scanner's state.
    val paste = KitAction(stringResource(Res.string.e7SetupPasteInstead), actions::close, icon = KitIcons.paste)
    KitScreen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            KitTopBar(stringResource(Res.string.e7SetupScanPairing), exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.e7SetupCloseScanner), onExit = actions::close)
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (slot.supported) {
                // Keep the same slot mounted across permission/starting/scanning states: remounting would ask twice
                // or continually reset a camera that just became ready. Only the kit's visible preview changes.
                KitScanner(
                    instruction = stringResource(Res.string.e7SetupScanInstruction),
                    previewLabel = stringResource(Res.string.kitScannerPreview),
                    active = stage == ScanStage.Scanning,
                    rejected = state.problem?.let { pairingProblemText(it) }
                        ?: if (state.rejected) stringResource(Res.string.previewQrScanRejected) else null,
                ) {
                    Box(Modifier.fillMaxSize().testTag("pairing-viewfinder")) {
                        slot.Viewfinder(handle, accepts, actions::scanned, actions::rejected, Modifier.fillMaxSize())
                    }
                }
            }
            when (stage) {
                ScanStage.Starting -> KitStateView(
                    kind = KitStateKind.Loading, title = stringResource(Res.string.pairingScannerStarting),
                    icon = KitIcons.camera, progress = KitProgress.Waiting(), since = started, onSlow = listOf(paste),
                    modifier = Modifier.fillMaxSize(),
                )
                ScanStage.NoCamera -> KitStateView(
                    kind = KitStateKind.Missing, title = stringResource(Res.string.e7SetupNoCamera),
                    body = stringResource(Res.string.e7SetupNoCameraDetail), icon = KitIcons.cameraOff,
                    primary = paste, modifier = Modifier.fillMaxSize(),
                )
                ScanStage.Denied -> KitStateView(
                    kind = KitStateKind.Missing, title = stringResource(Res.string.e7SetupCameraNeeded),
                    body = stringResource(Res.string.e7SetupCameraPrivacy), icon = KitIcons.camera,
                    primary = KitAction(stringResource(Res.string.pairingScannerAllowCamera), { handle.allowRequests++ }, icon = KitIcons.camera),
                    secondary = paste, prerequisite = true, modifier = Modifier.fillMaxSize(),
                )
                ScanStage.Blocked -> KitStateView(
                    kind = KitStateKind.Missing, title = stringResource(Res.string.e7SetupCameraDisabled),
                    body = stringResource(Res.string.e7SetupCameraSettingsDetail), icon = KitIcons.cameraOff,
                    primary = KitAction(stringResource(Res.string.e7SetupOpenAppSettings), { handle.settingsRequests++ }, icon = KitIcons.settings),
                    secondary = paste, prerequisite = true, modifier = Modifier.fillMaxSize(),
                )
                ScanStage.Failed -> KitStateView(
                    kind = KitStateKind.Error, title = stringResource(Res.string.e7SetupCameraFailed),
                    body = stringResource(Res.string.e7SetupCameraFailedDetail), icon = KitIcons.error,
                    primary = KitAction(stringResource(Res.string.isolatedTaskRetryOpen), { handle.retries++ }, icon = KitIcons.retry),
                    secondary = paste, modifier = Modifier.fillMaxSize(),
                )
                ScanStage.Scanning -> Unit
            }
        }
    }
}
