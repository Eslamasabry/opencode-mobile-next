package io.github.eslamasabry.oc.feature.factsbook.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.Fact
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureReminder
import io.github.eslamasabry.oc.feature.factsbook.presentation.ConnectActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.ConnectUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.SearchActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.SearchUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputOperationDialog
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.action.KitCopyButton
import io.github.eslamasabry.oc.ui.kit.row.KitFactRow
import io.github.eslamasabry.oc.ui.kit.row.KitProvenance
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun BackBar(title: String, onBack: () -> Unit, subtitle: String? = null) {
    KitTopBar(
        title = title,
        subtitle = subtitle,
        exit = KitTopBarExit.Back,
        exitLabel = stringResource(Res.string.kitTopBarBack),
        onExit = onBack,
    )
}

@Composable
private fun gutter() = LocalKitTokens.current.gutter.dp

/** An error page with a way forward; the code and request id are under Details (the body's second line). */
@Composable
private fun ErrorState(error: io.github.eslamasabry.oc.engine.factsbook.FactsBookError?, retry: () -> Unit) {
    val details = failureDetails(error)
    KitStateView(
        KitStateKind.Error,
        stringResource(Res.string.fbErrorTitle),
        body = failureWords(error) + (details?.let { "\n\n$it" } ?: ""),
        primary = KitAction(stringResource(Res.string.commonRetry), retry),
    )
}

// ---- Areas --------------------------------------------------------------------------------------------------------

/** Areas and sharing (F: areas_screen.dart). The node has no call to make a new area, so none is offered. */
@Composable
public fun AreasScreen(state: AreasUi, actions: AreasActions) {
    val title = stringResource(Res.string.fbAreasTitle)
    KitSheetHost(Modifier.fillMaxSize()) {
    KitScreen(
        topBar = { BackBar(title, actions::back) },
        width = KitScreenWidth.Reading,
        loading = (state.loading && state.areas != null) || state.working.isNotEmpty(),
        loadingLabel = title,
        refresh = if (state.areas != null) KitScreenRefresh(refreshing = state.loading, onRefresh = actions::reload) else null,
        edges = KitScreenEdges.Bleed,
    ) {
        val areas = state.areas
        when {
            areas == null && state.error == null -> KitSkeletonRows()
            areas == null -> ErrorState(state.error, actions::reload)
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    item("intro") {
                        KitText(
                            stringResource(Res.string.fbAreasIntro),
                            Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                        )
                    }
                    if (!state.canApprove) {
                        item("owner") {
                            KitNotice(
                                message = stringResource(Res.string.fbCareNeedsOwner),
                                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S3.dp),
                                icon = KitIcons.info,
                                actions = listOf(KitLineAction(stringResource(Res.string.fbCareSetupApproval), actions::setUpApproval)),
                            )
                        }
                    }
                    state.failure?.let { failure ->
                        item("failure") {
                            KitNotice(
                                message = failureWords(failure),
                                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S3.dp),
                                tone = StatusTone.Attention,
                                dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                            )
                        }
                    }
                    areas.forEach { area ->
                        item(area.slug) {
                            KitRowGroup(label = areaName(area.slug)) {
                                KitSwitchRow(
                                    title = stringResource(Res.string.fbAreasOthersRead),
                                    checked = area.share.readByDefault,
                                    supporting = stringResource(Res.string.fbAreasOthersReadDetail),
                                    enabled =
                                        when {
                                            !state.canApprove -> KitEnabled.Disabled(stringResource(Res.string.fbCareNeedsOwnerShort))
                                            area.slug in state.working -> KitEnabled.Disabled(stringResource(Res.string.fbAreasOthersRead))
                                            else -> KitEnabled.Enabled
                                        },
                                    onCheckedChange = { on -> actions.share(area, on) },
                                )
                                KitRow(
                                    title =
                                        stringResource(
                                            if (area.sensitiveStorage) Res.string.fbAreasLockedFolder else Res.string.fbAreasNormalStorage
                                        ),
                                    supporting =
                                        stringResource(if (area.reviewFirst) Res.string.fbAreasReviewFirst else Res.string.fbAreasReviewAuto),
                                    supportingMaxLines = 2,
                                    leading = KitRowLeading.Glyph(if (area.sensitiveStorage) KitIcons.locked else KitIcons.folders),
                                )
                            }
                        }
                    }
                    item("fixed") { KitGroupNote(stringResource(Res.string.fbAreasFixed)) }
                }
        }
    }
    val confirm = state.confirmShare
    KitConfirm(
        spec =
            confirm?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.fbAreasShareConfirmTitle, areaName(it.slug)),
                    confirmLabel = stringResource(Res.string.fbAreasShareConfirm),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.fbAreasShareConfirmBody),
                )
            },
        onConfirm = actions::confirmShare,
        onCancel = actions::cancelShare,
    )
    }
}

