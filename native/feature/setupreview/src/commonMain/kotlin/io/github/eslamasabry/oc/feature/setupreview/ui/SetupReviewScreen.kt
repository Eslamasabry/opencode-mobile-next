package io.github.eslamasabry.oc.feature.setupreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.setupreview.presentation.SetupMcpState
import io.github.eslamasabry.oc.feature.setupreview.presentation.SetupReviewActions
import io.github.eslamasabry.oc.feature.setupreview.presentation.SetupReviewPhase
import io.github.eslamasabry.oc.feature.setupreview.presentation.SetupReviewUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * One read-only list: suggestions, tool status, then the original ordered configuration sources.
 */
@Composable
public fun SetupReviewScreen(state: SetupReviewUi, actions: SetupReviewActions) {
    val loading = stringResource(Res.string.setupNativeLoading)
    val refresh = stringResource(Res.string.setupNativeRefresh)
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.setupNativeTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.setupNativeBack),
                onExit = actions::back,
            )
        },
        loading = state.refreshing || state.phase == SetupReviewPhase.Loading,
        loadingLabel = loading,
        width = KitScreenWidth.Reading,
        bottom =
            if (!state.online || state.phase == SetupReviewPhase.Unsupported) null
            else
                ({
                    KitButton(
                        label =
                            if (state.refreshing) stringResource(Res.string.setupNativeRefreshing)
                            else refresh,
                        onClick = actions::refresh,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    horizontal = LocalKitTokens.current.gutter.dp,
                                    vertical = Space.S2.dp,
                                ),
                        role = KitButtonRole.Secondary,
                        working = state.refreshing,
                    )
                }),
    ) {
        when {
            state.guided != null -> GuidedSetupContent(state.guided, actions)
            state.phase == SetupReviewPhase.Unsupported ->
                KitStateView(
                    KitStateKind.Missing,
                    stringResource(Res.string.setupNativeUnsupported),
                    body = stringResource(Res.string.setupNativeUnsupportedBody),
                )
            state.phase == SetupReviewPhase.NeedsSignIn ->
                KitStateView(
                    KitStateKind.Missing,
                    stringResource(Res.string.setupNativeSignIn),
                    body = stringResource(Res.string.setupNativeSignInBody),
                    primary =
                        KitAction(stringResource(Res.string.setupNativeServers), actions::signIn),
                )
            state.content.isEmpty && state.phase == SetupReviewPhase.Loading ->
                KitStateView(KitStateKind.Loading, loading)
            state.content.isEmpty && state.phase == SetupReviewPhase.Offline ->
                KitStateView(
                    KitStateKind.Calm,
                    stringResource(Res.string.setupNativeOffline),
                    body = stringResource(Res.string.setupNativeOfflineBody),
                )
            state.content.isEmpty && state.phase == SetupReviewPhase.Error ->
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.setupNativeError),
                    body = stringResource(Res.string.setupNativeErrorBody),
                )
            state.content.isEmpty ->
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.setupNativeEmpty),
                    body = stringResource(Res.string.setupNativeEmptyBody),
                )
            else -> SetupList(state, actions)
        }
    }
}

private fun urgency(status: SetupMcpState): Int =
    when (status) {
        SetupMcpState.NeedsAuth -> 0
        SetupMcpState.Failed -> 1
        SetupMcpState.Pending,
        SetupMcpState.Unknown -> 2
        SetupMcpState.Disabled -> 3
        SetupMcpState.Connected -> 4
    }

@Composable
private fun SetupList(state: SetupReviewUi, actions: SetupReviewActions) {
    val servers = state.content.servers.sortedBy { urgency(it.status) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitGroupNote(
            stringResource(
                when (state.phase) {
                    SetupReviewPhase.Offline -> Res.string.setupNativeOfflineStale
                    SetupReviewPhase.Error -> Res.string.setupNativeErrorBody
                    else -> Res.string.setupNativeReviewOnly
                }
            )
        )
        KitRow(title = stringResource(Res.string.setupNativeGuided), onClick = actions::openGuided)
        val suggestions = servers.filter {
            it.status == SetupMcpState.NeedsAuth || it.status == SetupMcpState.Failed
        }
        if (suggestions.isNotEmpty() || servers.isEmpty()) {
            KitRowGroup(label = stringResource(Res.string.setupNativeSuggestions)) {
                for (server in suggestions) KitRow(
                    title =
                        stringResource(
                            if (server.status == SetupMcpState.NeedsAuth)
                                Res.string.setupNativeSignInTool
                            else Res.string.setupNativeFixTool,
                            server.name,
                        ),
                    supportingMaxLines = 3,
                )
                if (servers.isEmpty())
                    KitRow(title = stringResource(Res.string.setupNativeAddTools))
            }
        }
        if (servers.isNotEmpty()) {
            KitRowGroup(label = stringResource(Res.string.setupNativeTools)) {
                for (server in servers) KitRow(
                    title = server.name,
                    supporting =
                        stringResource(
                            when (server.status) {
                                SetupMcpState.Connected -> Res.string.setupNativeConnected
                                SetupMcpState.Pending -> Res.string.setupNativePending
                                SetupMcpState.Disabled -> Res.string.setupNativeDisabled
                                SetupMcpState.Failed -> Res.string.setupNativeFailed
                                SetupMcpState.NeedsAuth -> Res.string.setupNativeNeedsAuth
                                SetupMcpState.Unknown -> Res.string.setupNativeUnknown
                            }
                        ),
                )
            }
        }
        if (state.content.sources.isNotEmpty()) {
            KitGroupNote(stringResource(Res.string.setupNativeSources))
            KitGroupNote(stringResource(Res.string.setupNativeSourcesBody))
            for ((index, source) in state.content.sources.withIndex()) {
                val type =
                    stringResource(
                        when (source.type) {
                            "document",
                            "file" -> Res.string.setupNativeFile
                            "inline" -> Res.string.setupNativeInline
                            else -> Res.string.setupNativeOtherSource
                        }
                    )
                KitRowGroup(
                    label = stringResource(Res.string.setupNativeSourceOrder, index + 1, type)
                ) {
                    source.path?.let { KitRow(title = it, supportingMaxLines = 3) }
                    if (source.values.isEmpty())
                        KitRow(title = stringResource(Res.string.setupNativeNoValues))
                    for (value in source.values) KitRow(
                        title = value.name,
                        supporting = value.value,
                        supportingMaxLines = 8,
                    )
                }
            }
        }
    }
}
