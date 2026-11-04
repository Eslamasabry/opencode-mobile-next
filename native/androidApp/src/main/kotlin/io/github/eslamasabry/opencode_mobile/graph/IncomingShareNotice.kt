package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.kit.status.LocalKitStatusConditions
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitMore
import io.github.eslamasabry.oc.ui.words.res.shareDiscarded
import io.github.eslamasabry.oc.ui.words.res.shareFailedAgainLine
import io.github.eslamasabry.oc.ui.words.res.shareFailedCopy
import io.github.eslamasabry.oc.ui.words.res.shareFailedDiscard
import io.github.eslamasabry.oc.ui.words.res.shareFailedLine
import io.github.eslamasabry.oc.ui.words.res.shareWaitingForServer
import org.jetbrains.compose.resources.stringResource

/** The incoming share uses the existing window status slot and kit overlays. */
@Composable
internal fun IncomingShareHost(owner: IncomingShare, content: @Composable () -> Unit) {
    val phase by owner.phase.collectAsState()
    var more by remember { mutableStateOf(false) }
    val failed = phase == IncomingSharePhase.Failed || phase == IncomingSharePhase.FailedAgain
    val message =
        when (phase) {
            IncomingSharePhase.Failed -> stringResource(Res.string.shareFailedLine)
            IncomingSharePhase.FailedAgain -> stringResource(Res.string.shareFailedAgainLine)
            else -> stringResource(Res.string.shareWaitingForServer)
        }
    val visible = phase != IncomingSharePhase.Empty && phase != IncomingSharePhase.Discarding
    val condition =
        if (visible)
            KitStatus(
                kind = KitStatusKind.Work,
                id = "incoming-share",
                message = message,
                tone = if (failed) StatusTone.Failure else StatusTone.Progress,
                action =
                    if (phase != IncomingSharePhase.Opening)
                        KitLineAction(stringResource(Res.string.kitMore), { more = true })
                    else null,
            )
        else null
    KitStatusScope(LocalKitStatusConditions.current + listOfNotNull(condition)) {
        KitSheetHost {
            Box(Modifier.fillMaxSize()) {
                content()
                KitUndoBar(
                    offer =
                        if (phase == IncomingSharePhase.Discarding)
                            KitUndoOffer(
                                "incoming-share-discard",
                                stringResource(Res.string.shareDiscarded),
                            )
                        else null,
                    onUndo = owner::undoDiscard,
                    onTimeout = owner::commitDiscard,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            KitSheet(
                visible = more && visible,
                onDismissRequest = { more = false },
                title = message,
                primary =
                    if (failed)
                        KitAction(
                            stringResource(Res.string.commonRetry),
                            {
                                more = false
                                owner.retry()
                            },
                        )
                    else null,
                secondary = KitAction(stringResource(Res.string.shareFailedCopy), { owner.copy() }),
                tertiary =
                    listOf(
                        KitAction(
                            stringResource(Res.string.shareFailedDiscard),
                            {
                                more = false
                                owner.discard()
                            },
                            destructive = true,
                        )
                    ),
            ) {
                KitText(message)
            }
        }
    }
}