// ---- Thing --------------------------------------------------------------------------------------------------------

/** One thing (F: thing_screen.dart): Add a fact, then every fact with provenance, source and date; tap one to change it. */
@Composable
public fun ThingScreen(state: ThingUi, actions: ThingActions) {
    val thing = state.thing
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Fact?>(null) }
    var explaining by remember { mutableStateOf<Fact?>(null) }
    KitSheetHost(Modifier.fillMaxSize()) {
    KitScreen(
        topBar = {
            BackBar(
                thing?.let { bidiName(it.title) } ?: "",
                actions::back,
                thing?.let { t -> listOfNotNull(kindWords(t.kind), t.area.takeIf { it.isNotEmpty() }?.let { areaName(it) }).joinToString(" · ") },
            )
        },
        width = KitScreenWidth.Reading,
        loading = (state.loading && thing != null) || state.working,
        loadingLabel = thing?.title ?: "",
        refresh = if (thing != null) KitScreenRefresh(refreshing = state.loading, onRefresh = actions::reload) else null,
        edges = KitScreenEdges.Bleed,
    ) {
        when {
            thing == null && state.error == null -> KitSkeletonRows()
            thing == null -> ErrorState(state.error, actions::reload)
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    if (thing.sensitive) {
                        item("locked") {
                            KitNotice(stringResource(Res.string.fbThingLocked), Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp))
                        }
                    }
                    val notice = state.notice
                    if (notice != null || state.writeError != null) {
                        item("notice") {
                            KitNotice(
                                message =
                                    when (notice) {
                                        ThingNotice.FactAdded -> stringResource(Res.string.fbCaptureFactAdded, bidiName(thing.title))
                                        ThingNotice.FactProposed -> stringResource(Res.string.fbCaptureFactProposed)
                                        ThingNotice.FactChanged -> stringResource(Res.string.fbThingFactChanged)
                                        ThingNotice.ChangeProposed -> stringResource(Res.string.fbThingChangeProposed)
                                        ThingNotice.ReminderMade -> stringResource(Res.string.fbThingReminderMade, bidiName(thing.title))
                                        null -> failureWords(state.writeError)
                                    },
                                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
                                tone = if (notice == null) StatusTone.Attention else StatusTone.Ok,
                                actions =
                                    if (notice?.waitsForOk == true) listOf(KitLineAction(stringResource(Res.string.fbCaptureSeeNeedsYou), actions::seeNeedsYou))
                                    else emptyList(),
                                dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                            )
                        }
                    }
                    if (state.canWrite || state.canRemind) {
                        item("add") {
                            KitRowGroup {
                                if (state.canWrite) {
                                    KitRow(
                                        title = stringResource(Res.string.fbThingAddFact, bidiName(thing.title)),
                                        leading = KitRowLeading.Glyph(KitIcons.add, tile = true),
                                        titleAccent = true,
                                        titleMaxLines = 2,
                                        trailing = KitRowTrailing.Chevron,
                                        onClick = { adding = true },
                                    )
                                }
                                if (state.canRemind) {
                                    KitRow(
                                        title = stringResource(Res.string.fbThingRemind, bidiName(thing.title)),
                                        leading = KitRowLeading.Glyph(KitIcons.clock),
                                        titleMaxLines = 2,
                                        trailing = KitRowTrailing.Chevron,
                                        onClick = actions::remind,
                                    )
                                }
                            }
                        }
                    }
                    item("label") { KitSectionLabel(stringResource(Res.string.fbThingFacts)) }
                    if (thing.facts.isEmpty()) {
                        item("none") {
                            KitText(
                                stringResource(Res.string.fbThingNoFacts),
                                Modifier.padding(horizontal = gutter()),
                                tone = TextTone.Secondary,
                            )
                        }
                    }
                    thing.facts.forEach { fact ->
                        item(fact.id) {
                            KitFactRow(
                                text = fact.text,
                                provenance = kitProvenanceOf(fact.provenance),
                                observed = observedWords(fact.observedAt),
                                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S1.dp),
                                source = sourceWords(fact.source),
                                waitingForOk = fact.waitingForOk,
                                onOpen = { explaining = fact },
                            )
                        }
                        // An edited fact keeps where the original was read: the node retains that evidence through edits.
                        fact.support.forEachIndexed { i, evidence ->
                            item("${fact.id}-support-$i") {
                                KitText(
                                    stringResource(Res.string.fbThingOriginally, sourceWords(evidence.source) ?: stringResource(Res.string.fbSourceOther), observedWords(evidence.observedAt)),
                                    Modifier.padding(start = gutter() + Space.S4.dp, end = gutter(), bottom = Space.S1.dp),
                                    role = TypeRole.Caption,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }
                    }
                    if (thing.facts.any { it.waitingForOk }) item("waiting") { KitGroupNote(stringResource(Res.string.fbThingWaitingNote)) }
                    if (thing.facts.isNotEmpty()) item("hint") { KitGroupNote(stringResource(Res.string.fbThingTapHint)) }
                }
        }
    }
    if (thing != null) {
        KitInputOperationDialog(
            visible = adding,
            title = stringResource(Res.string.fbThingAddFact, bidiName(thing.title)),
            fieldLabel = stringResource(Res.string.fbCareEditLabel),
            confirmLabel = stringResource(Res.string.fbThingAddFactSave),
            initial = "",
            helper = stringResource(Res.string.fbThingOneLine),
            onSubmit = {
                adding = false
                actions.addFact(it)
            },
            onDismissRequest = { adding = false },
        )
    }
    val about = explaining
    if (about != null) {
        val date = observedWords(about.observedAt)
        val source = sourceWords(about.source)
        val words =
            stringResource(
                when (kitProvenanceOf(about.provenance)) {
                    KitProvenance.Told -> Res.string.fbThingExplainTold
                    KitProvenance.Seen -> if (source == null) Res.string.fbThingExplainSeenNoSource else Res.string.fbThingExplainSeen
                    KitProvenance.Guessed -> if (source == null) Res.string.fbThingExplainGuessedNoSource else Res.string.fbThingExplainGuessed
                    KitProvenance.Calculated -> Res.string.fbThingExplainCalculated
                },
                *when (kitProvenanceOf(about.provenance)) {
                    KitProvenance.Told, KitProvenance.Calculated -> arrayOf<Any>(date)
                    else -> if (source == null) arrayOf<Any>(date) else arrayOf<Any>(source, date)
                },
            )
        KitSheet(
            visible = true,
            onDismissRequest = { explaining = null },
            title = bidiName(about.text),
            closeLabel = stringResource(Res.string.fbDismiss),
            icon = KitIcons.info,
            primary =
                if (state.canWrite) {
                    KitAction(stringResource(Res.string.fbThingEditFact), { explaining = null; editing = about }, icon = KitIcons.edit)
                } else null,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitText(words)
                if (about.waitingForOk) KitText(stringResource(Res.string.fbThingExplainWaiting))
            }
        }
    }
    val fact = editing
    KitInputOperationDialog(
        visible = fact != null,
        title = stringResource(Res.string.fbThingEditFact),
        fieldLabel = stringResource(Res.string.fbCareEditLabel),
        confirmLabel = stringResource(Res.string.fbThingEditFactSave),
        initial = fact?.text.orEmpty(),
        helper = stringResource(Res.string.fbThingOneLine),
        onSubmit = { text ->
            editing = null
            fact?.let { actions.editFact(it, text) }
        },
        onDismissRequest = { editing = null },
    )
    }
}

