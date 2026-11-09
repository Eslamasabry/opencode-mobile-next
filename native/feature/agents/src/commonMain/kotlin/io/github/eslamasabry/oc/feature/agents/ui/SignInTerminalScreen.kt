package io.github.eslamasabry.oc.feature.agents.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import io.github.eslamasabry.oc.feature.agents.signin.SignInStartStep
import io.github.eslamasabry.oc.ui.words.res.agentsSignInPaste
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStartAttach
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStartCheck
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStartConnect
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStartPrepare
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStartServer
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStartTerminal
import io.github.eslamasabry.oc.feature.agents.presentation.SignInStep
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalActions
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitPageState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentAccountApiKey
import io.github.eslamasabry.oc.ui.words.res.agentsSignInChecking
import io.github.eslamasabry.oc.ui.words.res.agentsAntigravitySignInIntro
import io.github.eslamasabry.oc.ui.words.res.agentsGeminiMigrationNote
import io.github.eslamasabry.oc.ui.words.res.agentsSignInCopyCode
import io.github.eslamasabry.oc.ui.words.res.agentsSignInDone
import io.github.eslamasabry.oc.ui.words.res.agentsSignInOpenPage
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStart
import io.github.eslamasabry.oc.ui.words.res.agentsSignInStopFailed
import io.github.eslamasabry.oc.ui.words.res.agentsSignInTerminalIntro
import io.github.eslamasabry.oc.ui.words.res.agentsSignInTerminalIntroOther
import io.github.eslamasabry.oc.ui.words.res.agentsSignInTerminalNotYet
import io.github.eslamasabry.oc.ui.words.res.agentsSignInTitle
import io.github.eslamasabry.oc.ui.words.res.agentsSignInUnavailable
import io.github.eslamasabry.oc.ui.words.res.agentsSignedIn
import io.github.eslamasabry.oc.ui.words.res.agentsSignedInBody
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkAction
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkBody
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.e7SetupCommandInput
import io.github.eslamasabry.oc.ui.words.res.e7SetupInputUnavailable
import io.github.eslamasabry.oc.ui.words.res.e7SetupNoOutput
import io.github.eslamasabry.oc.ui.words.res.e7SetupSendCommand
import io.github.eslamasabry.oc.ui.words.res.e7SetupTranscript
import io.github.eslamasabry.oc.ui.words.res.e7SharedHTTPIsNotEncryptedOtherDevicesOn
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenHTTPLink
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenInsecureHTTPLink
import io.github.eslamasabry.oc.ui.words.res.externalLinkDontOpen
import io.github.eslamasabry.oc.ui.words.res.kitCapPermNotificationsWhy
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.localTerminalFailedBody
import io.github.eslamasabry.oc.ui.words.res.localTerminalFailedTitle
import io.github.eslamasabry.oc.ui.words.res.localTerminalStarting
import io.github.eslamasabry.oc.ui.words.res.terminalScreenReadableMode
import org.jetbrains.compose.resources.stringResource

/**
 * G03 `AgentSignInTerminalScreen` (plan 07 §5 G, `AgentsRoute.SignInTerminal(agentId)`): the
 * agent's own sign-in in a live terminal with accessible mode (plan 06 §7.7), "Open sign-in page"
 * and "Copy code" above the terminal in both modes (17 §1.5), and the account check when it ends.
 * Arranges kit parts only.
 */
