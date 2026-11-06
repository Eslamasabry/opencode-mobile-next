package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatCycleNext
import io.github.eslamasabry.oc.ui.words.res.chatCyclePrevious
import io.github.eslamasabry.oc.ui.words.res.chatModelsCurrent
import io.github.eslamasabry.oc.ui.words.res.chatModelsNoMatch
import io.github.eslamasabry.oc.ui.words.res.chatModelsRecent
import io.github.eslamasabry.oc.ui.words.res.chatModelsSearch
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorite_add
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorite_remove
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorites
import io.github.eslamasabry.oc.ui.words.res.composerFullAgent
import io.github.eslamasabry.oc.ui.words.res.composerFullModelChangeFailed
import io.github.eslamasabry.oc.ui.words.res.composerFullThinking
import io.github.eslamasabry.oc.ui.words.res.composerFullThinkingDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsClose
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsEmpty
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsFailed
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsLoading
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsRetry
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsTitle
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
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.composerPreviewModelsTitle),
        closeLabel = stringResource(Res.string.composerPreviewModelsClose),
    ) {
        when {
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
                // B3 (QA wave 1): a searchable list grouped by provider, recent picks first, the
                // current one marked.
                var modelQuery by remember(targetKey) { mutableStateOf("") }
                KitSearchField(
                    label = stringResource(Res.string.chatModelsSearch),
                    onSearch = { modelQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("chat-model-search"),
                )
                val currentTag = stringResource(Res.string.chatModelsCurrent)
                val sections =
                    remember(models, modelQuery) { ModelPicker.sections(models, modelQuery) }
                // The groups keep a step of air between them, so Server default, Favorites and each provider read
                // as separate lists (and a raised style's panels never touch).
                Column(
                    Modifier.fillMaxWidth().testTag("chat-model-choices"),
                    verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
                ) {
                    // Server default sits in a group of its own, on the same edge as the model rows.
                    if (models.selected != null && modelQuery.isBlank()) {
                        KitRowGroup {
                            KitChoiceRow(
                                title = stringResource(Res.string.composerPreviewModelDefault),
                                selected = models.usesDefault,
                                mark = KitChoiceMark.Tick,
                                tag = if (models.usesDefault) currentTag else null,
                                onClick = { selectAndClose(null) },
                            )
                        }
                    }
                    if (sections.isEmpty()) KitText(stringResource(Res.string.chatModelsNoMatch))
                    sections.forEach { section ->
                        KitRowGroup(
                            label =
                                if (section.favorites)
                                    stringResource(Res.string.chat_model_favorites)
                                else
                                    section.providerName?.let {
                                        providerRouteName(section.rows.first().model.providerId, it)
                                    } ?: stringResource(Res.string.chatModelsRecent)
                        ) {
                            section.rows.forEach { pick ->
                                val favorite =
                                    models.favorites.any {
                                        it.providerId == pick.model.providerId &&
                                            it.modelId == pick.model.modelId
                                    }
                                // Looks pass: Flutter's picker row — the provider mark, the name,
                                // the chosen
                                // row banded with a mint check, and the star as the row's one icon
                                // (its words
                                // are what TalkBack says), not a text button on every row.
                                // The provider's mark only where providers mix (Favorites, Recent); under a
                                // provider's own heading it would repeat one glyph down the whole list.
                                val mixed = section.providerName == null
                                KitChoiceRow(
                                    title = pick.model.label,
                                    leading =
                                        if (mixed) ({
                                            KitProviderLogo(
                                                pick.model.providerId,
                                                pick.model.providerName,
                                            )
                                        })
                                        else null,
                                    selected = pick.current,
                                    mark = KitChoiceMark.Tick,
                                    // In "Recent" the provider tells equal names apart.
                                    supporting =
                                        if (section.providerName == null)
                                            providerRouteName(
                                                pick.model.providerId,
                                                pick.model.providerName,
                                            )
                                        else null,
                                    tag = if (pick.current) currentTag else null,
                                    trailing = {
                                        KitIconButton(
                                            icon =
                                                if (favorite) KitIcons.starFilled
                                                else KitIcons.star,
                                            label =
                                                stringResource(
                                                    if (favorite)
                                                        Res.string.chat_model_favorite_remove
                                                    else Res.string.chat_model_favorite_add
                                                ),
                                            onClick = {
                                                scope.launch {
                                                    actions.toggleModelFavorite(pick.model)
                                                }
                                            },
                                            tone =
                                                if (favorite) TextTone.Accent
                                                else TextTone.Secondary,
                                        )
                                    },
                                    onClick = { selectAndClose(pick.model) },
                                    modifier = Modifier.testTag("chat-model-choice"),
                                )
                            }
                        }
                    }
                }
                val selected = models.selected
                if (selected != null && selected.variants.isNotEmpty()) {
                    KitSectionLabel(stringResource(Res.string.composerFullThinking))
                    KitChipWrap(Modifier.fillMaxWidth().testTag("chat-thinking-choices")) {
                        (listOf("") + selected.variants).forEach { level ->
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
        }
    }
}

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
