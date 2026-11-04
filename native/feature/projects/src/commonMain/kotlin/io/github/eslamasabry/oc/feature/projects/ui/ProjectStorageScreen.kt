package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.projects.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A question about this folder, only when it needs phone storage access. */
@Composable
public fun ProjectStorageScreen(state: ProjectStorageUi, actions: ProjectStorageActions) {
    val restart = state.status as? ProjectStorageStatus.NeedsRestart
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (restart != null) Res.string.storageRestartTitle
                        else Res.string.storageAccessTitle
                    ),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = state.checking,
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(
                stringResource(
                    if (restart != null) Res.string.storageRestartBody
                    else Res.string.storageAccessBody
                )
            )
            if (restart != null)
                KitText(
                    stringResource(
                        if (restart.workRunning) Res.string.storageRestartBusy
                        else Res.string.storageRestartPause
                    )
                )
            else {
                KitText(stringResource(Res.string.storageAccessWhyScope))
                KitText(stringResource(Res.string.storageAccessWhyAgent))
                KitText(stringResource(Res.string.storageAccessWhyOff))
            }
            state.failure?.let {
                KitErrorNotice(
                    stringResource(
                        if (restart != null) Res.string.storageRestartFailedBody
                        else Res.string.storageAccessRefusedBody
                    ),
                    failure = it,
                )
            }
            if (state.refused) KitText(stringResource(Res.string.storageAccessRefusedBody))
            KitButton(
                stringResource(
                    if (restart != null) Res.string.storageRestartConfirm
                    else Res.string.storageAccessAllow
                ),
                if (restart != null) actions::restart else actions::allow,
                working = state.checking,
            )
            KitButton(
                stringResource(Res.string.storageAccessNotNow),
                actions::back,
                role = KitButtonRole.Secondary,
            )
            KitButton(
                stringResource(Res.string.storageAccessUseAppSpace),
                actions::useAppSpace,
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.kitTryAgain),
                actions::retry,
                role = KitButtonRole.Tertiary,
            )
        }
    }
}
