package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationViewModel
import androidx.compose.foundation.layout.Arrangement
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.configInspectionExplanation
import io.github.eslamasabry.oc.ui.words.res.configInspectionRefresh
import io.github.eslamasabry.oc.ui.words.res.configInspectionTitle
import io.github.eslamasabry.oc.ui.words.res.configInspectionUnavailable
import io.github.eslamasabry.oc.ui.words.res.configFilesLabel
import io.github.eslamasabry.oc.ui.words.res.configFoldersLabel
import io.github.eslamasabry.oc.ui.words.res.configNoFileBody
import io.github.eslamasabry.oc.ui.words.res.configNoFileTitle
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServerConfigurationPage(onBack: () -> Unit) {
    val vm = metroViewModel<ServerConfigurationViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    ServerConfigurationScreen(state, vm, onBack)
}

/**
 * What the server reads its configuration from, in plain words: the files it found (or that there is none yet) and
 * the folders it looks in. The masked source sits under Details only. The adapter has already removed sensitive
 * values before this state exists, and the lists carry nothing but paths.
 */
@Composable
public fun ServerConfigurationScreen(
    state: ServerConfigurationUi,
    actions: ServerConfigurationActions,
    onBack: () -> Unit,
) {
    val title = stringResource(Res.string.configInspectionTitle)
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onBack,
                actions =
                    if (state is ServerConfigurationUi.Ready)
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.configInspectionRefresh),
                                actions::refresh,
                            )
                        )
                    else emptyList(),
            )
        },
        width = KitScreenWidth.Reading,
        page = true,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LocalKitTokens.current.sectionGap.dp),
        ) {
            KitGroupNote(stringResource(Res.string.configInspectionExplanation))
            when (state) {
                ServerConfigurationUi.Loading ->
                    KitStateView(kind = KitStateKind.Loading, title = title, size = KitStateSize.Inline)
                ServerConfigurationUi.Unavailable ->
                    KitStateView(
                        kind = KitStateKind.Error,
                        title = stringResource(Res.string.configInspectionUnavailable),
                        primary =
                            KitAction(stringResource(Res.string.commonRetry), actions::refresh),
                        size = KitStateSize.Inline,
                    )
                is ServerConfigurationUi.Ready -> {
                    KitRowGroup(label = stringResource(Res.string.configFilesLabel)) {
                        if (state.files.isEmpty())
                            KitRow(
                                title = stringResource(Res.string.configNoFileTitle),
                                modifier = Modifier.testTag("server-configuration-no-file"),
                                supporting = stringResource(Res.string.configNoFileBody),
                                supportingMaxLines = 4,
                            )
                        for (path in state.files)
                            KitRow(
                                title = KitBidi.ltr(path.substringAfterLast('/').ifBlank { path }),
                                modifier = Modifier.testTag("server-configuration-file"),
                                supporting = KitBidi.ltr(path),
                                supportingMaxLines = 3,
                            )
                    }
                    if (state.folders.isNotEmpty())
                        KitRowGroup(label = stringResource(Res.string.configFoldersLabel)) {
                            for (path in state.folders)
                                KitRow(title = KitBidi.ltr(path), titleMaxLines = 3)
                        }
                    KitDetailsFold(
                        label = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        modifier =
                            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                                .testTag("server-configuration-masked"),
                        notes = listOf(KitRedact.text(state.text)),
                    )
                }
            }
        }
    }
}
