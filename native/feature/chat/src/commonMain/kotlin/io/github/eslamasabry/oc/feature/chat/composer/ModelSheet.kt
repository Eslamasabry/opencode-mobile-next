package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitThinkingLevels
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
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
import io.github.eslamasabry.oc.ui.words.res.modelPickerModeAutoName
import io.github.eslamasabry.oc.ui.words.res.modelPickerModeDefaultName
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
    // N-51: the one kit model sheet, as New conversation shows it; the chat adds its notices, the
    // recent-model cycle, thinking and agent around the list.
    val options = remember(models.choices) { models.choices.map(::kitOption) }
    KitModelSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        options = options,
        current = if (models.usesDefault) null else models.selected?.let(::kitOption)?.key,
        onChoose = { option ->
            selectAndClose(
                option?.let { o ->
                    models.choices.firstOrNull {
                        it.providerId == o.providerId && it.modelId == o.modelId
                    }
                }
            )
        },
        favorites = remember(models.favorites) { models.favorites.map(::kitOption) },
        recent = remember(models.recent) { models.recent.map(::kitOption) },
        onToggleFavorite = { option ->
            models.choices
                .firstOrNull { it.providerId == option.providerId && it.modelId == option.modelId }
                ?.let { scope.launch { actions.toggleModelFavorite(it) } }
        },
        offersServerDefault = ModelPicker.offersServerDefault(models),
        serverDefault = models.serverDefault?.let(::kitOption),
        resetKey = targetKey,
        // The catalog read is running: "Loading models…" with no list yet, the working mark over a kept list.
        loading = models.loading,
        above = {
            when {
                // The kit draws its own loading row (and no `above`) while loading with no list; kept as a guard.
                models.loading && models.choices.isEmpty() ->
                    KitNotice(
                        message = stringResource(Res.string.composerPreviewModelsLoading),
                        tone = StatusTone.Progress,
                    )
                models.choices.isEmpty() && models.failure != null ->
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
                models.choices.isEmpty() ->
                    KitNotice(message = stringResource(Res.string.composerPreviewModelsEmpty))
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
        // The thinking level comes first, under the title and above the search: it frames the choice of model, and it
        // stays put while the list scrolls. Every level is shown whole (the chips wrap, never cut by the edge); a model
        // without levels, or one whose name carries its effort, has no row at all.
        header = {
            val selected = models.selected
            val levels = ModelPicker.thinkingLevels(models)
            if (models.choices.isNotEmpty() && selected != null && levels.isNotEmpty()) {
                KitThinkingLevels(
                    title = stringResource(Res.string.composerFullThinking),
                    labels =
                        levels.map {
                            if (it.isEmpty()) {
                                stringResource(Res.string.composerFullThinkingDefault)
                            } else {
                                effortWords(it)
                            }
                        },
                    selected = levels.indexOf(models.variant),
                    onSelect = { scope.launch { actions.selectModel(selected, levels[it]) } },
                    modifier = Modifier.fillMaxWidth().testTag("chat-thinking-choices"),
                )
            }
        },
        below = {
            if (models.choices.isNotEmpty()) {
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
            }
        },
    )
}

private fun kitOption(model: ComposerModel): KitModelOption =
    KitModelOption(model.providerId, model.modelId, model.label, model.providerName)

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
internal fun agentTitle(id: String): String =
    when (id) {
        "build" -> stringResource(Res.string.modelPickerAgentBuildName)
        "plan" -> stringResource(Res.string.modelPickerAgentPlanName)
        "default" -> stringResource(Res.string.modelPickerModeDefaultName)
        "auto" -> stringResource(Res.string.modelPickerModeAutoName)
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
