package io.github.eslamasabry.oc.feature.chat.inbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxActions
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
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
    KitSheetHost {
        if (open)
            KitSheet(visible = true, title = title, onDismissRequest = onDismiss) {
                Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                                    )
                                )
                            if (!row.local && state.canSteer && row.delivery != InboxDelivery.Steer)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingSteer),
                                        { actions.steer(row) },
                                        !state.busy,
                                    )
                                )
                            if (row.local && !row.dispatched)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingEdit),
                                        { actions.editLocal(row) },
                                        !state.busy && !row.sending,
                                    )
                                )
                            if (row.local && row.dispatched)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.nativePendingCheck),
                                        { actions.check(row) },
                                        !state.busy && !row.sending,
                                    )
                                )
                        }
                        KitNotice(message = row.text, title = heading, actions = buttons)
                    }
                }
            }
    }
}