@Composable
public fun SignInTerminalScreen(
    state: SignInTerminalUi,
    actions: SignInTerminalActions,
    modifier: Modifier = Modifier,
    pageApproval: @Composable () -> Unit = {},
) {
    LaunchedEffect(state.step) {
        // Only the agent's account check can publish SignedIn. A clean terminal exit alone never
        // does.
        if (state.step == SignInStep.SignedIn) actions.leave()
    }
    KitScreen(
        modifier = modifier,
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.agentsSignInTitle, state.agentName),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::leave,
            )
        },
        page = true,
        loading =
            state.step == SignInStep.Preparing ||
                state.step == SignInStep.Starting ||
                state.step == SignInStep.Checking,
        loadingLabel =
            stringResource(
                if (state.step == SignInStep.Checking) Res.string.agentsSignInChecking
                else Res.string.localTerminalStarting
            ),
        header = {
            Column {
                KitText(
                    if (state.pastesBrowserCode)
                        stringResource(Res.string.agentsAntigravitySignInIntro)
                    else
                        stringResource(
                            if (state.usesClaudeAccount) Res.string.agentsSignInTerminalIntro
                            else Res.string.agentsSignInTerminalIntroOther,
                            state.agentName,
                        ),
                    Modifier.padding(
                        start = LocalKitTokens.current.gutter.dp,
                        top = Space.S1.dp,
                        end = LocalKitTokens.current.gutter.dp,
                        bottom = Space.S2.dp,
                    ),
                    tone = TextTone.Secondary,
                )
                // Gemini CLI's own sign-in may ask people to move to Antigravity: say what to do here.
                if (state.offersAntigravityNote && state.step != SignInStep.SignedIn)
                    KitNotice(
                        stringResource(Res.string.agentsGeminiMigrationNote),
                        Modifier.padding(
                            start = LocalKitTokens.current.gutter.dp,
                            end = LocalKitTokens.current.gutter.dp,
                            bottom = Space.S2.dp,
                        ),
                    )
            }
        },
        edges = KitScreenEdges.Bleed,
    ) {
        KitBackGuard(state.linkPrompt == null, actions::leave)
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (state.stopFailed) {
                KitPageState(
                    KitStateKind.Error,
                    stringResource(Res.string.agentsSignInStopFailed),
                    icon = KitIcons.error,
                    primary =
                        KitAction(stringResource(Res.string.commonRetry), actions::startAgain),
                )
            } else
                when (state.step) {
                    SignInStep.Preparing,
                    SignInStep.Starting -> Unit
                    SignInStep.Running -> Running(state, actions, Modifier.weight(1f))
                    SignInStep.Checking ->
                        if (state.terminal != null || state.transcript.isNotEmpty())
                            Running(state, actions, Modifier.weight(1f))
                    SignInStep.SignedIn ->
                        KitPageState(
                            KitStateKind.Finished,
                            stringResource(Res.string.agentsSignedIn),
                            icon = KitIcons.checkCircle,
                            body = stringResource(Res.string.agentsSignedInBody, state.agentName),
                            primary =
                                KitAction(
                                    stringResource(Res.string.agentsSignInDone, state.agentName),
                                    actions::leave,
                                ),
                        )
                    SignInStep.NotYet -> {
                        Running(state, actions, Modifier.weight(1f))
                        KitInlineState(
                            title =
                                stringResource(
                                    Res.string.agentsSignInTerminalNotYet,
                                    state.agentName,
                                ),
                            icon = KitIcons.terminal,
                            modifier =
                                Modifier.padding(
                                    horizontal = LocalKitTokens.current.gutter.dp,
                                    vertical = Space.S2.dp,
                                ),
                            primary =
                                KitAction(
                                    stringResource(Res.string.agentsSignInStart, state.agentName),
                                    actions::startAgain,
                                ),
                        )
                    }
                    SignInStep.Failed,
                    SignInStep.NotificationsBlocked -> {
                        val title =
                            if (state.step == SignInStep.NotificationsBlocked) {
                                stringResource(Res.string.kitCapPermNotificationsWhy)
                            } else {
                                stringResource(Res.string.localTerminalFailedTitle)
                            }
                        val reason = state.startStep?.let { startStepWords(it) }
                        KitPageState(
                            KitStateKind.Error,
                            title,
                            icon = KitIcons.error,
                            details = state.failureDetails ?: state.failureCode,
                            body =
                                if (state.step == SignInStep.Failed)
                                    listOfNotNull(
                                            reason,
                                            stringResource(Res.string.localTerminalFailedBody),
                                        )
                                        .joinToString(" ")
                                else null,
                            primary =
                                KitAction(
                                    stringResource(Res.string.agentsSignInStart, state.agentName),
                                    actions::startAgain,
                                ),
                        )
                    }
                    SignInStep.ProviderKeyOnHost ->
                        KitPageState(
                            KitStateKind.Calm,
                            stringResource(Res.string.agentAccountApiKey),
                            icon = KitIcons.locked,
                            body = stringResource(Res.string.agentsSignInUnavailable),
                        )
                    SignInStep.Unavailable ->
                        KitPageState(
                            KitStateKind.Missing,
                            stringResource(Res.string.agentsSignInUnavailable),
                            icon = KitIcons.blocked,
                        )
                }
        }
        pageApproval()
        KitConfirm(
            state.linkPrompt?.let { prompt ->
                KitConfirmSpec(
                    title =
                        stringResource(
                            if (prompt.insecure) Res.string.e7SharedOpenInsecureHTTPLink
                            else Res.string.chatFeelOpenLinkTitle
                        ),
                    confirmLabel =
                        stringResource(
                            if (prompt.insecure) Res.string.e7SharedOpenHTTPLink
                            else Res.string.chatFeelOpenLinkAction
                        ),
                    body = stringResource(Res.string.chatFeelOpenLinkBody, prompt.host),
                    kind =
                        if (prompt.insecure) KitConfirmKind.Destructive else KitConfirmKind.Neutral,
                    consequences =
                        if (prompt.insecure)
                            listOf(
                                stringResource(Res.string.e7SharedHTTPIsNotEncryptedOtherDevicesOn)
                            )
                        else emptyList(),
                    cancelLabel =
                        if (prompt.insecure) stringResource(Res.string.externalLinkDontOpen)
                        else null,
                )
            },
            actions::confirmSignInPage,
            actions::dismissSignInPage,
        )
    }
}

