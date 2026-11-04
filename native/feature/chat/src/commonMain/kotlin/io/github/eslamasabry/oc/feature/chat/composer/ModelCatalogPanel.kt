package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The session-scoped model review. Nothing mutates the session before Apply or an explicit quick
 * choice.
 */
@Composable
internal fun ModelCatalogPanel(
    models: ComposerModels,
    actions: BasicComposerActions,
    targetKey: String,
    onApplied: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val searchField = remember(targetKey) { TextFieldState() }
    var query by remember(targetKey) { mutableStateOf("") }
    var collection by remember(targetKey) { mutableStateOf(0) }
    var intent by remember(targetKey) { mutableStateOf(0) }
    var provider by remember(targetKey) { mutableStateOf<String?>(null) }
    var all by remember(targetKey) { mutableStateOf(false) }
    var draft by remember(targetKey) { mutableStateOf(models.selected) }
    var level by remember(targetKey) { mutableStateOf(models.variant) }
    var agent by remember(targetKey) { mutableStateOf(models.agent) }
    var dirty by remember(targetKey) { mutableStateOf(false) }
    var applying by remember(targetKey) { mutableStateOf(false) }
    var error by remember(targetKey) { mutableStateOf(0) }
    var visible by remember(targetKey) { mutableStateOf(40) }
    LaunchedEffect(models.selected, models.variant, models.agent) {
        if (!dirty && !applying) {
            draft = models.selected
            level = models.variant
            agent = models.agent
        }
    }
    fun apply(model: ComposerModel, variant: String, chosenAgent: String?, quick: Boolean = false) {
        if (applying || !model.enabled) return
        applying = true
        error = 0
        scope.launch {
            try {
                if (!actions.selectModel(model, variant)) {
                    error = 1
                    return@launch
                }
                if (
                    !quick &&
                        chosenAgent != null &&
                        chosenAgent != models.agent &&
                        !actions.selectAgent(chosenAgent)
                ) {
                    error = 2
                    return@launch
                }
                dirty = false
                onApplied()
            } finally {
                applying = false
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        val failureWords =
            when {
                error == 2 -> Res.string.modelChoicePartialSaveError
                error == 1 || models.failure != null -> Res.string.modelChoiceModelSaveError
                (models.libraryFailure as? io.github.eslamasabry.oc.core.common.Failure.Unavailable)
                    ?.feature
                    ?.name == "clipboard" -> Res.string.native44CopyFailed
                models.libraryFailure != null -> Res.string.e7ModelUiFavoritesFailed
                else -> null
            }
        if (failureWords != null) KitNotice(stringResource(failureWords), tone = StatusTone.Failure)
        if (
            models.selected != null &&
                (!models.selected.enabled || models.choices.none { same(it, models.selected) })
        )
            KitNotice(stringResource(Res.string.modelUnavailableSelection))
        val choices = modelRecommendations(models)
        if (
            !all &&
                choices.isNotEmpty() &&
                choices.any { !same(it.model, models.selected) } &&
                models.choices.size > choices.size
        ) {
            KitRowGroup {
                choices.forEach { choice ->
                    val model = choice.model
                    val reason = choice.reason
                    val label =
                        when (reason) {
                            0 -> stringResource(Res.string.modelPickerInUse)
                            1 -> stringResource(Res.string.modelPickerReasonDefault)
                            2 -> stringResource(Res.string.modelPickerReasonRecent)
                            else -> stringResource(Res.string.modelPickerReasonCheapest)
                        }
                    KitRow(
                        title = label,
                        supporting =
                            buildList {
                                    add(model.label)
                                    if (choice.alsoCheapest)
                                        add(stringResource(Res.string.modelPickerReasonCheapest))
                                    val free = stringResource(Res.string.modelPickerFree)
                                    if (choice.free && !model.label.trimEnd().endsWith(free, true))
                                        add(free)
                                }
                                .joinToString(" · "),
                        selected = same(model, models.selected),
                        enabled = !applying,
                        disabledReason =
                            if (applying) stringResource(Res.string.e7ModelUiLoading) else null,
                        onClick = {
                            if (same(model, models.selected)) onApplied()
                            else apply(model, "", models.agent, true)
                        },
                        modifier = Modifier.testTag("model-choice-$reason"),
                    )
                }
                KitRow(
                    title =
                        stringResource(
                            Res.string.native44AllModels,
                            Numbers.format(models.choices.size),
                        ),
                    onClick = { all = true },
                )
            }
        } else {
            KitSearchField(
                label = stringResource(Res.string.modelSearchHint),
                onSearch = {
                    query = it
                    visible = 40
                },
                state = searchField,
                modifier = Modifier.fillMaxWidth().testTag("model-picker-search"),
            )
            KitSegmented(
                listOf(
                    KitSegment(0, stringResource(Res.string.modelAll)),
                    KitSegment(1, stringResource(Res.string.modelFavorites)),
                    KitSegment(2, stringResource(Res.string.modelRecent)),
                ),
                collection,
                stringResource(Res.string.modelPickerCollections),
            ) {
                collection = it
                visible = 40
            }
            KitChipWrap {
                listOf(
                        Res.string.e7ModelUiAnyCapability,
                        Res.string.e7ModelUiFastModes,
                        Res.string.e7ModelUiReasoning,
                        Res.string.e7ModelUiLargestContext,
                    )
                    .forEachIndexed { index, word ->
                        KitChip(
                            stringResource(word),
                            kind = KitChipKind.Action,
                            selected = intent == index,
                            onClick = {
                                intent = index
                                visible = 40
                            },
                        )
                    }
            }
            KitChipWrap {
                KitChip(
                    stringResource(Res.string.e7ModelUiAllProviders),
                    kind = KitChipKind.Action,
                    selected = provider == null,
                    onClick = {
                        provider = null
                        visible = 40
                    },
                )
                models.choices
                    .distinctBy { it.providerId }
                    .forEach { model ->
                        KitChip(
                            model.providerName.ifBlank { model.providerId },
                            kind = KitChipKind.Action,
                            selected = provider == model.providerId,
                            onClick = {
                                provider = model.providerId
                                visible = 40
                            },
                        )
                    }
            }
            val base =
                when (collection) {
                    1 -> models.favorites
                    2 -> models.recent
                    else -> models.choices
                }
            val found =
                base
                    .filter { model ->
                        (provider == null || provider == model.providerId) &&
                            (query.isBlank() ||
                                listOf(
                                        model.label,
                                        model.modelId,
                                        model.providerId,
                                        model.providerName,
                                    )
                                    .any { query.trim().lowercase() in it.lowercase() }) &&
                            when (intent) {
                                1 -> model.fast
                                2 -> model.reasoning
                                else -> true
                            }
                    }
                    .let {
                        if (intent == 3) it.sortedByDescending { model -> model.contextLimit }
                        else it
                    }
            if (found.isEmpty()) {
                KitText(
                    stringResource(
                        when (collection) {
                            1 -> Res.string.e7ModelUiFavoritesEmpty
                            2 -> Res.string.e7ModelUiRecentEmpty
                            else -> Res.string.e7ModelUiNoMatches
                        }
                    )
                )
                KitButton(
                    stringResource(Res.string.e7ModelUiClearFilters),
                    {
                        query = ""
                        searchField.edit { replace(0, length, "") }
                        provider = null
                        intent = 0
                        collection = 0
                    },
                    role = KitButtonRole.Tertiary,
                )
            } else
                KitRowGroup {
                    found.take(visible).forEach { model ->
                        val favorite = models.favorites.any { same(it, model) }
                        val chosen = same(draft, model)
                        val details = buildList {
                            if (same(models.selected, model))
                                add(stringResource(Res.string.modelPickerInUse))
                            if (model.isNew) add(stringResource(Res.string.modelNewBadge))
                            add(model.providerName.ifBlank { model.providerId })
                            if (model.contextLimit > 0)
                                add(
                                    stringResource(
                                        Res.string.native44Context,
                                        Numbers.format(model.contextLimit),
                                    )
                                )
                            if (model.status.equals("deprecated", true))
                                add(stringResource(Res.string.e7ModelUiDeprecated))
                            if (model.status.lowercase() in setOf("alpha", "beta"))
                                add(stringResource(Res.string.e7ModelUiPreview))
                        }
                            .joinToString(" · ")
                        KitRow(
                            title = model.label,
                            supporting = details,
                            supportingMaxLines = 2,
                            selected = chosen,
                            enabled = model.enabled && !applying,
                            disabledReason =
                                if (applying) stringResource(Res.string.e7ModelUiLoading)
                                else if (!model.enabled)
                                    stringResource(Res.string.modelPickerUnavailableReason)
                                else null,
                            trailing =
                                KitRowTrailing.Action(
                                    KitLineAction(
                                        stringResource(
                                            if (favorite) Res.string.e7ModelUiUnfavorite
                                            else Res.string.e7ModelUiFavorite,
                                            model.label,
                                        ),
                                        { actions.toggleFavorite(model) },
                                    )
                                ),
                            onClick = {
                                draft = model
                                level = if (same(model, models.selected)) models.variant else ""
                                dirty = true
                                error = 0
                            },
                            modifier =
                                Modifier.testTag(
                                    "model-option-${model.providerId}-${model.modelId}"
                                ),
                        )
                        if (chosen) {
                            val info = buildList {
                                if (model.outputLimit > 0)
                                    add(
                                        stringResource(
                                            Res.string.native44Output,
                                            Numbers.format(model.outputLimit),
                                        )
                                    )
                                if (model.reasoning)
                                    add(stringResource(Res.string.modelPickerCanThink))
                                if (model.tools)
                                    add(stringResource(Res.string.modelPickerCanUseTools))
                                if (model.supportsAttachments)
                                    add(stringResource(Res.string.modelPickerCanReadAttachments))
                                if (model.inputCost != null && model.outputCost != null)
                                    add(
                                        stringResource(
                                            Res.string.native44Price,
                                            Numbers.decimal(model.inputCost, 2),
                                            Numbers.decimal(model.outputCost, 2),
                                        )
                                    )
                            }
                            if (info.isNotEmpty()) KitText(info.joinToString(" · "))
                            KitButton(
                                stringResource(Res.string.modelPickerCopyId),
                                { actions.copyModelId(model) },
                                role = KitButtonRole.Tertiary,
                            )
                        }
                    }
                    if (found.size > visible)
                        KitRow(
                            stringResource(
                                Res.string.native44MoreModels,
                                Numbers.format(found.size - visible),
                            ),
                            onClick = { visible += 40 },
                        )
                }
            val selected = draft?.let { held ->
                models.choices.firstOrNull { same(it, held) && it.enabled }
            }
            if (selected != null && selected.variants.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.modelPickerThinking))
                KitChipWrap {
                    (listOf("") + selected.variants).forEach { variant ->
                        KitChip(
                            if (variant.isEmpty()) stringResource(Res.string.e7ModelUiDefault)
                            else variantWords(selected, variant),
                            kind = KitChipKind.Action,
                            selected = level == variant,
                            onClick = {
                                if (!applying) {
                                    level = variant
                                    dirty = true
                                }
                            },
                        )
                    }
                }
            }
            if (models.agents.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.e7ModelUiAgent))
                KitChipWrap {
                    models.agents.forEach { candidate ->
                        KitChip(
                            when (candidate.id) {
                                "build" -> stringResource(Res.string.modelPickerAgentBuildName)
                                "plan" -> stringResource(Res.string.modelPickerAgentPlanName)
                                else -> candidate.id
                            },
                            kind = KitChipKind.Action,
                            selected = candidate.id == agent,
                            onClick = {
                                if (!applying) {
                                    agent = candidate.id
                                    dirty = true
                                }
                            },
                        )
                    }
                }
                models.agents
                    .firstOrNull { it.id == agent }
                    ?.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let { KitText(it) }
            }
            KitButton(
                label = stringResource(Res.string.e7ModelUiUseSession),
                onClick = { selected?.let { apply(it, level, agent) } },
                enabled =
                    if (selected == null || applying)
                        KitEnabled.Disabled(stringResource(Res.string.modelPickerChooseFirst))
                    else KitEnabled.Enabled,
                working = applying,
                modifier = Modifier.testTag("model-picker-apply"),
            )
        }
        KitButton(
            stringResource(Res.string.e7ModelUiRefresh),
            actions::loadModels,
            role = KitButtonRole.Tertiary,
        )
        KitRowGroup(label = stringResource(Res.string.native44ModelShortcuts)) {
            KitRow(
                stringResource(Res.string.modelShortcutsNextRecent),
                enabled = models.recent.isNotEmpty(),
                disabledReason =
                    if (models.recent.isEmpty()) stringResource(Res.string.modelShortcutsNoRecent)
                    else null,
                onClick = { actions.cycleModel() },
            )
            KitRow(
                stringResource(Res.string.modelShortcutsPreviousRecent),
                enabled = models.recent.isNotEmpty(),
                disabledReason =
                    if (models.recent.isEmpty()) stringResource(Res.string.modelShortcutsNoRecent)
                    else null,
                onClick = { actions.cycleModel(reverse = true) },
            )
            KitRow(
                stringResource(Res.string.modelNextFavorite),
                enabled = models.favorites.isNotEmpty(),
                disabledReason =
                    if (models.favorites.isEmpty())
                        stringResource(Res.string.modelShortcutsNoFavorite)
                    else null,
                onClick = { actions.cycleModel(favoritesOnly = true) },
            )
        }
    }
}

