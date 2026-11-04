package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Saved-server metadata and explicit commands, with no change to the app's selected target. */
@Composable
public fun ServerSettingsScreen(state: ServerSettingsUi, actions: ServerSettingsActions) {
    val title =
        when (state.page) {
            ServerSettingsPage.Home ->
                state.facts?.name ?: stringResource(Res.string.serverSettingsTitle)
            ServerSettingsPage.Check -> stringResource(Res.string.connCheckTitle)
            ServerSettingsPage.Shell -> stringResource(Res.string.serverSettingsShell)
            ServerSettingsPage.Capabilities -> stringResource(Res.string.settingsV3Supports)
            ServerSettingsPage.Reach -> stringResource(Res.string.reachTitle)
        }
    LifecycleResumeEffect(actions) {
        if (state.page == ServerSettingsPage.Shell) actions.refresh()
        onPauseOrDispose {}
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading || state.checkingHealth || state.checking || state.working,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp, horizontal = LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.failure?.let { failure ->
                KitErrorNotice(
                    message = stringResource(Res.string.serverSettingsFailed),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refresh),
                )
            }
            when {
                state.page == ServerSettingsPage.Reach -> ReachComputerBody()
                state.loading -> KitText(stringResource(Res.string.connCheckRunning))
                state.facts == null -> KitNotice(stringResource(Res.string.settingsV3ServerGone))
                else ->
                    when (state.page) {
                        ServerSettingsPage.Home -> ServerHome(state, actions)
                        ServerSettingsPage.Check -> ConnectionCheckBody(state, actions)
                        ServerSettingsPage.Shell -> ShellBody(state, actions)
                        ServerSettingsPage.Capabilities -> CapabilitiesBody(state)
                        ServerSettingsPage.Reach -> Unit
                    }
            }
        }
    }
}

@Composable
private fun ServerHome(state: ServerSettingsUi, actions: ServerSettingsActions) {
    val facts = state.facts ?: return
    KitSectionLabel(stringResource(Res.string.settingsV3SectionConnection))
    KitRowGroup {
        KitRow(
            title =
                stringResource(
                    when {
                        !facts.connected -> Res.string.serverSettingsDisconnected
                        state.checkingHealth -> Res.string.e7SettingsUi11
                        state.health?.healthy == true -> Res.string.e7SettingsUi59
                        else -> Res.string.e7SettingsUi60
                    }
                ),
            supporting =
                (state.health?.version ?: facts.lastVersion)?.let { version ->
                    if (facts.connected && state.health != null) version
                    else stringResource(Res.string.settingsV3NotConnectedVersion, version)
                },
            onClick = if (facts.connected) actions::refresh else actions::connect,
            enabled = !state.working && !state.checkingHealth,
            disabledReason = stringResource(Res.string.connCheckRunning),
        )
        KitRow(
            title = stringResource(Res.string.settingsV3SignIn),
            supporting =
                stringResource(
                    if (!facts.hasSecret) Res.string.settingsV3SignInNone
                    else if (facts.tokenSignIn) Res.string.settingsV3SignInToken
                    else Res.string.settingsV3SignInPassword
                ),
            onClick = actions::edit,
            trailing = KitRowTrailing.Chevron,
        )
        KitRow(
            title = stringResource(Res.string.settingsV3NameAddress),
            onClick = actions::edit,
            trailing = KitRowTrailing.Chevron,
        )
        if (facts.canCheck)
            KitRow(
                title = stringResource(Res.string.connCheckTitle),
                supporting = stringResource(Res.string.connCheckRowDetail),
                onClick = actions::checkConnection,
                trailing = KitRowTrailing.Chevron,
            )
        if (facts.connected)
            KitRow(
                title = stringResource(Res.string.settingsV3Supports),
                onClick = actions::showCapabilities,
                trailing = KitRowTrailing.Chevron,
            )
    }
    if (facts.connected) {
        KitNotice(stringResource(Res.string.serverSettingsOnServer, facts.name))
        KitRowGroup {
            if (facts.capabilities?.shellSettings == true)
                KitRow(
                    title = stringResource(Res.string.serverSettingsShell),
                    onClick = actions::showShells,
                    trailing = KitRowTrailing.Chevron,
                )
            if (facts.capabilities?.hostAgentProviders == true)
                KitRow(
                    title = stringResource(Res.string.hostUpdateRow, facts.name),
                    onClick = actions::computerTools,
                    trailing = KitRowTrailing.Chevron,
                )
            if (facts.capabilities?.savedPermissionList == true)
                KitRow(
                    title = stringResource(Res.string.permissions40Title),
                    onClick = actions::savedPermissions,
                    trailing = KitRowTrailing.Chevron,
                )
        }
    } else
        KitNotice(
            stringResource(Res.string.settingsV3ConnectToChange),
            actions =
                listOf(
                    KitLineAction(
                        stringResource(Res.string.serverSettingsConnect),
                        actions::connect,
                    )
                ),
        )
    KitRow(
        title = stringResource(Res.string.serverSettingsAddressLabel),
        supporting = facts.address,
    )
    KitButton(
        label = stringResource(Res.string.serverSettingsRemoveTitle, facts.name),
        destructive = true,
        role = KitButtonRole.Tertiary,
        onClick = actions::askRemove,
        enabled =
            if (state.working) KitEnabled.Disabled(stringResource(Res.string.serverSettingsSaving))
            else KitEnabled.Enabled,
    )
}

