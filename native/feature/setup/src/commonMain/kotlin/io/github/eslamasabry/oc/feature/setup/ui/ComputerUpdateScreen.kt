package io.github.eslamasabry.oc.feature.setup.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateActions
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateUi
import io.github.eslamasabry.oc.feature.setup.presentation.RecheckAnswer
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitContentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.connectionCheckAgain
import io.github.eslamasabry.oc.ui.words.res.connectionCheckRunning
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure16
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure17
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure33
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure35
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure41
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure42
import io.github.eslamasabry.oc.ui.words.res.e7SetupFullWalkthrough
import io.github.eslamasabry.oc.ui.words.res.e7SetupHostFirstSetup
import io.github.eslamasabry.oc.ui.words.res.hostServiceIntro
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCommand
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.openCode1Unsupported
import io.github.eslamasabry.oc.ui.words.res.openCode1UnsupportedTitle
import io.github.eslamasabry.oc.ui.words.res.openCode2HostInstall
import io.github.eslamasabry.oc.ui.words.res.openCode2HostInstallDetail
import io.github.eslamasabry.oc.ui.words.res.serverSwitcherOpen
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailProfileMissing
import org.jetbrains.compose.resources.stringResource

/**
 * "Update this server": a computer server that still runs OpenCode 1 (Flutter connection_failure.dart's OpenCode 1
 * case: its title, its explanation and the update command, with Change server; plus host_management_screen.dart's
 * install row). Check again connects through the app; what it finds is said in plain words, never as a raw error.
 */
@Composable
public fun ComputerUpdateScreen(state: ComputerUpdateUi, actions: ComputerUpdateActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.openCode1UnsupportedTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
        bottom = if (state.loading || state.missing) null else ({ RecheckBar(state, actions) }),
    ) {
        if (state.missing) {
            KitStateView(
                kind = KitStateKind.Missing,
                title = stringResource(Res.string.sessionAddressFailProfileMissing),
            )
        } else if (!state.loading) {
            GuideBody(state, actions)
        }
    }
}

@Composable
private fun GuideBody(state: ComputerUpdateUi, actions: ComputerUpdateActions) {
    var clip by remember { mutableStateOf<String?>(null) }
    var clipId by remember { mutableLongStateOf(0L) }
    KitClipboardEffect(if (clip == null) null else clipId, clip, onDone = { clip = null })
    Column(
        Modifier.fillMaxWidth().verticalScroll(kitPageScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        KitText(stringResource(Res.string.openCode1Unsupported))
        KitText(stringResource(Res.string.hostServiceIntro, state.server), tone = TextTone.Secondary)
        KitRowGroup(label = stringResource(Res.string.e7SetupHostFirstSetup)) {
            KitContentRow(
                stringResource(Res.string.openCode2HostInstall),
                supporting = stringResource(Res.string.openCode2HostInstallDetail),
            ) {
                KitCodeBlock(
                    source = state.command,
                    kind = KitCodeKind.Command,
                    modifier = Modifier.testTag("computer-update-command"),
                    wrap = true,
                    copyLabel = stringResource(Res.string.kitCodeCopyCommand),
                    copiedLabel = stringResource(Res.string.kitCopied),
                    onCopy = { value ->
                        clipId++
                        clip = value
                    },
                )
            }
        }
        KitButton(
            stringResource(Res.string.e7SetupFullWalkthrough),
            actions::openGuide,
            role = KitButtonRole.Tertiary,
            icon = KitIcons.externalLink,
            fullWidth = false,
        )
        when (state.answer) {
            null -> Unit
            RecheckAnswer.StillOpenCode1 ->
                KitNotice(
                    stringResource(Res.string.openCode1Unsupported),
                    title = stringResource(Res.string.openCode1UnsupportedTitle),
                    tone = StatusTone.Attention,
                )
            RecheckAnswer.PasswordRejected ->
                KitNotice(
                    stringResource(Res.string.e7ConnectionFailure17),
                    title = stringResource(Res.string.e7ConnectionFailure16),
                    tone = StatusTone.Attention,
                )
            RecheckAnswer.NoAnswer ->
                KitNotice(
                    stringResource(Res.string.e7ConnectionFailure35),
                    title = stringResource(Res.string.e7ConnectionFailure33),
                    tone = StatusTone.Attention,
                )
            RecheckAnswer.Other ->
                KitNotice(
                    stringResource(Res.string.e7ConnectionFailure41),
                    title = stringResource(Res.string.e7ConnectionFailure42),
                    tone = StatusTone.Attention,
                )
        }
    }
}

@Composable
private fun RecheckBar(state: ComputerUpdateUi, actions: ComputerUpdateActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitButton(
            stringResource(if (state.checking) Res.string.connectionCheckRunning else Res.string.connectionCheckAgain),
            actions::recheck,
            modifier = Modifier.testTag("computer-update-recheck"),
            working = state.checking,
            fullWidth = true,
        )
        KitButton(
            stringResource(Res.string.serverSwitcherOpen),
            actions::changeServer,
            role = KitButtonRole.Secondary,
            fullWidth = true,
        )
    }
}
