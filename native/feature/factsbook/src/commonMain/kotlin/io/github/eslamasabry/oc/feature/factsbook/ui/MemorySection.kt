package io.github.eslamasabry.oc.feature.factsbook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouItem
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionViewModel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Memory section that Chats home shows under Needs you while the developer switch is on (F: memory_section.dart).
 * :feature:chats places it; it brings its own ViewModel.
 */
@Composable
public fun FactsBookMemorySection(modifier: Modifier = Modifier) {
    val model = metroViewModel<MemorySectionViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    MemorySectionContent(state, model, modifier)
}

@Composable
public fun MemorySectionContent(state: MemorySectionUi, actions: MemorySectionActions, modifier: Modifier = Modifier) {
    // Off, or not read yet: nothing to say.
    if (!state.shown || !state.loaded) return
    val title = stringResource(Res.string.fbMemoryTitle)
    Column(modifier.fillMaxWidth().testTag("factsbook-memory")) {
        if (!state.paired) {
            KitRowGroup(label = title) {
                KitRow(
                    title = stringResource(Res.string.fbConnectRow),
                    supporting = stringResource(Res.string.fbConnectRowDetail),
                    leading = KitRowLeading.Glyph(KitIcons.database, tile = true),
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::setUp,
                )
            }
            return@Column
        }
        // Stopped or starting on this phone: said in words, with Start, instead of "did not answer".
        val unanswered = state.load == FactsBookLoad.Failed || state.error != null
        if ((unanswered || state.nodeStarting) && (state.nodeStopped || state.nodeStarting)) {
            KitRowGroup(label = title) {
                KitRow(
                    title = stringResource(if (state.nodeStarting) Res.string.fbMemoryStarting else Res.string.fbMemoryStopped),
                    supporting = stringResource(if (state.nodeStarting) Res.string.fbNodeStepStarting else Res.string.fbMemoryStoppedDetail),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.pause),
                    trailing =
                        if (state.nodeStarting) KitRowTrailing.None
                        else KitRowTrailing.Action(KitLineAction(stringResource(Res.string.fbMemoryStart), actions::startNode, semanticsLabel = stringResource(Res.string.fbNodeStart))),
                )
            }
            return@Column
        }
        // The label above the loading rows, or above the error when nothing is held on this phone (F: memory_section.dart:291).
        if (state.load == FactsBookLoad.Loading || (state.load == FactsBookLoad.Failed && state.needsYou.isEmpty())) {
            KitText(
                title,
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                role = TypeRole.Label,
                tone = TextTone.Secondary,
            )
        }
        if (state.load == FactsBookLoad.Loading) {
            KitSkeletonRows(count = 2)
        } else {
            val failed = state.load == FactsBookLoad.Failed
            if (failed) {
                KitNotice(
                    message = failureWords(state.error),
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                    title = stringResource(Res.string.fbErrorTitle),
                    tone = StatusTone.Attention,
                    actions = listOf(KitLineAction(stringResource(Res.string.commonRetry), actions::retry)),
                )
            }
            // Notes held on this phone show even when the node is away.
            if (!failed || state.needsYou.isNotEmpty()) {
                val heading =
                    if (state.needsYouCount > 0) stringResource(Res.string.fbMemoryFromFactsBook, state.needsYouCount)
                    else stringResource(Res.string.fbMemoryFromFactsBookNone)
                KitRowGroup(label = "$title · $heading", modifier = Modifier.testTag("factsbook-memory-needs")) {
                    state.needsYou.forEach { NeedsRow(it, state.nowMs, actions) }
                }
            }
        }
        if (state.recent.isNotEmpty()) {
            KitRowGroup(label = stringResource(Res.string.fbMemoryRecent)) {
                state.recent.forEach { RecentRow(it, actions) }
            }
        }
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.fbMemoryOpenAboutYou),
                supporting = stringResource(Res.string.fbMemoryOpenAboutYouDetail),
                leading = KitRowLeading.Glyph(KitIcons.database, tile = true),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::openAboutYou,
            )
            KitRow(
                title = stringResource(Res.string.fbMemoryAddSomething),
                supporting = stringResource(Res.string.fbMemoryAddSomethingDetail),
                leading = KitRowLeading.Glyph(KitIcons.add, tile = true),
                titleAccent = true,
                trailing = KitRowTrailing.Chevron,
                onClick = actions::addSomething,
            )
        }
    }
}

@Composable
private fun NeedsRow(item: NeedsYouItem, nowMs: Long, actions: MemorySectionActions) {
    val (title, detail) =
        when (item.kind) {
            NeedsYouKind.Review ->
                pluralStringResource(Res.plurals.fbMemoryReview, item.count, item.count) to stringResource(Res.string.fbMemoryReviewDetail)
            NeedsYouKind.NotSaved ->
                pluralStringResource(Res.plurals.fbMemoryNotSaved, item.count, item.count) to
                    stringResource(Res.string.fbMemoryNotSavedDetail)
            NeedsYouKind.Offer ->
                pluralStringResource(Res.plurals.fbNeedsYouOffers, item.count, item.count) to stringResource(Res.string.fbOfferTitle)
        }
    val age = item.since?.let { kitRelativeAge(it.toEpochMilliseconds(), nowMs) }
    KitRow(
        title = title,
        supporting = if (age == null) detail else "$detail · $age",
        supportingMaxLines = 2,
        titleMaxLines = 2,
        leading = KitRowLeading.Glyph(KitIcons.notificationImportant),
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.openNeedsYou(item.kind) },
    )
}

@Composable
internal fun factLine(thing: Thing): String? {
    val fact = thing.newestFact ?: return null
    val observed = observedWords(fact.observedAt)
    val source = sourceWords(fact.source)
    return provenanceLine(kitProvenanceOf(fact.provenance), observed, source, fact.waitingForOk)
}

@Composable
private fun RecentRow(thing: Thing, actions: MemorySectionActions) {
    KitRow(
        title = bidiName(thing.title),
        supporting = factLine(thing),
        supportingMaxLines = 2,
        titleMaxLines = 2,
        leading = KitRowLeading.Glyph(kindIcon(thing.kind)),
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.openThing(thing.id) },
    )
}
