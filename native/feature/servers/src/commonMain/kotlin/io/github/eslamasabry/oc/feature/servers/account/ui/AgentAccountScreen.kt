package io.github.eslamasabry.oc.feature.servers.account.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.agents.account.*
import io.github.eslamasabry.oc.feature.servers.account.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Official host account truth, device-login ownership and unknown metrics, arranged entirely from
 * kit parts.
 */
@Composable
public fun AgentAccountScreen(state: AgentAccountUi, actions: AgentAccountActions) {
    KitBackGuard(true, actions::back)
    val panel = state.panel
    val busy =
        state.phase == AgentAccountPhase.Loading ||
            (state.phase == AgentAccountPhase.Panel &&
                (panel.status == AccountPanelStatus.Loading ||
                    panel.loginStatus in
                        setOf(AccountLoginStatus.Starting, AccountLoginStatus.Cancelling)))
    val loading = stringResource(Res.string.account40Loading)
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.account40Title),
                subtitle = state.profileName.takeIf { it.isNotEmpty() },
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.account40Back),
                onExit = actions::back,
                actions =
                    if (state.phase == AgentAccountPhase.Panel)
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.account40Refresh),
                                actions::refresh,
                                enabled = panel.status != AccountPanelStatus.Loading,
                                disabledReason = loading,
                            )
                        )
                    else emptyList(),
            )
        },
        width = KitScreenWidth.Reading,
        loading = busy,
        loadingLabel = loading,
        bottom =
            if (state.phase == AgentAccountPhase.Panel && panel.canSignIn)
                ({
                    KitActionBlock(
                        Modifier.padding(LocalKitTokens.current.gutter.dp),
                        primary =
                            KitAction(stringResource(Res.string.account40SignIn), actions::signIn),
                    )
                })
            else null,
    ) {
        KitConfirm(
            state.askedHost?.let {
                KitConfirmSpec(
                    stringResource(Res.string.account40OpenQuestion, it),
                    stringResource(Res.string.account40OpenPage),
                )
            },
            actions::confirmOpen,
            actions::dismissOpen,
        )
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.failure?.let {
                KitErrorNotice(
                    stringResource(Res.string.account40OpenFailed),
                    failure = it,
                    detailsLabel = stringResource(Res.string.account40Details),
                    copyLabel = stringResource(Res.string.account40CopyDetails),
                )
            }
            when (state.phase) {
                AgentAccountPhase.Loading -> KitStateView(KitStateKind.Loading, loading)
                AgentAccountPhase.ScopeLost ->
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.account40ScopeLostTitle),
                        body = stringResource(Res.string.account40ScopeLost),
                        primary =
                            KitAction(
                                stringResource(Res.string.account40BackToServers),
                                actions::back,
                            ),
                    )
                AgentAccountPhase.Unsupported ->
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.account40Unavailable),
                        body = stringResource(Res.string.account40UnsupportedDetail),
                    )
                AgentAccountPhase.Disconnected ->
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.account40Disconnected),
                        body = stringResource(Res.string.account40ReconnectDetail),
                    )
                AgentAccountPhase.Panel -> {
                    Identity(panel, actions)
                    if (panel.loginStatus != AccountLoginStatus.Idle) Login(state, actions)
                    if (
                        panel.status == AccountPanelStatus.Ready && panel.account?.signedIn == true
                    ) {
                        Limits(state)
                        Usage(state)
                    }
                    state.updated?.let {
                        KitText(
                            stringResource(Res.string.account40Updated, it),
                            role = KitTextRole.Supporting,
                        )
                    }
                    KitDetailsFold(
                        stringResource(Res.string.account40Details),
                        stringResource(Res.string.account40CopyDetails),
                        notes =
                            listOfNotNull(
                                stringResource(Res.string.account40HostNote),
                                if (panel.account?.signedIn == true)
                                    stringResource(Res.string.account40UsageNote)
                                else null,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Identity(panel: AccountPanelState, actions: AgentAccountActions) {
    val account = panel.account
    val headline =
        stringResource(
            when (panel.status) {
                AccountPanelStatus.Loading -> Res.string.account40Loading
                AccountPanelStatus.Unavailable -> Res.string.account40Unavailable
                AccountPanelStatus.Error -> Res.string.account40ReadFailed
                AccountPanelStatus.Disconnected -> Res.string.account40Disconnected
                AccountPanelStatus.Ready ->
                    when {
                        account?.signedIn == true -> Res.string.account40Connected
                        account?.requiresSignIn != true -> Res.string.account40NoAuth
                        panel.loginStatus in
                            setOf(
                                AccountLoginStatus.Starting,
                                AccountLoginStatus.Waiting,
                                AccountLoginStatus.Cancelling,
                            ) -> Res.string.account40InProgress
                        panel.loginStatus == AccountLoginStatus.Uncertain ->
                            Res.string.account40NeedsAttention
                        else -> Res.string.account40SignedOut
                    }
            }
        )
    when (panel.status) {
        AccountPanelStatus.Loading -> KitStateView(KitStateKind.Loading, headline)
        AccountPanelStatus.Error ->
            KitErrorNotice(
                headline,
                retry =
                    KitLineAction(stringResource(Res.string.account40Refresh), actions::refresh),
            )
        AccountPanelStatus.Unavailable ->
            KitStateView(
                KitStateKind.Calm,
                headline,
                body = stringResource(Res.string.account40UnsupportedDetail),
            )
        AccountPanelStatus.Disconnected ->
            KitStateView(
                KitStateKind.Calm,
                headline,
                body = stringResource(Res.string.account40ReconnectDetail),
            )
        AccountPanelStatus.Ready ->
            if (account?.signedIn == true) {
                KitRowGroup {
                    KitRow(headline, supporting = account.email, supportingMaxLines = 2)
                    KitRow(
                        stringResource(Res.string.account40SignInMethod),
                        trailing =
                            KitRowTrailing.Value(
                                when (account.type) {
                                    "chatgpt" -> stringResource(Res.string.account40ChatGpt)
                                    "apiKey" -> stringResource(Res.string.account40ApiKey)
                                    "amazonBedrock" -> stringResource(Res.string.account40Bedrock)
                                    else -> stringResource(Res.string.account40HostAuth)
                                },
                                chevron = false,
                            ),
                    )
                    account.plan?.let {
                        KitRow(
                            stringResource(Res.string.account40PlanTitle),
                            trailing = KitRowTrailing.Value(it, chevron = false),
                        )
                    }
                }
            } else
                KitStateView(
                    KitStateKind.Calm,
                    headline,
                    body =
                        if (panel.canSignIn) stringResource(Res.string.account40SignInNote)
                        else null,
                )
    }
}

@Composable
private fun Login(state: AgentAccountUi, actions: AgentAccountActions) {
    val panel = state.panel
    val label =
        stringResource(
            when (panel.loginStatus) {
                AccountLoginStatus.Idle -> Res.string.account40SignIn
                AccountLoginStatus.Starting -> Res.string.account40Starting
                AccountLoginStatus.Waiting -> Res.string.account40Waiting
                AccountLoginStatus.Cancelling -> Res.string.account40Cancelling
                AccountLoginStatus.Cancelled -> Res.string.account40Cancelled
                AccountLoginStatus.Failed -> Res.string.account40LoginFailed
                AccountLoginStatus.Uncertain -> Res.string.account40LoginUncertain
                AccountLoginStatus.Completed -> Res.string.account40LoginCompleted
            }
        )
    if (panel.loginStatus == AccountLoginStatus.Failed) KitErrorNotice(label) else KitNotice(label)
    panel.deviceCode?.let { code ->
        KitText(stringResource(Res.string.account40CodeHint), role = KitTextRole.Supporting)
        KitText(
            code.userCode,
            Modifier.semantics { contentDescription = code.userCode.toList().joinToString(" ") },
            role = TypeRole.Title,
        )
        KitButton(
            stringResource(Res.string.account40CopyCode),
            actions::copyCode,
            role = KitButtonRole.Secondary,
        )
        if (state.copied) KitNotice(stringResource(Res.string.account40Copied))
        KitButton(
            stringResource(Res.string.account40OpenSignIn),
            actions::openSignIn,
            role = KitButtonRole.Secondary,
        )
    }
    if (panel.canCancel)
        KitButton(
            stringResource(Res.string.account40Cancel),
            actions::cancel,
            role = KitButtonRole.Tertiary,
        )
}

@Composable
private fun Limits(state: AgentAccountUi) {
    val panel = state.panel
    val windows =
        panel.limits.orEmpty().flatMap { b ->
            listOfNotNull(b.primary, b.secondary).map { b to it }
        }
    KitRowGroup(label = stringResource(Res.string.account40Limits)) {
        when {
            panel.metricsLoading && panel.limits == null ->
                KitStateView(KitStateKind.Loading, stringResource(Res.string.account40Allowance))
            windows.isEmpty() ->
                KitRow(
                    stringResource(Res.string.account40LimitsUnavailable),
                    supporting = stringResource(Res.string.account40UsageNote),
                    supportingMaxLines = 3,
                )
            else ->
                for ((bucket, window) in windows) {
                    val name =
                        listOf(
                                bucket.name ?: stringResource(Res.string.account40Allowance),
                                duration(window.durationMinutes),
                            )
                            .joinToString(" · ")
                    val value = stringResource(Res.string.account40PercentUsed, window.usedPercent)
                    KitWindowRow(
                        name,
                        (window.usedPercent / 100f).coerceIn(0f, 1f),
                        value,
                        value,
                        note = reset(state, window.resetsAtEpochMillis, false),
                    )
                }
        }
    }
    windows
        .firstOrNull { it.second.usedPercent >= 100 }
        ?.second
        ?.let {
            KitNotice(
                stringResource(
                    Res.string.account40LimitReached,
                    reset(state, it.resetsAtEpochMillis, true),
                )
            )
        }
}

@Composable
private fun Usage(state: AgentAccountUi) {
    KitRowGroup(label = stringResource(Res.string.account40Usage)) {
        if (state.panel.metricsLoading && state.lifetime == null && state.peak == null)
            KitStateView(KitStateKind.Loading, stringResource(Res.string.account40Usage))
        else if (state.lifetime == null && state.peak == null)
            KitRow(
                stringResource(Res.string.account40UsageUnavailable),
                supporting = stringResource(Res.string.account40UsageNote),
                supportingMaxLines = 3,
            )
        state.lifetime?.let {
            KitRow(
                stringResource(Res.string.account40LifetimeTokens),
                trailing = KitRowTrailing.Value(it, chevron = false),
            )
        }
        state.peak?.let {
            KitRow(
                stringResource(Res.string.account40PeakTokens),
                trailing = KitRowTrailing.Value(it, chevron = false),
            )
        }
    }
}

@Composable
private fun duration(minutes: Int?): String =
    when {
        minutes == null -> stringResource(Res.string.account40WindowUnknown)
        minutes > 0 && minutes % 1440 == 0 ->
            stringResource(Res.string.account40WindowDays, minutes / 1440)
        minutes > 0 && minutes % 60 == 0 ->
            stringResource(Res.string.account40WindowHours, minutes / 60)
        else -> stringResource(Res.string.account40WindowMinutes, minutes)
    }

@Composable
private fun reset(state: AgentAccountUi, at: Long?, withDate: Boolean): String {
    if (at == null) return stringResource(Res.string.account40ResetUnknown)
    val left = at - state.nowMs
    val relative =
        when {
            left < 0 -> stringResource(Res.string.account40ResetDue)
            left >= 48 * 3600000L -> {
                val days = (left / 86400000L).toInt()
                pluralStringResource(Res.plurals.account40ResetInDays, days, days)
            }
            left >= 3600000 -> {
                val hours = (left / 3600000).toInt()
                pluralStringResource(Res.plurals.account40ResetInHours, hours, hours)
            }
            else -> {
                val minutes = (left / 60000).coerceAtLeast(1).toInt()
                pluralStringResource(Res.plurals.account40ResetInMinutes, minutes, minutes)
            }
        }
    return if (withDate && state.resetMoments[at] != null)
        stringResource(Res.string.account40ResetWhen, relative, state.resetMoments[at].orEmpty())
    else relative
}
