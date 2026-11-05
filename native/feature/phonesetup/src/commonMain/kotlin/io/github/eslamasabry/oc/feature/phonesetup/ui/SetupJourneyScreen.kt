package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.style.TextAlign
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.setup.*
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitLockedRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
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
    KitScreen(
        topBar = {
            KitTopBar(
                journeyTitle(state),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.checking,
        bottom = if (idle || state.customizing || state.checking) null else ({ JobAction(state, actions) }),
    ) {
        if (!state.checking) {
            if (state.customizing || idle) StartHero(state, actions)
            else Job(state, actions)
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
                val minutes = setupPromiseMinutes(state.seconds) // NV: estimated seconds converted to whole minutes
                val time = if (minutes == 1) stringResource(Res.string.phoneSetupStartStepsTime__eq1)
                    else org.jetbrains.compose.resources.pluralStringResource(Res.plurals.phoneSetupStartStepsTime, minutes, minutes)
                if (!blocked) KitText(stringResource(Res.string.phoneSetupStartSteps, time), role = TypeRole.Label)
                includes?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
                if (check is SetupPreflightResult.MayBeSlow) KitText(stringResource(Res.string.phoneSetupPreflightMayBeSlow, Numbers.format(check.totalMemoryMb)), role = TypeRole.Secondary)
                if (state.storageUnopened) KitErrorNotice(stringResource(Res.string.r40SetupStorageUnopened))
                if (state.failure != null) KitErrorNotice(stringResource(Res.string.r40SetupCouldNotCheck),
                    retry = KitLineAction(stringResource(Res.string.r40SetupRetry), actions::retry))
            }
        },
    )
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
            val minutes = setupPromiseMinutes(state.seconds)
            val duration =
                if (minutes == 1) stringResource(Res.string.phoneSetupStartAboutMinutes__eq1)
                else
                    org.jetbrains.compose.resources.pluralStringResource(
                        Res.plurals.phoneSetupStartAboutMinutes,
                        minutes,
                        minutes,
                    )
            KitText(
                stringResource(Res.string.phoneSetupStartTotals, duration, journeySize(state.bytes)),
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
    )
}

