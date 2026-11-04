package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckReason
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckResult
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckState
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckStep
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ConnectionCheckSheet(state: ConnectionCheckUi?, actions: ServersActions) {
    KitSheet(
        visible = state != null,
        onDismissRequest = actions::closeConnectionCheck,
        title = stringResource(Res.string.connectionCheckTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
    ) {
        if (state != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                KitText(state.serverName)
                for (step in ConnectionCheckStep.entries) {
                    val result = state.results.getValue(step)
                    KitRow(
                        title =
                            stringResource(
                                when (step) {
                                    ConnectionCheckStep.Address -> Res.string.connectionCheckAddress
                                    ConnectionCheckStep.SignIn -> Res.string.connectionCheckSignIn
                                    ConnectionCheckStep.Version -> Res.string.connectionCheckVersion
                                    ConnectionCheckStep.LiveUpdates ->
                                        Res.string.connectionCheckLive
                                    ConnectionCheckStep.Speed -> Res.string.connectionCheckSpeed
                                }
                            ),
                        supporting = checkWords(result, step),
                        supportingMaxLines = 4,
                    )
                }
                if (!state.running) {
                    KitButton(
                        stringResource(Res.string.connectionCheckAgain),
                        { actions.checkConnection(state.id) },
                        role = KitButtonRole.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun checkWords(result: ConnectionCheckResult, step: ConnectionCheckStep): String {
    if (result.state == ConnectionCheckState.Waiting)
        return stringResource(Res.string.connectionCheckWaiting)
    if (result.state == ConnectionCheckState.Running)
        return stringResource(Res.string.connectionCheckRunning)
    val reason =
        when (result.reason) {
            ConnectionCheckReason.Ok -> {
                if (step == ConnectionCheckStep.Speed)
                    return stringResource(Res.string.connectionCheckMillis, result.value.orEmpty())
                if (step == ConnectionCheckStep.Version && !result.value.isNullOrBlank())
                    return stringResource(Res.string.connectionCheckVersionValue, result.value)
                Res.string.connectionCheckPassed
            }
            ConnectionCheckReason.Unreachable -> Res.string.connectionCheckUnreachable
            ConnectionCheckReason.NotServer -> Res.string.connectionCheckNotServer
            ConnectionCheckReason.NeedsPassword -> Res.string.connectionCheckNeedsPassword
            ConnectionCheckReason.PasswordRejected -> Res.string.connectionCheckPasswordRejected
            ConnectionCheckReason.VersionUnsupported -> Res.string.connectionCheckUnsupported
            ConnectionCheckReason.VersionNotReady -> Res.string.connectionCheckNotReady
            ConnectionCheckReason.StreamNotChecked -> Res.string.connectionCheckStreamUnchecked
            ConnectionCheckReason.StreamConnecting -> Res.string.connectionCheckStreamConnecting
            ConnectionCheckReason.StreamDown -> Res.string.connectionCheckStreamDown
            ConnectionCheckReason.Slow ->
                return stringResource(Res.string.connectionCheckSlow, result.value.orEmpty())
            ConnectionCheckReason.EarlierFailed -> Res.string.connectionCheckSkipped
            ConnectionCheckReason.Trust -> Res.string.connectionCheckTrust
            ConnectionCheckReason.Changed -> Res.string.connectionCheckChanged
        }
    return stringResource(reason)
}
