package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.bootstrapFailedBody
import io.github.eslamasabry.oc.ui.words.res.bootstrapFailedTitle
import io.github.eslamasabry.oc.ui.words.res.bootstrapOpeningBody
import io.github.eslamasabry.oc.ui.words.res.bootstrapOpeningTitle
import io.github.eslamasabry.oc.ui.words.res.bootstrapResetFailedBody
import io.github.eslamasabry.oc.ui.words.res.bootstrapResetFailedTitle
import io.github.eslamasabry.oc.ui.words.res.bootstrapResettingBody
import io.github.eslamasabry.oc.ui.words.res.bootstrapResettingTitle
import io.github.eslamasabry.oc.ui.words.res.bootstrapStartFresh
import io.github.eslamasabry.oc.ui.words.res.bootstrapStartFreshBody
import io.github.eslamasabry.oc.ui.words.res.bootstrapStartFreshConfirm
import io.github.eslamasabry.oc.ui.words.res.bootstrapStartFreshTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveProgress
import org.jetbrains.compose.resources.stringResource

/**
 * The opening gate (07 §5 A01): drawn by the shell before the graph, on the first frame. Arranges kit parts only.
 * Fixed words, never an exception text; a slow wait escalates at 8 s inside the kit's state view.
 */
@Composable
public fun OpeningGateScreen(state: OpeningGateUi, actions: OpeningGateActions) {
    if (state is OpeningGateUi.Open) return
    val retry = stringResource(Res.string.commonRetry)
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        when (state) {
            is OpeningGateUi.Waiting ->
                KitStateView(
                    kind = KitStateKind.Loading,
                    // TODO(words): "Updating your app…" (S2-CHATS words request); the move line stands in.
                    title = stringResource(if (state.updating) Res.string.openCode2MoveProgress else Res.string.bootstrapOpeningTitle),
                    body = if (state.updating) null else stringResource(Res.string.bootstrapOpeningBody),
                    since = state.since,
                    onSlow = listOf(KitAction(retry, actions::retry)),
                )
            is OpeningGateUi.Failed -> {
                KitStateView(
                    kind = KitStateKind.Error,
                    title = stringResource(Res.string.bootstrapFailedTitle),
                    body = stringResource(Res.string.bootstrapFailedBody),
                    primary = KitAction(retry, actions::retry),
                    secondary = KitAction(stringResource(Res.string.kitReportProblem), actions::reportProblem),
                    tertiary = listOf(KitAction(stringResource(Res.string.bootstrapStartFresh), actions::askStartFresh)),
                )
                KitConfirm(
                    spec =
                        if (state.confirming) {
                            KitConfirmSpec(
                                title = stringResource(Res.string.bootstrapStartFreshTitle),
                                confirmLabel = stringResource(Res.string.bootstrapStartFreshConfirm),
                                kind = KitConfirmKind.Destructive,
                                body = stringResource(Res.string.bootstrapStartFreshBody),
                            )
                        } else {
                            null
                        },
                    onConfirm = actions::confirmStartFresh,
                    onCancel = actions::cancelStartFresh,
                )
            }
            OpeningGateUi.Resetting ->
                KitStateView(
                    kind = KitStateKind.Working,
                    title = stringResource(Res.string.bootstrapResettingTitle),
                    body = stringResource(Res.string.bootstrapResettingBody),
                )
            OpeningGateUi.ResetFailed ->
                KitStateView(
                    kind = KitStateKind.Error,
                    title = stringResource(Res.string.bootstrapResetFailedTitle),
                    body = stringResource(Res.string.bootstrapResetFailedBody),
                    primary = KitAction(retry, actions::retry),
                    secondary = KitAction(stringResource(Res.string.kitReportProblem), actions::reportProblem),
                )
            OpeningGateUi.Open -> Unit
        }
    }
}
