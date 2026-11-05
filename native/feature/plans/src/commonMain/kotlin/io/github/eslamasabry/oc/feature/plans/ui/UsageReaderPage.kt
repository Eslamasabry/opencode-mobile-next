package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Source: usage/usage_read_from_screen.dart; the existing reader owns all consent and checks. */
@Composable
internal fun UsageReaderBody(
    state: PlansManagementUi,
    plans: PlansUi,
    actions: PlansManagementActions,
) {
    val words = plansWords()
    val reader = state.readers[state.readerId]
    if (reader == null || reader.problem == PlanReaderProblem.Gone) {
        KitStateView(
            KitStateKind.Missing,
            stringResource(Res.string.usageReadFromGoneTitle),
            body = stringResource(Res.string.usageReadFromGoneBody),
        )
        return
    }
    val local = reader.kind == PlanReaderKind.InAppUbuntu
    val name = state.readerName
    val working = reader.busy || state.busy
    val hold =
        if (working) KitEnabled.Disabled(stringResource(Res.string.m40UsageChecking))
        else KitEnabled.Enabled
    val good =
        !working &&
            reader.phase == PlanReaderPhase.Ready &&
            reader.problem == null &&
            state.failure == null
    var submitted by rememberSaveable(reader.id) { mutableStateOf(false) }
    var observedWork by remember(reader.id) { mutableStateOf(false) }
    LaunchedEffect(submitted, working, good, reader.problem, state.failure) {
        if (submitted && working) observedWork = true
        if (submitted && observedWork && !working) {
            submitted = false
            observedWork = false
            if (local && good) actions.dismiss()
        }
    }
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val providers =
        plans.cards
            .filter { it.fix == UsageFix.SetUp && it.fixConnection?.id == reader.id }
            .map { it.providerId }
            .distinct()
            .joinToString(", ") { words.providerName(it) }
    Column(gutter.padding(top = Space.S2.dp, bottom = Space.S6.dp)) {
        if (providers.isNotEmpty()) {
            KitText(
                stringResource(Res.string.usageReadFromSignedIn, providers, name),
                role = TypeRole.Headline,
            )
            Spacer(Modifier.height(Space.S2.dp))
        }
        KitText(
            if (local) stringResource(Res.string.usageReadFromBodyInApp)
            else stringResource(Res.string.usageComputerExplain, name)
        )
        if (!local) {
            Spacer(Modifier.height(Space.S3.dp))
            KitDetailsFold(
                label = stringResource(Res.string.usageComputerWhatRuns),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                notes =
                    listOf(
                        stringResource(Res.string.usageComputerCommandLabel) +
                            ": " +
                            stringResource(Res.string.m40UsageReadCommand),
                        stringResource(Res.string.usageComputerReadsLabel) +
                            ": " +
                            stringResource(Res.string.usageComputerReadsValue, name),
                    ),
            )
        }
        if (working) {
            Spacer(Modifier.height(Space.S3.dp))
            KitText(
                if (!local) stringResource(Res.string.usageComputerChecking, name)
                else
                    when (reader.phase) {
                        PlanReaderPhase.Copying -> stringResource(Res.string.quotaInAppStepCopying)
                        PlanReaderPhase.SignIns -> stringResource(Res.string.quotaInAppStepSignIns)
                        PlanReaderPhase.Starting ->
                            stringResource(Res.string.quotaInAppStepStarting)
                        PlanReaderPhase.Reading -> stringResource(Res.string.quotaInAppStepReading)
                        else -> stringResource(Res.string.quotaInAppStepChecking, name)
                    },
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        } else if (reader.problem != null) {
            Spacer(Modifier.height(Space.S3.dp))
            KitErrorNotice(readerProblem(reader.problem, local, name))
        } else if (!local && good) {
            Spacer(Modifier.height(Space.S3.dp))
            val found =
                reader.foundProviderIds.distinct().joinToString(", ") {
                    words.providerName(it)
                }
            KitText(
                if (found.isEmpty()) stringResource(Res.string.usageComputerFoundNone, name)
                else stringResource(Res.string.usageComputerFound, found, name)
            )
        } else if (!local && reader.enabled) {
            Spacer(Modifier.height(Space.S3.dp))
            KitText(
                stringResource(Res.string.usageComputerOnNote, name),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        if (state.failure != null) {
            Spacer(Modifier.height(Space.S3.dp))
            KitErrorNotice(
                stringResource(Res.string.usageSaveFailed),
                failure = state.failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
            )
        }
        Spacer(Modifier.height(Space.S4.dp))
        KitActionBlock(
            primary =
                KitAction(
                    label =
                        when {
                            local -> stringResource(Res.string.usageReadFromSetUpInApp)
                            reader.enabled && good -> stringResource(Res.string.usageComputerDone)
                            reader.enabled -> stringResource(Res.string.usageComputerReadNow)
                            else -> stringResource(Res.string.usageComputerTurnOn)
                        },
                    onClick = {
                        if (!local && reader.enabled && good) actions.dismiss()
                        else {
                            submitted = true
                            observedWork = false
                            if (local || !reader.enabled) actions.readerStart()
                            else actions.readerRead()
                        }
                    },
                    enabled = hold,
                    working = working,
                ),
            tertiary =
                buildList {
                    if (local || !reader.enabled)
                        add(
                            KitAction(
                                stringResource(Res.string.usageNotNow),
                                actions::dismiss,
                                enabled = hold,
                            )
                        )
                    else {
                        add(
                            KitAction(
                                stringResource(Res.string.usageComputerTurnOff),
                                actions::readerOff,
                                enabled = hold,
                            )
                        )
                        if (good)
                            add(
                                KitAction(
                                    stringResource(Res.string.usageComputerReadNow),
                                    {
                                        submitted = true
                                        observedWork = false
                                        actions.readerRead()
                                    },
                                    enabled = hold,
                                )
                            )
                    }
                },
        )
    }
}

@Composable
private fun readerProblem(problem: PlanReaderProblem, local: Boolean, name: String): String =
    if (local)
        when (problem) {
            PlanReaderProblem.LinuxNotReady -> stringResource(Res.string.quotaInAppFailLinux, name)
            PlanReaderProblem.NodeMissing -> stringResource(Res.string.quotaInAppFailNoNode, name)
            PlanReaderProblem.NodeTooOld -> stringResource(Res.string.quotaInAppFailNodeOld, name)
            PlanReaderProblem.Prepare -> stringResource(Res.string.quotaInAppFailPrepare, name)
            PlanReaderProblem.Start -> stringResource(Res.string.quotaInAppFailStart, name)
            PlanReaderProblem.Remove -> stringResource(Res.string.quotaInAppFailRemove, name)
            else -> stringResource(Res.string.m40UsageNoAnswer)
        }
    else
        when (problem) {
            PlanReaderProblem.NodeMissing,
            PlanReaderProblem.NodeTooOld -> stringResource(Res.string.usageComputerNeedsNode, name)
            PlanReaderProblem.ServerTooOld ->
                stringResource(Res.string.usageComputerServerTooOld, name)
            PlanReaderProblem.Unreachable -> stringResource(Res.string.usageComputerOffline, name)
            PlanReaderProblem.SaveFailed -> stringResource(Res.string.usageSaveFailed)
            else -> stringResource(Res.string.usageComputerNoAnswer, name)
        }
