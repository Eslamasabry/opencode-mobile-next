package io.github.eslamasabry.oc.feature.phonesetup.ui

import io.github.eslamasabry.oc.ui.kit.wait.KitWaitKind
import io.github.eslamasabry.oc.ui.kit.wait.KitWaitMarkHere
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.kit.setup.*
import androidx.compose.foundation.rememberScrollState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.RingCostPart
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupRingCost
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.layout
import io.github.eslamasabry.oc.ui.kit.row.KitLockedRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryOpen
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupMaker
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKill
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKillMessage
import androidx.compose.foundation.layout.Arrangement
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectNameProblem
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNodeCannotRun
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeConnectComputer
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeReportFailure
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/** One durable setup, with dependency-aware first-install and add-tools choices. */
@Composable
public fun SetupJourneyScreen(state: SetupJourneyUi, actions: SetupJourneyActions) {
    LifecycleStartEffect(actions) {
        actions.retry()
        onStopOrDispose {}
    }
    val job = state.progress
    val idle = job.state == SetupState.Idle
    val title =
        when {
            state.customizing && state.adding -> Res.string.phoneSetupStartAddTitle
            state.customizing -> Res.string.phoneSetupStartCustomizeTitle
            idle -> Res.string.phoneSetupStartScreenTitle
            else -> Res.string.phoneSetupStartScreenTitle
        }
    val wide = LocalKitWindow.current.cls.isWide
    val wideJob = wide && !state.checking && journeyBody(state) == JourneyBody.Job
    val beforeJob = idle || state.customizing || state.checking
    val barTitle = journeyTitle(state)
    KitScreen(
        topBar = {
            KitTopBar(
                if (wideJob) "" else barTitle,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = if (wideJob) KitScreenWidth.Full else KitScreenWidth.Reading,
        loading = state.checking,
        bottom = if (beforeJob || wideJob) null else ({ JobAction(state, actions) }),
        edges = KitScreenEdges.Bleed,
    ) {
        if (state.checking) {
            // C12: the checking page says what it is doing instead of drawing nothing under the progress line.
            KitStateView(kind = KitStateKind.Loading, title = stringResource(Res.string.phoneSetupStartChecking))
        } else {
            when (journeyBody(state)) {
                JourneyBody.Nothing -> Unit
                JourneyBody.Hero -> StartHero(state, actions)
                JourneyBody.Job -> Job(state, actions, wide = wideJob, pageTitle = barTitle)
            }
        }
        KitSheet(
            visible = state.customizing,
            onDismissRequest = actions::back,
            title = stringResource(title),
            icon = KitIcons.download,
            dismissible = !state.working,
        ) {
            Choices(state, actions)
            if (state.failure != null) KitErrorNotice(stringResource(Res.string.r40SetupCouldNotCheck),
                retry = KitLineAction(stringResource(Res.string.r40SetupRetry), actions::retry))
        }
        KitSheet(
            visible = state.confirmStop != null,
            onDismissRequest = actions::cancelConfirm,
            title = stringResource(Res.string.phoneSetupProgressStopTitle),
            dismissible = !state.working,
            primary =
                KitAction(
                    stringResource(Res.string.phoneSetupProgressStopConfirm),
                    actions::confirmStop,
                    working = state.working,
                ),
            secondary =
                KitAction(
                    stringResource(Res.string.phoneSetupProgressKeepGoing),
                    actions::cancelConfirm,
                ),
        ) {
            KitText(stringResource(Res.string.phoneSetupProgressStopMessage))
            if (state.failure != null)
                KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
        }
        KitSheet(
            visible = state.confirmRemoveVoice,
            onDismissRequest = actions::cancelConfirm,
            title = stringResource(Res.string.r40SetupRemoveVoiceTitle),
            dismissible = !state.working,
            primary =
                KitAction(
                    stringResource(Res.string.r40SetupRemoveVoice),
                    actions::confirmRemoveVoice,
                    destructive = true,
                    working = state.working,
                ),
            secondary =
                KitAction(stringResource(Res.string.r40SetupKeepVoice), actions::cancelConfirm),
        ) {
            KitText(stringResource(Res.string.r40SetupRemoveVoiceBody))
            state.options
                ?.components
                ?.firstOrNull { it.id == SetupComponentIds.VOICE }
                ?.installedBytes
                ?.let {
                    KitText(journeySize(it))
                }
            if (state.failure != null)
                KitErrorNotice(stringResource(Res.string.r40SetupRemoveVoiceFailed))
        }
    }
}

/** The persisted job names its additions, in job order, even after the app is reopened. */
@Composable
private fun journeyTitle(state: SetupJourneyUi): String {
    if (state.adding && state.progress.adding.isEmpty())
        return stringResource(Res.string.settingsV3ThisPhone)
    if (state.progress.adding.isEmpty())
        return stringResource(Res.string.phoneSetupStartScreenTitle)
    // F: lib/builtin/setup/setup_engine/progress_helpers.dart:335-356
    val names =
        state.progress.adding.flatMap { id ->
            state.options
                ?.components
                .orEmpty()
                .filter { it.id == id }
                .map {
                    if (id == SetupComponentIds.ESSENTIALS)
                        stringResource(Res.string.phoneSetupEssentialsShort)
                    else itemTitle(id, it.shortTitle)
                }
        }
    val joined =
        when (names.size) {
            0 -> ""
            1 -> names.single()
            else ->
                stringResource(
                    Res.string.phoneSetupStartListPair,
                    names
                        .dropLast(1)
                        .joinToString(stringResource(Res.string.phoneSetupStartListSeparator)),
                    names.last(),
                )
        }
    return stringResource(Res.string.aiteamComponentAddingTitle, joined)
}

@Composable
private fun StartHero(state: SetupJourneyUi, actions: SetupJourneyActions) {
    val names = state.install.filter { !it.required || it.id in setOf(SetupComponentIds.ESSENTIALS, SetupComponentIds.NODE) }
        .filter { it.id != SetupComponentIds.LINUX && it.id != SetupComponentIds.OPENCODE }
        .map { if (it.id == SetupComponentIds.ESSENTIALS) stringResource(Res.string.phoneSetupEssentialsShort) else itemTitle(it.id, it.shortTitle) }
    val includes = if (names.isEmpty()) null else {
        val joined = if (names.size == 1) names.single() else stringResource(Res.string.phoneSetupStartListPair,
            names.dropLast(1).joinToString(stringResource(Res.string.phoneSetupStartListSeparator)), names.last())
        stringResource(Res.string.phoneSetupStartIncludes, joined)
    }
    val check = state.preflight
    val blocked = check?.supported == false
    val title = when (check) {
        is SetupPreflightResult.UnsupportedAbi -> stringResource(Res.string.phoneSetupPreflightUnsupportedHeadline)
        is SetupPreflightResult.LowMemory -> stringResource(Res.string.phoneSetupPreflightLowMemoryHeadline)
        is SetupPreflightResult.LowSpace -> stringResource(Res.string.phoneSetupPreflightLowSpaceHeadline)
        else -> stringResource(Res.string.phoneSetupStartHeadline)
    }
    val body = when (check) {
        is SetupPreflightResult.UnsupportedAbi -> stringResource(Res.string.phoneSetupPreflightUnsupportedBody, check.reportedAbi)
        is SetupPreflightResult.LowMemory -> stringResource(Res.string.phoneSetupPreflightLowMemoryBody, Numbers.format(SetupPreflight.MINIMUM_MEMORY_MB), Numbers.format(check.totalMemoryMb))
        is SetupPreflightResult.LowSpace -> stringResource(Res.string.phoneSetupPreflightLowSpaceBody, journeySize(check.bytesToFree))
        else -> if (state.bytes > 0) stringResource(Res.string.phoneSetupStartPromise, journeySize(state.bytes))
            else stringResource(Res.string.phoneSetupStartPromiseNoSize)
    }
    KitSetupHero(
        title = title,
        body = body,
        primary = KitAction(stringResource(Res.string.phoneSetupStartSetUp),
            onClick = actions::start, working = state.working,
            enabled = KitEnabled.of(state.canConfirm, stringResource(Res.string.r40SetupCheckDevice))),
        tertiary = buildList {
            if (check is SetupPreflightResult.LowSpace) add(KitAction(stringResource(Res.string.phoneSetupPreflightOpenStorage), actions::openStorage))
            if (includes != null && check !is SetupPreflightResult.UnsupportedAbi && check !is SetupPreflightResult.LowMemory)
                add(KitAction(stringResource(Res.string.phoneSetupStartCustomize), actions::customize))
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                // No minutes promise: the registry's sum ("about 4 min") ran past 9 minutes on a real phone, and
                // the running page has its own honest "time left". The steps stay.
                if (!blocked) KitText(stringResource(Res.string.phoneSetupStartStepsNoTime), role = TypeRole.Label)
                includes?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
                if (check is SetupPreflightResult.MayBeSlow) KitText(stringResource(Res.string.phoneSetupPreflightMayBeSlow, Numbers.format(check.totalMemoryMb)), role = TypeRole.Secondary)
                state.spaceTight?.let { (need, free) ->
                    KitText(
                        stringResource(Res.string.phoneSetupPreflightSpaceNumbers, journeySize(need), journeySize(free)),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                }
                if (state.storageUnopened) KitErrorNotice(stringResource(Res.string.r40SetupStorageUnopened))
                if (state.failure != null) KitErrorNotice(stringResource(Res.string.r40SetupCouldNotCheck),
                    retry = KitLineAction(stringResource(Res.string.r40SetupRetry), actions::retry))
                BatteryCardNotice(state, actions)
            }
        },
        footer = { OtherWays(actions) },
    )
}

/**
 * F: lib/ui/screens/phone_setup/phone_setup_start_screen.dart:686-760 (`_otherWays`): a divider, then one row that
 * unfolds in place. Native keeps only the way it can take: Termux setup was dropped (D7), so "Connect to a computer
 * by address" is the one row.
 */
@Composable
private fun OtherWays(actions: SetupJourneyActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // The hairline sits on the top edge of the row and takes no height, so the step above stays the hero's
        // 16 dp (native/docs/kit/ALIGN-RULES.md gap-step).
        KitDivider(
            Modifier.layout { measurable, constraints ->
                val line = measurable.measure(constraints)
                layout(line.width, 0) { line.place(0, -line.height) }
            }
        )
        KitExpandRow(
            title = stringResource(Res.string.phoneSetupStartOtherWays),
            expanded = open,
            onExpandedChange = { open = it },
            modifier = Modifier.testTag("phone-setup-start-other-ways"),
            // NV: the hero column is already on the 16 dp gutter; a second row inset would put the title on 32,
            // off the page's columns (native/docs/kit/ALIGN-RULES.md start-edge).
            inset = false,
        ) {
            KitRow(
                title = stringResource(Res.string.phoneSetupStartByAddress),
                leading = KitRowLeading.Glyph(KitIcons.link),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::connectComputer,
            )
        }
    }
}

@Composable
private fun Choices(state: SetupJourneyUi, actions: SetupJourneyActions) {
    val options = state.options
    if (options == null) {
        if (state.checking) KitText(stringResource(Res.string.phoneSetupStartChecking))
        return
    }
    val choices = options.components.filter { !it.jobStep && (!state.adding || !it.required) }
    val everythingInstalled =
        state.adding &&
            !state.checking &&
            choices.isNotEmpty() &&
            choices.all { it.id in options.installed }
    // Flutter keeps the required base and optional tools in separate groups, in registry order.
    for (required in listOf(true, false)) {
        val group = choices.filter { it.required == required }
        if (group.isEmpty()) continue
        KitRowGroup {
            for (component in group) {
                val installed = component.id in options.installed
                Column {
                    if (required || installed) {
                        KitLockedRow(
                            title = itemTitle(component.id, component.title),
                            lockedLabel =
                                stringResource(
                                    if (required) Res.string.phoneSetupCustomizeIncluded
                                    else Res.string.phoneSetupStartInstalled
                                ),
                            supporting =
                                if (required) component.why
                                else if (component.app != null) component.summary else null,
                            modifier = Modifier.testTag("phone-setup-customize-${component.id}"),
                        )
                    } else {
                        val description = buildList {
                            component.summary?.let { add(it) }
                            component.downloadBytes
                                ?.takeIf { it > 0 }
                                ?.let {
                                    add(
                                        stringResource(
                                                Res.string.phoneSetupStartApproxSize,
                                                journeySize(it),
                                            )
                                            .replace(' ', '\u00a0')
                                    )
                                }
                        }
                            .joinToString(" · ")
                            .ifEmpty { null }
                        KitSwitchRow(
                            title = itemTitle(component.id, component.title),
                            checked = component.id in state.chosen,
                            supporting = description,
                            enabled =
                                KitEnabled.of(
                                    !state.working && !state.checking,
                                    stringResource(
                                        if (state.checking) Res.string.phoneSetupStartChecking
                                        else Res.string.r40SetupWorking
                                    ),
                                ),
                            modifier = Modifier.testTag("phone-setup-customize-${component.id}"),
                            onCheckedChange = { actions.toggle(component.id, it) },
                        )
                    }
                    if (installed && component.id == SetupComponentIds.VOICE) {
                        KitButton(
                            stringResource(Res.string.voiceComponentRemove),
                            actions::askRemoveVoice,
                            modifier =
                                Modifier.padding(start = LocalKitTokens.current.gutter.dp)
                                    .testTag("phone-setup-customize-remove-${component.id}"),
                            role = KitButtonRole.Tertiary,
                            icon = KitIcons.delete,
                            destructive = true,
                            enabled =
                                KitEnabled.of(
                                    !state.working && !state.checking,
                                    stringResource(Res.string.r40SetupWorking),
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.S4.dp))
    }
    val totalsModifier =
        Modifier.testTag("phone-setup-customize-totals").semantics {
            liveRegion = LiveRegionMode.Polite
        }
    val blocked = state.preflight?.supported == false && state.install.isNotEmpty()
    when {
        state.checking ->
            KitText(
                stringResource(Res.string.phoneSetupStartChecking),
                totalsModifier,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        everythingInstalled ->
            KitText(
                stringResource(Res.string.phoneSetupCustomizeAllInstalled),
                totalsModifier,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        state.install.isEmpty() ->
            KitText(
                stringResource(Res.string.phoneSetupStartNothingChosen),
                totalsModifier,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        blocked -> Preflight(state, actions)
        else -> {
            // The size only: the minutes estimate was not honest (see StartHero).
            KitText(
                stringResource(Res.string.teamDiscoverDownloadTitle, journeySize(state.bytes)),
                totalsModifier.padding(vertical = Space.S1.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
    }
    if (state.storageUnopened) KitErrorNotice(stringResource(Res.string.r40SetupStorageUnopened))
    Spacer(Modifier.height(Space.S4.dp))
    val disabled =
        when {
            state.checking -> stringResource(Res.string.phoneSetupStartChecking)
            state.failure != null -> stringResource(Res.string.r40SetupCouldNotCheck)
            everythingInstalled -> stringResource(Res.string.phoneSetupCustomizeAllInstalled)
            state.adding && state.install.isEmpty() ->
                stringResource(Res.string.phoneSetupStartNothingChosen)
            blocked -> stringResource(Res.string.r40SetupCheckDevice)
            else -> stringResource(Res.string.r40SetupWorking)
        }
    KitButton(
        stringResource(
            if (state.adding) Res.string.phoneSetupStartAdd else Res.string.phoneSetupStartDone
        ),
        actions::accept,
        modifier = Modifier.testTag("phone-setup-customize-done"),
        enabled = KitEnabled.of(state.canConfirm, disabled),
        working = state.working,
        // F: lib/ui/screens/phone_setup/phone_setup_customize_sheet.dart:465 (`KitActionBlock(primary:)`, full width)
        fullWidth = true,
    )
}

@Composable
private fun Preflight(state: SetupJourneyUi, actions: SetupJourneyActions) {
    when (val check = state.preflight) {
        is SetupPreflightResult.UnsupportedAbi ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightUnsupportedHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightUnsupportedBody,
                        check.reportedAbi,
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowMemory ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowMemoryHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowMemoryBody,
                        Numbers.format(SetupPreflight.MINIMUM_MEMORY_MB),
                        Numbers.format(check.totalMemoryMb),
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowSpace ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowSpaceHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowSpaceBody,
                        journeySize(check.bytesToFree),
                    ),
                tone = StatusTone.Failure,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.phoneSetupPreflightOpenStorage),
                            actions::openStorage,
                        )
                    ),
            )
        is SetupPreflightResult.MayBeSlow ->
            KitNotice(
                stringResource(
                    Res.string.phoneSetupPreflightMayBeSlow,
                    Numbers.format(check.totalMemoryMb),
                )
            )
        else -> Unit
    }
}

@Composable
private fun Job(
    state: SetupJourneyUi,
    actions: SetupJourneyActions,
    wide: Boolean = false,
    pageTitle: String = "",
) {
    val progress = state.progress
    var detailsOpen by remember { mutableStateOf(false) }
    val rows =
        progress.components.ifEmpty {
            state.install.map { ComponentProgress(it.id, ComponentState.Pending) }
        }
    val memory =
        remember(progress.jobId ?: rows.joinToString { it.id }) {
            progress.jobId?.let { journeyMemory(it) } ?: JourneyProgressMemory()
        }
    val ringBody: @Composable () -> Unit = {
        RingBodyPane(state, actions, memory, wide, onOpenDetails = { detailsOpen = true })
    }
    if (wide) {
        WideJobPane(state, pageTitle, ringBody)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = (maxHeight - Space.S4.dp).coerceAtLeast(0.dp)
        Box(
            Modifier.fillMaxWidth()
                .verticalScroll(kitPageScrollState())
                .padding(bottom = Space.S4.dp)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = availableHeight)
                    .testTag("setup-progress-viewport"),
                contentAlignment =
                    BiasAlignment(
                        0f,
                        -.3f,
                    ), // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:384
            ) {
                ringBody()
            }
        }
    }
    JobDetailsSheet(state, rows, progress, detailsOpen) { detailsOpen = false }
}

/** The wide two-column frame of the setup job: the ring at the start, the parts panel at the end. */
@Composable
private fun WideJobPane(
    state: SetupJourneyUi,
    pageTitle: String,
    ringBody: @Composable () -> Unit,
) {
    val progress = state.progress
    val rows =
        progress.components.ifEmpty {
            state.install.map { ComponentProgress(it.id, ComponentState.Pending) }
        }
    val gutter = LocalKitTokens.current.gutter.dp
    WideTwoColumnFrame(
        pageTitle = pageTitle,
        verticalAlignment = Alignment.CenterVertically,
        viewportModifier = Modifier.padding(horizontal = gutter).testTag("setup-progress-viewport"),
        start = ringBody,
        end = {
            WidePartsPanel(
                items = wideJourneyParts(state, rows),
                showDoneCount = true,
                showStoppedNote =
                    progress.canContinue &&
                        progress.state == SetupState.Failed &&
                        state.systemKill == null &&
                        !nodeCannotRun(progress),
                details = state.details,
            )
        },
    )
}

/** The cards under the ring's lines: the details fold (narrow), the battery note, failure, and the wide action. */
@Composable
internal fun RingTailCards(
    state: SetupJourneyUi,
    actions: SetupJourneyActions,
    wide: Boolean,
    onOpenDetails: () -> Unit,
) {
    if (!wide) {
        Spacer(Modifier.height(Space.S3.dp))
        KitButton(
            stringResource(Res.string.kitDetails),
            onOpenDetails,
            role = KitButtonRole.Tertiary,
        )
    }
    BatteryCardNotice(state, actions)
    if (
        state.failure != null &&
            state.confirmStop == null &&
            !state.confirmRemoveVoice
    ) {
        if (wide) Spacer(Modifier.height(Space.S3.dp))
        KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
    }
    if (wide) {
        Box(Modifier.padding(top = Space.S5.dp).widthIn(max = Layout.CONTROL_MAX_WIDTH.dp)) {
            JobAction(state, actions)
        }
    }
}

@Composable
internal fun wideJourneyParts(
    state: SetupJourneyUi,
    rows: List<ComponentProgress>,
): List<WidePartItem> =
    rows.map { component ->
        val fallback =
            state.options?.components?.firstOrNull { it.id == component.id }?.title ?: component.id
        val total = component.bytesTotal
        val done = component.bytesDone
        val pct = component.percent?.takeIf { component.state == ComponentState.Running }
        val progressNote =
            when {
                total != null && total > 0 && done != null -> {
                    val (received, whole) = SetupProgressRules.formatBytePair(done, total)
                    stringResource(Res.string.setupProgressViewBytes, received, whole)
                }
                pct != null ->
                    stringResource(
                        Res.string.setupProgressViewPercent,
                        pct.coerceIn(0.0, JOURNEY_PERCENT_MAX).roundToInt(),
                    )
                else -> null
            }
        val status =
            when (component.state) {
                ComponentState.Done, ComponentState.Skipped -> WidePartStatus.Done
                ComponentState.Running, ComponentState.Checking -> WidePartStatus.Working
                ComponentState.Failed -> WidePartStatus.Failed
                ComponentState.Pending -> WidePartStatus.Waiting
            }
        WidePartItem(component.id, itemTitle(component.id, fallback), status, progressNote)
    }

private const val JOURNEY_PERCENT_MAX = 100.0
