package io.github.eslamasabry.oc.feature.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.feature.files.presentation.FileActions
import io.github.eslamasabry.oc.feature.files.presentation.ShareSheetUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.qr.KitQr
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.fileShareCopyLink
import io.github.eslamasabry.oc.ui.words.res.fileShareDownloads
import io.github.eslamasabry.oc.ui.words.res.fileShareEnded
import io.github.eslamasabry.oc.ui.words.res.fileShareLinkLabel
import io.github.eslamasabry.oc.ui.words.res.fileShareNoWifi
import io.github.eslamasabry.oc.ui.words.res.fileSharePrivate
import io.github.eslamasabry.oc.ui.words.res.fileShareQrLabel
import io.github.eslamasabry.oc.ui.words.res.fileShareStop
import io.github.eslamasabry.oc.ui.words.res.fileShareTimeLeft
import io.github.eslamasabry.oc.ui.words.res.fileShareTitle
import io.github.eslamasabry.oc.ui.words.res.fileShareUnavailable
import io.github.eslamasabry.oc.ui.words.res.fileShareWarning
import io.github.eslamasabry.oc.ui.words.res.filesShareStarting
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * D05 (07 §5): Share on Wi-Fi. A QR code and the link for a device on the same Wi-Fi, who can see
 * it (anyone on this Wi-Fi with the link), downloads so far and the time left; Stop sharing is the
 * visible primary action. Closing the sheet stops the share. A refused share (no Wi-Fi, a file that
 * may hold keys or passwords) says why and offers nothing else.
 */
@Composable
internal fun FileShareSheet(
    share: ShareSheetUi?,
    name: String,
    actions: FileActions,
    sizeBytes: Long?,
) {
    var copying by remember(share is ShareSheetUi.Running) { mutableStateOf(false) }
    KitClipboardEffect(if (copying) 1L else null, (share as? ShareSheetUi.Running)?.url) {
        copying = false
    }
    val closeLabel = stringResource(Res.string.kitTopBarClose)
    KitSheet(
        visible = share != null,
        onDismissRequest = actions::closeShare,
        title = stringResource(Res.string.fileShareTitle),
        closeLabel = closeLabel,
        primary =
            when (share) {
                is ShareSheetUi.Running ->
                    KitAction(stringResource(Res.string.fileShareStop), actions::stopSharing)
                is ShareSheetUi.Refused ->
                    if (share.failure == Failure.Media(MediaProblem.WifiSharePrivateFile)) null
                    else KitAction(stringResource(Res.string.kitTryAgain), actions::shareOnWifi)
                else -> null
            },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            when (share) {
                null -> Unit
                ShareSheetUi.Starting -> {
                    KitText(
                        stringResource(Res.string.filesShareStarting),
                        tone = TextTone.Secondary,
                    )
                    KitSkeletonRows(count = 1)
                }
                is ShareSheetUi.Refused -> KitNotice(refusedWords(share.failure))
                ShareSheetUi.Ended -> KitNotice(stringResource(Res.string.fileShareEnded))
                is ShareSheetUi.Running -> {
                    KitText(
                        listOfNotNull(share.name, ViewerText.sizeInWords(sizeBytes))
                            .joinToString(" · "),
                        role = TypeRole.RowTitle,
                    )
                    KitQr(
                        share.url,
                        stringResource(Res.string.fileShareQrLabel, name),
                        Modifier.widthIn(max = 280.dp).align(Alignment.CenterHorizontally),
                    )
                    KitText(
                        stringResource(Res.string.fileShareLinkLabel),
                        role = TypeRole.Label,
                        tone = TextTone.Secondary,
                    )
                    KitText(share.url, role = TypeRole.Mono)
                    KitButton(
                        stringResource(Res.string.fileShareCopyLink),
                        { copying = true },
                        role = KitButtonRole.Tertiary,
                    )
                    KitText(stringResource(Res.string.fileShareWarning), tone = TextTone.Secondary)
                    KitText(
                        pluralStringResource(
                            Res.plurals.fileShareDownloads,
                            share.downloads,
                            share.downloads,
                        )
                    )
                    share.remainingMs?.let { left ->
                        KitText(
                            stringResource(Res.string.fileShareTimeLeft, clock(left)),
                            tone = TextTone.Secondary,
                        )
                    }
                }
            }
        }
    }
}

/** m:ss, rounded up so the last second still reads 0:01. */
internal fun clock(ms: Long): String {
    val seconds = ((ms + 999) / 1000).coerceAtLeast(0)
    val s = (seconds % 60).toString().padStart(2, '0')
    return "${seconds / 60}:$s"
}

@Composable
private fun refusedWords(failure: Failure): String =
    stringResource(
        when ((failure as? Failure.Media)?.problem) {
            MediaProblem.WifiShareNoWifi -> Res.string.fileShareNoWifi
            MediaProblem.WifiSharePrivateFile -> Res.string.fileSharePrivate
            else -> Res.string.fileShareUnavailable
        }
    )
