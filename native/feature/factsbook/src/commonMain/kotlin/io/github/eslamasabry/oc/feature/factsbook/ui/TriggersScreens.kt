package io.github.eslamasabry.oc.feature.factsbook.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.EventTrust
import io.github.eslamasabry.oc.engine.factsbook.Rule
import io.github.eslamasabry.oc.engine.factsbook.RuleTestResult
import io.github.eslamasabry.oc.engine.factsbook.TriggerEvent
import io.github.eslamasabry.oc.engine.factsbook.TriggerReason
import io.github.eslamasabry.oc.engine.factsbook.TriggerSourceKind
import io.github.eslamasabry.oc.engine.factsbook.TriggersLoad
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
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
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun railGutter() = LocalKitTokens.current.gutter.dp

@Composable
private fun Bar(title: String, onBack: () -> Unit, subtitle: String? = null) {
    KitTopBar(title = title, subtitle = subtitle, exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = onBack)
}

@Composable
private fun Failed(error: FactsBookError?, retry: () -> Unit) {
    val details = failureDetails(error)
    KitStateView(
        KitStateKind.Error,
        stringResource(Res.string.fbErrorTitle),
        body = failureWords(error) + (details?.let { "\n\n$it" } ?: ""),
        primary = KitAction(stringResource(Res.string.commonRetry), retry),
    )
}

@Composable
private fun OwnerNotice(message: String, setUp: () -> Unit) {
    KitNotice(
        message,
        Modifier.padding(horizontal = railGutter(), vertical = Space.S2.dp),
        icon = KitIcons.info,
        actions = listOf(KitLineAction(stringResource(Res.string.fbCareSetupApproval), setUp)),
    )
}

@Composable
private fun Said(message: String, good: Boolean, dismiss: () -> Unit) {
    KitNotice(
        message,
        Modifier.padding(horizontal = railGutter(), vertical = Space.S2.dp),
        icon = if (good) KitIcons.check else KitIcons.info,
        tone = if (good) StatusTone.Ok else StatusTone.Attention,
        dismiss = KitLineAction(stringResource(Res.string.fbDismiss), dismiss),
    )
}

// ---- Triggers -----------------------------------------------------------------------------------------------------

/** Triggers (F: triggers_screen.dart). */
@Composable
public fun TriggersScreen(state: TriggersUi, actions: TriggersActions) {
    val title = stringResource(Res.string.fbTriggersTitle)
    val ready = state.load == TriggersLoad.Ready
    KitScreen(
        topBar = { Bar(title, actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.load == TriggersLoad.Loading,
        loadingLabel = title,
        bottom =
            if (!ready) null
            else {
                {
                    KitActionBlock(
                        Modifier.fillMaxWidth().padding(horizontal = railGutter(), vertical = Space.S2.dp),
                        primary = KitAction(stringResource(Res.string.fbTriggersNew), actions::newRule, icon = KitIcons.add),
                    )
                }
            },
        edges = KitScreenEdges.Bleed,
    ) {
        when (state.load) {
            TriggersLoad.Unavailable ->
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.fbTriggersUnavailableTitle),
                    body = stringResource(Res.string.fbTriggersUnavailableBody),
                    icon = KitIcons.sparkle,
                )
            TriggersLoad.Failed -> Failed(state.error, actions::refresh)
            TriggersLoad.Ready ->
                KitRefresh(state.refreshing, actions::refresh, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) { triggerList(state, actions) }
                }
            else -> KitSkeletonRows()
        }
    }
}

