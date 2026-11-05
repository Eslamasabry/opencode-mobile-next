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
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.core.common.text.Numbers
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
                title = stringResource(Res.string.agentAccountTitle),
                subtitle = state.accountTarget?.name,
                actions = listOf(KitLineAction(stringResource(Res.string.agentAccountRefresh), actions::refreshAccount,
                    enabled = !state.accountOpening && panel?.status != AccountPanelStatus.Loading,
                    disabledReason = stringResource(Res.string.agentAccountLoading))),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::closeAccount,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.accountOpening || panel?.status == AccountPanelStatus.Loading || panel?.loginStatus == AccountLoginStatus.Starting || panel?.loginStatus == AccountLoginStatus.Cancelling,
        loadingLabel = stringResource(Res.string.agentAccountLoading),
        bottom = if (panel?.canSignIn != true) null else ({
            KitButton(stringResource(Res.string.agentAccountSignIn), actions::signInAccount, fullWidth = true, icon = KitIcons.login)
        }),
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.accountOpening) KitText(stringResource(Res.string.agentAccountLoading))
            if (state.accountFailed) KitNotice(stringResource(Res.string.agentAccountReadFailed))
            if (panel != null) {
                val headline = stringResource(when (panel.status) {
                    AccountPanelStatus.Loading -> Res.string.agentAccountLoading
                    AccountPanelStatus.Unavailable -> Res.string.agentAccountUnavailable
                    AccountPanelStatus.Error -> Res.string.agentAccountReadFailed
                    AccountPanelStatus.Disconnected -> Res.string.agentAccountDisconnected
                    AccountPanelStatus.Ready -> if (panel.account?.signedIn == true) Res.string.agentAccountConnected
                        else if (panel.account?.requiresSignIn == true) Res.string.agentAccountSignedOut else Res.string.agentAccountNoAuth
                })
                val account = panel.account
                if (panel.status == AccountPanelStatus.Ready && account?.signedIn == true) {
                    KitRowGroup {
                        KitRow(headline, supporting = account.email?.takeIf(String::isNotBlank), leading = KitRowLeading.Glyph(KitIcons.privacy))
                        account.type?.let { type ->
                            KitRow(stringResource(Res.string.agentAccountSignInMethod), leading = KitRowLeading.Glyph(KitIcons.login),
                                trailing = KitRowTrailing.Value(stringResource(when (type) {
                                    "apiKey" -> Res.string.agentAccountApiKey
                                    "chatgpt" -> Res.string.rb49_accountChatGpt
                                    "amazonBedrock" -> Res.string.rb49_accountAmazonBedrock
                                    else -> Res.string.agentAccountHostAuth
                                }), chevron = false))
                        }
                        account.plan?.takeIf(String::isNotBlank)?.let {
                            KitRow(stringResource(Res.string.agentAccountPlanTitle), leading = KitRowLeading.Glyph(KitIcons.star), trailing = KitRowTrailing.Value(it, chevron = false))
                        }
                    }
                } else {
                    KitInlineState(
                        title = headline, icon = KitIcons.account,
                        body = when (panel.status) {
                            AccountPanelStatus.Unavailable -> stringResource(Res.string.agentAccountUnsupportedDetail)
                            AccountPanelStatus.Disconnected -> stringResource(Res.string.agentAccountReconnectDetail)
                            else -> if (panel.canSignIn) stringResource(Res.string.agentAccountSignInNote) else null
                        },
                        progress = if (panel.status == AccountPanelStatus.Loading) KitProgress.Waiting() else null,
                    )
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
                    if (panel.canCancel)
                        KitButton(
                            stringResource(Res.string.agentAccountCancel),
                            actions::cancelAccountSignIn,
                            role = KitButtonRole.Secondary,
                        )
                }
                if (panel.account?.signedIn == true && panel.account?.type == "chatgpt") {
                    KitSectionLabel(stringResource(Res.string.agentAccountLimits))
                    val buckets = panel.limits
                    if (buckets.isNullOrEmpty())
                        KitText(stringResource(Res.string.agentAccountLimitsUnavailable))
                    else
                        KitRowGroup {
                        buckets.forEach { bucket ->
                            listOfNotNull(bucket.primary, bucket.secondary).forEach { window ->
                                val duration = window.durationMinutes
                                val windowWords = when {
                                    duration == null -> stringResource(Res.string.agentAccountWindowUnknown)
                                    duration > 0 && duration % 1440 == 0 -> stringResource(Res.string.agentAccountWindowDays, duration / 1440) // F: lib/ui/screens/agent_account_screen.dart:580-584
                                    duration > 0 && duration % 60 == 0 -> stringResource(Res.string.agentAccountWindowHours, duration / 60) // F: lib/ui/screens/agent_account_screen.dart:580-584
                                    else -> stringResource(Res.string.agentAccountWindowMinutes, duration)
                                }
                                KitRow(
                                    title = (bucket.name?.takeIf(String::isNotBlank) ?: stringResource(Res.string.agentAccountAllowance)) + " · " + windowWords,
                                    supporting = stringResource(Res.string.agentAccountPercentUsed, window.usedPercent),
                                )
                                KitProgressView(KitProgress.Known((window.usedPercent / 100f).coerceIn(0f, 1f)), // F: lib/ui/screens/agent_account_screen.dart:519
                                    semanticsLabel = stringResource(Res.string.agentAccountAllowance))
                            }
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
                            trailing = KitRowTrailing.Value(Numbers.format(it), chevron = false),
                        )
                    }
                    usage?.peakDailyTokens?.let {
                        KitRow(
                            stringResource(Res.string.agentAccountPeakTokens),
                            trailing = KitRowTrailing.Value(Numbers.format(it), chevron = false),
                        )
                    }
                }
            }
            KitDetailsFold(label = stringResource(Res.string.kitDetails), copyLabel = stringResource(Res.string.kitCopyDetails),
                notes = listOfNotNull(stringResource(Res.string.agentAccountHostNote),
                    if (panel?.account?.signedIn == true) stringResource(Res.string.agentAccountUsageNote) else null))
        }
    }
}
