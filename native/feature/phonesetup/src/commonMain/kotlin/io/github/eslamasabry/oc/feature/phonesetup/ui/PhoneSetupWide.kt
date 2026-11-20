package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupComponentIds
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflight
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress as JourneyComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState as JourneyComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgressRules
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState as JourneySetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.setup.KitProgressRing
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupPhone
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupPhoneMood
import io.github.eslamasabry.oc.ui.kit.setup.KitTrailMark
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressTone
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.diagnosticsUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupLinuxWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeConnectComputer
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeReportFailure
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupOpenCodeWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowMemoryBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowMemoryHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowSpaceBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowSpaceHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightMayBeSlow
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightOpenStorage
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightUnsupportedHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressKeepGoing
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressStopConfirm
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressStopMessage
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressStopTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingSafeToLeaveQuiet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingStoppedNote
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartChecking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartListSeparator
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromise
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromiseNoSize
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartReadyBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartScreenTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartSetUp
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartStepsNoTime
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartWhy
import io.github.eslamasabry.oc.ui.words.res.r40SetupActionFailed
import io.github.eslamasabry.oc.ui.words.res.setupHeartbeatQuiet
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewBytes
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewCancel
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewCancelled
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewContinue
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewGettingStarted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewInterrupted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewOverallLabel
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewPercent
import io.github.eslamasabry.oc.ui.words.res.teamUiHomeRunProgress
import io.github.eslamasabry.oc.ui.words.res.voiceComponentSummary
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/**
 * Wide-window (Expanded+, >= 840 dp) two-column layout for [PhoneSetupScreen]: title sits directly above the two
 * top-aligned columns in one vertically centred composition, with overview/progress and main action on the start side
 * and the natural-height Setup details panel on the end side.
 */