private fun LazyListScope.triggerList(state: TriggersUi, actions: TriggersActions) {
    if (!state.canApprove) item("owner") { OwnerNotice(stringResource(Res.string.fbRuleNeedsOwner), actions::setUpApproval) }
    if (state.notice != null || state.failure != null) {
        item("notice") {
            Said(
                when (state.notice) {
                    TriggersNotice.Paused -> stringResource(Res.string.fbRulePausedDone)
                    TriggersNotice.Resumed -> stringResource(Res.string.fbRuleResumedDone)
                    TriggersNotice.Stale -> stringResource(Res.string.fbRuleStale)
                    null -> failureWords(state.failure)
                },
                good = state.notice == TriggersNotice.Paused || state.notice == TriggersNotice.Resumed,
                dismiss = actions::dismiss,
            )
        }
    }
    if (state.groups.isEmpty()) {
        item("empty") {
            KitStateView(
                KitStateKind.Empty,
                stringResource(Res.string.fbTriggersEmptyTitle),
                body = stringResource(Res.string.fbTriggersEmptyBody),
                icon = KitIcons.sparkle,
            )
        }
    }
    state.groups.forEach { group ->
        item(group.kind.wire) {
            KitRowGroup(label = sourceGroupWords(group.kind, group.rules.size)) { group.rules.forEach { RuleRow(it, state, actions) } }
        }
    }
    if (state.events.isNotEmpty()) {
        item("recent") {
            KitRowGroup(label = stringResource(Res.string.fbTriggersRecent)) { state.events.forEach { EventRow(it, actions) } }
        }
    }
    item("foot") { KitGroupNote(stringResource(Res.string.fbTriggersFootnote)) }
}

@Composable
private fun RuleRow(rule: Rule, state: TriggersUi, actions: TriggersActions) {
    val d = rule.definition
    val activity = state.activity[rule.id]
    val week =
        activity?.let {
            if (it.offers > 0) pluralStringResource(Res.plurals.fbRuleWeekOffers, it.offers, it.proposed, it.ignored, it.offers)
            else stringResource(Res.string.fbRuleWeek, it.proposed, it.ignored)
        }
    val name = bidiName(d.name)
    val busy = rule.id in state.working
    KitRow(
        title = name,
        supporting = listOfNotNull(ruleLine(d), week).joinToString("\n"),
        supportingMaxLines = 4,
        leading = KitRowLeading.Glyph(KitIcons.sparkle),
        state = if (d.enabled) null else KitRowState(stringResource(Res.string.fbRulePaused), KitRowStateTone.Quiet),
        trailing =
            KitRowTrailing.IconAction(
                KitLineAction(
                    stringResource(if (d.enabled) Res.string.fbRulePause else Res.string.fbRuleResume, name),
                    { actions.togglePause(rule) },
                    enabled = state.canApprove && !busy,
                    disabledReason = if (state.canApprove) null else stringResource(Res.string.fbCareNeedsOwnerShort),
                    icon = if (d.enabled) KitIcons.pause else KitIcons.play,
                )
            ),
        onClick = { actions.openRule(rule) },
    )
}

@Composable
private fun EventRow(event: TriggerEvent, actions: TriggersActions) {
    KitRow(
        title = stringResource(Res.string.fbEventWhen, sourceKindWords(event.source.kind), whenWords(event.capturedAt)),
        supporting = triggerReasonWords(event.reason),
        supportingMaxLines = 3,
        leading = KitRowLeading.Glyph(KitIcons.article),
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.openEvent(event) },
    )
}

// ---- Rule editor --------------------------------------------------------------------------------------------------

