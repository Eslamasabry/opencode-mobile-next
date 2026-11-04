package io.github.eslamasabry.oc.feature.desktopupdates.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopCheckPhase
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopUpdatesActions
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopUpdatesUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckNow
import io.github.eslamasabry.oc.ui.words.res.appUpdateChecking
import io.github.eslamasabry.oc.ui.words.res.appUpdateCurrentVersion
import io.github.eslamasabry.oc.ui.words.res.appUpdateNeverChecked
import io.github.eslamasabry.oc.ui.words.res.appUpdateOpenRelease
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.desktopReleaseAvailable
import io.github.eslamasabry.oc.ui.words.res.desktopReleaseCurrent
import io.github.eslamasabry.oc.ui.words.res.desktopReleaseFailed
import io.github.eslamasabry.oc.ui.words.res.desktopReleaseLimit
import io.github.eslamasabry.oc.ui.words.res.desktopReleaseOpenFailed
import io.github.eslamasabry.oc.ui.words.res.desktopReleaseOpenQuestion
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop Settings › Updates: a bounded release check and an explicitly confirmed browser handoff.
 */
@Composable
public fun DesktopUpdatesScreen(state: DesktopUpdatesUi, actions: DesktopUpdatesActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.appUpdatesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onClose,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.appUpdateCurrentVersion, state.version),
                    supporting =
                        when (state.phase) {
                            DesktopCheckPhase.Never ->
                                stringResource(Res.string.appUpdateNeverChecked)
                            DesktopCheckPhase.Checking ->
                                stringResource(Res.string.appUpdateChecking)
                            DesktopCheckPhase.Current ->
                                stringResource(Res.string.desktopReleaseCurrent)
                            DesktopCheckPhase.Available ->
                                stringResource(
                                    Res.string.desktopReleaseAvailable,
                                    state.releaseTag.orEmpty(),
                                )
                            DesktopCheckPhase.Failed ->
                                stringResource(Res.string.desktopReleaseFailed)
                        },
                    supportingMaxLines = 3,
                    modifier = Modifier.testTag("desktop-updates-state"),
                )
            }
            Column(
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitButton(
                    stringResource(Res.string.appUpdateCheckNow),
                    actions::onCheck,
                    working = state.phase == DesktopCheckPhase.Checking,
                    modifier = Modifier.testTag("desktop-updates-check"),
                )
                if (state.releasePage != null) {
                    KitButton(
                        stringResource(Res.string.appUpdateOpenRelease),
                        actions::onOpenRelease,
                        role = KitButtonRole.Secondary,
                        modifier = Modifier.testTag("desktop-updates-open-release"),
                    )
                }
                if (state.openFailed) KitNotice(stringResource(Res.string.desktopReleaseOpenFailed))
            }
            KitGroupNote(stringResource(Res.string.desktopReleaseLimit))
        }
    }
    KitConfirm(
        spec =
            if (state.askingToOpen)
                KitConfirmSpec(
                    title = stringResource(Res.string.desktopReleaseOpenQuestion),
                    confirmLabel = stringResource(Res.string.appUpdateOpenRelease),
                    body = state.releasePage,
                )
            else null,
        onConfirm = actions::onConfirmOpen,
        onCancel = actions::onCancelOpen,
    )
}
