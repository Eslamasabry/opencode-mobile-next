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
import io.github.eslamasabry.oc.engine.media.attach.ShareInProblem
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
import io.github.eslamasabry.oc.ui.words.res.shareInGotIt
import io.github.eslamasabry.oc.ui.words.res.shareInNoneTooLarge
import io.github.eslamasabry.oc.ui.words.res.shareInNoneUnreadable
import io.github.eslamasabry.oc.ui.words.res.shareInNoneUnsupported
import io.github.eslamasabry.oc.ui.words.res.shareInPartial
import io.github.eslamasabry.oc.ui.words.res.shareInReasonLimit
import io.github.eslamasabry.oc.ui.words.res.shareInReasonTooLarge
import io.github.eslamasabry.oc.ui.words.res.shareInReasonUnreadable
import io.github.eslamasabry.oc.ui.words.res.shareInReasonUnsupported
import io.github.eslamasabry.oc.ui.words.res.shareFailedAgainLine
import io.github.eslamasabry.oc.ui.words.res.shareFailedCopy
import io.github.eslamasabry.oc.ui.words.res.shareFailedDiscard
import io.github.eslamasabry.oc.ui.words.res.shareFailedLine
import io.github.eslamasabry.oc.ui.words.res.shareWaitingForServer
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The incoming share uses the existing window status slot and kit overlays. */
@Composable
internal fun IncomingShareHost(owner: IncomingShare, content: @Composable () -> Unit) {
    val phase by owner.phase.collectAsState()
    var more by remember { mutableStateOf(false) }
    val failed = phase == IncomingSharePhase.Failed || phase == IncomingSharePhase.FailedAgain
    val note by owner.note.collectAsState()
    val noted = phase == IncomingSharePhase.Noted
    val message =
        when (phase) {
            IncomingSharePhase.Noted -> note?.let { shareInLine(it) }.orEmpty()
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
                tone =
                    when {
                        failed -> StatusTone.Failure
                        noted -> StatusTone.Attention
                        else -> StatusTone.Progress
                    },
                action =
                    if (noted) KitLineAction(stringResource(Res.string.shareInGotIt), owner::dismissNote)
                    else if (phase != IncomingSharePhase.Opening)
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
                visible = more && visible && !noted,
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

/** Plain words for what a share-in left out: how many were added, then why. No names, no technical text. */
@Composable
private fun shareInLine(note: ShareInNote): String {
    if (note.added == 0)
        return stringResource(
            when (note.problem) {
                ShareInProblem.Unsupported -> Res.string.shareInNoneUnsupported
                ShareInProblem.Unreadable -> Res.string.shareInNoneUnreadable
                ShareInProblem.TooLarge,
                ShareInProblem.OverLimit -> Res.string.shareInNoneTooLarge
            }
        )
    val reason =
        stringResource(
            when (note.problem) {
                ShareInProblem.OverLimit -> Res.string.shareInReasonLimit
                ShareInProblem.TooLarge -> Res.string.shareInReasonTooLarge
                ShareInProblem.Unsupported -> Res.string.shareInReasonUnsupported
                ShareInProblem.Unreadable -> Res.string.shareInReasonUnreadable
            }
        )
    return pluralStringResource(Res.plurals.shareInPartial, note.added, note.added, note.total, reason)
}
