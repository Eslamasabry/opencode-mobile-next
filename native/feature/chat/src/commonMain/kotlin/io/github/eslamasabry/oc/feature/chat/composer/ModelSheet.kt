package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelSection
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatCycleNext
import io.github.eslamasabry.oc.ui.words.res.chatCyclePrevious
import io.github.eslamasabry.oc.ui.words.res.composerFullAgent
import io.github.eslamasabry.oc.ui.words.res.composerFullModelChangeFailed
import io.github.eslamasabry.oc.ui.words.res.composerFullThinking
import io.github.eslamasabry.oc.ui.words.res.composerFullThinkingDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsEmpty
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsFailed
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsLoading
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsRetry
import io.github.eslamasabry.oc.ui.words.res.modelEffortExtraHigh
import io.github.eslamasabry.oc.ui.words.res.modelEffortHigh
import io.github.eslamasabry.oc.ui.words.res.modelEffortLow
import io.github.eslamasabry.oc.ui.words.res.modelEffortMax
import io.github.eslamasabry.oc.ui.words.res.modelEffortMedium
import io.github.eslamasabry.oc.ui.words.res.modelEffortMinimal
import io.github.eslamasabry.oc.ui.words.res.modelEffortNone
import io.github.eslamasabry.oc.ui.words.res.modelPickerAgentBuild
import io.github.eslamasabry.oc.ui.words.res.modelPickerAgentBuildName
import io.github.eslamasabry.oc.ui.words.res.modelPickerAgentPlan
import io.github.eslamasabry.oc.ui.words.res.modelPickerAgentPlanName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** "Choose a model": the conversation's model, its thinking level and its agent, from kit parts. */
@Composable
internal fun ModelSheet(
    visible: Boolean,
    targetKey: String,
    models: ComposerModels,
    actions: BasicComposerActions,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectAndClose: (ComposerModel?) -> Unit = { choice ->
        scope.launch { if (actions.selectModel(choice)) onDismiss() }
    }
    // The model list loads when the sheet opens empty; a retry after failure is the person's own
    // tap.
    LaunchedEffect(visible, targetKey) {
        if (visible && !models.loading && models.choices.isEmpty()) {
            actions.loadModels()
        }
    }
    val noList = models.choices.isEmpty()
    // N-51: the one kit sheet New conversation uses too; this one adds Recent, thinking and agent.
    KitModelSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        groups = { query -> ModelPicker.sections(models, query).map { it.kitGroup(models) } },
        usesDefault = models.usesDefault,
        onChoose = { choice ->
            if (choice == null) selectAndClose(null)
            else
                models.choices
                    .firstOrNull { it.providerId == choice.providerId && it.modelId == choice.modelId }
                    ?.let(selectAndClose)
        },
        onFavorite = { choice ->
            models.choices
                .firstOrNull { it.providerId == choice.providerId && it.modelId == choice.modelId }
                ?.let { scope.launch { actions.toggleModelFavorite(it) } }
        },
        offersDefault = ModelPicker.offersServerDefault(models),
        listShown = !noList,
        resetKey = targetKey,
        above = {
            when {
                models.loading && noList ->
                    KitNotice(
                        message = stringResource(Res.string.composerPreviewModelsLoading),
                        tone = StatusTone.Progress,
                    )
                noList && models.failure != null ->
                    KitNotice(
                        message = stringResource(Res.string.composerPreviewModelsFailed),
                        tone = StatusTone.Failure,
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.composerPreviewModelsRetry),
                                    actions::loadModels,
                                )
                            ),
                    )
                noList -> KitNotice(message = stringResource(Res.string.composerPreviewModelsEmpty))
                else -> {
                    if (models.failure != null)
                        KitNotice(
                            message = stringResource(Res.string.composerFullModelChangeFailed),
                            tone = StatusTone.Failure,
                        )
                    if (models.recent.size > 1) {
                        KitChipWrap {
                            KitChip(
                                label = stringResource(Res.string.chatCyclePrevious),
                                kind = KitChipKind.Action,
                                onClick = { scope.launch { actions.cycleModel(reverse = true) } },
                            )
                            KitChip(
                                label = stringResource(Res.string.chatCycleNext),
                                kind = KitChipKind.Action,
                                onClick = { scope.launch { actions.cycleModel(reverse = false) } },
                            )
                        }
                    }
                }
            }
        },
        below = {
            val selected = models.selected
            val levels = ModelPicker.thinkingLevels(models)
            if (selected != null && levels.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.composerFullThinking))
                // One line that scrolls sideways (never wraps), the chosen level filled.
                KitFilterChips(Modifier.fillMaxWidth().testTag("chat-thinking-choices")) {
                    levels.forEach { level ->
                        KitChip(
                            label =
                                if (level.isEmpty())
                                    stringResource(Res.string.composerFullThinkingDefault)
                                else effortWords(level),
                            kind = KitChipKind.Action,
                            selected = level == models.variant,
                            onClick = { scope.launch { actions.selectModel(selected, level) } },
                        )
                    }
                }
            }
            if (models.agents.isNotEmpty()) {
                // Flutter's agent rows: the built-in agents by name with what they do, never the server's id.
                KitRowGroup(
                    Modifier.testTag("chat-agent-choices"),
                    label = stringResource(Res.string.composerFullAgent),
                    section = true,
                ) {
                    models.agents.forEach { agent ->
                        KitChoiceRow(
                            title = agentTitle(agent.id),
                            supporting = agentSupporting(agent.id, agent.description),
                            selected = agent.id == models.agent,
                            mark = KitChoiceMark.Tick,
                            onClick = { scope.launch { actions.selectAgent(agent.id) } },
                        )
                    }
                }
            }
        },
    )
}

/** A picker section as the kit sheet shows it: each provider by the name people know it by. */
@Composable
private fun ModelSection.kitGroup(models: ComposerModels): KitModelGroup =
    KitModelGroup(
        kind =
            when {
                favorites -> KitModelGroupKind.Favorites
                providerName == null -> KitModelGroupKind.Recent
                else -> KitModelGroupKind.Provider
            },
        name = providerName?.let { providerRouteName(rows.first().model.providerId, it) }.orEmpty(),
        choices =
            rows.map { pick ->
                val m = pick.model
                KitModelChoice(
                    providerId = m.providerId,
                    modelId = m.modelId,
                    label = m.label,
                    providerName = providerRouteName(m.providerId, m.providerName),
                    current = pick.current,
                    favorite =
                        models.favorites.any {
                            it.providerId == m.providerId && it.modelId == m.modelId
                        },
                )
            },
    )

/** A thinking level as people read it (Flutter `presentedEffort`): "Extra high" for `xhigh`; unknown ids as they are. */
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

/** The built-in agents by name ("Build", "Plan"); any other agent by the name its server gave it (Flutter). */
@Composable
private fun agentTitle(id: String): String =
    when (id) {
        "build" -> stringResource(Res.string.modelPickerAgentBuildName)
        "plan" -> stringResource(Res.string.modelPickerAgentPlanName)
        else -> id
    }

/** What an agent does: the server's description, else the built-in words (Flutter `_agentSupporting`). */
@Composable
private fun agentSupporting(id: String, description: String?): String? =
    description?.trim()?.takeIf { it.isNotEmpty() }
        ?: when (id) {
            "build" -> stringResource(Res.string.modelPickerAgentBuild)
            "plan" -> stringResource(Res.string.modelPickerAgentPlan)
            else -> null
        }