@Composable
internal fun PhoneSetupWideScreen(state: PhoneSetupUi, actions: PhoneSetupActions) {
    val progress = state.progress
    val pageTitle =
        stringResource(
            if (progress == null) Res.string.phoneSetupStartScreenTitle
            else Res.string.phoneSetupProgressTitle,
        )
    KitScreen(
        topBar = {
            KitTopBar(
                title = "",
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Full,
        loading = state.checking,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Box(Modifier.fillMaxWidth().verticalScroll(kitPageScrollState())) {
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(min = viewportHeight)
                        .padding(top = Space.S2.dp, bottom = Space.S6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Space.S5.dp),
                    ) {
                        KitText(
                            pageTitle,
                            Modifier.semantics { heading() },
                            role = TypeRole.LargeTitle,
                            kind = KitTextKind.Frame,
                            pageTitle = true,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.S6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start),
                                verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
                            ) {
                                if (progress == null) WideStartSummary(state, actions)
                                else WideJobSummary(progress, state, actions)
                            }
                            Box(Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start)) {
                                WidePartsPanel(state, progress, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
    WideStopConfirm(state, actions)
}

@Composable
private fun WideArtworkBay(mood: KitSetupPhoneMood) {
    KitSurface(
        modifier = Modifier.fillMaxWidth(),
        level = SurfaceLevel.Surface1,
        shape = KitShapeId.Panel,
        padding = KitSurfacePadding.Panel,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = Space.S2.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Explicit 4:3 size so Row(IntrinsicSize.Min) measures the artwork's real height, not full column width.
            KitSetupPhone(
                mood = mood,
                modifier = Modifier.size(width = ARTWORK_WIDTH_DP.dp, height = ARTWORK_HEIGHT_DP.dp),
            )
        }
    }
}

@Composable
private fun WideStartSummary(state: PhoneSetupUi, actions: PhoneSetupActions) {
    val mood =
        when {
            state.preflight?.supported == false -> KitSetupPhoneMood.Stopped
            state.starting -> KitSetupPhoneMood.Starting
            else -> KitSetupPhoneMood.Fresh
        }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
        WideArtworkBay(mood)
        Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitText(stringResource(Res.string.phoneSetupStartHeadline), role = TypeRole.Title)
            if (state.checking) {
                KitText(stringResource(Res.string.phoneSetupStartChecking), tone = TextTone.Secondary)
            } else {
                val size = sizeWords(state.downloadBytes)
                KitText(
                    if (state.downloadBytes > 0) stringResource(Res.string.phoneSetupStartPromise, size)
                    else stringResource(Res.string.phoneSetupStartPromiseNoSize),
                    tone = TextTone.Secondary,
                )
            }
        }
        if (!state.checking && state.preflight?.supported != false) {
            WideStartStepList()
        }
        if (!state.checking) WidePreflightNotice(state.preflight, actions)
    }
    if (!state.checking && state.preflight?.supported != false) {
        Box(Modifier.padding(top = Space.S4.dp).widthIn(max = Layout.CONTROL_MAX_WIDTH.dp)) {
            KitButton(
                stringResource(Res.string.phoneSetupStartSetUp),
                actions::setUp,
                working = state.starting,
                fullWidth = true,
                hero = true,
            )
        }
    }
}

/** Three numbered future steps ("1 Install", "2 Name a project", "3 Start a conversation") from the promise line. */
@Composable
private fun WideStartStepList() {
    val raw = stringResource(Res.string.phoneSetupStartStepsNoTime)
    val sep = stringResource(Res.string.phoneSetupStartListSeparator).trim()
    val steps =
        raw.split(sep)
            .map { part -> part.trim().replaceFirstChar { ch -> ch.uppercase() } }
            .filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                KitSurface(
                    level = SurfaceLevel.Surface2,
                    shape = KitShapeId.Circle,
                    padding = KitSurfacePadding.None,
                ) {
                    Box(Modifier.size(STEP_BADGE_DP.dp), contentAlignment = Alignment.Center) {
                        KitText(
                            Numbers.format(index + 1),
                            role = TypeRole.Caption,
                            tone = TextTone.Accent,
                        )
                    }
                }
                KitText(step, role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
    }
}

@Composable
private fun WidePreflightNotice(preflight: SetupPreflightResult?, actions: PhoneSetupActions) {
    when (preflight) {
        is SetupPreflightResult.UnsupportedAbi ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightUnsupportedHeadline),
                message = stringResource(Res.string.phoneSetupPreflightUnsupportedBody, preflight.reportedAbi),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowMemory ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowMemoryHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowMemoryBody,
                        "${SetupPreflight.MINIMUM_MEMORY_MB}",
                        "${preflight.totalMemoryMb}",
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowSpace ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowSpaceHeadline),
                message = stringResource(Res.string.phoneSetupPreflightLowSpaceBody, sizeWords(preflight.bytesToFree)),
                tone = StatusTone.Failure,
                actions = listOf(KitLineAction(stringResource(Res.string.phoneSetupPreflightOpenStorage), actions::openStorage)),
            )
        is SetupPreflightResult.MayBeSlow ->
            KitNotice(stringResource(Res.string.phoneSetupPreflightMayBeSlow, "${preflight.totalMemoryMb}"))
        else -> Unit
    }
}

@Composable
private fun WideJobSummary(progress: SetupProgress, state: PhoneSetupUi, actions: PhoneSetupActions) {
    val current = progress.components.firstOrNull { it.id == progress.current }
    val stage =
        current?.stage
            ?: if (progress.state == SetupState.Running) {
                stringResource(Res.string.setupProgressViewGettingStarted)
            } else {
                null
            }
    val pct = (progress.overall * PERCENT_MAX).roundToInt().coerceIn(0, PERCENT_MAX)
    val bar = KitProgress.Known(value = progress.overall.toFloat().coerceIn(0f, 1f), eta = progress.etaSeconds?.seconds)
    val tone =
        when (progress.state) {
            SetupState.Failed -> KitProgressTone.Failed
            SetupState.Running, SetupState.Done -> KitProgressTone.Moving
            else -> KitProgressTone.Stopped
        }
    val mood =
        when (progress.state) {
            SetupState.Running -> KitSetupPhoneMood.Starting
            SetupState.Done -> KitSetupPhoneMood.Celebration
            else -> KitSetupPhoneMood.Stopped
        }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
        WideArtworkBay(mood)
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            val headline =
                when {
                    stage != null -> setupMessageWords(stage)
                    progress.state == SetupState.Done -> stringResource(Res.string.phoneSetupReadyTitle)
                    else -> null
                }
            val pctRole = if (progress.state == SetupState.Running) TypeRole.Title else TypeRole.Headline
            val pctTone =
                if (progress.state == SetupState.Running || progress.state == SetupState.Done) {
                    TextTone.Primary
                } else {
                    TextTone.Secondary
                }
            if (headline != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    KitText(headline, Modifier.weight(1f), role = TypeRole.Title)
                    KitText("$pct%", role = pctRole, tone = pctTone)
                }
            } else {
                KitText("$pct%", role = pctRole, tone = pctTone)
            }
            KitProgressView(
                bar,
                semanticsLabel = stringResource(Res.string.setupProgressViewOverallLabel),
                tone = tone,
            )
        }
        WideJobNoteBody(progress, state)
    }
    Box(Modifier.padding(top = Space.S4.dp)) {
        WideJobActionFoot(progress, state, actions)
    }
}