/** The rule editor (F: rule_editor_screen.dart). */
@Composable
public fun RuleEditorScreen(state: RuleEditorUi, actions: RuleEditorActions) {
    val saved = state.saved
    // A new rule starts in the app's language (F: rule_editor_screen.dart initState).
    val appIsArabic = Locale.current.language == "ar"
    var languageSet by remember { mutableStateOf(false) }
    LaunchedEffect(state.formSeed, state.canApprove) {
        if (!languageSet && state.formSeed > 0 && state.canApprove && saved == null) {
            languageSet = true
            if (appIsArabic && state.form.locale != "ar") actions.edit { it.copy(locale = "ar") }
        }
    }
    val owner = if (state.canApprove) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.fbCareNeedsOwnerShort))
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                Bar(
                    stringResource(if (saved == null) Res.string.fbRuleNewTitle else Res.string.fbRuleEditTitle),
                    actions::back,
                    saved?.let { bidiName(it.definition.name) },
                )
            },
            width = KitScreenWidth.Reading,
            loading = state.saving,
            loadingLabel = stringResource(Res.string.fbRuleSave),
            bottom = {
                KitActionBlock(
                    Modifier.fillMaxWidth().padding(horizontal = railGutter(), vertical = Space.S2.dp),
                    primary =
                        KitAction(
                            stringResource(Res.string.fbRuleSave),
                            actions::save,
                            enabled = if (!state.canApprove) owner else KitEnabled.of(state.dirty, stringResource(Res.string.fbRuleSaved)),
                            working = state.saving,
                        ),
                )
            },
            edges = KitScreenEdges.Bleed,
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (!state.canApprove) item("owner") { OwnerNotice(stringResource(Res.string.fbRuleNeedsOwner), actions::setUpApproval) }
                if (state.notice != null || state.failure != null) {
                    item("notice") {
                        Said(
                            when (state.notice) {
                                RuleNotice.Saved -> stringResource(Res.string.fbRuleSaved)
                                RuleNotice.Stale -> stringResource(Res.string.fbRuleStale)
                                null -> failureWords(state.failure)
                            },
                            good = state.notice == RuleNotice.Saved,
                            dismiss = actions::dismiss,
                        )
                    }
                }
                item("name") { NameAndSource(state, actions, owner) }
                item("says") { Says(state, actions, owner) }
                item("then") { Then(state, actions, owner) }
                item("test") { TestSection(state, actions) }
                if (saved != null) {
                    item("delete") {
                        KitButton(
                            KitAction(stringResource(Res.string.fbRuleDelete), actions::askDelete, enabled = owner, icon = KitIcons.delete, destructive = true),
                            Modifier.padding(horizontal = railGutter(), vertical = Space.S4.dp),
                            role = KitButtonRole.Secondary,
                        )
                    }
                }
            }
        }
        KitConfirm(
            spec =
                if (!state.confirmDelete || saved == null) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.fbRuleDeleteTitle),
                        confirmLabel = stringResource(Res.string.fbRuleDelete),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.fbRuleDeleteBody, bidiName(saved.definition.name)),
                    ),
            onConfirm = actions::delete,
            onCancel = actions::cancelDelete,
        )
    }
}

@Composable
private fun NameAndSource(state: RuleEditorUi, actions: RuleEditorActions, owner: KitEnabled) {
    val name = rememberTextFieldState(state.form.name)
    LaunchedEffect(state.formSeed) { if (name.text.toString() != state.form.name) name.setTextAndPlaceCursorAtEnd(state.form.name) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitField(
            name,
            stringResource(Res.string.fbRuleNameLabel),
            Modifier.padding(horizontal = railGutter()),
            placeholder = stringResource(Res.string.fbRuleNameHint),
            enabled = owner,
            onTextChanged = { text -> actions.edit { it.copy(name = text) } },
        )
        KitSectionLabel(stringResource(Res.string.fbRuleWhen))
        KitSegmented(
            segments =
                listOf(
                    KitSegment(TriggerSourceKind.Share, stringResource(Res.string.fbSourceKindShare)),
                    KitSegment(TriggerSourceKind.ChatSelection, stringResource(Res.string.fbSourceKindChat)),
                    KitSegment(TriggerSourceKind.Note, stringResource(Res.string.fbSourceKindNote)),
                ),
            selected = state.form.kind,
            semanticsLabel = stringResource(Res.string.fbRuleWhenLabel),
            modifier = Modifier.padding(horizontal = railGutter()),
            enabled = owner,
            onChange = { kind -> actions.edit { it.copy(kind = kind) } },
        )
    }
}

