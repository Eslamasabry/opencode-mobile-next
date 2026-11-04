package io.github.eslamasabry.oc.feature.preview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewActions
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewUi
import io.github.eslamasabry.oc.feature.preview.presentation.PagePhase
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBlock
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewEgress
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewNotice
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.header.KitChatMoreMenu
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.devPreviewAddress
import io.github.eslamasabry.oc.ui.words.res.devPreviewDetails
import io.github.eslamasabry.oc.ui.words.res.devPreviewFailedBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewLoading
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoHostBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoHostTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoServerBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewOpenInBrowser
import io.github.eslamasabry.oc.ui.words.res.devPreviewPortTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewReload
import io.github.eslamasabry.oc.ui.words.res.devPreviewRemoteNote
import io.github.eslamasabry.oc.ui.words.res.devPreviewRetry
import io.github.eslamasabry.oc.ui.words.res.devPreviewTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewUnsupportedTitle
import io.github.eslamasabry.oc.ui.words.res.e7SharedHTTPIsNotEncryptedOtherDevicesOn
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenHTTPLink
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenInsecureHTTPLink
import io.github.eslamasabry.oc.ui.words.res.externalLinkBlockedBody
import io.github.eslamasabry.oc.ui.words.res.externalLinkBlockedTitle
import io.github.eslamasabry.oc.ui.words.res.externalLinkDontOpen
import io.github.eslamasabry.oc.ui.words.res.externalLinkOpenFailedTitle
import io.github.eslamasabry.oc.ui.words.res.externalLinkOpensHost
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.previewQrLeaveTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource

/**
 * The in-app preview of a dev server (Flutter `DevPreviewScreen`, map page `dev-preview`): the
 * running page fills the screen under one slim bar ("Preview", Port 8080 under it, Refresh page,
 * Open in browser). Tapping the title opens Details: the real address and, for a remote server, the
 * "listen on 0.0.0.0" note. Back steps back through the page's own history first, then leaves. A
 * link to elsewhere never loads here; it asks to open in the browser.
 */
