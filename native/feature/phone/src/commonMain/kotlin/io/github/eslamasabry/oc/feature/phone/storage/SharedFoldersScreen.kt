package io.github.eslamasabry.oc.feature.phone.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.manageSpaceTryAgain
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersAll
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersAllDetail
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersAllRisk
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersAndroid
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersAndroidDetail
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersFailed
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersGranted
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersProtection
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersRestart
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersSelected
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersTitle
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersWorking
import io.github.eslamasabry.oc.ui.words.res.storageAccessRefusedBody
import org.jetbrains.compose.resources.stringResource

/**
 * Android permission and Linux mount scope are separate. Widening is an explicit visible choice,
 * never a recovery side effect.
 */
@Composable
public fun SharedFoldersScreen(state: SharedFoldersUi, actions: SharedFoldersActions) {
    LifecycleStartEffect(actions) {
        actions.refresh()
        onStopOrDispose {}
    }
    val wait = stringResource(Res.string.sharedFoldersWorking)
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.sharedFoldersTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            KitNotice(stringResource(Res.string.sharedFoldersSelected))
            if (state.failed)
                KitNotice(
                    stringResource(Res.string.sharedFoldersFailed),
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.manageSpaceTryAgain),
                                actions::refresh,
                            )
                        ),
                )
            if (state.declined) KitNotice(stringResource(Res.string.storageAccessRefusedBody))
            state.state?.let { scope ->
                if (scope.restartRequired)
                    KitNotice(
                        stringResource(Res.string.sharedFoldersRestart),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.settingsV3ThisPhone),
                                    actions::openThisPhone,
                                )
                            ),
                    )
                KitRowGroup {
                    KitRow(
                        title = stringResource(Res.string.sharedFoldersAndroid),
                        supporting =
                            stringResource(
                                if (scope.permissionGranted == true) Res.string.sharedFoldersGranted
                                else Res.string.sharedFoldersAndroidDetail
                            ),
                        enabled = !state.busy,
                        disabledReason = wait,
                        onClick =
                            if (scope.permissionGranted == true) null
                            else actions::allowAndroidAccess,
                    )
                    if (scope.canAllowAllFolders)
                        KitSwitchRow(
                            title = stringResource(Res.string.sharedFoldersAll),
                            supporting = stringResource(Res.string.sharedFoldersAllDetail),
                            risk = stringResource(Res.string.sharedFoldersAllRisk),
                            checked = scope.allFolders,
                            enabled =
                                if (state.busy) KitEnabled.Disabled(wait) else KitEnabled.Enabled,
                            onCheckedChange = actions::allowAllFolders,
                        )
                }
                if (!scope.canAllowAllFolders)
                    KitNotice(stringResource(Res.string.sharedFoldersProtection))
            }
        }
    }
}
