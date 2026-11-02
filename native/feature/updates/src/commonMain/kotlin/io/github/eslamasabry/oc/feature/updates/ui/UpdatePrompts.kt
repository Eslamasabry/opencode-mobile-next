package io.github.eslamasabry.oc.feature.updates.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.updates.presentation.NoticeUi
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateNoticeActions
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appUpdateCancel
import io.github.eslamasabry.oc.ui.words.res.appUpdateDownloadingTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstall
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstallReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstalling
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.appUpdateNow
import io.github.eslamasabry.oc.ui.words.res.appUpdateOpenSettings
import io.github.eslamasabry.oc.ui.words.res.appUpdatePercent
import io.github.eslamasabry.oc.ui.words.res.appUpdatePermissionNeeded
import io.github.eslamasabry.oc.ui.words.res.appUpdateReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.appUpdateVerifying
import io.github.eslamasabry.oc.ui.words.res.appUpdateWhatsNew
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import org.jetbrains.compose.resources.stringResource

private const val NOTICE_ID = "update:release"

/**
 * The update's words for the one status line (Flutter `AppUpdateNotice._status`), for a screen or shell that passes
 * a [KitStatus] into `KitScreen(status = …)` so the line competes with the other conditions by priority.
 */
@Composable
public fun updateStatus(notice: NoticeUi, actions: UpdateNoticeActions): KitStatus {
    val notNow = stringResource(Res.string.appUpdateNotNow)
    return when (notice) {
        is NoticeUi.Ready ->
            KitStatus(
                kind = KitStatusKind.Update,
                message = stringResource(Res.string.appUpdateReadyTitle, notice.version),
                id = NOTICE_ID,
                action = KitLineAction(stringResource(Res.string.appUpdateUpdate), actions::onUpdate, testTag = "update-notice-update"),
                onDismiss = actions::onNotNow,
                dismissLabel = notNow,
            )
        is NoticeUi.Downloading ->
            KitStatus(
                kind = KitStatusKind.Update,
                message = stringResource(Res.string.appUpdateDownloadingTitle, notice.version),
                id = NOTICE_ID,
                tone = StatusTone.Progress,
                supporting = notice.percent?.let { stringResource(Res.string.appUpdatePercent, it.toString()) },
                action = KitLineAction(stringResource(Res.string.appUpdateCancel), actions::onCancel, testTag = "update-notice-cancel"),
            )
        NoticeUi.Verifying ->
            KitStatus(
                kind = KitStatusKind.Update,
                message = stringResource(Res.string.appUpdateVerifying),
                id = NOTICE_ID,
                tone = StatusTone.Progress,
            )
        NoticeUi.Installing ->
            KitStatus(
                kind = KitStatusKind.Update,
                message = stringResource(Res.string.appUpdateInstalling),
                id = NOTICE_ID,
                tone = StatusTone.Progress,
            )
        is NoticeUi.ReadyToInstall ->
            KitStatus(
                kind = KitStatusKind.Update,
                message = stringResource(Res.string.appUpdateInstallReadyTitle, notice.version),
                id = NOTICE_ID,
                supporting = if (notice.needsPermission) stringResource(Res.string.appUpdatePermissionNeeded) else null,
                action = KitLineAction(stringResource(Res.string.appUpdateInstall), actions::onInstall, testTag = "update-notice-install"),
                onDismiss = actions::onNotNow,
                dismissLabel = notNow,
            )
    }
}

/** The update notice drawn as the kit's one status line. */
@Composable
public fun UpdateNotice(notice: NoticeUi, actions: UpdateNoticeActions, modifier: Modifier = Modifier) {
    KitStatusLine(updateStatus(notice, actions), modifier)
}

/**
 * The sheet the Settings gear opens while a release waits (Flutter `showKitUpdateSheet`; B: "a tap asks: Update now
 * or Open Settings"): "Version 2.2.0 is ready", the release's first plain sentence, then Update now or Open Settings.
 * Each choice closes the sheet ([onDismiss]) and then acts; Back, Close and the scrim only close it and choose nothing.
 * Needs a KitSheetHost above it (KitScreen places one).
 */
@Composable
public fun UpdateOfferSheet(
    version: String,
    onUpdateNow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    summary: String = "",
    visible: Boolean = true,
    onWhatsNew: (() -> Unit)? = null,
) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.appUpdateReadyTitle, version),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary =
            KitAction(
                stringResource(Res.string.appUpdateNow),
                {
                    onDismiss()
                    onUpdateNow()
                },
            ),
        secondary =
            KitAction(
                stringResource(Res.string.appUpdateOpenSettings),
                {
                    onDismiss()
                    onOpenSettings()
                },
            ),
        tertiary = listOfNotNull(onWhatsNew?.let { KitAction(stringResource(Res.string.appUpdateWhatsNew), it) }),
    ) {
        if (summary.isNotEmpty()) KitText(summary)
    }
}