/** Flutter setupDurationText/setupStepsText round the registry estimate to the nearest minute. */
internal fun setupPromiseMinutes(seconds: Long): Int =
    (seconds / 60.0)
        .roundToInt()
        .coerceAtLeast(1) // F: lib/ui/screens/phone_setup/phone_setup_selection.dart:63,71

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
private fun Job(state: SetupJourneyUi, actions: SetupJourneyActions) {
    val progress = state.progress
    var detailsOpen by remember { mutableStateOf(false) }
    val rows =
        progress.components.ifEmpty {
            state.install.map { ComponentProgress(it.id, ComponentState.Pending) }
        }
    val memory = remember(progress.jobId ?: rows.joinToString { it.id }) { JourneyProgressMemory() }
    fun finished(row: ComponentProgress) =
        row.state == ComponentState.Done || row.state == ComponentState.Skipped
    val done = rows.count(::finished)
    var current =
        rows
            .indexOfFirst {
                it.state == ComponentState.Running ||
                    it.state == ComponentState.Checking ||
                    it.state == ComponentState.Failed
            }
            .let { found ->
                if (found >= 0) found
                else rows.indexOfFirst { it.id == progress.current && !finished(it) }
            }
    val halted = progress.canContinue
    if (current < 0 && halted) current = rows.indexOfFirst { !finished(it) }
    val row = rows.getOrNull(current)
    val checking =
        rows.isNotEmpty() &&
            rows.all { it.state == ComponentState.Checking || finished(it) } &&
            rows.any { it.state == ComponentState.Checking }
    val measured = if (!halted) memory.measured(row) else null
    val eta = memory.eta(progress, state.options?.components.orEmpty())
    val subject =
        row?.let { currentRow ->
                if (currentRow.id == SetupComponentIds.ESSENTIALS)
                    stringResource(Res.string.phoneSetupEssentialsShort)
                else
                    itemTitle(
                        currentRow.id,
                        state.options
                            ?.components
                            ?.firstOrNull { it.id == currentRow.id }
                            ?.shortTitle ?: currentRow.id,
                    )
            }
            .orEmpty()
    val unpacking = stringResource(Res.string.phoneSetupStageUnpackingLinux)
    val title =
        when (progress.state) {
            SetupState.Done -> stringResource(Res.string.setupProgressViewDone)
            SetupState.Failed -> stringResource(Res.string.setupProgressViewFailedTitle)
            SetupState.Interrupted -> stringResource(Res.string.phoneSetupRingInterruptedTitle)
            SetupState.Cancelled -> stringResource(Res.string.phoneSetupRingPausedTitle)
            else ->
                when {
                    checking -> stringResource(Res.string.phoneSetupRingChecking)
                    row == null || row.state == ComponentState.Pending ->
                        stringResource(Res.string.setupProgressViewGettingStarted)
                    row.id == SetupComponentIds.START ->
                        stringResource(Res.string.phoneSetupStageStarting)
                    row.stage?.let {
                        it == unpacking ||
                            Regex("unpack|extract", RegexOption.IGNORE_CASE).containsMatchIn(it)
                    } == true -> stringResource(Res.string.phoneSetupRingUnpacking, subject)
                    ((row.bytesTotal ?: 0) > (row.bytesDone ?: 0)) ||
                        row.stage?.let {
                            Regex("download|fetch", RegexOption.IGNORE_CASE).containsMatchIn(it)
                        } == true -> stringResource(Res.string.phoneSetupRingDownloading, subject)
                    else -> stringResource(Res.string.phoneSetupRingInstalling, subject)
                }
        }
    val note =
        when (progress.state) {
            SetupState.Running,
            SetupState.Idle -> stringResource(Res.string.phoneSetupRingSafeToLeaveQuiet)
            SetupState.Failed -> journeyFailure(state)
            SetupState.Interrupted,
            SetupState.Cancelled -> stringResource(Res.string.phoneSetupRingStoppedNote)
            SetupState.Done -> null
        }
    val bytesDone = row?.bytesDone
    val bytesTotal = row?.bytesTotal
    val caption =
        if (measured != null && bytesDone != null && bytesTotal != null && bytesTotal > 0) {
            val (received, total) = SetupProgressRules.formatBytePair(bytesDone, bytesTotal)
            stringResource(
                Res.string.setupProgressViewBytes,
                "\u2066$received\u2069",
                "\u2066$total\u2069",
            )
        } else null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = (maxHeight - Space.S4.dp).coerceAtLeast(0.dp)
        Box(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                Column(
                    Modifier.fillMaxWidth()
                        .testTag("setup-progress-body")
                        .padding(horizontal = Space.S5.dp, vertical = Space.S6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KitProgressRing(
                        title,
                        stringResource(Res.string.setupProgressViewOverallLabel),
                        value = measured,
                        done = done,
                        of = rows.size,
                        finished = progress.state == SetupState.Done,
                        halted = halted,
                        caption = caption,
                        trail =
                            rows.mapIndexed { index, step ->
                                when {
                                    finished(step) -> KitTrailMark.Done
                                    index == current && !checking -> KitTrailMark.Current
                                    else -> KitTrailMark.Waiting
                                }
                            },
                    )
                    if (eta != null) {
                        Spacer(Modifier.height(Space.S6.dp))
                        val cost =
                            if (
                                eta < 60 && rows.none { it.state == ComponentState.Pending }
                            ) // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:300
                             stringResource(Res.string.phoneSetupRingUnderMinute)
                            else {
                                val minutes =
                                    (eta / 60.0)
                                        .roundToInt()
                                        .coerceAtLeast(
                                            1
                                        ) // NV: seconds to nearest whole minute, as Flutter's cost
                                          // line
                                org.jetbrains.compose.resources.pluralStringResource(
                                    Res.plurals.phoneSetupRingMinutesLeft,
                                    minutes,
                                    minutes,
                                )
                            }
                        KitText(
                            cost,
                            role = TypeRole.RowTitle,
                            tone = TextTone.Secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (note != null) {
                        Spacer(Modifier.height(if (eta == null) Space.S6.dp else Space.S2.dp))
                        KitText(
                            note,
                            Modifier.widthIn(max = 320.dp),
                            role = TypeRole.Secondary,
                            tone = TextTone.Tertiary,
                            textAlign = TextAlign.Center,
                        ) // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:553
                    }
                    Spacer(Modifier.height(Space.S3.dp))
                    KitButton(
                        stringResource(Res.string.kitDetails),
                        { detailsOpen = true },
                        role = KitButtonRole.Tertiary,
                    )
                    if (
                        state.failure != null &&
                            state.confirmStop == null &&
                            !state.confirmRemoveVoice
                    ) {
                        KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
                    }
                }
            }
        }
    }
    KitSheet(
        visible = detailsOpen,
        onDismissRequest = { detailsOpen = false },
        title = stringResource(Res.string.phoneSetupRingDetailsTitle),
    ) {
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, component ->
                val measured =
                    if ((component.bytesTotal ?: 0) > 0 && component.bytesDone != null) {
                        val (received, total) =
                            SetupProgressRules.formatBytePair(
                                component.bytesDone!!,
                                component.bytesTotal!!,
                            )
                        stringResource(Res.string.setupProgressViewBytes, received, total)
                    } else
                        component.percent?.let {
                            stringResource(
                                Res.string.setupProgressViewPercent,
                                it.coerceIn(0.0, 100.0).toInt(),
                            )
                        }
                val stage =
                    component.stage?.let { setupMessageWords(it) }
                        ?: if (component.state == ComponentState.Checking)
                            stringResource(Res.string.setupProgressViewChecking)
                        else null
                val supporting =
                    when (component.state) {
                        ComponentState.Done,
                        ComponentState.Skipped -> component.version
                        ComponentState.Failed -> runtimeProblem(component.error ?: progress.error)
                        ComponentState.Pending -> null
                        else ->
                            when {
                                stage != null && measured != null ->
                                    stringResource(
                                        Res.string.setupProgressViewStageMeasured,
                                        stage,
                                        measured,
                                    )
                                else ->
                                    measured
                                        ?: stage
                                        ?: stringResource(Res.string.setupProgressViewStarting)
                            }
                    }
                KitChecklistStep(
                    itemTitle(
                        component.id,
                        state.options?.components?.firstOrNull { it.id == component.id }?.title
                            ?: component.id,
                    ),
                    state =
                        when (component.state) {
                            ComponentState.Done,
                            ComponentState.Skipped -> KitStepState.Done
                            ComponentState.Running,
                            ComponentState.Checking -> KitStepState.Working
                            ComponentState.Failed -> KitStepState.Failed
                            ComponentState.Pending -> KitStepState.Waiting
                        },
                    index = index + 1,
                    total = rows.size,
                    supporting = supporting,
                )
            }
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                text = state.details,
            )
        }
    }
}