// ---- Search -------------------------------------------------------------------------------------------------------

/** Search results (F: search_screen.dart): every hit with its source and date. */
@Composable
public fun SearchScreen(state: SearchUi, actions: SearchActions) {
    val title = stringResource(Res.string.fbSearchTitle)
    val result = state.result
    val field = rememberTextFieldState(state.query)
    KitScreen(
        topBar = { BackBar(title, actions::back) },
        width = KitScreenWidth.Reading,
        loading = result == null && state.error == null,
        loadingLabel = title,
        header = {
            KitSearchField(
                label = stringResource(Res.string.fbSearchLabel),
                onSearch = {},
                onSubmit = actions::search,
                state = field,
                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
            )
        },
        edges = KitScreenEdges.Bleed,
    ) {
        when {
            state.error != null -> ErrorState(state.error) { actions.search(field.text.toString().ifBlank { state.query }) }
            result == null -> KitSkeletonRows()
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    item("count") {
                        Column(Modifier.padding(horizontal = gutter()), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                            KitText(
                                pluralStringResource(Res.plurals.fbSearchMatches, result.hits.size, result.hits.size, bidiName(state.query)),
                                role = TypeRole.Label,
                            )
                            KitText(stringResource(Res.string.fbSearchWordsOnly), role = TypeRole.Secondary, tone = TextTone.Secondary)
                        }
                    }
                    if (result.hits.isEmpty()) {
                        item("none") {
                            KitStateView(
                                KitStateKind.Empty,
                                stringResource(Res.string.fbSearchNoneTitle),
                                body = stringResource(Res.string.fbSearchNoneBody),
                                icon = KitIcons.search,
                            )
                        }
                    }
                    result.hits.forEach { hit ->
                        item(hit.factId) {
                            KitFactRow(
                                text = hit.snippet,
                                provenance = kitProvenanceOf(hit.provenance),
                                observed = observedWords(hit.observedAt),
                                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S1.dp),
                                source = sourceWords(hit.source),
                                title = bidiName(hit.title),
                                onOpen = { actions.openThing(hit.thingId) },
                            )
                        }
                    }
                    if (result.lockedNotesSkipped) item("locked") { KitGroupNote(stringResource(Res.string.fbSearchLocked)) }
                    if (result.hits.isNotEmpty()) item("each") { KitGroupNote(stringResource(Res.string.fbSearchEachHit)) }
                }
        }
    }
}

