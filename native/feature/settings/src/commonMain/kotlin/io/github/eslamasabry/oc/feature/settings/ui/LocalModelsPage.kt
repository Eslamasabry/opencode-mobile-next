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
import io.github.eslamasabry.oc.ui.words.res.manageSpaceTitle
import io.github.eslamasabry.oc.ui.words.res.migrationStorageAppDetail
import io.github.eslamasabry.oc.ui.words.res.migrationStorageAssistant
import io.github.eslamasabry.oc.ui.words.res.migrationStorageDelete
import io.github.eslamasabry.oc.ui.words.res.migrationStorageDeleteBody
import io.github.eslamasabry.oc.ui.words.res.migrationStorageDeleteTitle
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
        // The page is the app's storage: its own measured page is the first row, and the assistant
        // model is one more row (with its Delete) only while one is stored.
        else -> {
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.manageSpaceTitle),
                    supporting = stringResource(Res.string.migrationStorageAppDetail),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.onOutside(SettingsOutside.AppStorage) },
                )
                if (state.present)
                    KitRow(
                        title = stringResource(Res.string.migrationStorageAssistant),
                        trailing = KitRowTrailing.Value(state.size, chevron = false),
                    )
            }
            if (state.present)
                KitActionBlock(
                    primary = KitAction(stringResource(Res.string.migrationStorageDelete), actions::onAskDeleteLocalModel),
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
