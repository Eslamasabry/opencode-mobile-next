package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsRow
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsMode
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ModelDefaultsScreen(state: ModelDefaultsUi, actions: ModelDefaultsActions) {
    val title =
        if (state.thisPhone) stringResource(Res.string.modelDefaultsPhoneTitle)
        else stringResource(Res.string.modelDefaultsNamedTitle, state.serverName)
    var picking by remember { mutableStateOf(false) }
    var choosingMode by remember { mutableStateOf(false) }
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
                val currentRow = state.current?.let { c -> state.models.firstOrNull { it.model.sameModel(c) } }
                KitRow(
                    title = stringResource(Res.string.composerPreviewModelsTitle),
                    // The server's default says so, with the model it means today when the list knows it.
                    supporting =
                        when {
                            currentRow == null -> stringResource(Res.string.composerPreviewModelDefault)
                            state.usesDefault ->
                                stringResource(Res.string.composerPreviewModelDefault) + " · " + currentRow.name
                            else -> currentRow.name
                        },
                    supportingMaxLines = 2,
                    enabled = !state.saving && !state.loading,
                    disabledReason = busyReason(state),
                    onClick = { picking = true },
                    modifier = Modifier.testTag("model-defaults-open-picker"),
                )
                // Only when the server offers modes to choose from: no dead row otherwise.
                if (state.modes.isNotEmpty())
                    KitRow(
                        title = stringResource(Res.string.upgradeMode),
                        supporting =
                            if (state.mode.isEmpty()) stringResource(Res.string.e7ModelUiServerDefault)
                            else modeName(state.mode),
                        supportingMaxLines = 2,
                        enabled = !state.saving && !state.loading,
                        disabledReason = busyReason(state),
                        onClick = { choosingMode = true },
                        modifier = Modifier.testTag("model-defaults-open-mode"),
                    )
                if (currentRow != null && currentRow.variants.isNotEmpty())
                    KitRow(
                        title = stringResource(Res.string.composerFullThinking),
                        supporting =
                            if (state.variant.isEmpty()) stringResource(Res.string.composerFullThinkingDefault)
                            else effortWords(state.variant),
                        enabled = !state.saving && !state.loading,
                        disabledReason = busyReason(state),
                        onClick = { picking = true },
                    )
            }
        }
    }
    KitSheet(
        visible = choosingMode && state.modes.isNotEmpty(),
        onDismissRequest = { choosingMode = false },
        title = stringResource(Res.string.upgradeMode),
    ) {
        Column(Modifier.fillMaxWidth().testTag("model-defaults-modes")) {
            KitRow(
                title = stringResource(Res.string.e7ModelUiServerDefault),
                selected = state.mode.isEmpty(),
                trailing = if (state.mode.isEmpty()) KitRowTrailing.Check else KitRowTrailing.None,
                onClick = {
                    actions.chooseMode("")
                    choosingMode = false
                },
            )
            state.modes.forEach { mode ->
                KitRow(
                    title = modeName(mode.id),
                    supporting = modeWords(mode).ifEmpty { null },
                    supportingMaxLines = 3,
                    selected = state.mode == mode.id,
                    trailing = if (state.mode == mode.id) KitRowTrailing.Check else KitRowTrailing.None,
                    onClick = {
                        actions.chooseMode(mode.id)
                        choosingMode = false
                    },
                    modifier = Modifier.testTag("model-defaults-mode-${mode.id}"),
                )
            }
        }
    }
    // The same kit model sheet the chat opens: search, favourites, recent, the server default, thinking levels.
    val options = remember(state.models) { state.models.map(::kitOption) }
    KitModelSheet(
        visible = picking,
        onDismissRequest = { picking = false },
        options = options,
        // Null marks the sheet's "Server default" row, as the chat's sheet does while nothing is chosen.
        current =
            if (state.usesDefault) null
            else state.current?.let { c -> state.models.firstOrNull { it.model.sameModel(c) }?.let(::kitOption)?.key },
        onChoose = { option ->
            val chosen = option?.let { o -> state.models.firstOrNull { it.model.providerId == o.providerId && it.model.modelId == o.modelId } }
            actions.choose(chosen?.model)
            picking = false
        },
        favorites = remember(state.favorites, state.models) { state.favorites.mapNotNull { f -> state.models.firstOrNull { it.model.sameModel(f) }?.let(::kitOption) } },
        recent = remember(state.recent, state.models) { state.recent.mapNotNull { r -> state.models.firstOrNull { it.model.sameModel(r) }?.let(::kitOption) } },
        onToggleFavorite = { option ->
            state.models.firstOrNull { it.model.providerId == option.providerId && it.model.modelId == option.modelId }?.let { actions.toggleFavorite(it.model) }
        },
        offersServerDefault = true,
        // The server's list is being read (also behind the kept list): the kit's loading row or working mark.
        loading = state.loading,
        below = {
            val row = state.current?.let { c -> state.models.firstOrNull { it.model.sameModel(c) } }
            if (row != null && row.variants.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.composerFullThinking))
                KitFilterChips(Modifier.fillMaxWidth().testTag("model-defaults-thinking")) {
                    (listOf("") + row.variants).forEach { level ->
                        KitChip(
                            label = if (level.isEmpty()) stringResource(Res.string.composerFullThinkingDefault) else effortWords(level),
                            kind = KitChipKind.Action,
                            selected = level == state.variant,
                            onClick = { actions.chooseVariant(level) },
                        )
                    }
                }
            }
        },
    )
}

private fun kitOption(row: ModelDefaultsRow): KitModelOption =
    KitModelOption(row.model.providerId, row.model.modelId, row.name, providerName = row.providerName)

/** Build and Plan by name; any other mode as the server names it. */
@Composable
private fun modeName(id: String): String =
    when (id.lowercase()) {
        "build" -> stringResource(Res.string.modelPickerAgentBuildName)
        "plan" -> stringResource(Res.string.modelPickerAgentPlanName)
        else -> id
    }

/** What a mode does: the server's own words, else the built-in line for Build and Plan, else nothing. */
@Composable
private fun modeWords(mode: ModelDefaultsMode): String {
    if (mode.description.isNotBlank()) return mode.description
    return when (mode.id.lowercase()) {
        "build" -> stringResource(Res.string.modelPickerAgentBuild)
        "plan" -> stringResource(Res.string.modelPickerAgentPlan)
        else -> ""
    }
}

/** A thinking level as people read it: "Extra high" for `xhigh`; unknown ids as they are. */
@Composable
private fun effortWords(id: String): String =
    when (id.trim().lowercase()) {
        "none" -> stringResource(Res.string.modelEffortNone)
        "minimal" -> stringResource(Res.string.modelEffortMinimal)
        "low" -> stringResource(Res.string.modelEffortLow)
        "medium" -> stringResource(Res.string.modelEffortMedium)
        "high" -> stringResource(Res.string.modelEffortHigh)
        "xhigh" -> stringResource(Res.string.modelEffortExtraHigh)
        "max" -> stringResource(Res.string.modelEffortMax)
        else -> id
    }

/** Why a choice is not open yet: saving, or the kept rows are being checked against the server. */
@Composable
private fun busyReason(state: ModelDefaultsUi): String? =
    when {
        state.saving -> stringResource(Res.string.modelDefaultsSaving)
        state.loading -> stringResource(Res.string.modelDefaultsChecking)
        else -> null
    }
