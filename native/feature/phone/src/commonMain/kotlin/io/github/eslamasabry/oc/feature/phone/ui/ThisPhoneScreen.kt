package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.engine.runtime.storage.HostStorageAvailability
import io.github.eslamasabry.oc.engine.runtime.storage.StoragePressure
import io.github.eslamasabry.oc.feature.phone.presentation.ServerRow
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneConfirm
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneNotice
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.feature.phone.presentation.UpdateRow
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.andrStorageAvailable
import io.github.eslamasabry.oc.ui.words.res.andrStorageHostSizes
import io.github.eslamasabry.oc.ui.words.res.andrStorageHostUnknown
import io.github.eslamasabry.oc.ui.words.res.andrStorageMeasure
import io.github.eslamasabry.oc.ui.words.res.andrStorageMeasuring
import io.github.eslamasabry.oc.ui.words.res.andrStorageNearlyFull
import io.github.eslamasabry.oc.ui.words.res.andrStorageTitle
import io.github.eslamasabry.oc.ui.words.res.andrStorageUnknown
import io.github.eslamasabry.oc.ui.words.res.keepRunningOpenFailed
import io.github.eslamasabry.oc.ui.words.res.keepRunningRowSubtitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningTitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRow
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRowDetail
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardUpdate
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardVersion
import io.github.eslamasabry.oc.ui.words.res.runningPhoneRowIdle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneRowNearLimit
import io.github.eslamasabry.oc.ui.words.res.runningPhoneTitle
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.thisPhoneBusy
import io.github.eslamasabry.oc.ui.words.res.thisPhoneRestartBusy
import io.github.eslamasabry.oc.ui.words.res.thisPhoneRestartRow
import io.github.eslamasabry.oc.ui.words.res.thisPhoneRestartTitle
import io.github.eslamasabry.oc.ui.words.res.thisPhoneStop
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpToDate
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpdate
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpdateBody
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpdateBusy
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpdateDetail
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpdateKept
import io.github.eslamasabry.oc.ui.words.res.thisPhoneUpdateTitle
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/** The runtime's name in "Update OpenCode?" (the product name, not copy). */
private const val OPENCODE = "OpenCode"

/**
 * PhoneRoute.ThisPhone (plan 07 F01). One answer at the top, its one act at the thumb, then the
 * rows ordered by likelihood: the server's own acts, the version, Running on this phone, Keep
 * running. Arranges kit parts only (K1).
 */
