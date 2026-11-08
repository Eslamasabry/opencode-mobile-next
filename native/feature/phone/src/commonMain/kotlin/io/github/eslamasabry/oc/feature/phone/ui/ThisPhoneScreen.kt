package io.github.eslamasabry.oc.feature.phone.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.state.KitStatusAnswer
import io.github.eslamasabry.oc.ui.kit.state.KitAnswerTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.words.res.firstRunAgentOpenCode
import io.github.eslamasabry.oc.ui.words.res.thisPhoneHostInApp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReadiness
import io.github.eslamasabry.oc.engine.runtime.storage.HostStorageAvailability
import io.github.eslamasabry.oc.engine.runtime.storage.StoragePressure
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.ServerRow
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneConfirm
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneNotice
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.feature.phone.presentation.UpdateRow
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitButton
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
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.linuxExitCheckStatus
import io.github.eslamasabry.oc.ui.words.res.linuxExitCrash
import io.github.eslamasabry.oc.ui.words.res.linuxExitForceStop
import io.github.eslamasabry.oc.ui.words.res.linuxExitKilled
import io.github.eslamasabry.oc.ui.words.res.linuxExitMemory
import io.github.eslamasabry.oc.ui.words.res.linuxExitMemoryLimit
import io.github.eslamasabry.oc.ui.words.res.linuxExitPolicyOff
import io.github.eslamasabry.oc.ui.words.res.linuxExitRestored
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRow
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRowDetail
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardUpdate
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardVersion
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartAddTitle
import io.github.eslamasabry.oc.ui.words.res.r40PhoneConnect
import io.github.eslamasabry.oc.ui.words.res.runningPhoneRowIdle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneRowNearLimit
import io.github.eslamasabry.oc.ui.words.res.runningPhoneTitle
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.sharedFoldersTitle
import io.github.eslamasabry.oc.ui.words.res.stateLifecycleChecking
import io.github.eslamasabry.oc.ui.words.res.stateLifecyclePartial
import io.github.eslamasabry.oc.ui.words.res.stateLifecycleReady
import io.github.eslamasabry.oc.ui.words.res.stateLifecycleStopped
import io.github.eslamasabry.oc.ui.words.res.stateLifecycleUnavailable
import io.github.eslamasabry.oc.ui.words.res.stateLifecycleUnreachable
import io.github.eslamasabry.oc.ui.words.res.stateStorageEmpty
import io.github.eslamasabry.oc.ui.words.res.stateStorageFailed
import io.github.eslamasabry.oc.ui.words.res.stateStoragePrivate
import io.github.eslamasabry.oc.ui.words.res.stateStorageProject
import io.github.eslamasabry.oc.ui.words.res.stateStorageRefresh
import io.github.eslamasabry.oc.ui.words.res.stateStorageSavedServers
import io.github.eslamasabry.oc.ui.words.res.stateStorageServer
import io.github.eslamasabry.oc.ui.words.res.stateStorageTitle
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesB
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesGb
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesKb
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesMb
import io.github.eslamasabry.oc.ui.words.res.thisPhoneBusy
import io.github.eslamasabry.oc.ui.words.res.thisPhoneExportProjects
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
import io.github.eslamasabry.oc.ui.words.res.workRunawaySeeRunning
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The runtime's name in "Update OpenCode?" (the product name, not copy). */
private const val OPENCODE = "OpenCode"

/**
 * PhoneRoute.ThisPhone (plan 07 F01). One answer at the top, its one act at the thumb, then the
 * rows ordered by likelihood: the server's own acts, the version, Running on this phone, Keep
 * running. Arranges kit parts only (K1).
 */