// ---- Capture ------------------------------------------------------------------------------------------------------

/** Add something (F: capture_screen.dart): a note or pasted text, where it goes, and Save at the thumb. */
@Composable
public fun CaptureScreen(state: CaptureUi, actions: CaptureActions) {
    val title = stringResource(Res.string.fbCaptureTitle)
    val text = rememberTextFieldState()
    LaunchedEffect(state.clearSeed) { if (state.clearSeed > 0) text.clearText() }
    val note = state.kind == CaptureKind.Note
    val thing = state.things.firstOrNull { it.id == state.thingId }.takeIf { note }
    KitScreen(
        topBar = { BackBar(title, actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.working,
        loadingLabel = title,
        bottom = {
            KitActionBlock(
                Modifier.fillMaxWidth().padding(horizontal = gutter(), vertical = Space.S2.dp),
                primary =
                    KitAction(
                        stringResource(Res.string.fbCaptureSave),
                        { actions.save(text.text.toString()) },
                        enabled =
                            if (text.text.isBlank()) KitEnabled.Disabled(stringResource(Res.string.fbCaptureNeedsText))
                            else KitEnabled.Enabled,
                        working = state.working,
                    ),
            )
        },
        edges = KitScreenEdges.Bleed,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("form") {
                Column(Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                    KitSegmented(
                        segments =
                            listOf(
                                KitSegment(CaptureKind.Note, stringResource(Res.string.fbCaptureNote)),
                                KitSegment(CaptureKind.Share, stringResource(Res.string.fbCapturePaste)),
                            ),
                        selected = state.kind,
                        semanticsLabel = stringResource(Res.string.fbCaptureKindLabel),
                        onChange = actions::kind,
                    )
                    KitField(
                        state = text,
                        label = stringResource(if (note) Res.string.fbCaptureNoteLabel else Res.string.fbCapturePasteLabel),
                        kind = KitFieldKind.Multiline,
                        placeholder = if (note) stringResource(Res.string.fbCaptureNoteHint) else null,
                    )
                }
            }
            item("where") {
                KitRowGroup {
                    if (note && state.things.isNotEmpty()) {
                        val none = stringResource(Res.string.fbCaptureAddToNone)
                        KitRow(
                            title = stringResource(Res.string.fbCaptureAddTo),
                            supporting = if (thing == null) null else stringResource(Res.string.fbCaptureAddToFactNote),
                            trailing =
                                KitRowTrailing.Menu(
                                    thing?.let { bidiName(it.title) } ?: none,
                                    listOf(KitMenuItem(none, { actions.thing(null) }, checked = thing == null)) +
                                        state.things.map { t -> KitMenuItem(bidiName(t.title), { actions.thing(t.id) }, checked = t.id == state.thingId) },
                                ),
                        )
                    }
                    if (thing == null && state.areas.isNotEmpty()) {
                        val auto = stringResource(Res.string.fbCaptureAreaAuto)
                        KitRow(
                            title = stringResource(Res.string.fbCaptureArea),
                            trailing =
                                KitRowTrailing.Menu(
                                    state.area?.let { areaName(it) } ?: auto,
                                    listOf(KitMenuItem(auto, { actions.area(null) }, checked = state.area == null)) +
                                        state.areas.map { slug -> KitMenuItem(areaName(slug), { actions.area(slug) }, checked = slug == state.area) },
                                ),
                        )
                    }
                }
            }
            if (note) {
                item("remind") {
                    KitRowGroup {
                        KitSwitchRow(
                            title = stringResource(Res.string.fbCaptureRemindToo),
                            checked = state.remind,
                            supporting = stringResource(Res.string.fbCaptureRemindTooDetail),
                            onCheckedChange = actions::remind,
                        )
                    }
                }
            }
            if (state.rulesSupported && thing == null) {
                item("rules") {
                    KitRowGroup {
                        KitSwitchRow(
                            title = stringResource(Res.string.fbCaptureRulesLook),
                            checked = state.rulesLook,
                            supporting = pluralStringResource(Res.plurals.fbCaptureRulesOn, state.rulesOn, state.rulesOn),
                            onCheckedChange = actions::rules,
                        )
                        KitRow(title = stringResource(Res.string.fbCaptureSeeRules), trailing = KitRowTrailing.Chevron, onClick = actions::seeRules)
                    }
                }
            }
            val notice = state.notice
            if (notice != null || state.error != null) {
                item("notice") {
                    val kept = notice == CaptureNotice.KeptOnPhone || notice == CaptureNotice.QueuedAtNode || notice == CaptureNotice.Full
                    val reminderWords =
                        when (val r = state.reminder) {
                            is CaptureReminder.Made ->
                                stringResource(Res.string.fbCaptureSavedReminder, r.timing?.let { timingWords(it) }.orEmpty())
                            CaptureReminder.Failed -> stringResource(Res.string.fbCaptureReminderFailed)
                            CaptureReminder.NotMade -> stringResource(Res.string.fbCaptureReminderNotMade)
                            null -> null
                        }
                    KitNotice(
                        message =
                            listOfNotNull(when (notice) {
                                CaptureNotice.Saved -> stringResource(Res.string.fbCaptureSaved)
                                CaptureNotice.QueuedAtNode -> stringResource(Res.string.fbCaptureQueuedNode)
                                CaptureNotice.KeptOnPhone -> stringResource(Res.string.fbCaptureKept)
                                CaptureNotice.Full -> stringResource(Res.string.fbCaptureFull)
                                CaptureNotice.FactAdded -> stringResource(Res.string.fbCaptureFactAdded, thing?.let { bidiName(it.title) }.orEmpty())
                                CaptureNotice.FactProposed -> stringResource(Res.string.fbCaptureFactProposed)
                                null -> failureWords(state.error)
                            }, reminderWords).joinToString(" "),
                        modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S3.dp),
                        tone = if (notice == null || kept) StatusTone.Attention else StatusTone.Ok,
                        actions =
                            when {
                                kept -> listOf(KitLineAction(stringResource(Res.string.fbCaptureSeeNotSaved), actions::seeNotSaved))
                                notice == CaptureNotice.FactProposed -> listOf(KitLineAction(stringResource(Res.string.fbCaptureSeeNeedsYou), actions::seeNeedsYou))
                                else -> emptyList()
                            },
                        dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                    )
                }
            }
            item("foot") { KitGroupNote(stringResource(Res.string.fbCaptureFootnote)) }
        }
    }
}