@Composable
private fun Says(state: RuleEditorUi, actions: RuleEditorActions, owner: KitEnabled) {
    val words = rememberTextFieldState(state.form.words)
    val sender = rememberTextFieldState(state.form.sender)
    LaunchedEffect(state.formSeed) {
        if (words.text.toString() != state.form.words) words.setTextAndPlaceCursorAtEnd(state.form.words)
        if (sender.text.toString() != state.form.sender) sender.setTextAndPlaceCursorAtEnd(state.form.sender)
    }
    val form = state.form
    val en = stringResource(Res.string.fbRuleLangEn)
    val ar = stringResource(Res.string.fbRuleLangAr)
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitSectionLabel(stringResource(Res.string.fbRuleSays))
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.fbRuleTemplateLabel),
                enabled = owner.isEnabled,
                disabledReason = owner.reason,
                trailing =
                    KitRowTrailing.Menu(
                        templateWords(form.template),
                        (state.templates + form.template).distinct().map { id ->
                            KitMenuItem(templateWords(id), { actions.edit { it.copy(template = id) } }, checked = id == form.template)
                        },
                        title = stringResource(Res.string.fbRuleTemplateLabel),
                    ),
            )
            KitRow(
                title = stringResource(Res.string.fbRuleLanguageLabel),
                enabled = owner.isEnabled,
                disabledReason = owner.reason,
                trailing =
                    KitRowTrailing.Menu(
                        if (form.locale == "ar") ar else en,
                        listOf(
                            KitMenuItem(en, { actions.edit { it.copy(locale = "en") } }, checked = form.locale != "ar"),
                            KitMenuItem(ar, { actions.edit { it.copy(locale = "ar") } }, checked = form.locale == "ar"),
                        ),
                        title = stringResource(Res.string.fbRuleLanguageLabel),
                    ),
            )
        }
        KitGroupNote(stringResource(Res.string.fbRuleTemplateHelp))
        KitField(
            words,
            stringResource(Res.string.fbRuleWordsLabel),
            Modifier.padding(horizontal = railGutter()),
            helper = stringResource(Res.string.fbRuleWordsHelp),
            enabled = owner,
            onTextChanged = { text -> actions.edit { it.copy(words = text) } },
        )
        KitField(
            sender,
            stringResource(Res.string.fbRuleSenderLabel),
            Modifier.padding(horizontal = railGutter()),
            helper = stringResource(Res.string.fbRuleSenderHelp),
            enabled = owner,
            onTextChanged = { text -> actions.edit { it.copy(sender = text) } },
        )
    }
}

@Composable
private fun Then(state: RuleEditorUi, actions: RuleEditorActions, owner: KitEnabled) {
    val form = state.form
    Column {
        KitSectionLabel(stringResource(Res.string.fbRuleThen))
        KitRowGroup {
            KitSwitchRow(
                title = stringResource(Res.string.fbRuleOutcomeReminder),
                checked = form.reminder,
                supporting = stringResource(Res.string.fbRuleOutcomeReminderDetail),
                enabled = owner,
                onCheckedChange = { on -> actions.edit { it.copy(reminder = on) } },
            )
            KitSwitchRow(
                title = stringResource(Res.string.fbRuleOutcomeConversation),
                checked = form.conversation,
                supporting = stringResource(Res.string.fbRuleOutcomeConversationDetail),
                enabled =
                    when {
                        !state.canApprove -> owner
                        !state.offersSupported -> KitEnabled.Disabled(stringResource(Res.string.fbRuleOfferUnavailable))
                        else -> KitEnabled.Enabled
                    },
                onCheckedChange = { on -> actions.edit { it.copy(conversation = on) } },
            )
            // The contract allows only false in this milestone; the reason says why.
            KitSwitchRow(
                title = stringResource(Res.string.fbRuleAutoStart),
                checked = false,
                enabled = KitEnabled.Disabled(stringResource(Res.string.fbRuleAutoStartOff)),
                onCheckedChange = {},
            )
        }
        if (form.kept.isNotEmpty()) KitGroupNote(stringResource(Res.string.fbRuleOutcomeFactKept))
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.fbRuleAreaLabel),
                enabled = owner.isEnabled,
                disabledReason = owner.reason,
                trailing =
                    KitRowTrailing.Menu(
                        areaName(form.area),
                        (state.areas + form.area).distinct().map { slug ->
                            KitMenuItem(areaName(slug), { actions.edit { it.copy(area = slug) } }, checked = slug == form.area)
                        },
                        title = stringResource(Res.string.fbRuleAreaLabel),
                    ),
            )
            KitRow(
                title = stringResource(Res.string.fbRuleLimit),
                enabled = owner.isEnabled,
                disabledReason = owner.reason,
                trailing =
                    KitRowTrailing.Menu(
                        pluralStringResource(Res.plurals.fbRuleLimitValue, form.limit.toInt(), form.limit.toInt()),
                        (listOf(1L, 3L, 5L, 10L, 20L) + form.limit).distinct().sorted().map { n ->
                            KitMenuItem(
                                pluralStringResource(Res.plurals.fbRuleLimitValue, n.toInt(), n.toInt()),
                                { actions.edit { it.copy(limit = n) } },
                                checked = n == form.limit,
                            )
                        },
                        title = stringResource(Res.string.fbRuleLimit),
                    ),
            )
            KitSwitchRow(
                title = stringResource(Res.string.fbRuleOn),
                checked = form.enabled,
                supporting = stringResource(Res.string.fbRuleOnDetail),
                enabled = owner,
                onCheckedChange = { on -> actions.edit { it.copy(enabled = on) } },
            )
        }
        state.problem?.let { problem ->
            KitNotice(problemWords(problem), Modifier.padding(horizontal = railGutter(), vertical = Space.S3.dp), icon = KitIcons.info)
        }
    }
}