@Composable
public fun ThisPhoneScreen(
    state: ThisPhoneUi,
    actions: ThisPhoneActions,
    onAddTools: (() -> Unit)? = null,
) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    val back = stringResource(Res.string.kitTopBarBack)
    val busyWords = stringResource(Res.string.thisPhoneBusy)
    var storageOpen by remember { mutableStateOf(false) }
    val busy = state.working != null
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(busy, state.serverRows, state.confirm) {
        if (busy || ServerRow.Stop !in state.serverRows || state.confirm != null) stopping = false
    }
    val installed = state.details.installed || state.version != null || state.serverRows.isNotEmpty()
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
        loading = state.answer.headline == AnswerHeadline.Checking,
        bottom = {
            state.answer.primary?.let { act ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KitButton(KitAction(label = act.words(), onClick = { actions.act(act) }, working = busy), fullWidth = true, hero = true)
                }
            }
        },
        edges = KitScreenEdges.Bleed,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.exitNotice?.let { exit ->
                KitNotice(
                    modifier = noticeEdge,
                    title =
                        stringResource(
                            when (exit.kind) {
                                PhoneExitKind.ForceStop -> Res.string.linuxExitForceStop
                                PhoneExitKind.LowMemory -> Res.string.linuxExitMemory
                                PhoneExitKind.MemoryLimit -> Res.string.linuxExitMemoryLimit
                                PhoneExitKind.Crash -> Res.string.linuxExitCrash
                                PhoneExitKind.Killed -> Res.string.linuxExitKilled
                            }
                        ),
                    message =
                        stringResource(
                            when {
                                state.exitSaysRunning -> Res.string.linuxExitRestored
                                !exit.recoveryAllowed -> Res.string.linuxExitPolicyOff
                                else -> Res.string.linuxExitCheckStatus
                            }
                        ),
                    actions =
                        if (exit.kind == PhoneExitKind.Crash) emptyList()
                        else
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.keepRunningTitle),
                                    actions::openKeepRunning,
                                )
                            ),
                    dismiss =
                        KitLineAction(
                            stringResource(Res.string.workspaceDismissNotice),
                            actions::dismiss,
                        ),
                )
            }
            Box(Modifier.fillMaxWidth().heightIn(min = if (installed) 0.dp else available)
                .padding(vertical = Space.S6.dp), contentAlignment = Alignment.Center) {
                KitStatusAnswer(
                    answer = state.answer.headline.words(),
                    line = state.answer.line?.words(),
                    action = if (installed && state.details.canConnect) KitAction(stringResource(Res.string.r40PhoneConnect), actions::connect, enabled = io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled.of(!busy, busyWords)) else null,
                    tone = when (state.answer.tone) {
                        AnswerTone.Ok -> KitAnswerTone.Ok
                        AnswerTone.Working -> KitAnswerTone.Working
                        AnswerTone.Off -> KitAnswerTone.Off
                        AnswerTone.Paused -> KitAnswerTone.Paused
                        AnswerTone.NeedsYou -> KitAnswerTone.NeedsYou
                    },
                )
            }
            if (installed) {
            KitRowGroup(leadingTiles = true) {
                KitRow(title = stringResource(Res.string.firstRunAgentOpenCode),
                    supporting = stringResource(Res.string.thisPhoneHostInApp),
                    leading = KitRowLeading.Glyph(KitIcons.phone))
            }
            state.recoveryNotice
                ?.let { readiness ->
                    KitNotice(
                        modifier = noticeEdge,
                        message =
                            stringResource(
                                when (readiness) {
                                    LifecycleReadiness.Unchecked,
                                    LifecycleReadiness.Checking -> Res.string.stateLifecycleChecking
                                    LifecycleReadiness.Unavailable ->
                                        Res.string.stateLifecycleUnavailable
                                    LifecycleReadiness.Stopped -> Res.string.stateLifecycleStopped
                                    LifecycleReadiness.Unreachable ->
                                        Res.string.stateLifecycleUnreachable
                                    LifecycleReadiness.Partial -> Res.string.stateLifecyclePartial
                                    LifecycleReadiness.Ready -> Res.string.stateLifecycleReady
                                }
                            ),
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismissRecovery,
                            ),
                    )
                }
            when (val notice = state.notice) {
                null -> Unit
                is ThisPhoneNotice.Failed -> PhoneNoticeLine(notice.op.failedWords(), actions)
                ThisPhoneNotice.UpdateBusy ->
                    PhoneNoticeLine(stringResource(Res.string.thisPhoneUpdateBusy), actions)
                ThisPhoneNotice.SettingsMissing ->
                    PhoneNoticeLine(stringResource(Res.string.keepRunningOpenFailed), actions)
            }
            if (state.serverRows.isNotEmpty()) {
                Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                KitRowGroup(leadingTiles = true) {
                    state.serverRows.forEach { row ->
                        when (row) {
                            ServerRow.Stop ->
                                KitRow(
                                    title = stringResource(Res.string.thisPhoneStop),
                                    leading = KitRowLeading.Glyph(KitIcons.stopCircle),
                                    enabled = !busy,
                                    disabledReason = busyWords,
                                    onClick = { stopping = true },
                                )
                            ServerRow.Restart ->
                                KitRow(
                                    title = stringResource(Res.string.thisPhoneRestartRow),
                                    leading = KitRowLeading.Glyph(KitIcons.restart),
                                    enabled = !busy,
                                    disabledReason = busyWords,
                                    onClick = actions::askRestart,
                                )
                        }
                    }
                }
            }
            // Flutter this-phone-free-model: replies from OpenCode's free model are the slow ones, said where this
            // server is looked after, with the way out.
            if (state.freeModel) {
                Spacer(Modifier.height(Space.S3.dp))
                KitNotice(
                    modifier = noticeEdge.testTag("this-phone-free-model"),
                    message = stringResource(Res.string.freeModelNotice),
                    icon = KitIcons.speed,
                    liveRegion = false,
                    actions = listOf(KitLineAction(stringResource(Res.string.freeModelSignIn), actions::addApiKey)),
                )
            }
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                state.leftover?.let { notice ->
                    KitNotice(
                        modifier = noticeEdge,
                        message = notice.message.words(),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.workRunawaySeeRunning),
                                    actions::openRunning,
                                )
                            ),
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismiss,
                            ),
                    )
                }
            KitRowGroup(leadingTiles = true) {
                // F: lib/ui/screens/this_phone_screen.dart:1116-1135: the version row (up to date, or Update) leads
                // the list, under the server group, not inside it.
                state.version?.let { version ->
                    val installed = version.installed
                    when (val update = version.update) {
                        UpdateRow.UpToDate ->
                            if (installed != null)
                                KitRow(
                                    title =
                                        stringResource(Res.string.thisPhoneUpToDate, installed),
                                    leading = KitRowLeading.Glyph(KitIcons.check),
                                )
                        is UpdateRow.Available,
                        is UpdateRow.Failed -> {
                            val offered =
                                if (update is UpdateRow.Available) update.version
                                else (update as UpdateRow.Failed).version
                            KitRow(
                                title = stringResource(Res.string.thisPhoneUpdate),
                                leading = KitRowLeading.Glyph(KitIcons.download),
                                trailing = KitRowTrailing.Chevron,
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
                val running = state.running
                val count = running.count
                if ((count ?: 0) > 0 || running.status != null || running.nearLimit) KitRow(
                    title = stringResource(Res.string.runningPhoneTitle),
                    leading = KitRowLeading.Glyph(KitIcons.terminal),
                    supporting =
                        if (running.status != null) {
                            running.status.words()
                        } else if (running.nearLimit && count != null) {
                            stringResource(Res.string.runningPhoneRowNearLimit, count, running.cap)
                        } else {
                            stringResource(Res.string.runningPhoneRowIdle)
                        },
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openRunning,
                )
                if (onAddTools != null) KitRow(
                    title = stringResource(Res.string.thisPhoneAddTools),
                    supporting = stringResource(Res.string.thisPhoneAddToolsDetail),
                    leading = KitRowLeading.Glyph(KitIcons.tools),
                    trailing = KitRowTrailing.Chevron,
                    enabled = !busy, disabledReason = busyWords, onClick = onAddTools,
                )
                PhoneInstalledPanel(state, actions)
                KitRow(
                    title = stringResource(Res.string.linuxMgrRow),
                    leading = KitRowLeading.Glyph(KitIcons.terminal),
                    supporting = stringResource(Res.string.linuxMgrRowDetail),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openLinux,
                )
                PhoneReplySpeed(state)
                KitRow(
                    title = stringResource(Res.string.thisPhoneExportProjects),
                    leading = KitRowLeading.Glyph(KitIcons.zip),
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.act(PhoneAct.ExportProjects) },
                )
                KitRow(
                    title = stringResource(Res.string.sharedFoldersTitle),
                    leading = KitRowLeading.Glyph(KitIcons.folders),
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.act(PhoneAct.SharedFolders) },
                )
                KitRow(
                    title = stringResource(Res.string.keepRunningTitle),
                    leading = KitRowLeading.Glyph(KitIcons.batteryCharging),
                    supporting = stringResource(Res.string.keepRunningRowSubtitle),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openKeepRunning,
                )
                KitRow(
                    title = stringResource(Res.string.libraryTerminalTitle),
                    leading = KitRowLeading.Glyph(KitIcons.terminal),
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openTerminal,
                )
            }
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            PhoneDetailsPanel(state, actions)
            if (state.storage != null || state.storageSupported || state.storageFailed) KitExpandRow(
                title = stringResource(Res.string.stateStorageTitle), expanded = storageOpen,
                onExpandedChange = { storageOpen = it }, leading = KitIcons.storage,
            ) {
            state.storage?.let { storage ->
                KitRowGroup(label = stringResource(Res.string.stateStorageTitle)) {
                    if (
                        storage.projects.isEmpty() &&
                            !storage.serverInstalled &&
                            storage.serverBytes == 0L
                    )
                        KitRow(title = stringResource(Res.string.stateStorageEmpty))
                    KitRow(
                        title = stringResource(Res.string.stateStorageServer),
                        supporting = storageSize(storage.serverBytes),
                    )
                    KitRow(
                        title = stringResource(Res.string.stateStoragePrivate),
                        supporting = storageSize(storage.privateDataBytes),
                    )
                    for (project in storage.projects) KitRow(
                        title = project.name,
                        supporting =
                            pluralStringResource(
                                Res.plurals.stateStorageProject,
                                project.files,
                                storageSize(project.bytes),
                                project.files,
                                project.privateFiles,
                            ),
                    )
                    KitRow(
                        title =
                            pluralStringResource(
                                Res.plurals.stateStorageSavedServers,
                                storage.savedServers,
                                storage.savedServers,
                            )
                    )
                    KitRow(
                        title = stringResource(Res.string.stateStorageRefresh),
                        onClick = actions::refreshStorage,
                    )
                }
            }
            if (state.storageFailed)
                KitNotice(
                    message = stringResource(Res.string.stateStorageFailed),
                    dismiss =
                        KitLineAction(
                            stringResource(Res.string.stateStorageRefresh),
                            actions::refreshStorage,
                        ),
                )
            if (state.storageSupported) {
                val storage = state.phoneStorage
                val bytes = storage?.availableBytes
                val host = storage?.builtinHost
                KitNotice(
                    title = stringResource(Res.string.andrStorageTitle),
                    message =
                        when {
                            // Unknown only after a reading finished; the first one is still
                            // running.
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
            }
            }
        }
        }
        PhoneConfirmation(state, actions, stopping, onStopDismiss = { stopping = false })
    }

}

