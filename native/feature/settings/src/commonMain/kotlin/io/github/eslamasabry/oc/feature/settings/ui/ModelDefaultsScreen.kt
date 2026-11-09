package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.engine.agents.models.ModelReason
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsRow
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ModelDefaultsScreen(state: ModelDefaultsUi, actions: ModelDefaultsActions) {
    val title =
        if (state.thisPhone) stringResource(Res.string.modelDefaultsPhoneTitle)
        else stringResource(Res.string.modelDefaultsNamedTitle, state.serverName)
    val known = state.models.isNotEmpty() || state.recommended.isNotEmpty()
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        edges = KitScreenEdges.Bleed,
        // Cache-first: a refresh over known rows is the screen's one loading line, never a body swap.
        loading = state.loading && known,
        loadingLabel = stringResource(Res.string.modelDefaultsChecking),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (state.loading && !known) KitSkeletonRows()
            state.failure?.let {
                KitErrorNotice(
                    stringResource(Res.string.modelDefaultsFailed),
                    failure = it,
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refresh),
                )
            }
            if (known || !state.loading) {
                KitRow(
                    title = stringResource(Res.string.modelDefaultsScope),
                    supportingMaxLines = 3,
                )
                KitRow(
                    title = stringResource(Res.string.modelDefaultsServerChoice),
                    enabled = !state.saving && !state.loading,
                    disabledReason = busyReason(state),
                    onClick = { actions.choose(null) },
                )
                if (state.recommended.isNotEmpty())
                    KitRowGroup(label = stringResource(Res.string.modelDefaultsRecommended)) {
                        state.recommended.forEach { ModelRow(it, state, actions) }
                    }
                KitRowGroup(label = stringResource(Res.string.modelDefaultsAll)) {
                    state.models.forEach { ModelRow(it, state, actions) }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(row: ModelDefaultsRow, state: ModelDefaultsUi, actions: ModelDefaultsActions) {
    val reason =
        row.reason?.let {
            stringResource(
                when (it) {
                    ModelReason.InUse -> Res.string.modelDefaultsInUse
                    ModelReason.ServerDefault -> Res.string.modelDefaultsDefault
                    ModelReason.Recent -> Res.string.modelDefaultsRecent
                    ModelReason.Cheapest -> Res.string.modelDefaultsCheapest
                }
            )
        }
    val price =
        when {
            row.free -> stringResource(Res.string.modelDefaultsFree)
            row.inputPrice != null && row.outputPrice != null ->
                stringResource(
                    Res.string.modelDefaultsPrice,
                    row.inputPrice.toString(),
                    row.outputPrice.toString(),
                )
            else -> null
        }
    KitRow(
        title = row.name,
        supporting = listOfNotNull(reason, price).joinToString(" · ").ifBlank { null },
        supportingMaxLines = 2,
        trailing =
            if (row.selected)
                KitRowTrailing.Value(
                    stringResource(Res.string.modelDefaultsSelected),
                    chevron = false,
                )
            else KitRowTrailing.None,
        enabled = !state.saving && !state.loading,
        disabledReason = busyReason(state),
        onClick = { actions.choose(row.model) },
    )
}

/** Why a choice is not open yet: saving, or the kept rows are being checked against the server. */
@Composable
private fun busyReason(state: ModelDefaultsUi): String? =
    when {
        state.saving -> stringResource(Res.string.modelDefaultsSaving)
        state.loading -> stringResource(Res.string.modelDefaultsChecking)
        else -> null
    }