@Composable
public fun ThisPhoneScreen(state: ThisPhoneUi, actions: ThisPhoneActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    val back = stringResource(Res.string.kitTopBarBack)
    val busyWords = stringResource(Res.string.thisPhoneBusy)
    val busy = state.working != null
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.settingsV3ThisPhone),
                exit = KitTopBarExit.Back,
                exitLabel = back,
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        bottom = {
            state.answer.primary?.let { act ->
                KitActionBlock(
                    primary =
                        KitAction(
                            label = act.words(),
                            onClick = { actions.act(act) },
                            working = busy,
                        )
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            KitNotice(
                message = state.answer.line?.words() ?: state.answer.headline.words(),
                title = if (state.answer.line != null) state.answer.headline.words() else null,
                tone = state.answer.tone.status(),
            )
            when (val notice = state.notice) {
                null -> Unit
                is ThisPhoneNotice.Failed -> PhoneNoticeLine(notice.op.failedWords(), actions)
                ThisPhoneNotice.UpdateBusy ->
                    PhoneNoticeLine(stringResource(Res.string.thisPhoneUpdateBusy), actions)
                ThisPhoneNotice.SettingsMissing ->
                    PhoneNoticeLine(stringResource(Res.string.keepRunningOpenFailed), actions)
            }
            if (state.serverRows.isNotEmpty() || state.version != null) {
                KitRowGroup {
                    state.serverRows.forEach { row ->
                        when (row) {
                            ServerRow.Stop ->
                                KitRow(
                                    title = stringResource(Res.string.thisPhoneStop),
                                    enabled = !busy,
                                    disabledReason = busyWords,
                                    onClick = actions::stopServer,
                                )
                            ServerRow.Restart ->
                                KitRow(
                                    title = stringResource(Res.string.thisPhoneRestartRow),
                                    enabled = !busy,
                                    disabledReason = busyWords,
                                    onClick = actions::askRestart,
                                )
                        }
                    }
                    state.version?.let { version ->
                        val installed = version.installed
                        when (val update = version.update) {
                            UpdateRow.UpToDate ->
                                if (installed != null)
                                    KitRow(
                                        title =
                                            stringResource(Res.string.thisPhoneUpToDate, installed)
                                    )
                            is UpdateRow.Available,
                            is UpdateRow.Failed -> {
                                installed?.let {
                                    KitRow(
                                        title =
                                            stringResource(Res.string.phoneServerCardVersion, it)
                                    )
                                }
                                val offered =
                                    if (update is UpdateRow.Available) update.version
                                    else (update as UpdateRow.Failed).version
                                KitRow(
                                    title = stringResource(Res.string.thisPhoneUpdate),
                                    supporting =
                                        stringResource(Res.string.thisPhoneUpdateDetail, offered),
                                    enabled = !busy,
                                    disabledReason = busyWords,
                                    onClick = actions::askUpdate,
                                )
                            }
                            UpdateRow.Updating ->
                                KitRow(
                                    title = stringResource(Res.string.phoneServerCardUpdate),
                                    enabled = false,
                                    disabledReason = busyWords,
                                )
                            null ->
                                installed?.let {
                                    KitRow(
                                        title =
                                            stringResource(Res.string.phoneServerCardVersion, it)
                                    )
                                }
                        }
                    }
                }
            }
            if (state.storageSupported) {
                val storage = state.storage
                val bytes = storage?.availableBytes
                val host = storage?.builtinHost
                KitNotice(
                    title = stringResource(Res.string.andrStorageTitle),
                    message =
                        when {
                            // Unknown only after a reading finished; the first one is still running.
                            storage == null && state.measuringStorage ->
                                stringResource(Res.string.andrStorageMeasuring)
                            bytes == null -> stringResource(Res.string.andrStorageUnknown)
                            storage?.pressure == StoragePressure.NearlyFull ->
                                stringResource(
                                    Res.string.andrStorageNearlyFull,
                                    PhoneSizes.text(bytes),
                                )
                            else ->
                                stringResource(
                                    Res.string.andrStorageAvailable,
                                    PhoneSizes.text(bytes),
                                )
                        },
                    tone =
                        if (storage?.pressure == StoragePressure.NearlyFull) StatusTone.Attention
                        else StatusTone.Neutral,
                    notes =
                        when {
                            host != null ->
                                listOf(
                                    stringResource(
                                        Res.string.andrStorageHostSizes,
                                        PhoneSizes.text(host.runtimeBytes),
                                        PhoneSizes.text(host.projectsBytes),
                                    )
                                )
                            storage?.builtinAvailability == HostStorageAvailability.Unavailable ->
                                listOf(stringResource(Res.string.andrStorageHostUnknown))
                            else -> emptyList()
                        },
                    actions =
                        listOf(
                            KitLineAction(
                                if (state.measuringStorage)
                                    stringResource(Res.string.andrStorageMeasuring)
                                else stringResource(Res.string.andrStorageMeasure),
                                actions::measureStorage,
                                enabled = !state.measuringStorage,
                                disabledReason = stringResource(Res.string.andrStorageMeasuring),
                            )
                        ),
                )
            }
            KitRowGroup {
                val running = state.running
                val count = running.count
                KitRow(
                    title = stringResource(Res.string.runningPhoneTitle),
                    supporting =
                        if (running.nearLimit && count != null) {
                            stringResource(Res.string.runningPhoneRowNearLimit, count, running.cap)
                        } else {
                            stringResource(Res.string.runningPhoneRowIdle)
                        },
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openRunning,
                )
                KitRow(
                    title = stringResource(Res.string.keepRunningTitle),
                    supporting = stringResource(Res.string.keepRunningRowSubtitle),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openKeepRunning,
                )
                KitRow(
                    title = stringResource(Res.string.linuxMgrRow),
                    supporting = stringResource(Res.string.linuxMgrRowDetail),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openLinux,
                )
            }
        }
    }
    KitConfirm(
        spec =
            when (val asked = state.confirm) {
                null -> null
                is ThisPhoneConfirm.Restart ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.thisPhoneRestartTitle),
                        confirmLabel = stringResource(Res.string.thisPhoneRestartRow),
                        kind = KitConfirmKind.Neutral,
                        body =
                            if (asked.replyRunning) stringResource(Res.string.thisPhoneRestartBusy)
                            else null,
                    )
                is ThisPhoneConfirm.Update ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.thisPhoneUpdateTitle, OPENCODE),
                        confirmLabel = stringResource(Res.string.thisPhoneUpdate),
                        body = stringResource(Res.string.thisPhoneUpdateBody, asked.version),
                        consequences = listOf(stringResource(Res.string.thisPhoneUpdateKept)),
                    )
            },
        onConfirm = actions::confirm,
        onCancel = actions::dismiss,
    )
}

@Composable
private fun PhoneNoticeLine(words: String, actions: ThisPhoneActions) {
    KitNotice(
        message = words,
        dismiss =
            KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss),
    )
}