@Composable
private fun ConnectionCheckBody(state: ServerSettingsUi, actions: ServerSettingsActions) {
    KitText(stringResource(Res.string.connCheckIntro))
    KitRowGroup(Modifier.testTag("connection-check-steps")) {
        state.results.forEach { result ->
            val mark =
                stringResource(
                    when (result.mark) {
                        ConnectionMark.Waiting -> Res.string.serverSettingsWaiting
                        ConnectionMark.Running -> Res.string.connCheckRunning
                        ConnectionMark.Pass -> Res.string.serverSettingsPassed
                        ConnectionMark.Warn -> Res.string.serverSettingsAttention
                        ConnectionMark.Fail -> Res.string.serverSettingsFailedCheck
                        ConnectionMark.Skipped -> Res.string.serverSettingsSkipped
                    }
                )
            KitRow(
                title =
                    stringResource(
                        when (result.step) {
                            ConnectionStep.Address -> Res.string.connCheckStepAddress
                            ConnectionStep.SignIn -> Res.string.connCheckStepSignIn
                            ConnectionStep.Version -> Res.string.connCheckStepVersion
                            ConnectionStep.LiveUpdates -> Res.string.connCheckStepLive
                            ConnectionStep.Speed -> Res.string.connCheckStepSpeed
                        }
                    ),
                supporting = listOfNotNull(mark, resultWords(result)).joinToString(" · "),
                supportingMaxLines = 6,
            )
        }
    }
    KitButton(
        stringResource(
            if (state.checking) Res.string.connCheckRunning else Res.string.connCheckAgain
        ),
        actions::refresh,
        enabled = !state.checking,
        disabledReason = stringResource(Res.string.connCheckRunning),
    )
    KitRow(
        title = stringResource(Res.string.connCheckReachHelp),
        supporting = stringResource(Res.string.connCheckReachHelpDetail),
        trailing = KitRowTrailing.Chevron,
        onClick = actions::reachHelp,
    )
}

