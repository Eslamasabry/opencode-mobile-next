package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRestartPhase
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderMode
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStepProgress
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTourStep
import io.github.eslamasabry.oc.ui.words.res.nlibChanged
import io.github.eslamasabry.oc.ui.words.res.nlibCopy
import io.github.eslamasabry.oc.ui.words.res.nlibCustomChangedByHand
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNotLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNotLoadedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedBusy
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedCheck
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedDeclared
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedFailed
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedHandKept
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedNotSetUp
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedUnreadable
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedUnsafe
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNotGoneBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNotGoneTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveRestartFailedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveRestartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartingBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartingTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepCheck
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepCheckGone
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepStart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepStop
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepsLabel
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableBody
import io.github.eslamasabry.oc.ui.words.res.nlibDetails
import org.jetbrains.compose.resources.stringResource

/**
 * The named steps of a custom-provider restart (DESIGN 5A with 5B's named step): waiting is not failing, so the
 * sheet names the step it is on and never an error before the 60th check. `restart()` is the phone host's one call
 * (stop, then the manual start), so the app reports two honest phases: restarting, with stop and start in flight,
 * and checking. The step label is heard by TalkBack only.
 */
@Composable
internal fun CustomProviderRestarting(
    name: String,
    expectGone: Boolean,
    phase: CustomProviderRestartPhase?,
) {
    KitNotice(
        stringResource(Res.string.nlibCustomRestartingBody),
        title = stringResource(Res.string.nlibCustomRestartingTitle),
        tone = StatusTone.Progress,
    )
    KitText(
        stringResource(Res.string.nlibCustomStepsLabel),
        role = TypeRole.Secondary,
        tone = TextTone.Secondary,
    )
    val checking = phase == CustomProviderRestartPhase.Checking
    val done = if (checking) 2 else 1
    KitStepProgress(RESTART_STEPS, done, label = stringResource(Res.string.kitTourStep, done + 1, RESTART_STEPS))
    val steps =
        listOf(
            stringResource(Res.string.nlibCustomStepStop),
            stringResource(Res.string.nlibCustomStepStart),
            stringResource(
                if (expectGone) Res.string.nlibCustomStepCheckGone else Res.string.nlibCustomStepCheck,
                name,
            ),
        )
    steps.forEachIndexed { index, step ->
        val now = if (checking) index == 2 else index < 2
        KitText(step, tone = if (now) TextTone.Primary else TextTone.Tertiary)
    }
}

/** The stable technical label (an exit status or a failure label), only inside Details. */
@Composable
internal fun CustomProviderDetails(detail: String) {
    KitDetailsFold(
        stringResource(Res.string.nlibDetails),
        stringResource(Res.string.nlibCopy),
        notes = listOf(detail),
    )
}

/** The result notice's title (naming the provider [name]) and body for [stage] in [mode]. */
@Composable
internal fun resultWords(
    stage: CustomProviderStage,
    name: String,
    mode: CustomProviderMode = CustomProviderMode.Add,
): Pair<String, String> =
    when {
        mode == CustomProviderMode.Remove && stage == CustomProviderStage.NotGoneAfterRestart ->
            stringResource(Res.string.nlibCustomRemoveNotGoneTitle, name) to
                stringResource(Res.string.nlibCustomRemoveNotGoneBody)
        mode == CustomProviderMode.Remove ->
            stringResource(Res.string.nlibCustomRemoveRestartFailedTitle, name) to
                stringResource(Res.string.nlibCustomRemoveRestartFailedBody)
        stage == CustomProviderStage.Loaded ->
            stringResource(Res.string.nlibCustomLoadedTitle, name) to
                stringResource(Res.string.nlibCustomLoadedBody)
        stage == CustomProviderStage.RestartFailed ->
            stringResource(Res.string.nlibCustomRestartFailedTitle, name) to
                stringResource(Res.string.nlibCustomRestartFailedBody)
        stage == CustomProviderStage.NotLoadedAfterRestart ->
            stringResource(Res.string.nlibCustomNotLoadedTitle, name) to
                stringResource(Res.string.nlibCustomNotLoadedBody)
        else ->
            stringResource(Res.string.nlibCustomSavedTitle, name) to
                stringResource(Res.string.nlibCustomSavedBody)
    }

internal fun resultTone(stage: CustomProviderStage): StatusTone =
    when (stage) {
        CustomProviderStage.Loaded -> StatusTone.Ok
        CustomProviderStage.Restarting -> StatusTone.Progress
        CustomProviderStage.RestartFailed,
        CustomProviderStage.NotLoadedAfterRestart,
        CustomProviderStage.NotGoneAfterRestart -> StatusTone.Attention
        else -> StatusTone.Neutral
    }

@Composable
internal fun refusalWords(refusal: CustomProviderRefusal): String =
    stringResource(
        when (refusal) {
            CustomProviderRefusal.Unavailable -> Res.string.nlibCustomUnavailableBody
            CustomProviderRefusal.Changed -> Res.string.nlibChanged
            CustomProviderRefusal.CouldNotCheck -> Res.string.nlibCustomRefusedCheck
            CustomProviderRefusal.NotSetUp -> Res.string.nlibCustomRefusedNotSetUp
            CustomProviderRefusal.Unreadable -> Res.string.nlibCustomRefusedUnreadable
            CustomProviderRefusal.HandManaged -> Res.string.nlibCustomRefusedHandKept
            CustomProviderRefusal.Unsafe -> Res.string.nlibCustomRefusedUnsafe
            CustomProviderRefusal.Busy -> Res.string.nlibCustomRefusedBusy
            CustomProviderRefusal.AlreadyDeclared -> Res.string.nlibCustomRefusedDeclared
            CustomProviderRefusal.ChangedByHand -> Res.string.nlibCustomChangedByHand
            CustomProviderRefusal.Failed -> Res.string.nlibCustomRefusedFailed
        }
    )

/** A custom-provider restart is three steps: stop, start, check. */
private const val RESTART_STEPS = 3
