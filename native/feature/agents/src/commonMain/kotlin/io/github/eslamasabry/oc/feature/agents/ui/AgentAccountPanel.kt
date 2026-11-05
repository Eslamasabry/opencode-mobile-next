package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.*
import kotlin.time.Clock
import kotlin.time.Instant
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitProgressRow
import org.jetbrains.compose.resources.pluralStringResource
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.KitSelectable
import androidx.compose.ui.Alignment
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
                    disabledReason = stringResource(Res.string.agentAccountLoading), icon = KitIcons.retry)),
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
                    KitRowGroup(leadingTiles = true) {
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
                if (loginWords != null) Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                val code = panel.deviceCode
                if (code != null) {
                    KitSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                            loginWords?.let { KitText(stringResource(it), role = KitTextRole.Title) }
                            KitText(stringResource(Res.string.agentAccountCodeHint), role = KitTextRole.Supporting)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KitSelectable(Modifier.weight(1f)) { KitText(code.userCode, role = KitTextRole.Title) }
                                KitIconButtonCopy(text = { code.userCode }, label = stringResource(Res.string.agentAccountCopyCode))
                            }
                            KitButton(stringResource(Res.string.agentAccountOpenSignIn), actions::openAccountSignIn,
                                role = KitButtonRole.Secondary, icon = KitIcons.externalLink)
                            // Only the host is shown here. The existing action owns external-link admission.
                            KitText(code.verificationUrl.substringAfter("://").substringBefore('/').substringBefore(':'), role = KitTextRole.Supporting)
                            if (panel.canCancel) KitButton(stringResource(Res.string.agentAccountCancel), actions::cancelAccountSignIn, role = KitButtonRole.Tertiary)
                        }
                    }
                } else {
                    loginWords?.let { KitNotice(stringResource(it)) }
                    if (panel.canCancel) {
                        Spacer(Modifier.height(Space.S2.dp))
                        KitButton(stringResource(Res.string.agentAccountCancel), actions::cancelAccountSignIn, role = KitButtonRole.Tertiary)
                    }
                }
                if (panel.account?.signedIn == true && panel.account?.type == "chatgpt") {
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    val buckets = panel.limits
                    KitRowGroup(label = stringResource(Res.string.agentAccountLimits)) {
                    if (panel.metricsLoading && buckets == null)
                        KitProgressRow(stringResource(Res.string.agentAccountAllowance), null)
                    else if (buckets.isNullOrEmpty())
                        KitRow(stringResource(Res.string.agentAccountLimitsUnavailable), titleMaxLines = 3) // F: lib/ui/screens/agent_account_screen.dart:508
                    else
                        buckets.forEach { bucket ->
                            listOfNotNull(bucket.primary, bucket.secondary).forEach { window ->
                                val duration = window.durationMinutes
                                val windowWords = when {
                                    duration == null -> stringResource(Res.string.agentAccountWindowUnknown)
                                    duration > 0 && duration % 1440 == 0 -> stringResource(Res.string.agentAccountWindowDays, duration / 1440) // F: lib/ui/screens/agent_account_screen.dart:580-584
                                    duration > 0 && duration % 60 == 0 -> stringResource(Res.string.agentAccountWindowHours, duration / 60) // F: lib/ui/screens/agent_account_screen.dart:580-584
                                    else -> stringResource(Res.string.agentAccountWindowMinutes, duration)
                                }
                                KitProgressRow(
                                    title = (bucket.name?.takeIf(String::isNotBlank) ?: stringResource(Res.string.agentAccountAllowance)) + " · " + windowWords,
                                    value = window.usedPercent / 100f, // F: lib/ui/screens/agent_account_screen.dart:519
                                    valueLabel = stringResource(Res.string.agentAccountPercentUsed, window.usedPercent) + " · " + accountReset(window.resetsAtEpochMillis),
                                )
                            }
                        }
                        }
                    val reached = buckets.orEmpty().flatMap { listOfNotNull(it.primary, it.secondary) }.firstOrNull { it.usedPercent >= 100 } // F: lib/ui/screens/agent_account_screen.dart:493
                    reached?.let {
                        Spacer(Modifier.height(Space.S3.dp))
                        KitNotice(stringResource(Res.string.agentAccountLimitReached, accountReset(it.resetsAtEpochMillis, withDate = true)))
                    }
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    val usage = panel.usage
                    KitRowGroup(label = stringResource(Res.string.agentAccountUsage)) {
                    if (panel.metricsLoading && usage == null) KitProgressRow(stringResource(Res.string.agentAccountUsage), null)
                    else if (usage?.lifetimeTokens == null && usage?.peakDailyTokens == null) {
                        KitRow(stringResource(Res.string.agentAccountUsageUnavailable), titleMaxLines = 3) // F: lib/ui/screens/agent_account_screen.dart:569
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
                panel.updatedAt?.let { at ->
                    val time = LocalKitPlatform.current.messageTime(at.toEpochMilliseconds(), Clock.System.now().toEpochMilliseconds())
                    if (time != null) {
                        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                        KitText(stringResource(Res.string.agentAccountUpdated, time), role = io.github.eslamasabry.oc.ui.design.type.TypeRole.Caption)
                    }
                }
            }
            Spacer(Modifier.height(Space.S4.dp))
            KitDetailsFold(label = stringResource(Res.string.kitDetails), copyLabel = stringResource(Res.string.kitCopyDetails),
                notes = listOfNotNull(stringResource(Res.string.agentAccountHostNote),
                    if (panel?.account?.signedIn == true) stringResource(Res.string.agentAccountUsageNote) else null))
        }
    }
}

@Composable
private fun accountReset(at: Long?, withDate: Boolean = false): String {
    if (at == null) return stringResource(Res.string.agentAccountResetUnknown)
    val now = Clock.System.now()
    val left = Instant.fromEpochMilliseconds(at) - now
    val relative = when {
        left.isNegative() -> stringResource(Res.string.agentAccountResetDue)
        left.inWholeHours >= 48 -> { // F: lib/ui/screens/agent_account_screen.dart:599
            val days = left.inWholeDays.toInt()
            pluralStringResource(Res.plurals.agentAccountResetInDays, days, days)
        }
        left.inWholeMinutes >= 60 -> { // F: lib/ui/screens/agent_account_screen.dart:601
            val hours = left.inWholeHours.toInt()
            pluralStringResource(Res.plurals.agentAccountResetInHours, hours, hours)
        }
        else -> {
            val minutes = left.inWholeMinutes.toInt().coerceAtLeast(1) // F: lib/ui/screens/agent_account_screen.dart:603
            pluralStringResource(Res.plurals.agentAccountResetInMinutes, minutes, minutes)
        }
    }
    val time = if (withDate) LocalKitPlatform.current.messageTime(at, now.toEpochMilliseconds()) else null
    return if (time == null) relative else stringResource(Res.string.agentAccountResetWhen, relative, time)
}