@Composable
private fun resultWords(result: ConnectionResult): String? {
    if (result.mark == ConnectionMark.Waiting || result.mark == ConnectionMark.Running) return null
    return when (result.reason) {
        ConnectionReason.Ok ->
            when (result.step) {
                ConnectionStep.Address -> stringResource(Res.string.connCheckAddressOk)
                ConnectionStep.SignIn -> stringResource(Res.string.connCheckSignInOk)
                ConnectionStep.Version ->
                    result.version
                        ?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(Res.string.connCheckVersionOk, it) }
                        ?: stringResource(Res.string.connCheckVersionOkUnknown)
                ConnectionStep.LiveUpdates -> stringResource(Res.string.connCheckLiveOk)
                ConnectionStep.Speed ->
                    stringResource(
                        Res.string.connCheckSpeedOk,
                        (result.millis ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
            }
        ConnectionReason.Unreachable -> stringResource(Res.string.connCheckAddressUnreachable)
        ConnectionReason.NotOpenCode -> stringResource(Res.string.connCheckAddressNotOpenCode)
        ConnectionReason.PasswordMissing -> stringResource(Res.string.connCheckNeedsPassword)
        ConnectionReason.PasswordRejected -> stringResource(Res.string.connCheckPasswordRejected)
        ConnectionReason.VersionUnsupported ->
            stringResource(Res.string.connCheckVersionUnsupported)
        ConnectionReason.NotReady -> stringResource(Res.string.connCheckVersionNotReady)
        ConnectionReason.LiveNotChecked -> stringResource(Res.string.connCheckLiveNotChecked)
        ConnectionReason.LiveConnecting -> stringResource(Res.string.connCheckLiveConnecting)
        ConnectionReason.LiveDown -> stringResource(Res.string.connCheckLiveDown)
        ConnectionReason.Slow ->
            stringResource(
                Res.string.connCheckSpeedOk,
                (result.millis ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            ) + " " + stringResource(Res.string.connCheckSpeedSlow)
        ConnectionReason.EarlierFailed -> stringResource(Res.string.connCheckSkipped)
    }
}

@Composable
private fun ShellBody(state: ServerSettingsUi, actions: ServerSettingsActions) {
    KitText(stringResource(Res.string.e7SettingsUi36))
    KitNotice(stringResource(Res.string.settingsV3ShellByServerNote))
    if (state.facts?.capabilities?.shellSettings != true) {
        KitNotice(stringResource(Res.string.serverSettingsShellUnavailable))
        return
    }
    val settings = state.shells
    if (settings == null) {
        if (!state.working) KitButton(stringResource(Res.string.kitTryAgain), actions::refresh)
        return
    }
    val only =
        settings.options.singleOrNull()?.takeIf {
            settings.selected.isEmpty() ||
                settings.selected == it.name ||
                settings.selected == it.path
        }
    if (only != null) {
        KitRow(
            title = stringResource(Res.string.serverSettingsShell),
            supporting = stringResource(Res.string.defaultShellOnlyOne, only.name),
        )
        return
    }
    KitChoiceList(
        choices =
            shellChoices(settings).map { choice ->
                KitChoice(
                    value = choice.value,
                    title = choice.label ?: stringResource(Res.string.e7SettingsUi39),
                    supporting =
                        if (choice.terminalOnly) stringResource(Res.string.e7SettingsUi37)
                        else null,
                    enabled =
                        if (state.working)
                            KitEnabled.Disabled(stringResource(Res.string.serverSettingsSaving))
                        else KitEnabled.Enabled,
                )
            },
        selected = settings.selected,
        semanticsLabel = stringResource(Res.string.serverSettingsShell),
        onSelect = actions::selectShell,
    )
}

@Composable
private fun CapabilitiesBody(state: ServerSettingsUi) {
    val flags = state.facts?.capabilities ?: return
    val rows =
        listOf(
            Res.string.serverSettingsTerminal to flags.terminal,
            Res.string.serverSettingsFiles to flags.fileBrowsing,
            Res.string.serverSettingsProjects to flags.projectManagement,
            Res.string.serverSettingsSchedules to flags.scheduledPrompts,
            Res.string.serverSettingsProviders to flags.serverCatalog,
            Res.string.serverSettingsPermissions to flags.permissionRequests,
            Res.string.serverSettingsSteering to flags.promptSteering,
            Res.string.serverSettingsShell to flags.shellSettings,
        )
    KitRowGroup {
        rows.forEach { (title, on) ->
            KitRow(
                title = stringResource(title),
                supporting =
                    stringResource(
                        if (on) Res.string.serverSettingsSupported
                        else Res.string.serverSettingsUnsupported
                    ),
            )
        }
    }
}

@Composable
private fun ReachComputerBody() {
    var copied by remember { mutableStateOf<String?>(null) }
    var copyId by remember { mutableLongStateOf(0) }
    KitClipboardEffect(if (copied == null) null else copyId, copied) { copied = null }
    KitText(stringResource(Res.string.reachIntro))
    KitSectionLabel(stringResource(Res.string.reachTailscaleHeading))
    listOf(
            Res.string.reachTailscale1,
            Res.string.reachTailscale2,
            Res.string.reachTailscale3,
            Res.string.reachTailscale4,
        )
        .forEachIndexed { index, key ->
            KitText("${index + 1}. ${stringResource(key)}", role = TypeRole.Body)
        }
    KitSectionLabel(stringResource(Res.string.reachSshHeading))
    listOf(Res.string.reachSsh1, Res.string.reachSsh2, Res.string.reachSsh3, Res.string.reachSsh4)
        .forEachIndexed { index, key ->
            KitText("${index + 1}. ${stringResource(key)}", role = TypeRole.Body)
        }
    KitCodeBlock(
        source = stringResource(Res.string.reachSshCommand),
        kind = KitCodeKind.Command,
        modifier = Modifier.testTag("reach-ssh-command"),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        copiedLabel = stringResource(Res.string.kitCopied),
        onCopy = {
            copyId++
            copied = it
        },
    )
    KitSectionLabel(stringResource(Res.string.reachSafetyHeading))
    KitText(stringResource(Res.string.reachSafety))
}
