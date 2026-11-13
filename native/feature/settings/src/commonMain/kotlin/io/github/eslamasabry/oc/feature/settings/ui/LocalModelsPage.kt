package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.settings.presentation.LocalModelStorageFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.filesOpenPhone
import io.github.eslamasabry.oc.ui.words.res.migrationStorageAssistant
import io.github.eslamasabry.oc.ui.words.res.migrationStorageDelete
import io.github.eslamasabry.oc.ui.words.res.migrationStorageDeleteBody
import io.github.eslamasabry.oc.ui.words.res.migrationStorageDeleteTitle
import io.github.eslamasabry.oc.ui.words.res.migrationStorageEmpty
import io.github.eslamasabry.oc.ui.words.res.localModelSaveFailed
import io.github.eslamasabry.oc.ui.words.res.migrationStorageTitle
import org.jetbrains.compose.resources.stringResource

/** The deferred assistant's retained model can be removed without enabling its runtime. */
@Composable
internal fun LocalModelsBody(state: LocalModelStorageFacts, actions: SettingsActions) {
    when {
        state.loading || state.deleting -> KitStateView(kind = KitStateKind.Loading, title = stringResource(Res.string.migrationStorageTitle))
        state.failed -> KitErrorNotice(
            title = stringResource(Res.string.localModelSaveFailed),
            message = stringResource(Res.string.localModelSaveFailed),
            retry = KitLineAction(stringResource(Res.string.commonRetry), actions::onRefreshLocalModels),
        )
        // No assistant model: say so, and lead to where the app's storage is measured (This phone ›
        // App storage) instead of a page with nothing else on it.
        !state.present ->
            KitStateView(
                kind = KitStateKind.Empty,
                title = stringResource(Res.string.migrationStorageEmpty),
                primary =
                    if (state.phoneStorage)
                        KitAction(
                            label = stringResource(Res.string.filesOpenPhone),
                            onClick = { actions.onOutside(SettingsOutside.ThisPhone) },
                        )
                    else null,
            )
        else -> {
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.migrationStorageAssistant),
                    trailing = KitRowTrailing.Value(state.size, chevron = false),
                )
            }
            KitActionBlock(
                primary = KitAction(stringResource(Res.string.migrationStorageDelete), actions::onAskDeleteLocalModel),
                // The rest of the app's storage is measured on This phone (App storage): the same door the empty page has.
                secondary =
                    if (state.phoneStorage)
                        KitAction(stringResource(Res.string.filesOpenPhone), { actions.onOutside(SettingsOutside.ThisPhone) })
                    else null,
            )
        }
    }
    KitConfirm(
        spec = if (state.confirmDelete) KitConfirmSpec(
            title = stringResource(Res.string.migrationStorageDeleteTitle),
            confirmLabel = stringResource(Res.string.migrationStorageDelete),
            kind = KitConfirmKind.Destructive,
            body = stringResource(Res.string.migrationStorageDeleteBody, state.size),
        ) else null,
        onConfirm = actions::onDeleteLocalModel,
        onCancel = actions::onCancelDeleteLocalModel,
    )
}
