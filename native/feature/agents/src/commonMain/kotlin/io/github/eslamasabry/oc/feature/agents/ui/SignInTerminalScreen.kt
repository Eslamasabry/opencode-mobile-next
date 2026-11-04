package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.agents.presentation.SignInStep
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalActions
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentAccountApiKey
import io.github.eslamasabry.oc.ui.words.res.agentsDone
import io.github.eslamasabry.oc.ui.words.res.agentsSignInAgain
import io.github.eslamasabry.oc.ui.words.res.agentsSignInChecking
import io.github.eslamasabry.oc.ui.words.res.agentsSignInCopyCode
import io.github.eslamasabry.oc.ui.words.res.agentsSignInDone
import io.github.eslamasabry.oc.ui.words.res.agentsSignInFailed
import io.github.eslamasabry.oc.ui.words.res.agentsSignInOpenPage
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
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
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
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (state.stopFailed) KitNotice(stringResource(Res.string.agentsSignInStopFailed))
            when (state.step) {
                SignInStep.Preparing,
                SignInStep.Starting ->
                    KitStateView(
                        KitStateKind.Loading,
                        stringResource(Res.string.localTerminalStarting),
                    )
                SignInStep.Running -> Running(state, actions, Modifier.weight(1f))
                SignInStep.Checking ->
                    KitStateView(
                        KitStateKind.Working,
                        stringResource(Res.string.agentsSignInChecking),
                    )
                SignInStep.SignedIn ->
                    KitStateView(
                        KitStateKind.Finished,
                        stringResource(Res.string.agentsSignedIn),
                        body = stringResource(Res.string.agentsSignedInBody, state.agentName),
                        primary =
                            KitAction(
                                stringResource(Res.string.agentsSignInDone, state.agentName),
                                actions::leave,
                            ),
                    )
                SignInStep.NotYet ->
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.agentsSignInTerminalNotYet, state.agentName),
                        primary =
                            KitAction(
                                stringResource(Res.string.agentsSignInAgain),
                                actions::startAgain,
                            ),
                    )
                SignInStep.Failed,
                SignInStep.NotificationsBlocked -> {
                    val title =
                        if (state.step == SignInStep.NotificationsBlocked) {
                            stringResource(Res.string.kitCapPermNotificationsWhy)
                        } else {
                            stringResource(Res.string.agentsSignInFailed)
                        }
                    KitStateView(
                        KitStateKind.Error,
                        title,
                        primary =
                            KitAction(
                                stringResource(Res.string.agentsSignInAgain),
                                actions::startAgain,
                            ),
                    )
                    state.failureCode?.let { code ->
                        KitDetailsFold(
                            label = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            notes = listOf(code),
                        )
                    }
                }
                SignInStep.ProviderKeyOnHost ->
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.agentAccountApiKey),
                        body = stringResource(Res.string.agentsSignInUnavailable),
                    )
                SignInStep.Unavailable ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.agentsSignInUnavailable),
                    )
            }
        }
        pageApproval()
    }
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
                kind = if (prompt.insecure) KitConfirmKind.Destructive else KitConfirmKind.Neutral,
                consequences =
                    if (prompt.insecure)
                        listOf(stringResource(Res.string.e7SharedHTTPIsNotEncryptedOtherDevicesOn))
                    else emptyList(),
                cancelLabel =
                    if (prompt.insecure) stringResource(Res.string.externalLinkDontOpen) else null,
            )
        },
        actions::confirmSignInPage,
        actions::dismissSignInPage,
    )
}

@Composable
private fun Running(state: SignInTerminalUi, actions: SignInTerminalActions, modifier: Modifier) {
    val intro =
        if (state.usesClaudeAccount) Res.string.agentsSignInTerminalIntro
        else Res.string.agentsSignInTerminalIntroOther
    KitText(stringResource(intro, state.agentName), tone = TextTone.Secondary)
    KitTerminalView(
        platformView = state.terminal?.native,
        title = state.title,
        transcript = state.transcript,
        accessibleMode = state.accessibleMode,
        onAccessibleModeChange = actions::setAccessibleMode,
        onSend = actions::send,
        words = terminalWords(),
        modifier = modifier.fillMaxWidth(),
        inputEnabled = state.inputEnabled,
    ) {
        if (state.canOpenPage || state.code != null) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                if (state.canOpenPage)
                    KitButton(
                        stringResource(Res.string.agentsSignInOpenPage),
                        actions::openSignInPage,
                    )
                state.code?.let { code ->
                    KitButton(
                        stringResource(Res.string.agentsSignInCopyCode, code),
                        actions::copyCode,
                        role = KitButtonRole.Secondary,
                    )
                }
            }
        }
    }
    KitButton(stringResource(Res.string.agentsDone), actions::finish, role = KitButtonRole.Tertiary)
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
