package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.agents.presentation.PhoneSignInController
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalUi
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun PhoneSignInScreen(
    state: SignInTerminalUi,
    pageHost: String?,
    actions: PhoneSignInController,
) {
    SignInTerminalScreen(
        state,
        actions,
        pageApproval = {
            KitConfirm(
                spec =
                    pageHost?.let {
                        KitConfirmSpec(
                            title = stringResource(Res.string.terminal40OpenPageQuestion),
                            confirmLabel = stringResource(Res.string.agentsSignInOpenPage),
                            body = stringResource(Res.string.terminal40OpenPageBody, it),
                        )
                    },
                onConfirm = actions::approvePage,
                onCancel = actions::dismissPage,
            )
        },
    )
}