@Composable
private fun WideJobNoteBody(progress: SetupProgress, state: PhoneSetupUi) {
    when (progress.state) {
        SetupState.Running ->
            KitText(
                stringResource(Res.string.phoneSetupRingSafeToLeaveQuiet),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        SetupState.Done ->
            KitText(stringResource(Res.string.phoneSetupStartReadyBody), tone = TextTone.Secondary)
        SetupState.Failed, SetupState.Interrupted, SetupState.Cancelled -> {
            val message =
                when (progress.state) {
                    SetupState.Interrupted -> stringResource(Res.string.setupProgressViewInterrupted)
                    SetupState.Cancelled -> stringResource(Res.string.setupProgressViewCancelled)
                    else ->
                        progress.error?.let { problemWords(it) }
                            ?: stringResource(Res.string.setupProgressViewFailedUnknown)
                }
            KitErrorNotice(
                message = message,
                title = stringResource(Res.string.setupProgressViewFailedTitle),
                details = state.details,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
            )
            if (state.reportFailed) KitNotice(stringResource(Res.string.diagnosticsUnavailable))
        }
        SetupState.Idle -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WideJobActionFoot(progress: SetupProgress, state: PhoneSetupUi, actions: PhoneSetupActions) {
    when (progress.state) {
        SetupState.Running ->
            KitButton(
                stringResource(Res.string.setupProgressViewCancel),
                actions::askStop,
                role = KitButtonRole.Secondary,
                expand = false,
            )
        SetupState.Done ->
            Box(Modifier.widthIn(max = Layout.CONTROL_MAX_WIDTH.dp)) {
                KitButton(
                    stringResource(Res.string.chatsHomeNewChat),
                    actions::done,
                    fullWidth = true,
                    hero = true,
                )
            }
        SetupState.Failed, SetupState.Interrupted, SetupState.Cancelled -> {
            val cannotRun = progress.state == SetupState.Failed && progress.error == SetupProblem.NodeCannotRun
            KitReveal(cannotRun) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    KitButton(
                        stringResource(Res.string.phoneSetupNodeConnectComputer),
                        actions::connectComputer,
                        expand = false,
                    )
                    KitButton(
                        stringResource(Res.string.phoneSetupNodeReportFailure),
                        actions::reportFailure,
                        role = KitButtonRole.Secondary,
                        expand = false,
                    )
                }
            }
            if (!cannotRun) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    KitButton(
                        stringResource(Res.string.setupProgressViewContinue),
                        actions::continueSetup,
                        working = state.starting,
                        expand = false,
                    )
                    if (progress.state == SetupState.Failed) {
                        KitButton(
                            stringResource(Res.string.kitReportProblem),
                            actions::reportFailure,
                            role = KitButtonRole.Secondary,
                            expand = false,
                        )
                    }
                }
            }
        }
        SetupState.Idle -> Unit
    }
}

private data class WidePartEntry(
    val id: String,
    val fallbackTitle: String,
    val progress: ComponentProgress?,
)

private fun mergedParts(state: PhoneSetupUi, progress: SetupProgress?): List<WidePartEntry> {
    if (progress == null) return state.items.map { WidePartEntry(it.id, it.title, null) }
    val byId = progress.components.associateBy { it.id }
    val fallbackState = if (progress.state == SetupState.Done) ComponentState.Done else ComponentState.Pending
    val seen = mutableSetOf<String>()
    val result = mutableListOf<WidePartEntry>()
    for (item in state.items) {
        seen += item.id
        result += WidePartEntry(item.id, item.title, byId[item.id] ?: ComponentProgress(item.id, fallbackState))
    }
    for (component in progress.components) {
        if (seen.add(component.id)) {
            result += WidePartEntry(component.id, component.id, component)
        }
    }
    return result
}