@Composable
private fun JobAction(state: SetupJourneyUi, actions: SetupJourneyActions) {
    when {
        state.progress.state == SetupState.Running -> KitButton(
            stringResource(Res.string.phoneSetupRingPause), { actions.askStop(); actions.confirmStop() },
            fullWidth = true, role = KitButtonRole.Secondary, working = state.working,
            icon = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.pause,
        )
        // Ownership recovery requires an app-settings action which this contract does not expose.
        SetupProgressRules.needsOwnershipRecovery(state.progress) -> Unit
        state.progress.canContinue -> KitButton(
            stringResource(Res.string.setupProgressViewContinue), actions::continueSetup, working = state.working,
            icon = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.play,
        )
        state.progress.state == SetupState.Done -> KitButton(stringResource(Res.string.phoneSetupStartOpen), actions::done)
    }
}

/** UI memory from Flutter's _track / _measuredOf: a new job resets it; a new stage resets only its arc. */
internal class JourneyProgressMemory {
    private var measuredKey: Pair<String, String?>? = null
    private var furthest = 0f
    private var remaining: Int? = null

    fun measured(row: ComponentProgress?): Float? {
        if (row?.state != ComponentState.Running) return null
        val fraction = row.fraction?.toFloat() ?: return null
        val key = row.id to row.stage
        furthest = if (key == measuredKey) maxOf(furthest, fraction) else fraction
        measuredKey = key
        return furthest
    }

