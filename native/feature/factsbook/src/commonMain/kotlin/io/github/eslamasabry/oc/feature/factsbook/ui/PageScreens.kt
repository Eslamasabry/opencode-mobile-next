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
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.Fact
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureReminder
import io.github.eslamasabry.oc.feature.factsbook.presentation.ConnectActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.ConnectUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedActions
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
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputOperationDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.action.KitCopyButton
import io.github.eslamasabry.oc.ui.kit.row.KitFactRow
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
    KitSheetHost(Modifier.fillMaxSize()) {
    KitScreen(
        topBar = { BackBar(thing?.let { bidiName(it.title) } ?: stringResource(Res.string.fbAboutYouTitle), actions::back, thing?.let { kindWords(it.kind) }) },
        width = KitScreenWidth.Reading,
        loading = state.working,
        loadingLabel = stringResource(Res.string.fbThingFacts),
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
                                        null -> failureWords(state.writeError)
                                    },
                                modifier = Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp),
                                tone = if (notice == null) StatusTone.Attention else StatusTone.Ok,
                                dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                            )
                        }
                    }
                    item("add") {
                        KitRowGroup {
                            KitRow(
                                title = stringResource(Res.string.fbThingAddFact, bidiName(thing.title)),
                                leading = KitRowLeading.Glyph(KitIcons.add, tile = true),
                                titleAccent = true,
                                onClick = { adding = true },
                            )
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
                                onOpen = { editing = fact },
                            )
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
    KitScreen(
        topBar = { BackBar(title, actions::back, state.query) },
        width = KitScreenWidth.Reading,
        loading = result == null && state.error == null,
        loadingLabel = title,
        edges = KitScreenEdges.Bleed,
    ) {
        when {
            state.error != null -> ErrorState(state.error) { actions.search(state.query) }
            result == null -> KitSkeletonRows()
            result.hits.isEmpty() ->
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.fbSearchNoneTitle),
                    body = stringResource(Res.string.fbSearchNoneBody),
                    icon = KitIcons.search,
                )
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    if (result.lockedNotesSkipped) {
                        item("locked") {
                            KitNotice(stringResource(Res.string.fbSearchLocked), Modifier.padding(horizontal = gutter(), vertical = Space.S2.dp))
                        }
                    }
                    item("count") { KitGroupNote(pluralStringResource(Res.plurals.fbSearchMatches, result.hits.size, result.hits.size, state.query)) }
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
                    item("words") { KitGroupNote(stringResource(Res.string.fbSearchWordsOnly)) }
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
                        error = if (state.needsText) stringResource(Res.string.fbCaptureNeedsText) else null,
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
                        actions = if (kept) listOf(KitLineAction(stringResource(Res.string.fbCaptureSeeNotSaved), actions::seeNotSaved)) else emptyList(),
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
        loading = state.working,
        loadingLabel = title,
        edges = KitScreenEdges.Bleed,
    ) {
        if (state.entries.isEmpty()) {
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
    KitRow(
        title = entry.text?.lineSequence()?.firstOrNull()?.take(80) ?: stringResource(Res.string.fbNotSavedNodeItem),
        supporting = lines.joinToString("\n"),
        supportingMaxLines = 4,
        leading = KitRowLeading.Glyph(if (entry.onPhone) KitIcons.phone else KitIcons.server),
        trailing =
            KitRowTrailing.Menu(
                stringResource(Res.string.fbNotSavedTryNow),
                listOfNotNull(
                    KitMenuItem(stringResource(Res.string.fbNotSavedRetry), { actions.retry(entry) }),
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
                    KitField(address, stringResource(Res.string.fbConnectAddress), placeholder = stringResource(Res.string.fbConnectAddressHint))
                    KitField(code, stringResource(Res.string.fbConnectCode), helper = stringResource(Res.string.fbConnectCodeHelp))
                    state.error?.let { error ->
                        // A refused code reads as "that code did not work", whatever the node calls it.
                        val refused = error.code == "forbidden_scope" || error.code == "unauthorized"
                        KitNotice(
                            if (refused) stringResource(Res.string.fbErrorPairCode) else failureWords(error),
                            title = stringResource(Res.string.fbConnectFailedTitle),
                            tone = StatusTone.Attention,
                        )
                    }
                    KitSectionLabel(stringResource(Res.string.fbConnectOwnerTitle))
                    KitText(stringResource(Res.string.fbConnectOwnerBody), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    state.ownerKey?.takeIf { it.isNotEmpty() }?.let { key ->
                        KitRowGroup {
                            KitRow(
                                title = stringResource(Res.string.fbConnectOwnerKey),
                                supporting = key,
                                supportingMaxLines = 3,
                                trailing = KitRowTrailing.Copy(key, stringResource(Res.string.fbConnectOwnerKey)),
                            )
                        }
                    }
                    KitField(owner, stringResource(Res.string.fbConnectOwnerId))
                }
            }
        }
    }
}
