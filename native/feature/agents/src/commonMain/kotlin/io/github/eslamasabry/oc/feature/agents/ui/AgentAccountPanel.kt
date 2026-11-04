package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.agents.account.AccountLoginStatus
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelStatus
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** One transient account panel; leaving it closes its owned login session and discards its code. */
@Composable
internal fun AgentAccountPanel(state: AgentsUi, actions: AgentsActions) {
    val panel = state.accountPanel
    KitScreen(
        topBar = {
            KitTopBar(
                title = state.accountTarget?.name.orEmpty(),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::closeAccount,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitNotice(stringResource(Res.string.agentAccountHostNote))
            if (state.accountOpening) KitText(stringResource(Res.string.agentAccountLoading))
            if (state.accountFailed) KitNotice(stringResource(Res.string.agentAccountReadFailed))
            if (panel != null) {
                KitText(
                    stringResource(
                        when (panel.status) {
                            AccountPanelStatus.Loading -> Res.string.agentAccountLoading
                            AccountPanelStatus.Unavailable -> Res.string.agentAccountUnavailable
                            AccountPanelStatus.Error -> Res.string.agentAccountReadFailed
                            AccountPanelStatus.Disconnected -> Res.string.agentAccountDisconnected
                            AccountPanelStatus.Ready ->
                                if (panel.account?.signedIn == true)
                                    Res.string.agentAccountConnected
                                else if (panel.account?.requiresSignIn == true)
                                    Res.string.agentAccountSignedOut
                                else Res.string.agentAccountNoAuth
                        }
                    )
                )
                panel.account?.let { account ->
                    account.email?.takeIf { it.isNotBlank() }?.let { KitText(it) }
                    account.type?.let { type ->
                        KitRow(
                            stringResource(Res.string.agentAccountSignInMethod),
                            supporting =
                                stringResource(
                                    if (type == "apiKey") Res.string.agentAccountApiKey
                                    else Res.string.agentAccountHostAuth
                                ),
                        )
                    }
                    account.plan
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            KitRow(
                                stringResource(Res.string.agentAccountPlanTitle),
                                supporting = it,
                            )
                        }
                }
                val loginWords =
                    when (panel.loginStatus) {
                        AccountLoginStatus.Starting -> Res.string.agentAccountStarting
                        AccountLoginStatus.Waiting -> Res.string.agentAccountWaiting
                        AccountLoginStatus.Cancelling -> Res.string.agentAccountCancelling
                        AccountLoginStatus.Failed -> Res.string.agentAccountLoginFailed
                        AccountLoginStatus.Uncertain -> Res.string.agentAccountLoginUncertain
                        AccountLoginStatus.Completed -> Res.string.agentAccountLoginCompleted
                        AccountLoginStatus.Cancelled -> Res.string.agentAccountLoginCancelled
                        AccountLoginStatus.Idle -> null
                    }
                loginWords?.let { KitNotice(stringResource(it)) }
                panel.deviceCode?.let { code ->
                    KitText(stringResource(Res.string.agentAccountCodeHint))
                    KitText(code.userCode)
                    KitButton(
                        stringResource(Res.string.agentAccountOpenSignIn),
                        actions::openAccountSignIn,
                    )
                    // The button opens without a second sheet only because it names its host here
                    // (Flutter shows the host under Open official sign-in).
                    KitText(
                        code.verificationUrl
                            .substringAfter("://")
                            .substringBefore('/')
                            .substringBefore(':'),
                        role = KitTextRole.Supporting,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitButton(
                        stringResource(Res.string.agentAccountRefresh),
                        actions::refreshAccount,
                        role = KitButtonRole.Secondary,
                    )
                    if (panel.canSignIn)
                        KitButton(
                            stringResource(Res.string.agentAccountSignIn),
                            actions::signInAccount,
                        )
                    if (panel.canCancel)
                        KitButton(
                            stringResource(Res.string.agentAccountCancel),
                            actions::cancelAccountSignIn,
                            role = KitButtonRole.Secondary,
                        )
                }
                if (panel.account?.type == "chatgpt") {
                    KitSectionLabel(stringResource(Res.string.agentAccountLimits))
                    val buckets = panel.limits
                    if (buckets.isNullOrEmpty())
                        KitText(stringResource(Res.string.agentAccountLimitsUnavailable))
                    else
                        buckets.forEach { bucket ->
                            bucket.name?.takeIf { it.isNotBlank() }?.let { KitText(it) }
                            listOfNotNull(bucket.primary, bucket.secondary).forEach { window ->
                                KitRow(
                                    stringResource(
                                        Res.string.agentAccountPercentUsed,
                                        window.usedPercent,
                                    ),
                                    supporting =
                                        window.durationMinutes?.let {
                                            stringResource(Res.string.agentAccountWindowMinutes, it)
                                        } ?: stringResource(Res.string.agentAccountWindowUnknown),
                                )
                            }
                        }
                    KitSectionLabel(stringResource(Res.string.agentAccountUsage))
                    val usage = panel.usage
                    if (usage?.lifetimeTokens == null && usage?.peakDailyTokens == null) {
                        KitText(stringResource(Res.string.agentAccountUsageUnavailable))
                    }
                    usage?.lifetimeTokens?.let {
                        KitRow(
                            stringResource(Res.string.agentAccountLifetimeTokens),
                            supporting = it.toString(),
                        )
                    }
                    usage?.peakDailyTokens?.let {
                        KitRow(
                            stringResource(Res.string.agentAccountPeakTokens),
                            supporting = it.toString(),
                        )
                    }
                    KitText(stringResource(Res.string.agentAccountUsageNote))
                }
            }
        }
    }
}
