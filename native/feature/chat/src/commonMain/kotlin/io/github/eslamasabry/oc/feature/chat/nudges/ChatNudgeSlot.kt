package io.github.eslamasabry.oc.feature.chat.nudges

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.settings.teaching.ActiveNudge
import io.github.eslamasabry.oc.engine.settings.teaching.NudgeId
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitOfferNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.approvalsUiMenu
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleFetch
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleMode
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleSearch
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleSkill
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleTask
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleWebSearch
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactSession
import io.github.eslamasabry.oc.ui.words.res.demoReviewChanges
import io.github.eslamasabry.oc.ui.words.res.e7GlossaryGotIt
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction1
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction2
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction3
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction4
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction5
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction6
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction7
import io.github.eslamasabry.oc.ui.words.res.nudgeApprovals
import io.github.eslamasabry.oc.ui.words.res.nudgeCompact
import io.github.eslamasabry.oc.ui.words.res.nudgeDismiss
import io.github.eslamasabry.oc.ui.words.res.nudgeLeave
import io.github.eslamasabry.oc.ui.words.res.nudgeReviewChanges
import io.github.eslamasabry.oc.ui.words.res.nudgeReviewChangesCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the nudge card's action opens; null hides the action (the card keeps Hide tip). */
public class ChatNudgeActions(
    public val approvals: (() -> Unit)?,
    public val review: () -> Unit,
    public val compact: () -> Unit,
    public val done: (NudgeId) -> Unit,
)

/**
 * S28 story 14 (Flutter `_nudgeSlot`, nudge_slot.dart:167-212): the one nudge card above the composer, with the act
 * it suggests and Hide tip. Taking the act also retires the card for good.
 */
@Composable
public fun ChatNudgeSlot(
    nudge: ActiveNudge?,
    filesChanged: Int,
    actions: ChatNudgeActions,
    modifier: Modifier = Modifier,
) {
    nudge ?: return
    val (message, label, act) =
        when (nudge.id) {
            NudgeId.Approvals ->
                Triple(
                    stringResource(Res.string.nudgeApprovals, permissionWords(nudge.detail)),
                    stringResource(Res.string.approvalsUiMenu),
                    actions.approvals,
                )
            NudgeId.ReviewChanges ->
                Triple(
                    if (filesChanged > 0)
                        pluralStringResource(Res.plurals.nudgeReviewChangesCount, filesChanged, filesChanged)
                    else stringResource(Res.string.nudgeReviewChanges),
                    stringResource(Res.string.demoReviewChanges),
                    actions.review,
                )
            NudgeId.Compact ->
                Triple(
                    stringResource(Res.string.nudgeCompact, nudge.detail),
                    stringResource(Res.string.chatUiCompactSession),
                    actions.compact,
                )
            // Work owns the pin tip; a conversation never shows it. Leaving: only the acknowledgement.
            NudgeId.LeaveAndBeTold,
            NudgeId.PinConversations -> Triple(stringResource(Res.string.nudgeLeave), null, null)
        }
    val gotIt = stringResource(Res.string.e7GlossaryGotIt)
    val dismiss = KitLineAction(stringResource(Res.string.nudgeDismiss), { actions.done(nudge.id) }, testTag = "chat-nudge-dismiss")
    KitOfferNotice(
        message = message,
        action =
            if (act != null && label != null)
                KitLineAction(label, {
                    actions.done(nudge.id)
                    act()
                }, testTag = "chat-nudge-action")
            else KitLineAction(gotIt, { actions.done(nudge.id) }, testTag = "chat-nudge-action"),
        dismiss = dismiss,
        modifier = modifier.testTag("chat-nudge-${nudge.id.wire}"),
    )
}

/** Flutter `permissionRequestTitle`: the kind of ask in words, never its raw id. */
@Composable
private fun permissionWords(permission: String): String =
    when (permission) {
        "bash", "shell" -> stringResource(Res.string.e7PermissionAction1)
        "edit" -> stringResource(Res.string.e7PermissionAction2)
        "read" -> stringResource(Res.string.e7PermissionAction3)
        "external_directory" -> stringResource(Res.string.e7PermissionAction4)
        "doom_loop" -> stringResource(Res.string.e7PermissionAction5)
        "webfetch" -> stringResource(Res.string.chatRequestTitleFetch)
        "websearch" -> stringResource(Res.string.chatRequestTitleWebSearch)
        "grep", "glob" -> stringResource(Res.string.chatRequestTitleSearch)
        "task" -> stringResource(Res.string.chatRequestTitleTask)
        "skill" -> stringResource(Res.string.chatRequestTitleSkill)
        "mode" -> stringResource(Res.string.chatRequestTitleMode)
        else ->
            if (permission.isBlank()) stringResource(Res.string.e7PermissionAction6)
            else stringResource(Res.string.e7PermissionAction7, permission.replace('_', ' '))
    }