@Composable
private fun WidePartsPanel(state: PhoneSetupUi, progress: SetupProgress?, modifier: Modifier = Modifier) {
    val entries = mergedParts(state, progress)
    val doneCount =
        entries.count {
            val s = it.progress?.state
            s == ComponentState.Done || s == ComponentState.Skipped
        }
    // Keep the download size once (in the left paragraph on start); name what the count measures during a job.
    val headerMeta =
        if (progress != null && entries.isNotEmpty()) {
            stringResource(Res.string.teamUiHomeRunProgress, doneCount, entries.size)
        } else {
            null
        }
    KitSurfacePanel(modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.S2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitText(stringResource(Res.string.phoneSetupRingDetailsTitle), role = TypeRole.Headline)
                headerMeta?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
            }
            KitDivider()
            entries.forEachIndexed { index, entry ->
                if (index > 0) KitDivider()
                WidePartRow(entry)
            }
            if (progress != null && progress.canContinue) {
                KitDivider()
                Spacer(Modifier.height(Space.S3.dp))
                KitText(
                    stringResource(Res.string.phoneSetupRingStoppedNote),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
        }
    }
}

@Composable
private fun WidePartRow(entry: WidePartEntry) {
    val title = itemTitle(entry.id, entry.fallbackTitle)
    val description = componentWhy(entry.id)
    val comp = entry.progress
    if (comp == null) {
        KitRow(
            title = title,
            supporting = description,
            leading = KitRowLeading.Glyph(componentIcon(entry.id), tile = true),
            supportingMaxLines = 2,
        )
        return
    }
    val progressNote = componentProgressWords(comp)
    when (comp.state) {
        ComponentState.Done, ComponentState.Skipped ->
            KitRow(
                title = title,
                supporting = description,
                leading = KitRowLeading.Glyph(KitIcons.checkCircle),
                leadingTone = TextTone.Success,
                meta = stringResource(Res.string.kitMarkDone),
                supportingMaxLines = 2,
            )
        ComponentState.Running, ComponentState.Checking ->
            KitRow(
                title = title,
                supporting = description,
                state = KitRowState(word = null, tone = KitRowStateTone.Running),
                meta =
                    listOfNotNull(stringResource(Res.string.kitMarkWorking), progressNote)
                        .joinToString(" · "),
                supportingMaxLines = 2,
            )
        ComponentState.Failed ->
            KitRow(
                title = title,
                supporting = description,
                leading = KitRowLeading.Glyph(KitIcons.error),
                leadingTone = TextTone.Primary,
                meta = stringResource(Res.string.kitMarkFailed),
                supportingMaxLines = 2,
            )
        ComponentState.Pending ->
            KitRow(
                title = title,
                supporting = description,
                leading = KitRowLeading.Glyph(KitIcons.clock),
                leadingTone = TextTone.Tertiary,
                meta = stringResource(Res.string.kitMarkWaiting),
                supportingMaxLines = 2,
            )
    }
}

@Composable
private fun componentProgressWords(component: ComponentProgress): String? {
    val total = component.bytesTotal
    val done = component.bytesDone
    if (total != null && total > 0 && done != null) {
        return stringResource(Res.string.setupProgressViewBytes, sizeWords(done), sizeWords(total))
    }
    val pct = component.percent?.takeIf { component.state == ComponentState.Running }
    if (pct != null) {
        return stringResource(
            Res.string.setupProgressViewPercent,
            pct.coerceIn(0.0, PERCENT_MAX.toDouble()).roundToInt(),
        )
    }
    return null
}

@Composable
internal fun componentWhy(id: String): String? =
    when (id) {
        SetupComponentIds.LINUX -> stringResource(Res.string.phoneSetupLinuxWhy)
        SetupComponentIds.ESSENTIALS -> stringResource(Res.string.phoneSetupEssentialsWhy)
        SetupComponentIds.NODE -> stringResource(Res.string.phoneSetupNodeWhy)
        SetupComponentIds.OPENCODE -> stringResource(Res.string.phoneSetupOpenCodeWhy)
        SetupComponentIds.START -> stringResource(Res.string.phoneSetupStartWhy)
        SetupComponentIds.VOICE -> stringResource(Res.string.voiceComponentSummary)
        else -> null
    }

