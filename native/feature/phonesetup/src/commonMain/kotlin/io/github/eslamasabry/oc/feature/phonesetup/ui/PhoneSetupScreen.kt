package io.github.eslamasabry.oc.feature.phonesetup.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupComponentIds
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.engine.runtime.messages.SetupMessages
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupPhoneMood
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressTone
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxRuntimeNodeTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneGroup__agentHelper
import io.github.eslamasabry.oc.ui.words.res.setupAgentUserStep
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.phoneServerPortBusy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorCannotStart
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorChecksum
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorInstall
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorInterrupted
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoInternet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoSpace
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNodeCannotRun
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOffline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeNoProgram
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeNoStart
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeWontRun
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorPackageRepair
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupLinuxTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartChecking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartGigabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartMegabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromise
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromiseNoSize
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartScreenTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewGettingStarted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewOverallLabel
import io.github.eslamasabry.oc.ui.words.res.voiceComponentTitle
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/**
 * PhoneSetupRoute.Setup (Flutter phone_setup_start_screen.dart + phone_setup_progress_screen.dart):
 * the start shows what is installed and the preflight (B-LINUX-209..211), then the job's weighted
 * bar with its stage and an honest ETA (B-LINUX-201), and a failure as one plain line with Details
 * (B-LINUX-203, B-SEC-166), Continue and Stop.
 */
@Composable
public fun PhoneSetupScreen(state: PhoneSetupUi, actions: PhoneSetupActions) {
    val progress = state.progress
    val wide = LocalKitWindow.current.cls.isWide
    val pageTitle =
        stringResource(
            if (progress == null) Res.string.phoneSetupStartScreenTitle
            else Res.string.phoneSetupProgressTitle
        )
    KitScreen(
        topBar = {
            KitTopBar(
                title = if (wide) "" else pageTitle,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = if (wide) KitScreenWidth.Full else KitScreenWidth.Reading,
        loading = state.checking,
        edges = if (wide) KitScreenEdges.Gutter else KitScreenEdges.Bleed,
    ) {
        if (wide) {
            WideTwoColumnFrame(
                pageTitle = pageTitle,
                verticalAlignment = Alignment.Top,
                start = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                        if (progress == null) Start(state, actions, wide = true)
                        else Job(progress, state, actions, wide = true)
                    }
                },
                end = {
                    WidePartsPanel(
                        items = wideSetupParts(state, progress),
                        showDoneCount = progress != null,
                        showStoppedNote = progress != null && progress.canContinue,
                    )
                },
            )
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(kitPageScrollState())
                    .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                if (progress == null) Start(state, actions) else Job(progress, state, actions)
            }
        }
    }
    SetupStopConfirm(state, actions)
}