@Composable
private fun TestSection(state: RuleEditorUi, actions: RuleEditorActions) {
    val sample = rememberTextFieldState(state.sample)
    LaunchedEffect(state.sample) { if (sample.text.toString() != state.sample) sample.setTextAndPlaceCursorAtEnd(state.sample) }
    val reason = state.testProblem?.let { problemWords(it) }
    val canTest = if (reason == null) KitEnabled.Enabled else KitEnabled.Disabled(reason)
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitSectionLabel(stringResource(Res.string.fbRuleTest))
        Column(Modifier.padding(horizontal = railGutter()), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitField(
                sample,
                stringResource(Res.string.fbRuleSampleLabel),
                kind = KitFieldKind.Multiline,
                enabled = canTest,
                onTextChanged = actions::sample,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitButton(KitAction(stringResource(Res.string.fbRuleSampleExample), actions::useExample, enabled = canTest), role = KitButtonRole.Secondary)
                KitButton(
                    KitAction(
                        stringResource(if (state.result == null) Res.string.fbRuleTestRun else Res.string.fbRuleTestAgain),
                        actions::test,
                        enabled = if (reason != null) canTest else KitEnabled.of(state.sample.isNotBlank(), stringResource(Res.string.fbRuleSampleLabel)),
                        working = state.testing,
                    ),
                    role = KitButtonRole.Secondary,
                )
            }
            KitText(stringResource(Res.string.fbRuleTestDraftNote), role = TypeRole.Caption, tone = TextTone.Secondary)
            state.testFailure?.let { KitNotice(failureWords(it), icon = KitIcons.info) }
            state.result?.let { TestResult(it, state) }
        }
    }
}

@Composable
private fun TestResult(result: RuleTestResult, state: RuleEditorUi) {
    if (!result.matched) {
        KitNotice("${stringResource(Res.string.fbRuleTestNoMatch)} ${triggerReasonWords(result.reason)}", icon = KitIcons.info)
        return
    }
    val what = result.summary
    val found =
        if (what == null) stringResource(Res.string.fbRuleTestMatchPlain)
        else stringResource(Res.string.fbRuleTestMatch, bidiName(what), result.whenText?.let { isoWhenWords(it) }.orEmpty())
    val outcomes = outcomesLine(state.form.definition("").outcomes)
    KitNotice(
        if (outcomes.isEmpty()) found else "$found ${stringResource(Res.string.fbRuleTestWould, outcomes)}",
        icon = KitIcons.check,
        tone = StatusTone.Ok,
    )
}