private fun same(first: ComposerModel?, second: ComposerModel?): Boolean =
    first != null &&
        second != null &&
        first.providerId == second.providerId &&
        first.modelId == second.modelId

/** One row per model, at most three; an equal published price never invents a cheapest choice. */
private data class ModelRecommendation(
    val model: ComposerModel,
    val reason: Int,
    val alsoCheapest: Boolean = false,
) {
    val free: Boolean
        get() = model.inputCost == 0.0 && model.outputCost == 0.0
}

private fun modelRecommendations(models: ComposerModels): List<ModelRecommendation> {
    val choices = models.choices.filter { it.enabled }
    fun find(model: ComposerModel?): ComposerModel? = choices.firstOrNull { same(it, model) }
    val current = find(models.selected)
    val default = find(models.defaultModel)
    val priced = choices.filter {
        it.inputCost != null &&
            it.outputCost != null &&
            it.inputCost.isFinite() &&
            it.outputCost.isFinite() &&
            it.inputCost >= 0 &&
            it.outputCost >= 0
    }
    fun price(model: ComposerModel): Double = model.inputCost!! + model.outputCost!!
    val cheapest =
        if (priced.size > 1 && priced.minOf(::price) < priced.maxOf(::price)) {
            val low = priced.minOf(::price)
            val tied = priced.filter { price(it) == low }
            fun rank(model: ComposerModel): Int {
                if (same(model, current)) return 0
                if (same(model, default)) return 1
                val recent = models.recent.indexOfFirst { same(it, model) }
                if (recent >= 0) return 2 + recent
                val favorite = models.favorites.indexOfFirst { same(it, model) }
                return if (favorite >= 0) 100 + favorite else 1000 + tied.indexOf(model)
            }
            tied.minByOrNull(::rank)
        } else null
    val rows = ArrayList<ModelRecommendation>()
    if (current != null && !same(current, default) && !same(current, cheapest))
        rows += ModelRecommendation(current, 0)
    if (default != null) rows += ModelRecommendation(default, 1, same(default, cheapest))
    val cheapestRow = cheapest?.takeUnless { same(it, default) }?.let { ModelRecommendation(it, 3) }
    if (rows.size + (if (cheapestRow == null) 0 else 1) < 3) {
        models.recent
            .mapNotNull(::find)
            .firstOrNull { !same(it, current) && !same(it, default) && !same(it, cheapest) }
            ?.let { rows += ModelRecommendation(it, 2) }
    }
    if (cheapestRow != null) rows += cheapestRow
    return rows.take(3)
}

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

@Composable
private fun variantWords(model: ComposerModel, id: String): String {
    model.variantLabels[id]?.let {
        return it
    }
    val effort = model.variantEfforts[id]
    return if (effort == null || id.equals(effort, true)) effortWords(id)
    else
        stringResource(Res.string.e7ModelUiEffort, effortWords(id), effortWords(effort).lowercase())
}