@Composable
public fun DevPreviewScreen(
    state: DevPreviewUi,
    actions: DevPreviewActions,
    slot: WebPreviewSlot,
    egress: PreviewEgress?,
    modifier: Modifier = Modifier,
    voices: List<KitComposerVoice> = emptyList(),
) {
    val handle = remember { WebPreviewHandle() }
    val ask = rememberTextFieldState()
    LaunchedEffect(state.askClear) { if (state.askClear > 0) ask.edit { replace(0, length, "") } }
    val voiceField =
        remember(actions) {
            KitVoiceField(
                key = "preview:${state.origin}",
                text = { ask.text.toString() },
                selection = { ask.selection.start until ask.selection.end },
                replace = { words -> ask.edit { replace(0, length, words) } },
                send = {},
                canSend = { false },
                dictationOnly = true,
            )
        }
    KitClipboardEffect(state.copied, state.copyText, actions::copied)
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(state.logsOpen, lifecycle) {
        if (state.logsOpen)
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    actions.refreshLogs()
                    delay(5_000)
                }
            }
    }
    val events =
        remember(actions) {
            object : WebPreviewEvents {
                override fun phase(phase: PagePhase) = actions.pagePhase(phase)

                override fun address(url: String) = actions.pageAddress(url)

                override fun canGoBack(can: Boolean) = actions.pageCanGoBack(can)

                override fun leave(url: String) = actions.pageLeave(url)
            }
        }
    val origin = state.origin
    val showing = origin != null && egress != null && slot.supported
    KitBackGuard(
        enabled = showing && state.canGoBack,
        onBack = { if (!handle.goBack()) actions.back() },
    )
    val reload = stringResource(Res.string.devPreviewReload)
    val browser = stringResource(Res.string.devPreviewOpenInBrowser)
    // The frame, its sheet and its confirmation share one modal layer (as the chat screen does).
    KitSheetHost(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            KitScreen(
                modifier = Modifier.fillMaxSize(),
                page = true,
                topBar = {
                    KitTopBar(
                        title = stringResource(Res.string.devPreviewTitle),
                        subtitle =
                            stringResource(Res.string.devPreviewPortTitle, state.port.toString()),
                        exit = KitTopBarExit.Back,
                        exitLabel = stringResource(Res.string.kitTopBarBack),
                        onExit = actions::back,
                        more =
                            if (origin != null)
                                ({ _ ->
                                    KitChatMoreMenu(
                                        label = stringResource(Res.string.kitTopBarMore),
                                        expanded = state.menu,
                                        items =
                                            listOf(
                                                KitMenuItem(
                                                    label =
                                                        stringResource(
                                                            Res.string.devPreviewDetails
                                                        ),
                                                    onSelect = { actions.showDetails(true) },
                                                )
                                            ),
                                        onOpen = { actions.menu(true) },
                                        onDismissRequest = { actions.menu(false) },
                                    )
                                })
                            else null,
                        actions =
                            if (origin == null) emptyList()
                            else
                                buildList {
                                    if (showing)
                                        add(
                                            KitLineAction(
                                                reload,
                                                { handle.reload() },
                                                testTag = "dev-preview-reload",
                                            )
                                        )
                                    add(
                                        KitLineAction(
                                            browser,
                                            actions::openInBrowser,
                                            testTag = "dev-preview-browser",
                                        )
                                    )
                                },
                    )
                },
                loading = showing && state.phase == PagePhase.Loading,
                loadingLabel = stringResource(Res.string.devPreviewLoading),
                bottom =
                    if (state.canAsk)
                        ({
                            Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                                KitField(
                                    ask,
                                    stringResource(Res.string.devPreviewAskHint),
                                    kind = KitFieldKind.Multiline,
                                    enabled =
                                        if (state.asking)
                                            KitEnabled.Disabled(
                                                stringResource(Res.string.serversWorking)
                                            )
                                        else KitEnabled.Enabled,
                                    multilineLines = 1..4,
                                )
                                voices.forEach { it.Panel(voiceField, Modifier.fillMaxWidth()) }
                                voices.forEach { it.Mic(voiceField, Modifier) }
                                KitActionBlock(
                                    primary =
                                        KitAction(
                                            stringResource(
                                                if (
                                                    state.delivery ==
                                                        io.github.eslamasabry.oc.feature.preview
                                                            .presentation
                                                            .PreviewSent
                                                            .RestartsTurn
                                                )
                                                    Res.string.devPreviewAskStopAndSend
                                                else Res.string.devPreviewAskSend
                                            ),
                                            { actions.ask(ask.text.toString()) },
                                            enabled =
                                                if (!state.asking && ask.text.isNotBlank())
                                                    KitEnabled.Enabled
                                                else
                                                    KitEnabled.Disabled(
                                                        stringResource(Res.string.devPreviewAskHint)
                                                    ),
                                            working = state.asking,
                                        )
                                )
                            }
                        })
                    else null,
            ) {
                Box(Modifier.fillMaxSize()) {
                    when {
                        state.blocked != null -> Blocked(state.blocked)
                        origin == null || egress == null -> Unit
                        !slot.supported ->
                            KitStateView(
                                kind = KitStateKind.Missing,
                                title = stringResource(Res.string.devPreviewUnsupportedTitle),
                                body = stringResource(Res.string.devPreviewUnsupportedBody),
                                primary = KitAction(browser, actions::openInBrowser),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        else ->
                            if (origin != null && egress != null) {
                                // The screen's tag sits on its own box so the slot's own semantics
                                // (and tags) stay intact.
                                Box(Modifier.fillMaxSize().testTag("dev-preview-page")) {
                                    slot.Page(
                                        origin,
                                        egress,
                                        handle,
                                        events,
                                        Modifier.fillMaxSize(),
                                    )
                                }
                                if (state.phase == PagePhase.Failed) {
                                    KitStateView(
                                        kind = KitStateKind.Error,
                                        title = stringResource(Res.string.devPreviewFailedTitle),
                                        body =
                                            stringResource(
                                                Res.string.devPreviewFailedBody,
                                                state.port.toString(),
                                            ),
                                        primary =
                                            KitAction(
                                                stringResource(Res.string.devPreviewRetry),
                                                { handle.reload() },
                                            ),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                    }
                    state.notice?.let { notice ->
                        KitNotice(
                            message =
                                stringResource(
                                    when (notice) {
                                        PreviewNotice.OpenFailed ->
                                            Res.string.externalLinkOpenFailedTitle
                                        PreviewNotice.LinkBlocked ->
                                            Res.string.externalLinkBlockedBody
                                        PreviewNotice.AskFailed -> Res.string.devPreviewAskNotSent
                                        PreviewNotice.Sent -> Res.string.devPreviewAskSent
                                        PreviewNotice.Joined -> Res.string.devPreviewAskSentJoins
                                        PreviewNotice.AfterReply ->
                                            Res.string.devPreviewAskSentAfter
                                        PreviewNotice.Restarted ->
                                            Res.string.devPreviewAskSentRestart
                                    }
                                ),
                            title =
                                if (notice == PreviewNotice.LinkBlocked)
                                    stringResource(Res.string.externalLinkBlockedTitle)
                                else null,
                            dismiss =
                                KitLineAction(
                                    stringResource(Res.string.kitSheetDismiss),
                                    actions::noticeShown,
                                ),
                            modifier =
                                Modifier.align(Alignment.TopCenter)
                                    .padding(
                                        horizontal = LocalKitTokens.current.gutter.dp,
                                        vertical = Space.S2.dp,
                                    ),
                        )
                    }
                    KitSheet(
                        visible = state.details,
                        onDismissRequest = { actions.showDetails(false) },
                        title = stringResource(Res.string.devPreviewDetails),
                        closeLabel = stringResource(Res.string.kitSheetClose),
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                        ) {
                            KitRow(
                                title = stringResource(Res.string.devPreviewAddress),
                                supporting = state.shown ?: origin.orEmpty(),
                                supportingMaxLines = 3,
                            )
                            KitButton(
                                stringResource(Res.string.devPreviewCopyAddress),
                                actions::copyAddress,
                                role = KitButtonRole.Tertiary,
                            )
                            if (state.logsAvailable)
                                KitButton(
                                    stringResource(Res.string.servicesLogs),
                                    { actions.showLogs(true) },
                                    role = KitButtonRole.Tertiary,
                                )
                            state.remoteHost?.let { host ->
                                KitNotice(
                                    stringResource(
                                        Res.string.devPreviewRemoteNote,
                                        state.port.toString(),
                                        host,
                                    )
                                )
                            }
                        }
                    }
                }
            }
            KitSheet(
                visible = state.logsOpen,
                onDismissRequest = { actions.showLogs(false) },
                title = stringResource(Res.string.servicesLogs),
                closeLabel = stringResource(Res.string.kitSheetClose),
            ) {
                if (state.logsFailed) KitNotice(stringResource(Res.string.servicesLogFailed))
                KitLogPanel(
                    state.logs,
                    rememberKitMask(),
                    actions::refreshLogs,
                    Modifier.fillMaxWidth().height(560.dp),
                )
            }
            KitConfirm(
                state.leaveHost?.let { host ->
                    KitConfirmSpec(
                        title =
                            stringResource(
                                if (state.leaveInsecure) Res.string.e7SharedOpenInsecureHTTPLink
                                else Res.string.previewQrLeaveTitle
                            ),
                        confirmLabel =
                            if (state.leaveInsecure) stringResource(Res.string.e7SharedOpenHTTPLink)
                            else browser,
                        kind =
                            if (state.leaveInsecure) KitConfirmKind.Destructive
                            else KitConfirmKind.Neutral,
                        consequences =
                            if (state.leaveInsecure)
                                listOf(
                                    stringResource(
                                        Res.string.e7SharedHTTPIsNotEncryptedOtherDevicesOn
                                    )
                                )
                            else emptyList(),
                        body = stringResource(Res.string.externalLinkOpensHost, host),
                        cancelLabel = stringResource(Res.string.externalLinkDontOpen),
                    )
                },
                onConfirm = actions::confirmLeave,
                onCancel = actions::cancelLeave,
            )
        }
    }
}

@Composable
private fun Blocked(why: PreviewBlock) {
    val noServer = why == PreviewBlock.NoServer
    KitStateView(
        kind = KitStateKind.Missing,
        title =
            stringResource(
                if (noServer) Res.string.devPreviewNoServerTitle
                else Res.string.devPreviewNoHostTitle
            ),
        body =
            stringResource(
                if (noServer) Res.string.devPreviewNoServerBody else Res.string.devPreviewNoHostBody
            ),
        modifier = Modifier.fillMaxSize(),
    )
}
