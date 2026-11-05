package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.CommonBindings
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.agents.account.AccountLoginStatus
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelStatus
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitSelectable
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitPageState
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressRow
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.text.kitDateTime
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Instant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One transient account panel; leaving it closes its owned login session and discards its code. */
@Composable
internal fun AgentAccountPanel(
    state: AgentsUi,
    actions: AgentsActions,
    now: Instant = CommonBindings.clock().now(),
) {
    val panel = state.accountPanel
    val signedIn = panel?.status == AccountPanelStatus.Ready && panel?.account?.signedIn == true
    val canRefresh =
        !state.accountOpening &&
            panel?.status != AccountPanelStatus.Loading &&
            panel?.status != AccountPanelStatus.Disconnected &&
            (panel != null || state.accountTarget != null)
    val refresh = {
        if (panel == null) state.accountTarget?.let { actions.openAccount(it.id) }
        else actions.refreshAccount()
        Unit
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.agentAccountTitle),
                subtitle = state.accountTarget?.name,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.agentAccountRefresh),
                            refresh,
                            enabled = canRefresh,
                            disabledReason = stringResource(Res.string.agentAccountLoading),
                            icon = KitIcons.retry,
                        )
                    ),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::closeAccount,
            )
        },
        width = KitScreenWidth.Reading,
        loading =
            state.accountOpening ||
                panel?.status == AccountPanelStatus.Loading ||
                panel?.loginStatus == AccountLoginStatus.Starting ||
                panel?.loginStatus == AccountLoginStatus.Cancelling,
        loadingLabel = stringResource(Res.string.agentAccountLoading),
        bottom =
            if (panel?.canSignIn != true) null
            else
                ({
                    KitActionBlock(
                        primary =
                            KitAction(
                                stringResource(Res.string.agentAccountSignIn),
                                actions::signInAccount,
                                icon = KitIcons.login,
                            )
                    )
                }),
    ) {
        if (panel == null) {
            KitPageState(
                kind = if (state.accountFailed) KitStateKind.Error else KitStateKind.Loading,
                title =
                    stringResource(
                        if (state.accountFailed) Res.string.agentAccountReadFailed
                        else Res.string.agentAccountLoading
                    ),
                icon = if (state.accountFailed) KitIcons.error else KitIcons.account,
                progress = if (state.accountFailed) null else KitProgress.Waiting(),
                primary =
                    if (state.accountFailed && state.accountTarget != null)
                        KitAction(stringResource(Res.string.commonRetry), refresh)
                    else null,
            )
        } else
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp)
            ) {
                if (state.accountFailed)
                    KitNotice(stringResource(Res.string.agentAccountReadFailed))
                if (panel != null) {
                    val headline =
                        stringResource(
                            when (panel.status) {
                                AccountPanelStatus.Loading -> Res.string.agentAccountLoading
                                AccountPanelStatus.Unavailable -> Res.string.agentAccountUnavailable
                                AccountPanelStatus.Error -> Res.string.agentAccountReadFailed
                                AccountPanelStatus.Disconnected ->
                                    Res.string.agentAccountDisconnected
                                AccountPanelStatus.Ready ->
                                    if (panel.account?.signedIn == true)
                                        Res.string.agentAccountConnected
                                    else if (panel.account?.requiresSignIn == true)
                                        when (panel.loginStatus) {
                                            AccountLoginStatus.Starting,
                                            AccountLoginStatus.Waiting,
                                            AccountLoginStatus.Cancelling ->
                                                Res.string.agentAccountInProgress
                                            AccountLoginStatus.Uncertain ->
                                                Res.string.agentAccountNeedsAttention
                                            else -> Res.string.agentAccountSignedOut
                                        }
                                    else Res.string.agentAccountNoAuth
                            }
                        )
                    val account = panel.account
                    if (signedIn) {
                        KitRowGroup(leadingTiles = true) {
                            KitRow(
                                headline,
                                supporting =
                                    account?.email?.takeIf(String::isNotBlank)?.let { "⁨$it⁩" },
                                leading = KitRowLeading.Glyph(KitIcons.privacy),
                            )
                            account?.type?.let { type ->
                                KitRow(
                                    stringResource(Res.string.agentAccountSignInMethod),
                                    leading = KitRowLeading.Glyph(KitIcons.login),
                                    trailing =
                                        KitRowTrailing.Value(
                                            stringResource(
                                                when (type) {
                                                    "apiKey" -> Res.string.agentAccountApiKey
                                                    "chatgpt" -> Res.string.rb49_accountChatGpt
                                                    "amazonBedrock" ->
                                                        Res.string.rb49_accountAmazonBedrock
                                                    else -> Res.string.agentAccountHostAuth
                                                }
                                            ),
                                            chevron = false,
                                        ),
                                )
                            }
                            account?.plan?.takeIf(String::isNotBlank)?.let {
                                KitRow(
                                    stringResource(Res.string.agentAccountPlanTitle),
                                    leading = KitRowLeading.Glyph(KitIcons.star),
                                    trailing = KitRowTrailing.Value("⁨$it⁩", chevron = false),
                                )
                            }
                        }
                    } else if (panel.status == AccountPanelStatus.Error) {
                        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                            message = headline,
                            retry =
                                KitLineAction(
                                    stringResource(Res.string.commonRetry),
                                    actions::refreshAccount,
                                ),
                        )
                    } else {
                        KitInlineState(
                            title = headline,
                            icon =
                                when (panel.status) {
                                    AccountPanelStatus.Unavailable -> KitIcons.blocked
                                    AccountPanelStatus.Disconnected -> KitIcons.cloudOff
                                    else -> KitIcons.account
                                },
                            body =
                                when (panel.status) {
                                    AccountPanelStatus.Unavailable ->
                                        stringResource(Res.string.agentAccountUnsupportedDetail)
                                    AccountPanelStatus.Disconnected ->
                                        stringResource(Res.string.agentAccountReconnectDetail)
                                    else ->
                                        if (panel.canSignIn)
                                            stringResource(Res.string.agentAccountSignInNote)
                                        else null
                                },
                            progress =
                                if (panel.status == AccountPanelStatus.Loading)
                                    KitProgress.Waiting()
                                else null,
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
                            AccountLoginStatus.Cancelled -> Res.string.agentAccountCancelled
                            AccountLoginStatus.Idle -> null
                        }
                    if (loginWords != null)
                        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    val code = panel.deviceCode
                    val cancelVisible =
                        panel.canCancel || panel.loginStatus == AccountLoginStatus.Cancelling
                    val cancelEnabled =
                        KitEnabled.of(
                            panel.canCancel,
                            stringResource(Res.string.agentAccountCancelling),
                        )
                    if (panel.loginStatus == AccountLoginStatus.Failed) {
                        io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                            stringResource(Res.string.agentAccountLoginFailed)
                        )
                    } else if (code != null && panel.loginStatus != AccountLoginStatus.Idle) {
                        KitSurfacePanel(
                            title = loginWords?.let { stringResource(it) },
                            icon = KitIcons.login,
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                KitText(
                                    stringResource(Res.string.agentAccountCodeHint),
                                    role = KitTextRole.Supporting,
                                )
                                Spacer(Modifier.height(Space.S3.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    KitSelectable(
                                        Modifier.weight(1f).clearAndSetSemantics {
                                            contentDescription = code.userCode.toCharArray().joinToString(" ")
                                        }
                                    ) {
                                        KitText(code.userCode, role = TypeRole.Title, tabular = true)
                                    }
                                    KitIconButtonCopy(
                                        text = { code.userCode },
                                        label = stringResource(Res.string.agentAccountCopyCode),
                                    )
                                }
                                Spacer(Modifier.height(Space.S3.dp))
                                KitButton(
                                    stringResource(Res.string.agentAccountOpenSignIn),
                                    actions::openAccountSignIn,
                                    role = KitButtonRole.Secondary,
                                    icon = KitIcons.externalLink,
                                    fullWidth = true,
                                )
                                // Only the host is shown here. The existing action owns
                                // external-link admission.
                                val host =
                                    (ExternalLinkPolicy().check(code.verificationUrl, LinkOrigin.ServerContent)
                                            as? LinkDecision.AskFirst)
                                        ?.shownHost
                                host?.let {
                                    Spacer(Modifier.height(Space.S1.dp))
                                    KitText("\u2066$it\u2069", role = TypeRole.Caption)
                                }
                                if (cancelVisible) {
                                    Spacer(Modifier.height(Space.S2.dp))
                                    KitButton(
                                        stringResource(Res.string.agentAccountCancel),
                                        actions::cancelAccountSignIn,
                                        role = KitButtonRole.Tertiary,
                                        icon = KitIcons.close,
                                        enabled = cancelEnabled,
                                    )
                                }
                            }
                        }
                    } else {
                        loginWords?.let { words ->
                            if (panel.loginStatus == AccountLoginStatus.Failed)
                                io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice(
                                    stringResource(words)
                                )
                            else
                                KitNotice(
                                    stringResource(words),
                                    icon =
                                        when (panel.loginStatus) {
                                            AccountLoginStatus.Completed -> KitIcons.checkCircle
                                            AccountLoginStatus.Uncertain -> KitIcons.warning
                                            else -> KitIcons.login
                                        },
                                    tone =
                                        if (panel.loginStatus == AccountLoginStatus.Completed)
                                            StatusTone.Ok
                                        else StatusTone.Neutral,
                                )
                        }
                        if (cancelVisible) {
                            Spacer(Modifier.height(Space.S2.dp))
                            KitButton(
                                stringResource(Res.string.agentAccountCancel),
                                actions::cancelAccountSignIn,
                                role = KitButtonRole.Tertiary,
                                icon = KitIcons.close,
                                enabled = cancelEnabled,
                            )
                        }
                    }
                    if (signedIn) {
                        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                        val buckets = panel.limits
                        val windows =
                            buckets.orEmpty().flatMap { bucket ->
                                listOfNotNull(bucket.primary, bucket.secondary).map { bucket to it }
                            }
                        KitSectionLabel(stringResource(Res.string.agentAccountLimits), margin = 0.dp, gapBefore = 0.dp)
                        KitRowGroup {
                            if (panel.metricsLoading && buckets == null)
                                KitProgressRow(
                                    stringResource(Res.string.agentAccountAllowance),
                                    null,
                                )
                            else if (windows.isEmpty())
                                KitRow(
                                    stringResource(Res.string.agentAccountLimitsUnavailable),
                                    titleMaxLines = 3,
                                    enabled = false,
                                    disabledReason =
                                        stringResource(Res.string.agentAccountUsageNote),
                                ) // F: lib/ui/screens/agent_account_screen.dart:508
                            else
                                buckets.orEmpty().forEach { bucket ->
                                    listOfNotNull(bucket.primary, bucket.secondary).forEach { window
                                        ->
                                        val duration = window.durationMinutes
                                        val windowWords =
                                            when {
                                                duration == null ->
                                                    stringResource(
                                                        Res.string.agentAccountWindowUnknown
                                                    )
                                                duration > 0 && duration % 1440 == 0 ->
                                                    stringResource(
                                                        Res.string.agentAccountWindowDays,
                                                        duration / 1440,
                                                    ) // F:
                                                      // lib/ui/screens/agent_account_screen.dart:580-584
                                                duration > 0 && duration % 60 == 0 ->
                                                    stringResource(
                                                        Res.string.agentAccountWindowHours,
                                                        duration / 60,
                                                    ) // F:
                                                      // lib/ui/screens/agent_account_screen.dart:580-584
                                                else ->
                                                    stringResource(
                                                        Res.string.agentAccountWindowMinutes,
                                                        duration,
                                                    )
                                            }
                                        KitProgressRow(
                                            title =
                                                (bucket.name?.takeIf(String::isNotBlank)
                                                    ?: stringResource(
                                                        Res.string.agentAccountAllowance
                                                    )) + " · " + windowWords,
                                            value =
                                                window.usedPercent /
                                                    100f, // F:
                                                          // lib/ui/screens/agent_account_screen.dart:519
                                            valueLabel =
                                                stringResource(
                                                    Res.string.agentAccountPercentUsed,
                                                    window.usedPercent,
                                                ) +
                                                    " · " +
                                                    accountReset(window.resetsAtEpochMillis, now),
                                        )
                                    }
                                }
                        }
                        val reached =
                            buckets
                                .orEmpty()
                                .flatMap { listOfNotNull(it.primary, it.secondary) }
                                .firstOrNull {
                                    it.usedPercent >= 100
                                } // F: lib/ui/screens/agent_account_screen.dart:493
                        reached?.let {
                            Spacer(Modifier.height(Space.S3.dp))
                            KitNotice(
                                stringResource(
                                    Res.string.agentAccountLimitReached,
                                    accountReset(it.resetsAtEpochMillis, now, withDate = true),
                                ),
                                icon = KitIcons.blocked,
                            )
                        }
                        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                        val usage = panel.usage
                        KitSectionLabel(stringResource(Res.string.agentAccountUsage), margin = 0.dp, gapBefore = 0.dp)
                        KitRowGroup {
                            if (
                                panel.metricsLoading &&
                                    usage?.lifetimeTokens == null &&
                                    usage?.peakDailyTokens == null
                            )
                                KitProgressRow(stringResource(Res.string.agentAccountUsage), null)
                            else if (
                                usage?.lifetimeTokens == null && usage?.peakDailyTokens == null
                            ) {
                                KitRow(
                                    stringResource(Res.string.agentAccountUsageUnavailable),
                                    titleMaxLines = 3,
                                    enabled = false,
                                    disabledReason =
                                        stringResource(Res.string.agentAccountUsageNote),
                                ) // F: lib/ui/screens/agent_account_screen.dart:569
                            }
                            usage?.lifetimeTokens?.let {
                                KitRow(
                                    stringResource(Res.string.agentAccountLifetimeTokens),
                                    trailing =
                                        KitRowTrailing.Value(Numbers.format(it), chevron = false),
                                )
                            }
                            usage?.peakDailyTokens?.let {
                                KitRow(
                                    stringResource(Res.string.agentAccountPeakTokens),
                                    trailing =
                                        KitRowTrailing.Value(Numbers.format(it), chevron = false),
                                )
                            }
                        }
                    }
                    panel.updatedAt?.let { at ->
                        val time = kitDateTime(at.toEpochMilliseconds())
                        if (time != null) {
                            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                            KitText(
                                stringResource(Res.string.agentAccountUpdated, time),
                                role = io.github.eslamasabry.oc.ui.design.type.TypeRole.Caption,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.S4.dp))
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes =
                        listOfNotNull(
                            stringResource(Res.string.agentAccountHostNote),
                            if (signedIn) stringResource(Res.string.agentAccountUsageNote)
                            else null,
                        ),
                )
            }
    }
}

@Composable
private fun accountReset(at: Long?, now: Instant, withDate: Boolean = false): String {
    if (at == null) return stringResource(Res.string.agentAccountResetUnknown)
    val left = Instant.fromEpochMilliseconds(at) - now
    val relative =
        when {
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
                val minutes =
                    left.inWholeMinutes
                        .toInt()
                        .coerceAtLeast(1) // F: lib/ui/screens/agent_account_screen.dart:603
                pluralStringResource(Res.plurals.agentAccountResetInMinutes, minutes, minutes)
            }
        }
    val time = if (withDate) kitDateTime(at) else null
    return if (time == null) relative
    else stringResource(Res.string.agentAccountResetWhen, relative, time)
}