@Composable
private fun PhoneConfirmation(
    state: ThisPhoneUi,
    actions: ThisPhoneActions,
    stopping: Boolean,
    onStopDismiss: () -> Unit,
) {
    KitConfirm(
        spec =
            if (stopping)
                KitConfirmSpec(
                    title = stringResource(Res.string.safetyStopLocalServerTitle),
                    body = stringResource(Res.string.safetyStopLocalServerBody),
                    confirmLabel = stringResource(Res.string.e7SetupStopLocal),
                    cancelLabel = stringResource(Res.string.safetyStopLocalServerKeep),
                    kind = KitConfirmKind.Stop,
                )
            else
                when (val asked = state.confirm) {
                    null -> null
                    is ThisPhoneConfirm.Restart ->
                        KitConfirmSpec(
                            title = stringResource(Res.string.thisPhoneRestartTitle),
                            confirmLabel = stringResource(Res.string.thisPhoneRestartRow),
                            // A restart ends a running reply: then it is a stop, with Keep running as the way back.
                            kind = if (asked.replyRunning) KitConfirmKind.Stop else KitConfirmKind.Neutral,
                            body =
                                if (asked.replyRunning)
                                    stringResource(Res.string.thisPhoneRestartBusy)
                                else null,
                        )
                    is ThisPhoneConfirm.Update ->
                        KitConfirmSpec(
                            title = stringResource(Res.string.thisPhoneUpdateTitle, OPENCODE),
                            confirmLabel = stringResource(Res.string.thisPhoneUpdate),
                            kind = KitConfirmKind.Neutral,
                            body = stringResource(Res.string.thisPhoneUpdateBody, asked.version),
                            consequences = listOf(stringResource(Res.string.thisPhoneUpdateKept)),
                        )
                },
        onConfirm = {
            if (stopping) {
                onStopDismiss()
                if (state.working == null && ServerRow.Stop in state.serverRows)
                    actions.stopServer()
            } else actions.confirm()
        },
        onCancel = { if (stopping) onStopDismiss() else actions.dismiss() },
        working = state.working != null,
    )
}

@Composable
private fun PhoneNoticeLine(words: String, actions: ThisPhoneActions) {
    KitNotice(
        modifier = noticeEdge,
        message = words,
        dismiss =
            KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss),
    )
}

/**
 * P-115: KitScreen's body runs edge to edge (rows carry their own gutter), so a notice on this page
 * starts its info mark on the page gutter, the rows' one start edge (16 dp), not on the screen edge.
 * Notices inside the storage fold already sit on the fold's gutter.
 */
private val noticeEdge: Modifier
    @Composable get() = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)

@Composable
private fun storageSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L ->
            stringResource(
                Res.string.termuxStorageBytesGb,
                "${bytes / 1_073_741_824L}.${bytes % 1_073_741_824L * 10 / 1_073_741_824L}",
            )
        bytes >= 1_048_576L ->
            stringResource(
                Res.string.termuxStorageBytesMb,
                "${bytes / 1_048_576L}.${bytes % 1_048_576L * 10 / 1_048_576L}",
            )
        bytes >= 1_024L -> stringResource(Res.string.termuxStorageBytesKb, "${bytes / 1_024L}")
        else -> stringResource(Res.string.termuxStorageBytesB, "$bytes")
    }
