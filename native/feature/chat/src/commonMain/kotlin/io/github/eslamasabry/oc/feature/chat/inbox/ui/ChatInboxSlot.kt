package io.github.eslamasabry.oc.feature.chat.inbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxActions
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxRow
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Server inbox and durable device sends share the existing composer slot, with explicit safe
 * actions.
 */
@Composable
public fun ChatInboxSlot(
    state: ChatInboxUi,
    actions: ChatInboxActions,
    restore: (String) -> Unit,
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.restore) {
        state.restore?.let {
            restore(it.text)
            actions.restoreDone(it.id)
        }
    }
    val title = stringResource(Res.string.nativePendingTitle)
    // N-48: an action waiting for another to finish says why (a disabled button always does, B-KIT-011).
    val wait = stringResource(Res.string.serversWorking)
    // N-48: the row and the answer being confirmed (true = Send again, false = Discard).
    var asking by remember { mutableStateOf<Pair<ChatInboxRow, Boolean>?>(null) }
    KitSheetHost {
        if (open)
            KitSheet(visible = true, title = title, onDismissRequest = onDismiss) {
                Column(modifier.padding(16.dp)) {
                    if (state.failed)
                        KitNotice(message = stringResource(Res.string.nativePendingFailed))
                    if (state.rows.isEmpty())
                        KitNotice(message = stringResource(Res.string.nativePendingEmpty))
                    state.rows.forEach { row ->
                        val heading =
                            stringResource(
                                if (row.local && row.dispatched) Res.string.nativePendingUnconfirmed
                                else if (row.local) Res.string.nativePendingOffline
                                else if (row.delivery == InboxDelivery.Steer)
                                    Res.string.nativePendingJoining
                                else Res.string.nativePendingAdmitted
                            )
                        val buttons = buildList {
                            if (!row.local && !row.dispatched)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingEdit),
                                        { actions.cancel(row) },
                                        !state.busy && !row.sending,
                                        wait,
                                    )
                                )
                            if (!row.local && state.canSteer && row.delivery != InboxDelivery.Steer)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingSteer),
                                        { actions.steer(row) },
                                        !state.busy,
                                        wait,
                                    )
                                )
                            if (row.local && !row.dispatched)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingEdit),
                                        { actions.editLocal(row) },
                                        !state.busy && !row.sending,
                                        wait,
                                    )
                                )
                            // N-48: a send that may already have arrived is never a dead end: Send again (warned
                            // it can repeat), Discard, or Check again.
                            if (row.local && row.dispatched) {
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.queuedResendConfirm),
                                        { asking = row to true },
                                        !state.busy && !row.sending,
                                        wait,
                                    )
                                )
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingDiscard),
                                        { asking = row to false },
                                        !state.busy && !row.sending,
                                        wait,
                                    )
                                )
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingCheck),
                                        { actions.check(row) },
                                        !state.busy && !row.sending,
                                        wait,
                                    )
                                )
                            }
                        }
                        KitNotice(message = row.text, title = heading, actions = buttons)
                    }
                }
            }
    }
    val ask = asking
    KitConfirm(
        spec =
            ask?.let { (_, again) ->
                if (again)
                    KitConfirmSpec(
                        title = stringResource(Res.string.queuedResendTitle),
                        confirmLabel = stringResource(Res.string.queuedResendConfirm),
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.queuedResendMessage),
                        cancelLabel = stringResource(Res.string.queuedKeepForReview),
                    )
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.nativePendingDiscardTitle),
                        confirmLabel = stringResource(Res.string.nativePendingDiscard),
                        kind = KitConfirmKind.Discard,
                        body = stringResource(Res.string.queuedDiscardUnconfirmedMessage),
                        cancelLabel = stringResource(Res.string.queuedKeepForReview),
                    )
            },
        onConfirm = {
            val (row, again) = ask ?: return@KitConfirm
            asking = null
            if (again) actions.sendAgain(row) else actions.discard(row)
        },
        onCancel = { asking = null },
    )
}