// ---- Not saved yet ------------------------------------------------------------------------------------------------

/** Notes not saved yet (F: not_saved_screen.dart): kept until the person discards them, with Retry and Discard. */
@Composable
public fun NotSavedScreen(state: NotSavedUi, actions: NotSavedActions) {
    val title = stringResource(Res.string.fbNotSavedTitle)
    KitSheetHost(Modifier.fillMaxSize()) {
    KitScreen(
        topBar = { BackBar(title, actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.working || state.load == FactsBookLoad.Loading,
        loadingLabel = title,
        edges = KitScreenEdges.Bleed,
        refresh = KitScreenRefresh(refreshing = false, onRefresh = actions::refresh, enabled = state.entries.isNotEmpty()),
    ) {
        // Notes kept on this phone show even when the node cannot be reached: they are what the person needs then.
        if (state.load == FactsBookLoad.Failed && state.entries.isEmpty()) {
            ErrorState(state.loadError, actions::refresh)
        } else if (state.entries.isEmpty()) {
            KitStateView(
                KitStateKind.Calm,
                stringResource(Res.string.fbNotSavedEmptyTitle),
                body = stringResource(Res.string.fbNotSavedEmptyBody),
                icon = KitIcons.checkCircle,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item("intro") {
                    KitText(
                        stringResource(Res.string.fbNotSavedIntro),
                        Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                }
                if (state.load == FactsBookLoad.Failed) {
                    item("unreachable") {
                        KitNotice(failureWords(state.loadError), Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp))
                    }
                }
                state.notice?.let { notice ->
                    item("notice") {
                        val saved = notice == NotSavedNotice.Saved
                        KitNotice(
                            stringResource(if (saved) Res.string.fbNotSavedSaved else Res.string.fbNotSavedStill),
                            Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
                            tone = if (saved) StatusTone.Ok else StatusTone.Neutral,
                            icon = if (saved) KitIcons.check else KitIcons.info,
                            dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                        )
                    }
                }
                state.error?.let { error ->
                    item("error") {
                        KitNotice(
                            failureWords(error),
                            Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
                            tone = StatusTone.Attention,
                            dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                        )
                    }
                }
                item("list") {
                    KitRowGroup(label = pluralStringResource(Res.plurals.fbNotSavedCount, state.entries.size, state.entries.size)) {
                        state.entries.forEach { EntryRow(it, state, actions) }
                    }
                }
                item("foot") { KitGroupNote(stringResource(Res.string.fbNotSavedFooter)) }
            }
        }
    }
    val confirm = state.confirmDiscard
    KitConfirm(
        spec =
            confirm?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.fbNotSavedDiscardTitle),
                    confirmLabel = stringResource(Res.string.fbNotSavedDiscard),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(if (it.onPhone) Res.string.fbNotSavedDiscardBody else Res.string.fbNotSavedDiscardBodyNode),
                )
            },
        onConfirm = actions::discard,
        onCancel = actions::cancelDiscard,
    )
    val export = state.export
    if (export != null) {
        KitSheet(
            visible = true,
            onDismissRequest = actions::closeExport,
            title = stringResource(Res.string.fbExportTitle),
            closeLabel = stringResource(Res.string.fbDismiss),
            icon = KitIcons.upload,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                KitText(stringResource(Res.string.fbExportBody), role = TypeRole.Secondary)
                KitText(stringResource(Res.string.fbExportObject), role = TypeRole.Label, tone = TextTone.Secondary)
                KitMonoText(export.text, maxLines = 12)
                KitCopyButton(stringResource(Res.string.fbExportCopy), stringResource(Res.string.kitCopied), { export.text })
            }
        }
    }
    }
}