@Composable
private fun Running(state: SignInTerminalUi, actions: SignInTerminalActions, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (state.step == SignInStep.Running && state.inputEnabled) {
            // Paste is always there while the sign-in runs (Flutter's terminal menu "Paste"): a
            // long press on the grid selects text, so without it a browser's code could not reach
            // Antigravity at all. It sits next to the page and code actions, never in a menu.
            @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
            val paste =
                KitAction(
                    stringResource(Res.string.agentsSignInPaste),
                    { clipboard.getText()?.text?.let(actions::paste) },
                    icon = KitIcons.paste,
                )
            // The code is a credential for the next minute: keep it out of recents and screenshots.
            if (state.code != null) io.github.eslamasabry.oc.ui.kit.platform.KitSecureEffect()
            val copy =
                state.code?.let {
                    KitAction(
                        stringResource(Res.string.agentsSignInCopyCode, "\u2066$it\u2069"),
                        actions::copyCode,
                    )
                }
            io.github.eslamasabry.oc.ui.kit.action.KitActionBlock(
                modifier =
                    Modifier.padding(
                        start = LocalKitTokens.current.gutter.dp,
                        end = LocalKitTokens.current.gutter.dp,
                        bottom = Space.S2.dp,
                    ),
                primary =
                    if (state.canOpenPage)
                        KitAction(
                            stringResource(Res.string.agentsSignInOpenPage),
                            actions::openSignInPage,
                            icon = KitIcons.externalLink,
                        )
                    else null,
                secondary = copy ?: paste,
                tertiary = if (copy != null) listOf(paste) else emptyList(),
            )
        }
        KitTerminalView(
            platformView = state.terminal?.native,
            title = state.title,
            transcript = state.transcript,
            accessibleMode = state.accessibleMode,
            onAccessibleModeChange = actions::setAccessibleMode,
            onSend = actions::send,
            words = terminalWords(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            inputEnabled = state.inputEnabled,
            defaultKeyBar = state.inputEnabled,
        )
    }
}

/** Why a start stopped, in plain words (the technical text stays under Details). */
@Composable
private fun startStepWords(step: SignInStartStep): String? =
    when (step) {
        SignInStartStep.Prepare -> stringResource(Res.string.agentsSignInStartPrepare)
        SignInStartStep.Server -> stringResource(Res.string.agentsSignInStartServer)
        SignInStartStep.Connect -> stringResource(Res.string.agentsSignInStartConnect)
        SignInStartStep.Check -> stringResource(Res.string.agentsSignInStartCheck)
        SignInStartStep.Terminal -> stringResource(Res.string.agentsSignInStartTerminal)
        SignInStartStep.Attach -> stringResource(Res.string.agentsSignInStartAttach)
        SignInStartStep.Unknown -> null
    }

// The Flutter terminal's own strings name the readable-mode switch and the input field (plan 06
// §7.7; S3-TERMMIN report, words requests).
@Composable
private fun terminalWords(): KitTerminalWords =
    KitTerminalWords(
        terminal = stringResource(Res.string.libraryTerminalTitle),
        accessibleMode = stringResource(Res.string.terminalScreenReadableMode),
        transcript = stringResource(Res.string.e7SetupTranscript),
        noOutput = stringResource(Res.string.e7SetupNoOutput),
        sendToTerminal = stringResource(Res.string.e7SetupCommandInput),
        send = stringResource(Res.string.e7SetupSendCommand),
        inputUnavailable = stringResource(Res.string.e7SetupInputUnavailable),
    )