internal fun componentIcon(id: String) =
    when (id) {
        SetupComponentIds.LINUX -> KitIcons.terminal
        SetupComponentIds.ESSENTIALS -> KitIcons.branch
        SetupComponentIds.NODE -> KitIcons.code
        SetupComponentIds.OPENCODE -> KitIcons.agent
        SetupComponentIds.VOICE -> KitIcons.mic
        else -> KitIcons.tools
    }

@Composable
private fun WideStopConfirm(state: PhoneSetupUi, actions: PhoneSetupActions) {
    KitConfirm(
        spec =
            if (state.confirmingStop) {
                KitConfirmSpec(
                    title = stringResource(Res.string.phoneSetupProgressStopTitle),
                    confirmLabel = stringResource(Res.string.phoneSetupProgressStopConfirm),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.phoneSetupProgressStopMessage),
                    cancelLabel = stringResource(Res.string.phoneSetupProgressKeepGoing),
                )
            } else {
                null
            },
        onConfirm = actions::stop,
        onCancel = actions::keepGoing,
    )
}

private const val ARTWORK_WIDTH_DP = 208
private const val ARTWORK_HEIGHT_DP = 156
private const val STEP_BADGE_DP = 22
private const val PERCENT_MAX = 100

/**
 * Wide-window (Expanded+, >= 840 dp) two-column layout for [SetupJourneyScreen]'s progress/ring page: the page title
 * sits directly above the two columns in one vertically centred group, with the progress ring, stage, live clock/cost,
 * note and action on the start side and all setup parts with Done/Working/Waiting/Failed marks on the end side.
 */