@Composable
private fun EntryRow(entry: CouldNotSaveEntry, state: NotSavedUi, actions: NotSavedActions) {
    val age = kitRelativeAge(entry.createdAt.toEpochMilliseconds(), state.nowMs)
    val where = stringResource(if (entry.onPhone) Res.string.fbNotSavedOnPhone else Res.string.fbNotSavedAtNode)
    val due = entry.needsThirtyDayPrompt(kotlin.time.Instant.fromEpochMilliseconds(state.nowMs))
    val lines =
        listOfNotNull(
            reasonWords(entry, state.canUnlock),
            "$where · ${stringResource(Res.string.fbNotSavedSince, age)}",
            if (due) stringResource(Res.string.fbNotSavedThirtyDays) else null,
        )
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val text = entry.text
    KitRow(
        title = text?.let { bidiName(it) } ?: stringResource(Res.string.fbNotSavedNodeItem),
        titleMaxLines = 3,
        supporting = lines.joinToString("\n"),
        supportingMaxLines = 4,
        leading = KitRowLeading.Glyph(if (entry.onPhone) KitIcons.phone else KitIcons.server),
        trailing =
            KitRowTrailing.Menu(
                stringResource(Res.string.fbNotSavedTryNow),
                listOfNotNull(
                    // From day 30 the page asks Retry, Export or Discard (F: not_saved_screen.dart `_entry`).
                    KitMenuItem(stringResource(if (due) Res.string.fbNotSavedRetry else Res.string.fbNotSavedTryNow), { actions.retry(entry) }),
                    if (text != null) {
                        KitMenuItem(
                            stringResource(if (due) Res.string.fbNotSavedExport else Res.string.fbNotSavedCopy),
                            { clipboard.setText(AnnotatedString(text)) },
                        )
                    } else null,
                    if (!entry.onPhone && state.canExport) KitMenuItem(stringResource(Res.string.fbNotSavedExport), { actions.export(entry) }) else null,
                    KitMenuItem(stringResource(Res.string.fbNotSavedDiscard), { actions.askDiscard(entry) }, destructive = true),
                ),
            ),
    )
}

