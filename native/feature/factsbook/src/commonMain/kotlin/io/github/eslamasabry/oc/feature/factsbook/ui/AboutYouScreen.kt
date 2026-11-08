package io.github.eslamasabry.oc.feature.factsbook.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingFilter
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChip
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * About you, the full place (F: about_you_screen.dart, spec 60 screen 8): a search field, the places FactsBook offers
 * as visible rows (Needs you, Areas; Reminders and triggers where the node supports them and once their pages are
 * ported), every thing by kind, and Add something at the thumb.
 */
@Composable
public fun AboutYouScreen(state: AboutYouUi, actions: AboutYouActions) {
    val title = stringResource(Res.string.fbAboutYouTitle)
    val gutter = LocalKitTokens.current.gutter.dp
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                subtitle = if (state.stale) stringResource(Res.string.fbStatusStale) else null,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.load == FactsBookLoad.Loading,
        loadingLabel = title,
        bottom = {
            KitActionBlock(
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp),
                primary = KitAction(stringResource(Res.string.fbMemoryAddSomething), actions::addSomething, icon = KitIcons.add),
            )
        },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("search") {
                KitSearchField(
                    label = stringResource(Res.string.fbSearchLabel),
                    onSearch = {},
                    onSubmit = actions::search,
                    modifier = Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                )
            }
            item("places") { Places(state, actions) }
            item("filters") {
                KitFilterChips {
                    ThingFilter.entries.forEach { filter ->
                        KitFilterChip(filterWords(filter), selected = state.filter == filter, onPressed = { actions.filter(filter) })
                    }
                }
            }
            item("locked") {
                KitGroupNote(
                    stringResource(if (state.lockedNotYet) Res.string.fbListLockedNoteNotYet else Res.string.fbListLockedNote)
                )
            }
            item("list") { ThingList(state, actions) }
        }
    }
}

@Composable
private fun filterWords(filter: ThingFilter): String =
    stringResource(
        when (filter) {
            ThingFilter.All -> Res.string.fbListAll
            ThingFilter.Cards -> Res.string.fbListCards
            ThingFilter.People -> Res.string.fbListPeople
            ThingFilter.Documents -> Res.string.fbListDocuments
        }
    )

@Composable
private fun Places(state: AboutYouUi, actions: AboutYouActions) {
    KitRowGroup {
        // The title already says "Needs you": no needs-you mark here, or a screen reader says it three times.
        KitRow(
            title = stringResource(Res.string.fbNeedsYouTitle),
            supporting =
                if (state.needsYouCount > 0) stringResource(Res.string.fbNeedsYouWaiting, state.needsYouCount)
                else stringResource(Res.string.fbNeedsYouNone),
            leading = KitRowLeading.Glyph(KitIcons.review),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openNeedsYou,
        )
        KitRow(
            title = stringResource(Res.string.fbAreasTitle),
            leading = KitRowLeading.Glyph(KitIcons.privacy),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openAreas,
        )
    }
}

@Composable
private fun ThingList(state: AboutYouUi, actions: AboutYouActions) {
    val things = state.things
    when {
        state.listError != null ->
            KitStateView(
                KitStateKind.Error,
                stringResource(Res.string.fbErrorTitle),
                body = failureWords(state.listError),
                primary = KitAction(stringResource(Res.string.commonRetry), actions::reload),
            )
        things == null -> KitSkeletonRows()
        things.isEmpty() ->
            KitStateView(
                KitStateKind.Empty,
                stringResource(Res.string.fbEmptyTitle),
                body = stringResource(Res.string.fbEmptyBody),
                icon = KitIcons.database,
            )
        else -> KitRowGroup { things.forEach { ThingRow(it, actions) } }
    }
}

@Composable
private fun ThingRow(thing: Thing, actions: AboutYouActions) {
    val kind = kindWords(thing.kind)
    val fact = thing.newestFact
    val head = if (fact == null) kind else stringResource(Res.string.fbKindLine, kind, fact.text)
    val line = factLine(thing)
    KitRow(
        title = bidiName(thing.title),
        supporting = if (line == null) head else "$head\n$line",
        supportingMaxLines = 4,
        leading = KitRowLeading.Glyph(kindIcon(thing.kind)),
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.openThing(thing.id) },
    )
}
