package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveProblem
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveFailedNotConfirmed
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveFailedNotReady
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveFailedSeparateHistory
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveFailedStopped
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveProgress
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveRetrying
import io.github.eslamasabry.oc.ui.words.res.openCode2UpgradeTitle
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardFailedTitle
import io.github.eslamasabry.oc.ui.words.res.serverSwitcherTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The first-launch move is drawn on kit ground in every language, with no raw diagnostics in its
 * main copy. A protected history move has a visible retry and an exit to the server list.
 */
@Composable
public fun ManagedMoveScreen(state: ManagedMoveUi, actions: ManagedMoveActions) {
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        Column(Modifier.testTag("managed-opencode2-upgrade")) {
            val problem = state.problem
            val changeServer =
                KitAction(stringResource(Res.string.serverSwitcherTitle), actions::changeServer)
            if (state.running || problem == null) {
                KitStateView(
                    kind = KitStateKind.Working,
                    title = stringResource(Res.string.openCode2UpgradeTitle),
                    body =
                        if (state.retry > 0)
                            stringResource(
                                Res.string.openCode2MoveRetrying,
                                state.retry,
                                state.retries,
                            )
                        else stringResource(Res.string.openCode2MoveProgress),
                    progress = KitProgress.Waiting(),
                    secondary = changeServer,
                )
            } else {
                KitStateView(
                    kind = KitStateKind.Error,
                    title = stringResource(Res.string.phoneServerCardFailedTitle),
                    body =
                        stringResource(
                            when (problem) {
                                ManagedMoveProblem.NotReady -> Res.string.openCode2MoveFailedNotReady
                                ManagedMoveProblem.SeparateHistory ->
                                    Res.string.openCode2MoveFailedSeparateHistory
                                ManagedMoveProblem.Stopped -> Res.string.openCode2MoveFailedStopped
                                ManagedMoveProblem.NotConfirmed ->
                                    Res.string.openCode2MoveFailedNotConfirmed
                            }
                        ),
                    primary =
                        if (problem == ManagedMoveProblem.SeparateHistory) null
                        else KitAction(stringResource(Res.string.commonRetry), actions::retry),
                    secondary = changeServer,
                )
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = state.failure?.details,
                )
            }
        }
    }
}