// ---- Connect ------------------------------------------------------------------------------------------------------

/** Set up FactsBook (F: connect_screen.dart): the node's private address and the one-time code from its console. */
@Composable
public fun ConnectScreen(state: ConnectUi, actions: ConnectActions) {
    val title = stringResource(Res.string.fbConnectTitle)
    val address = rememberTextFieldState()
    val code = rememberTextFieldState()
    val owner = rememberTextFieldState()
    KitScreen(
        topBar = { BackBar(title, actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.working,
        loadingLabel = title,
        bottom = {
            KitActionBlock(
                Modifier.fillMaxWidth().padding(horizontal = gutter(), vertical = Space.S2.dp),
                primary =
                    KitAction(
                        stringResource(Res.string.fbConnectButton),
                        { actions.connect(address.text.toString(), code.text.toString(), owner.text.toString()) },
                        enabled =
                            KitEnabled.of(
                                address.text.isNotBlank() && code.text.isNotBlank() && !state.working,
                                stringResource(Res.string.fbConnectNeeds),
                            ),
                        working = state.working,
                    ),
            )
        },
        edges = KitScreenEdges.Bleed,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("form") {
                Column(Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                    KitText(stringResource(Res.string.fbConnectIntro), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    KitField(address, stringResource(Res.string.fbConnectAddress), placeholder = stringResource(Res.string.fbConnectAddressHint), kind = KitFieldKind.Url)
                    KitField(code, stringResource(Res.string.fbConnectCode), helper = stringResource(Res.string.fbConnectCodeHelp), kind = KitFieldKind.Mono)
                    state.error?.let { error ->
                        // A refused code reads as "that code did not work", whatever the node calls it.
                        val refused = error.code == "forbidden_scope" || error.code == "unauthorized"
                        KitNotice(
                            if (refused) stringResource(Res.string.fbErrorPairCode) else failureWords(error),
                            title = stringResource(Res.string.fbConnectFailedTitle),
                            tone = StatusTone.Attention,
                            notes = listOfNotNull(failureDetails(error)),
                        )
                    }
                    KitSectionLabel(stringResource(Res.string.fbConnectOwnerTitle))
                    KitText(stringResource(Res.string.fbConnectOwnerBody), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    state.ownerKey?.takeIf { it.isNotEmpty() }?.let { key ->
                        KitDetailsFold(
                            label = stringResource(Res.string.fbConnectOwnerKey),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            notes = listOf(key),
                        )
                    }
                    KitField(owner, stringResource(Res.string.fbConnectOwnerId), kind = KitFieldKind.Mono)
                }
            }
        }
    }
}
