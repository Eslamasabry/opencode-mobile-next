package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveProblem
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A protected history move has a visible retry and an exit to the server list. */
@Composable
public fun ManagedMoveScreen(state: ManagedMoveUi, actions: ManagedMoveActions) {
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        KitStateView(
            kind = if (state.running) KitStateKind.Working else KitStateKind.Error,
            title = stringResource(Res.string.openCode2MoveProgress),
            body =
                state.problem?.let {
                    stringResource(
                        when (it) {
                            ManagedMoveProblem.SeparateHistory ->
                                Res.string.openCode2MoveFailedSeparateHistory
                            ManagedMoveProblem.NotReady -> Res.string.openCode2MoveFailedNotReady
                            ManagedMoveProblem.NotConfirmed ->
                                Res.string.openCode2MoveFailedNotConfirmed
                            ManagedMoveProblem.Stopped -> Res.string.openCode2MoveFailedStopped
                        }
                    )
                },
            primary =
                if (state.running) null
                else KitAction(stringResource(Res.string.commonRetry), actions::retry),
            secondary =
                KitAction(stringResource(Res.string.serverSwitcherTitle), actions::changeServer),
        )
    }
}