// ---- What happened to this message --------------------------------------------------------------------------------

/** "What happened to this message" (F: event_detail_screen.dart). */
@Composable
public fun EventScreen(state: EventUi, actions: EventActions) {
    val title = stringResource(Res.string.fbEventTitle)
    val event = state.event
    KitScreen(
        topBar = {
            Bar(title, actions::back, event?.let { stringResource(Res.string.fbEventWhen, sourceKindWords(it.source.kind), whenWords(it.capturedAt)) })
        },
        width = KitScreenWidth.Reading,
        loading = state.loading && event != null,
        loadingLabel = title,
        refresh = if (event != null) KitScreenRefresh(refreshing = state.loading, onRefresh = actions::reload) else null,
        edges = KitScreenEdges.Bleed,
    ) {
        when {
            event == null && state.error == null -> KitSkeletonRows()
            event == null -> Failed(state.error, actions::reload)
            else -> LazyColumn(Modifier.fillMaxSize()) { eventDetail(state, event) }
        }
    }
}

private fun LazyListScope.eventDetail(state: EventUi, event: TriggerEvent) {
    item("outcome") {
        // The outcome comes first and says why once: what was suggested, or the one reason nothing was.
        val proposed =
            state.proposals.map { p ->
                val what = bidiName(p.preview.what)
                p.preview.at?.let { stringResource(Res.string.fbCareProposalWhen, what, isoWhenWords(it.toString())) } ?: what
            } + state.offers.filter { it.state == "offered" }.map { bidiName(it.summary) } + state.facts.mapNotNull { it.text?.let { t -> bidiName(t) } }
        val outcome = if (proposed.isEmpty()) triggerReasonWords(event.reason) else pluralStringResource(Res.plurals.fbEventOutcomeProposed, proposed.size, proposed.size)
        Column {
            KitRowGroup { KitRow(title = outcome, titleMaxLines = 4) }
            if (proposed.isNotEmpty()) {
                KitRowGroup(label = stringResource(Res.string.fbEventProposed)) {
                    proposed.forEach { KitRow(title = it, titleMaxLines = 4, supporting = stringResource(Res.string.fbEventWaitingForOk)) }
                }
            }
            if (state.firings.isNotEmpty()) {
                KitRowGroup(label = stringResource(Res.string.fbEventRule)) {
                    state.firings.forEach { f -> KitRow(title = state.ruleNames[f.id]?.let { bidiName(it) } ?: templateWords(f.template.id)) }
                }
            }
            KitRowGroup(label = stringResource(Res.string.fbEventFrom)) {
                KitRow(title = sourceKindWords(event.source.kind), supporting = whenWords(event.capturedAt))
                event.claims.sender?.let { KitRow(title = stringResource(Res.string.fbEventSender), supporting = bidiName(it)) }
                event.claims.app?.let { KitRow(title = stringResource(Res.string.fbEventApp), supporting = bidiName(it)) }
                KitRow(title = stringResource(Res.string.fbEventTrust), supporting = trustWords(event.trust))
            }
            val collapsed = state.firings.sumOf { it.collapsedCount }.toInt()
            val aside =
                listOfNotNull(
                    if (event.sensitive && event.reason != TriggerReason.SensitiveUnavailable) stringResource(Res.string.fbReasonUncertain) else null,
                    if (collapsed > 0) pluralStringResource(Res.plurals.fbEventCollapsed, collapsed, collapsed) else null,
                ).filter { it != outcome }
            if (aside.isNotEmpty()) {
                KitRowGroup(label = stringResource(Res.string.fbEventIgnored)) { aside.forEach { KitRow(title = it, titleMaxLines = 4) } }
            }
            if (event.trust != EventTrust.UserAuthored) {
                KitNotice(
                    stringResource(Res.string.fbEventUnverified),
                    Modifier.padding(horizontal = railGutter(), vertical = Space.S3.dp),
                    icon = KitIcons.shield,
                )
            }
            KitGroupNote(stringResource(Res.string.fbEventFootnote))
        }
    }
}