@Composable
internal fun SetupJourneyWideJob(
    pageTitle: String,
    state: SetupJourneyUi,
    rows: List<JourneyComponentProgress>,
    done: Int,
    title: String,
    measured: Float?,
    halted: Boolean,
    caption: String?,
    trail: List<KitTrailMark>,
    costLine: String?,
    heartbeatLine: String?,
    showQuietNote: Boolean,
    note: String?,
    batteryNotice: @Composable () -> Unit,
    actionFoot: @Composable () -> Unit,
) {
    val progress = state.progress
    val gutter = LocalKitTokens.current.gutter.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(Modifier.fillMaxWidth().verticalScroll(kitPageScrollState())) {
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = gutter)
                    .padding(top = Space.S2.dp, bottom = Space.S6.dp)
                    .testTag("setup-progress-viewport"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.S5.dp),
                ) {
                    KitText(
                        pageTitle,
                        Modifier.semantics { heading() },
                        role = TypeRole.LargeTitle,
                        kind = KitTextKind.Frame,
                        pageTitle = true,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.S6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f)
                                .kitMaxWidth(CapRole.Reading, CapAlign.Start)
                                .testTag("setup-progress-body"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            KitProgressRing(
                                title,
                                stringResource(Res.string.setupProgressViewOverallLabel),
                                value = measured,
                                done = done,
                                of = rows.size,
                                finished = progress.state == JourneySetupState.Done,
                                halted = halted,
                                caption = caption,
                                trail = trail,
                            )
                            if (costLine != null) {
                                Spacer(Modifier.height(Space.S5.dp))
                                KitText(
                                    costLine,
                                    Modifier.testTag("phone-setup-progress-cost"),
                                    role = TypeRole.RowTitle,
                                    tone = TextTone.Secondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (heartbeatLine != null) {
                                Spacer(Modifier.height(if (costLine == null) Space.S5.dp else Space.S2.dp))
                                KitText(
                                    heartbeatLine,
                                    Modifier.testTag("phone-setup-progress-heartbeat"),
                                    role = TypeRole.RowTitle,
                                    tone = TextTone.Secondary,
                                    textAlign = TextAlign.Center,
                                )
                                if (showQuietNote) {
                                    Spacer(Modifier.height(Space.S1.dp))
                                    KitText(
                                        stringResource(Res.string.setupHeartbeatQuiet),
                                        role = TypeRole.Secondary,
                                        tone = TextTone.Tertiary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            if (note != null) {
                                Spacer(
                                    Modifier.height(
                                        if (costLine == null && heartbeatLine == null) Space.S5.dp else Space.S2.dp,
                                    ),
                                )
                                KitText(
                                    note,
                                    Modifier.widthIn(max = Layout.CONTROL_MAX_WIDTH.dp),
                                    role = TypeRole.Secondary,
                                    tone = TextTone.Tertiary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            batteryNotice()
                            if (
                                state.failure != null &&
                                    state.confirmStop == null &&
                                    !state.confirmRemoveVoice
                            ) {
                                Spacer(Modifier.height(Space.S3.dp))
                                KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
                            }
                            Box(Modifier.padding(top = Space.S5.dp).widthIn(max = Layout.CONTROL_MAX_WIDTH.dp)) {
                                actionFoot()
                            }
                        }
                        Box(Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start)) {
                            WideJourneyPartsPanel(state, rows, done)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WideJourneyPartsPanel(
    state: SetupJourneyUi,
    rows: List<JourneyComponentProgress>,
    doneCount: Int,
) {
    val progress = state.progress
    val headerMeta =
        if (rows.isNotEmpty()) {
            stringResource(Res.string.teamUiHomeRunProgress, doneCount, rows.size)
        } else {
            null
        }
    KitSurfacePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.S2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitText(stringResource(Res.string.phoneSetupRingDetailsTitle), role = TypeRole.Headline)
                headerMeta?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
            }
            KitDivider()
            rows.forEachIndexed { index, component ->
                if (index > 0) KitDivider()
                val fallback =
                    state.options?.components?.firstOrNull { it.id == component.id }?.title
                        ?: component.id
                WideJourneyPartRow(component, fallback)
            }
            if (
                progress.canContinue &&
                    progress.state == JourneySetupState.Failed &&
                    state.systemKill == null &&
                    !nodeCannotRun(progress)
            ) {
                KitDivider()
                Spacer(Modifier.height(Space.S3.dp))
                KitText(
                    stringResource(Res.string.phoneSetupRingStoppedNote),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
            if (state.details != null) {
                KitDivider()
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = state.details,
                )
            }
        }
    }
}

@Composable
private fun WideJourneyPartRow(component: JourneyComponentProgress, fallbackTitle: String) {
    val title = itemTitle(component.id, fallbackTitle)
    val description = componentWhy(component.id)
    val measured = journeyPartProgressWords(component)
    when (component.state) {
        JourneyComponentState.Done, JourneyComponentState.Skipped ->
            KitRow(
                title = title,
                supporting = description,
                leading = KitRowLeading.Glyph(KitIcons.checkCircle),
                leadingTone = TextTone.Success,
                meta = stringResource(Res.string.kitMarkDone),
                supportingMaxLines = 2,
            )
        JourneyComponentState.Running, JourneyComponentState.Checking ->
            KitRow(
                title = title,
                supporting = description,
                state = KitRowState(word = null, tone = KitRowStateTone.Running),
                meta =
                    listOfNotNull(stringResource(Res.string.kitMarkWorking), measured)
                        .joinToString(" · "),
                supportingMaxLines = 2,
            )
        JourneyComponentState.Failed ->
            KitRow(
                title = title,
                supporting = description,
                leading = KitRowLeading.Glyph(KitIcons.error),
                leadingTone = TextTone.Primary,
                meta = stringResource(Res.string.kitMarkFailed),
                supportingMaxLines = 2,
            )
        JourneyComponentState.Pending ->
            KitRow(
                title = title,
                supporting = description,
                leading = KitRowLeading.Glyph(KitIcons.clock),
                leadingTone = TextTone.Tertiary,
                meta = stringResource(Res.string.kitMarkWaiting),
                supportingMaxLines = 2,
            )
    }
}

@Composable
private fun journeyPartProgressWords(component: JourneyComponentProgress): String? {
    val total = component.bytesTotal
    val done = component.bytesDone
    if (total != null && total > 0 && done != null) {
        val (received, whole) = SetupProgressRules.formatBytePair(done, total)
        return stringResource(Res.string.setupProgressViewBytes, received, whole)
    }
    val pct = component.percent?.takeIf { component.state == JourneyComponentState.Running }
    if (pct != null) {
        return stringResource(
            Res.string.setupProgressViewPercent,
            pct.coerceIn(0.0, PERCENT_MAX.toDouble()).roundToInt(),
        )
    }
    return null
}
