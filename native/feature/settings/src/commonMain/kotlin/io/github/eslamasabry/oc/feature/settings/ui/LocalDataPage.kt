package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Local content stays until the person confirms a specific discard. Uncertain sends are retained.
 */
@Composable
internal fun ColumnScope.LocalDataBody(ui: LocalDataUi, actions: SettingsActions) {
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.keptPromptsTitle),
            onClick = { actions.onOutside(SettingsOutside.KeptPrompts) },
        )
    }
    if (ui.failed) KitNotice(stringResource(Res.string.settings40_discard_failed))
    ui.result?.let { result ->
        KitNotice(
            stringResource(
                when {
                    !result.payloadsCollected -> Res.string.settings40_discard_cleanup
                    result.kept > 0 -> Res.string.settings40_discard_kept
                    else -> Res.string.settings40_discard_done
                },
                result.removed,
                result.kept,
            )
        )
    }
    val facts = ui.facts
    if (facts == null) {
        KitGroupNote(stringResource(Res.string.settings40_storage_reading))
        return
    }
    if (facts.servers.isNotEmpty())
        KitRowGroup(label = stringResource(Res.string.privacySharedSection)) {
            KitRow(
                title = stringResource(Res.string.shareSessionViewsTitle),
                supporting =
                    stringResource(
                        Res.string.settingsV3SharingCount,
                        facts.sharing,
                        facts.servers.size,
                    ),
                onClick = {
                    if (facts.servers.size == 1)
                        actions.onSavedServer(facts.servers.single(), SettingsOutside.ReadState)
                    else actions.onOpen(SettingsPage.ByServer(SettingsOutside.ReadState))
                },
            )
        }
    KitRowGroup(label = stringResource(Res.string.settings40_local_work)) {
        KitRow(
            title = stringResource(Res.string.settings40_queue_title),
            supporting =
                if (facts.queueReadable)
                    stringResource(
                        Res.string.settings40_storage_size,
                        facts.queued,
                        localBytes(facts.queueBytes),
                    )
                else stringResource(Res.string.settings40_storage_unknown),
            supportingMaxLines = 3,
        )
        KitRow(
            title = stringResource(Res.string.settings40_discard_queue),
            supporting = stringResource(Res.string.settings40_queue_keeps),
            supportingMaxLines = 3,
            enabled = !ui.busy && facts.queueReadable && facts.queued > 0,
            disabledReason =
                stringResource(
                    when {
                        ui.busy -> Res.string.settings40_discard_working
                        !facts.queueReadable -> Res.string.settings40_storage_unknown
                        else -> Res.string.settings40_queue_empty
                    }
                ),
            onClick = { actions.onAskDiscard(LocalDataKind.Queue) },
        )
        KitRow(
            title = stringResource(Res.string.settings40_drafts_title),
            supporting =
                if (facts.draftsReadable)
                    stringResource(
                        Res.string.settings40_storage_size,
                        facts.drafts,
                        localBytes(facts.draftBytes),
                    )
                else stringResource(Res.string.settings40_storage_unknown),
            supportingMaxLines = 3,
        )
        KitRow(
            title = stringResource(Res.string.settings40_discard_drafts),
            enabled =
                !ui.busy && facts.draftsReadable && (facts.drafts > 0 || facts.draftBytes > 0),
            disabledReason =
                stringResource(
                    when {
                        ui.busy -> Res.string.settings40_discard_working
                        !facts.draftsReadable -> Res.string.settings40_storage_unknown
                        else -> Res.string.settings40_drafts_empty
                    }
                ),
            onClick = { actions.onAskDiscard(LocalDataKind.Drafts) },
        )
    }
    KitConfirm(
        spec =
            ui.confirm?.let { kind ->
                KitConfirmSpec(
                    title =
                        stringResource(
                            if (kind == LocalDataKind.Queue) Res.string.settings40_queue_question
                            else Res.string.settings40_drafts_question
                        ),
                    confirmLabel =
                        stringResource(
                            if (kind == LocalDataKind.Queue) Res.string.settings40_discard_queue
                            else Res.string.settings40_discard_drafts
                        ),
                    body =
                        stringResource(
                            if (kind == LocalDataKind.Queue) Res.string.settings40_queue_keeps
                            else Res.string.settings40_drafts_body
                        ),
                    kind = KitConfirmKind.Destructive,
                )
            },
        onConfirm = actions::onConfirmDiscard,
        onCancel = actions::onCancelDiscard,
    )
}

private fun localBytes(bytes: Long): String {
    val unit = if (bytes >= 1024 * 1024) 1024L * 1024 else if (bytes >= 1024) 1024L else 1L
    return if (unit == 1L) "$bytes B"
    else "${bytes / unit}.${bytes % unit * 10 / unit} ${if (unit == 1024L) "KB" else "MB"}"
}