@Composable
private fun Start(state: PhoneSetupUi, actions: PhoneSetupActions, wide: Boolean = false) {
    if (wide) {
        WideArtworkBay(
            when {
                state.preflight?.supported == false -> KitSetupPhoneMood.Stopped
                state.starting -> KitSetupPhoneMood.Starting
                else -> KitSetupPhoneMood.Fresh
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitText(stringResource(Res.string.phoneSetupStartHeadline), role = TypeRole.Title)
            KitText(
                when {
                    state.checking -> stringResource(Res.string.phoneSetupStartChecking)
                    state.downloadBytes > 0 ->
                        stringResource(Res.string.phoneSetupStartPromise, sizeWords(state.downloadBytes))
                    else -> stringResource(Res.string.phoneSetupStartPromiseNoSize)
                },
                tone = TextTone.Secondary,
            )
        }
        if (state.checking) return
        if (state.preflight?.supported != false) WideStartStepList()
    } else {
        KitText(stringResource(Res.string.phoneSetupStartHeadline))
        if (state.checking) {
            KitText(stringResource(Res.string.phoneSetupStartChecking))
            return
        }
        val size = sizeWords(state.downloadBytes)
        KitText(
            if (state.downloadBytes > 0) stringResource(Res.string.phoneSetupStartPromise, size)
            else stringResource(Res.string.phoneSetupStartPromiseNoSize)
        )
    }
    StartPreflightNotices(state.preflight, actions)
    StartControls(state, actions, wide)
}

@Composable
private fun Job(
    progress: SetupProgress,
    state: PhoneSetupUi,
    actions: PhoneSetupActions,
    wide: Boolean = false,
) {
    val current = progress.components.firstOrNull { it.id == progress.current }
    val stage =
        current?.stage
            ?: if (progress.state == SetupState.Running)
                stringResource(Res.string.setupProgressViewGettingStarted)
            else null
    val pct = (progress.overall * PERCENT_MAX).roundToInt().coerceIn(0, PERCENT_MAX)
    val bar =
        KitProgress.Known(
            value = progress.overall.toFloat().coerceIn(0f, 1f),
            eta = progress.etaSeconds?.seconds,
        )
    val tone =
        when (progress.state) {
            SetupState.Failed -> KitProgressTone.Failed
            SetupState.Running,
            SetupState.Done -> KitProgressTone.Moving
            else -> KitProgressTone.Stopped
        }
    if (wide) {
        SetupJobWideHeader(progress, stage, bar, tone, pct)
    } else {
        KitProgressView(
            bar,
            semanticsLabel = stringResource(Res.string.setupProgressViewOverallLabel),
            tone = tone,
        )
        KitText("$pct%")
        stage?.let { KitText(setupMessageWords(it)) }
    }
    when (progress.state) {
        SetupState.Running -> SetupRunningControls(progress, state, actions, wide)
        SetupState.Done -> SetupDoneControls(actions, wide)
        SetupState.Failed,
        SetupState.Interrupted,
        SetupState.Cancelled -> SetupFailedPanel(progress, state, actions, wide)
        SetupState.Idle -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FailedActionRow(primary: KitAction, secondary: KitAction?, wide: Boolean) {
    if (wide) {
        FlowRow(
            Modifier.padding(top = Space.S4.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitButton(primary, role = KitButtonRole.Primary, expand = false)
            secondary?.let { KitButton(it, role = KitButtonRole.Secondary, expand = false) }
        }
    } else {
        KitActionBlock(primary = primary, secondary = secondary)
    }
}

@Composable
private fun wideSetupParts(state: PhoneSetupUi, progress: SetupProgress?): List<WidePartItem> {
    if (progress == null) {
        return state.items.map { WidePartItem(it.id, itemTitle(it.id, it.title), WidePartStatus.Start) }
    }
    val byId = progress.components.associateBy { it.id }
    val fallbackState = if (progress.state == SetupState.Done) ComponentState.Done else ComponentState.Pending
    val seen = mutableSetOf<String>()
    val entries = buildList {
        for (item in state.items) {
            seen += item.id
            add(item.title to (byId[item.id] ?: ComponentProgress(item.id, fallbackState)))
        }
        for (component in progress.components) {
            if (seen.add(component.id)) add(component.id to component)
        }
    }
    return entries.map { (fallback, comp) -> widePartEntry(fallback, comp) }
}

internal const val PERCENT_MAX = 100

/** The kit row state (word and tone mark), never a stand-in character. */
@Composable
internal fun rowState(state: ComponentState): KitRowState? =
    when (state) {
        ComponentState.Done,
        ComponentState.Skipped -> KitRowState(stringResource(Res.string.kitMarkDone), KitRowStateTone.Quiet)
        ComponentState.Running,
        ComponentState.Checking -> KitRowState(stringResource(Res.string.kitMarkWorking), KitRowStateTone.Running)
        ComponentState.Failed -> KitRowState(stringResource(Res.string.kitMarkFailed), KitRowStateTone.Failed)
        ComponentState.Pending -> null
    }

/**
 * A component's name in the person's language; Python, Node.js and OpenCode are names and stay as
 * they are.
 */
@Composable
internal fun itemTitle(id: String, fallback: String): String =
    when (id) {
        SetupComponentIds.LINUX -> stringResource(Res.string.phoneSetupLinuxTitle)
        SetupComponentIds.ESSENTIALS -> stringResource(Res.string.phoneSetupEssentialsTitle)
        SetupComponentIds.START -> stringResource(Res.string.phoneSetupStartTitle)
        SetupComponentIds.VOICE -> stringResource(Res.string.voiceComponentTitle)
        else -> agentComponentTitle(id) ?: fallback
    }

/** The names the app uses elsewhere for the parts of an agents' install job (never the raw component id). */
@Composable
private fun agentComponentTitle(id: String): String? =
    when {
        id == "agent-node" -> stringResource(Res.string.linuxRuntimeNodeTitle)
        id == "agent-user" -> stringResource(Res.string.setupAgentUserStep)
        id == "agent-paseo" -> stringResource(Res.string.runningPhoneGroup__agentHelper)
        id.startsWith("agent-") -> AgentCatalog.builtIn.byId(AgentId(id.removePrefix("agent-")))?.name
        else -> null
    }

/**
 * The job's problem in plain words (app_en.arb phoneSetupError*); the technical reason stays under
 * Details.
 */
@Composable
internal fun problemWords(problem: SetupProblem): String =
    when (problem) {
        is SetupProblem.NoSpace -> stringResource(Res.string.phoneSetupErrorNoSpace, problem.name)
        SetupProblem.Interrupted -> stringResource(Res.string.phoneSetupErrorInterrupted)
        is SetupProblem.Offline -> stringResource(Res.string.phoneSetupErrorOffline, problem.name)
        SetupProblem.NoInternet -> stringResource(Res.string.phoneSetupErrorNoInternet)
        is SetupProblem.Checksum -> stringResource(Res.string.phoneSetupErrorChecksum, problem.name)
        is SetupProblem.Install -> stringResource(Res.string.phoneSetupErrorInstall, problem.name)
        SetupProblem.PackageRepair -> stringResource(Res.string.phoneSetupErrorPackageRepair)
        is SetupProblem.PortBusy ->
            stringResource(Res.string.phoneServerPortBusy, problem.port.toString())
        SetupProblem.CannotStart,
        SetupProblem.RuntimeOwnershipUnknown ->
            stringResource(Res.string.phoneSetupErrorCannotStart)
        SetupProblem.OpenCodeNoProgram ->
            stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
        SetupProblem.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
        SetupProblem.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
        SetupProblem.NodeCannotRun -> stringResource(Res.string.phoneSetupErrorNodeCannotRun)
        is SetupProblem.StartFailed ->
            SetupMessages.plain(problem.details)?.let { setupMessageWords(it) }
                ?: stringResource(Res.string.setupProgressViewFailedUnknown)
    }

/** "~350 MB" / "1.2 GB" (decimal units, as the Flutter start screen). */
@Composable
internal fun sizeWords(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) {
        stringResource(
            Res.string.phoneSetupStartGigabytes,
            ((mb / 100).roundToInt() / 10.0).toString(),
        )
    } else {
        stringResource(
            Res.string.phoneSetupStartMegabytes,
            mb.roundToInt().coerceAtLeast(1).toString(),
        )
    }
}