    fun eta(progress: SetupProgress, registry: List<SetupComponent>): Int? {
        if (progress.state != SetupState.Running) return null
        progress.etaSeconds?.let { raw ->
            val pending = progress.components.filter { it.state == ComponentState.Pending }.sumOf { row ->
                registry.firstOrNull { it.id == row.id }?.estimatedSeconds ?: 0
            }
            val estimate = maxOf(raw, pending)
            remaining = remaining?.let { minOf(it, estimate) } ?: estimate
        }
        return remaining
    }
}

/** The job answer uses Flutter's ownership/network/known-reason/stage fallback order. */
@Composable
private fun journeyFailure(state: SetupJourneyUi): String {
    val progress = state.progress
    val failed = progress.components.filter { it.state == ComponentState.Failed }
    val errors = listOfNotNull(progress.error) + failed.mapNotNull { it.error }
    if (SetupError.OwnershipUnknown in errors)
        return stringResource(Res.string.inAppServerOwnershipRecoveryBody)
    if (errors.any { it == SetupError.NoInternet || it is SetupError.Offline })
        return stringResource(Res.string.setupProgressViewNoInternet)
    val row = failed.firstOrNull()
    val error = row?.error ?: progress.error
    if (
        error != null &&
            error !in setOf(SetupError.Unexpected, SetupError.HostRefused, SetupError.HostMismatch)
    )
        return runtimeProblem(error)
    // F: lib/ui/widgets/setup_progress_view.dart:144-167
    if (row != null) {
        return row.stage?.let {
            stringResource(Res.string.setupProgressViewFailedDuring, setupMessageWords(it))
        } ?: stringResource(Res.string.setupProgressViewFailedStep)
    }
    val current =
        progress.current ?: return stringResource(Res.string.setupProgressViewFailedUnknown)
    val component = state.options?.components?.firstOrNull { it.id == current }
    val title =
        if (current == SetupComponentIds.ESSENTIALS)
            stringResource(Res.string.phoneSetupEssentialsShort)
        else itemTitle(current, component?.shortTitle ?: current)
    return stringResource(Res.string.setupProgressViewFailedAt, title)
}

@Composable
private fun runtimeProblem(error: SetupError?): String =
    when (error) {
        is SetupError.NoSpace -> stringResource(Res.string.phoneSetupErrorNoSpace, error.title)
        is SetupError.Offline -> stringResource(Res.string.phoneSetupErrorOffline, error.title)
        is SetupError.Checksum -> stringResource(Res.string.phoneSetupErrorChecksum, error.title)
        is SetupError.Install -> stringResource(Res.string.phoneSetupErrorInstall, error.title)
        SetupError.NoInternet -> stringResource(Res.string.phoneSetupErrorNoInternet)
        SetupError.PackageRepair -> stringResource(Res.string.phoneSetupErrorPackageRepair)
        SetupError.CannotStart -> stringResource(Res.string.phoneSetupErrorCannotStart)
        SetupError.OwnershipUnknown -> stringResource(Res.string.inAppServerOwnershipRecoveryBody)
        SetupError.StatusUnreadable -> stringResource(Res.string.setupRbStatusUnreadable)
        SetupError.ToolsUncheckable -> stringResource(Res.string.setupRbToolsUncheckable)
        SetupError.PersistenceFailure -> stringResource(Res.string.linuxSetupSavedFailed)
        SetupError.Interrupted -> stringResource(Res.string.phoneSetupErrorInterrupted)
        SetupError.OpenCodeNoProgram -> stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
        SetupError.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
        SetupError.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
        else -> stringResource(Res.string.setupProgressViewFailedUnknown)
    }

@Composable
internal fun journeySize(bytes: Long): String =
    if (bytes >= 1_000_000_000)
        stringResource(
            Res.string.phoneSetupStartGigabytes,
            Numbers.decimal(bytes / 1_000_000_000.0, 1),
        )
    else
        stringResource(
            Res.string.phoneSetupStartMegabytes,
            Numbers.format((bytes / 1_000_000.0).roundToInt().coerceAtLeast(1)),
        )
